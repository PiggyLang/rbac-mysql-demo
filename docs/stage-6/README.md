# 阶段六：MySQL 索引与执行计划

本阶段围绕独立实验表 `order_lab` 学习索引、执行计划、覆盖索引、索引条件下推和分页代价。以下记录区分用户在 MySQL/Navicat 中实际操作的结果、口头讲解的概念，以及明确略过的练习；讲解内容不代表已经验证。

## 实验数据与基线

实验表 `order_lab` 与 RBAC 业务表、阶段五的 `account` 表分开。脚本 [`db/index-lab.sql`](../../db/index-lab.sql) 创建主键为 `id` 的 InnoDB 表并按缺失 id 补齐 1 至 50,000 行。除主键外，字段为 `user_id`、`status`、`created_at`、`amount`；脚本中的二级索引语句保持注释。脚本幂等，不更新或删除已有 id 的行。

种子值由 id 确定，不用随机数：

- `id` 为 1 至 50,000；`user_id = MOD(id * 7919 + 17, 1000) + 1`，覆盖 1 至 1,000。
- 每连续十个 id 中有六个 `PAID`、两个 `PENDING`、两个 `CANCELLED`，总数分别为 30,000、10,000、10,000。
- `created_at` 从 `2025-07-01 00:00:00` 起，按 `MOD((id - 1) * 37, 365)` 天和 `MOD(id * 17, 1440)` 分钟确定，覆盖约一年。
- `amount = 10.00 + MOD(id * 7919, 99001) / 100.00`，保留两位小数。

首次确认目标库没有 `order_lab` 后执行脚本，得到无二级索引的基线。随后脚本曾重跑以检查幂等性；重跑只补缺失 id，不会删除已有索引，也不会重新构造无索引基线。之后用户在 Navicat 手动创建了 `idx_order_lab_status_created_at(status, created_at)`。因此，有索引结果是后续手动实验，不能描述为本次重新运行 SQL 得到的结果。

此前本地核验的数据为 50,000 行，id 1 至 50,000；生成时间从 `2025-07-01 00:07:00` 到 `2026-06-30 23:46:00`，金额范围 `10.01` 至 `999.99`，1,000 个用户各有 50 笔订单。状态分布为 `PAID=30000`、`PENDING=10000`、`CANCELLED=10000`。按月行数如下：

| 月份 | 行数 | 月份 | 行数 |
| --- | ---: | --- | ---: |
| 2025-07 | 4247 | 2026-01 | 4247 |
| 2025-08 | 4247 | 2026-02 | 3835 |
| 2025-09 | 4110 | 2026-03 | 4246 |
| 2025-10 | 4247 | 2026-04 | 4109 |
| 2025-11 | 4110 | 2026-05 | 4246 |
| 2025-12 | 4246 | 2026-06 | 4110 |

典型基线查询及实测计划：

```sql
SELECT id, user_id, status, created_at, amount
FROM order_lab
WHERE status = 'PAID'
  AND created_at >= '2026-01-01'
  AND created_at < '2026-02-01';
```

匹配行数为 2,547。首次传统 EXPLAIN 为 `type=ALL`、`key=NULL`、估算 `rows=50000`、`filtered=1.11`、`Extra=Using where`；后续脚本幂等重跑时观察到 `rows=49996`，其他字段相同。轻微估算差异不代表实际扫描行数变化。`rows` 是优化器估算，不能当作实际读取行数；本阶段没有对该基线查询计时。

## 用户实测：RBAC 表索引

- `user` 表的唯一索引 `uq_user_username`：`WHERE username='alice'` 查询 `username/password_hash/enabled` 时，计划为 `type=const`、`key=uq_user_username`、`key_len=258`、`rows=1`、`ref=const`、`Extra=NULL`。只选 `id,username` 时 `Extra=Using index`，说明该次查询可由索引覆盖。这里的 `rows=1` 是样本估算，唯一性来自唯一约束，并非这个估算值的保证。
- `user_role` 主键为 `(user_id,role_id)`。仅 `user_id=1` 时计划为 `type=ref`、`key=PRIMARY`、`key_len=8`；`user_id` 和 `role_id` 双等值时为 `type=const`、`key_len=16`。仅 `role_id=1` 时使用 `fk_user_role_role`，计划为 `type=ref`、`key_len=8`、`Extra=Using index`。

## 用户实测：联合索引与过滤

