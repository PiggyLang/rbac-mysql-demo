# 阶段 1 验收记录

日期：2026-10-07（Asia/Shanghai）

## 实际工具版本

| 组件 | 实测版本 / 状态 |
| --- | --- |
| Java（项目构建） | OpenJDK 17.0.20.1，Homebrew |
| Maven | 3.9.16 |
| Spring Boot | 3.5.15 |
| Spring Security Crypto | 6.5.11 |
| MySQL Connector/J | 9.7.0 |
| Docker Compose | v5.5.1 |
| MySQL 容器 | 8.4.11，镜像 `mysql:8.4` |

系统默认 `java` 为 Oracle Java 8；项目脚本显式选择 Java 17。Maven 本地依赖缓存位于忽略目录 `target/m2`。`.env` 权限为 `600`，实际凭据未写入本记录或日志。

## 构建与测试

执行：

```sh
./scripts/mvn-java17.sh test
./scripts/mvn-java17.sh package
```

最终实现上两个命令均成功。每次均运行 4 项测试，`Failures: 0, Errors: 0, Skipped: 0`。其中数据库集成测试使用实际 MySQL 连接，覆盖 BCrypt `matches`、admin/reader 权限差异、独立菜单关联，以及 HTTP 检查接口的实际七表计数和敏感字段排除。

TDD 记录：在接口测试已编写后临时去掉 `/api/db-check` 控制器路由，`DbCheckEndpointTest` 以 `Status expected:<200> but was:<404>` 失败；恢复路由后测试通过。最早一次测试框架启动因缺少 Spring Boot 配置类而失败，那次只是测试搭建错误，不计为行为 RED。

## MySQL / SQL 验证

执行 `docker compose --env-file .env up -d --wait --wait-timeout 180` 后，容器报告 Healthy。MySQL 返回版本 `8.4.11`，数据库 `rbac_mysql_demo` 和用户 `piggy` 可由 Java 应用实际连接。

执行 `db/verification.sql` 中的 JOIN 查询，实际结果为：

- `admin` 关联 `user:read`、`user:create`、`user:delete`；`reader` 仅关联 `user:read`。
- 菜单通过独立 `role_menu` 查询：`admin` 关联 `users`、`reports`；`reader` 关联 `reports`。
- 七表行数依次为：`user=2`、`role=2`、`permission=3`、`menu=2`、`user_role=2`、`role_permission=4`、`role_menu=3`。

在事务中尝试重复 `user_role` 复合键，MySQL 返回预期错误 `1062 Duplicate entry ... for key 'user_role.PRIMARY'`；尝试不存在的用户外键，返回预期错误 `1452 ... FOREIGN KEY ...`。两条语句之后各自执行 `ROLLBACK`，连接断开后再次检查，原有行数仍为 `2/2/3/2/2/4/3`。

对 `admin` 角色名称做了一次临时自定义修改，再次执行 `db/seed.sql`。重播种前后七表行数相同，角色名称仍保留自定义值；随后已把本次临时改动还原为 `Administrator`。这证明脚本不会更新已有业务字段。若有人删除基线关联后重跑，seed 会补回缺失的演示关系；详情见 README。

种子脚本中的两条 `password_hash` 均由 `BCryptPasswordEncoder` 实际生成。真实库集成测试分别以明文演示密码调用 `matches` 并成功，库中没有保存明文。

## HTTP 验证

应用真实启动于 `127.0.0.1:8082`，PID 与启动日志分别保存在忽略目录 `target/app.pid` 和 `target/app.log`。请求：

```sh
curl --fail --silent --show-error http://127.0.0.1:8082/api/db-check
```

实际响应：

```json
{"selectOne":1,"tables":{"user":2,"role":2,"permission":3,"menu":2,"user_role":2,"role_permission":4,"role_menu":3}}
```

响应只包含 `SELECT 1` 和七表计数，不含账户名称、密码哈希或用户实体字段。系统 nginx 占用 `8080` 与 `8081`；未改动该服务，项目使用检查确认空闲的 `8082`。

## Navicat 人工验证

用户已在 Navicat Premium Lite 亲自验证 `db/verification.sql` 的用户→角色→权限 JOIN 和用户→角色→菜单 JOIN 结果。用户还确认重复 `user_role` 复合主键返回 MySQL 1062 并执行 `ROLLBACK`（有截图），以及不存在用户外键目标返回 1452 并执行 `ROLLBACK`。截图由用户确认存在，未保存在仓库。连接参数为主机 `127.0.0.1`、端口 `3307`、数据库 `rbac_mysql_demo`、用户 `piggy`；密码只在本机 `.env` 中查看。

## 本机凭据更新（2026-10-07）

按本机维护要求同步更新 `.env` 中的 MySQL 应用密码、root 密码与 Spring 数据源密码，并将现有 `piggy` 与 `root` 账户凭据同步。`.env` 权限保持为 `600`；未将凭据写入本文档或应用日志。仅重建专用 MySQL 容器，复用 `rbac_mysql_demo_data` 命名卷。两个账户均通过 `SELECT 1` 验证，容器状态为 Healthy，HTTP `/api/db-check` 返回 200；七表行数仍为 `2/2/3/2/2/4/3`。

## 演示用户名更新（2026-10-07）

本节之后的当前用户名为 `alice`（Alice）和 `bob`（Bob）；上文原验收结果记录的是改名之前的 `admin` 与 `reader` 用户名。现将用户 `id=1` 从 `admin` 改为 `alice`，将用户 `id=2` 从 `reader` 改为 `bob`，并分别将显示名更新为 Alice、Bob。用户 ID、`user_role` 关系、角色 code（`admin`、`reader`）、权限与菜单关联均保持不变；MySQL 连接账号 `piggy` 未变。当前 JOIN 核验结果为 `alice → admin`、`bob → reader`，对应权限仍为 Alice 三项、Bob 仅 `user:read`，菜单仍为 Alice 的 `users`、`reports` 与 Bob 的 `reports`。七表行数仍为 `2/2/3/2/2/4/3`。更新后的 `db/seed.sql` 已重播一次成功；未再出现旧用户名对应的用户。`./scripts/mvn-java17.sh test` 最终通过 4 项（0 failures、0 errors、0 skipped），包括两名演示用户的 BCrypt `matches`。重命名前的非提权测试尝试受本机 Java agent 附加限制而未启动测试；以允许本地 MySQL 和 agent 附加的方式重跑后，alice/bob 断言按预期失败，完成数据库更新后全量测试通过。当前运行中的应用请求 `/api/db-check` 返回 HTTP 200，响应计数仍为 `2/2/3/2/2/4/3`。