表定义中 `status` 为非空 `VARCHAR(20)`、字符集 `utf8mb4`，最大键部分为 80 字节，加长度字节后 `key_len=82`；`created_at` 为 `DATETIME`，占 5 字节。因此联合索引计划显示最大 `key_len=87`。它表示优化器使用的键部分长度，不是每行实际占用空间。

| 查询与投影 | 用户观察到的计划 |
| --- | --- |
| `status='PAID'` 加一月半开日期范围，选择全部五列 | 基线：`ALL`，`key=NULL`，估算 `rows=49996`、`filtered=1.11`、`Using where`。手动建联合索引后：`range`，`key=idx_order_lab_status_created_at`，`key_len=87`，估算 `rows=2547`、`filtered=100`、`Using index condition`。 |
| 同一条件，仅选择 `id,status,created_at` | `range`，联合索引，`key_len=87`，估算 `rows=2547`、`filtered=100`，`Using where; Using index`。 |
| 仅日期范围，仅选择 `id,status,created_at` | `range`，联合索引，`key_len=87`，估算 `rows=5554`，`Using where; Using index for skip scan`。 |
| 仅日期范围，选择全部五列 | `ALL`，`key=NULL`，估算 `rows=49996`、`filtered=11.11`，`Using where`。 |
| 仅 `status='PAID'`，选择全部五列 | `ref`，联合索引，`key_len=82`，估算 `rows=24998`、`filtered=100`，`Extra=NULL`。 |
| `status='PAID'` 且 `created_at >= '2026-01-01 00:00:00' AND created_at < '2026-01-02 00:00:00'` | `range`，联合索引，`key_len=87`，估算 `rows=69`，`Using index condition`。 |
| `status='PAID' AND DATE(created_at)='2026-01-01'` | `ref`，联合索引，`key_len=82`，估算 `rows=24998`，`Using index condition`。对列套 `DATE()` 后没有形成日期范围访问；ICP 仍可在存储引擎层检查索引条件。 |

PAID 实际有 30,000 行，而 `rows=24998` 是统计信息下的估算。不要把估算偏差当作实际数据计数，也不要据此声称查询返回 24,998 行。

## 用户实测：排序、覆盖和分页

以下三条均用 `status='PAID'`：

```sql
SELECT id, status, created_at FROM order_lab
WHERE status = 'PAID' ORDER BY created_at LIMIT 10;

SELECT id, status, created_at FROM order_lab
WHERE status = 'PAID' ORDER BY created_at DESC LIMIT 10;

SELECT id, status, created_at, amount FROM order_lab
WHERE status = 'PAID' ORDER BY amount LIMIT 10;
```

升序时间查询为 `ref`、`key_len=82`、估算 `rows=24998`、`Using index`，未见 `Using filesort`。降序时间查询显示 `Backward index scan; Using index`。金额排序查询为 `ref`、`key_len=82`、估算 `rows=24998`、`Using filesort`。同一筛选条件下，索引顺序能满足按时间排序，而金额不在该索引顺序中。

用户还提供了下列 `EXPLAIN ANALYZE` 实际输出摘要。相应 SQL 如下：

```sql
-- A：降序时间，索引覆盖
EXPLAIN ANALYZE
SELECT id, status, created_at FROM order_lab
WHERE status = 'PAID' ORDER BY created_at DESC LIMIT 10;

-- B：金额 Top-N 排序
EXPLAIN ANALYZE
SELECT id, status, created_at, amount FROM order_lab
WHERE status = 'PAID' ORDER BY amount LIMIT 10;

-- C：深分页
EXPLAIN ANALYZE
SELECT id, status, created_at FROM order_lab
WHERE status = 'PAID' ORDER BY created_at DESC, id DESC
LIMIT 20000, 10;
```

| 查询 | 用户提供的执行观测（毫秒） |
| --- | --- |
| A | `Limit: 10 row(s)`：cost `2788`、估算 10 行、actual `3.16..3.21`、10 行、1 loop。子节点 `Covering index lookup`（`status='PAID'`，reverse）：cost `2788`、估算 24,998 行、actual `3.16..3.2`、10 行、1 loop。 |
| B | `Limit: 10 row(s)`：cost `2765`、估算 10 行、actual `44..44`、10 行、1 loop。`Sort: amount, limit input to 10 row(s) per chunk`：cost `2765`、估算 24,998 行、actual `43.1..43.1`、10 行、1 loop。子节点 `Index lookup`（`status='PAID'`）：cost `2765`、估算 24,998 行、actual `3.37..39`、30,000 行、1 loop。 |
| C | `Limit/Offset: 10/20000 row(s)`：cost `2788`、估算 10 行、actual `13.8..13.8`、10 行、1 loop。子节点 `Covering index lookup`（`status='PAID'`，reverse）：cost `2788`、估算 24,998 行、actual `0.71..11.2`、20,010 行、1 loop。 |

`actual=a..b` 中，`a` 和 `b` 分别是该节点从开始执行到首次输出、到执行结束的耗时（毫秒）；多次 loops 时显示平均值。父节点时间包含子节点执行时间，不能逐节点相加。cost 是优化器成本值，没有固定的毫秒单位。以上是单次观测，不能据此推导固定倍数的性能差异。LIMIT 可以在满足结果数后停止，但不表示只访问 10 个数据页，也不等于精确物理读取量；金额 Top-N 的子节点实际输出 30,000 个 PAID 候选行，再从中选出 10 行。深分页需要越过前 20,000 行，观测子节点实际输出 20,010 行。

## 已讲解，尚未手动验证

以下内容在交流中讲解过，但没有作为本阶段用户实测结论：

- InnoDB B+ 树：聚簇索引叶子包含整行，二级索引叶子包含索引列和主键；树的导航、叶子有序及叶子链；B 树、B+ 树与哈希索引的区别。
- 主键宽度、插入位置及索引维护成本；普通索引、唯一索引与联合唯一约束。
- `EXPLAIN` 的 `select_type`（如 `SIMPLE`、派生表）、访问类型 `type` 与 `ref` 字段、`filtered`（筛选通过率，不是覆盖率）。
- 统计估算可能有偏差；`ANALYZE TABLE` 用于更新统计信息，`EXPLAIN ANALYZE` 执行查询并给出实际观测。用户没有运行 `ANALYZE TABLE`。
- `LIKE` 前导 `%`、隐式类型转换、`OR` 与 Index Merge 的影响。是否使用索引取决于语句、索引和优化器选择，不将其概括成索引必然失效。
- 面向“指定用户、指定状态、最近 10 笔”的假设性复合索引设计，例如 `(user_id,status,created_at[,amount])`；没有创建该索引。
- 游标分页的思路；用户跳过了相应手动实验。

## 本阶段明确略过

用户明确要求略过 `IS NULL`、`!=`、范围条件之后的后续列及相关 ICP 延伸讲解。这些主题不记作已掌握或已验证；前文已记录的 ICP 实测结果仍属用户实测。

## 暂缓纪要与后续方向

用户认为后续并发、锁、日志等内容过多，要求先搁置。本阶段目前记录已发生的实验和讲解，不把待学主题记为完成，也没有新增功能或开启后续实验；不同数据规模下的执行计划对比也未做，暂不扩展实验。

并发扣款中的丢失更新只作为概念引入和示例，没有实测。未来可由用户选择是否继续，建议按小步顺序推进：阶段七讨论并发控制、锁与隔离（MVCC 快照读/当前读、行锁、间隙锁、临键锁和死锁）；阶段八讨论 undo、redo、binlog 与恢复；阶段九讨论连接池、事务和连接耗尽、慢 SQL 与锁等待的关系。这些是建议方向，不表示后续阶段已经实施。恢复学习时由用户选择主题和步幅，不自动继续，也不扩展到 Spring 业务改动。

## 参考资料

- [MySQL 8.4 EXPLAIN 输出](https://dev.mysql.com/doc/refman/8.4/en/explain-output.html)
- [MySQL 8.4 EXPLAIN 语句与 EXPLAIN ANALYZE](https://dev.mysql.com/doc/refman/8.4/en/explain.html)
- [MySQL 8.4 范围优化](https://dev.mysql.com/doc/refman/8.4/en/range-optimization.html)
- [MySQL 8.4 索引条件下推](https://dev.mysql.com/doc/refman/8.4/en/index-condition-pushdown-optimization.html)
- [InnoDB 索引类型](https://dev.mysql.com/doc/refman/8.4/en/innodb-index-types.html)
- [MySQL 8.4 类型转换](https://dev.mysql.com/doc/refman/8.4/en/type-conversion.html)
- [MySQL 8.4 Index Merge 优化](https://dev.mysql.com/doc/refman/8.4/en/index-merge-optimization.html)
