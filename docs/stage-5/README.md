# 阶段五第一步：本地账户转账与事务回滚

## 范围

新增独立的 `account` 演示表和 A（id 1）到 B（id 2）的固定转账接口，不改 RBAC 七张表，也不改变原接口的认证和授权行为。事务实验接口单独允许匿名调用和免 CSRF。账户余额使用 `DECIMAL(12, 2)`，转账金额固定为 `100.00`。表使用 InnoDB、账户名唯一约束和非负余额检查。

手动部署时，在 `rbac_mysql_demo` 数据库中单独执行 [`db/transaction-lab.sql`](../../db/transaction-lab.sql)。SQL 只创建不存在的 `account` 表，并仅在对应 id 和账户名都不存在时插入 A、B 各 `1000.00`；重复执行不会重置已有余额。执行前若数据库中已有 `account` 表，应先检查其结构和数据是否符合本实验，不能用本文件覆盖或调整现有表。

## API 与预期

本实验 API 刻意允许匿名访问，POST 无需 CSRF token，方便在本机独立学习事务行为。这个公开规则只针对 `/api/transactions/**`；用户 API 和其他接口仍沿用原认证、权限和 CSRF 规则。实验面向本地学习环境。

| 请求 | 行为 |
| --- | --- |
| `GET /api/transactions/accounts` | 按 id 查询 id、name、balance |
| `POST /api/transactions/transfer` | A 余额充足时扣除 `100.00`，再给 B 增加 `100.00`，成功返回 `200` 和 `{"message":"transferred","amount":100.00}` |
| `POST /api/transactions/transfer-fail` | 扣除 A 后故意抛出运行时异常；事务代理应回滚扣款，返回 `409 {"error":"demo_transfer_failed"}` |
| `POST /api/transactions/transfer-checked-default` | 扣除 A 后抛出受检异常；默认事务规则提交扣款，返回 `409 {"error":"checked_transfer_failed"}` |
| `POST /api/transactions/transfer-checked-rollback` | 相同步骤，但 `rollbackFor` 使扣款回滚；同样返回 `409 {"error":"checked_transfer_failed"}` |

余额不足返回 `409 {"error":"insufficient_balance"}`；实验账户缺失或 id/name 不匹配返回 `409 {"error":"invalid_lab_accounts"}`。原 RBAC 接口（例如 `/api/users`）仍要求认证，匿名 GET 返回 `401`；该接口的 POST 缺少 CSRF token 返回 `403`。失败异常由只针对转账控制器的 advice 映射；服务不会吞掉异常，也不捕获通用 SQL 错误。

服务先校验 A、B 的 id/name 配对，再用带 `balance >= 100.00` 条件的 SQL 扣除 A。扣款必须恰好影响一行，否则返回余额不足或实验账户无效；给 B 入账也必须恰好影响一行，否则抛出运行时异常，让整个转账回滚。金额全程使用 `BigDecimal` 和 MySQL `DECIMAL`，不转换为浮点数。

服务是单独注入的 Spring Bean，事务方法为公开的 `@Transactional` 方法，控制器通过 Spring 代理调用它。`CheckedTransferFailedException extends Exception` 是受检异常，因此 Java 要求调用方捕获或在方法签名中声明 `throws`；`throws` 只满足编译器的异常处理要求，不会自行设置事务回滚。Spring 默认对未捕获的运行时异常回滚，而受检异常默认提交；显式 `rollbackFor = CheckedTransferFailedException.class` 才会令第二个示例回滚。

两个受检异常接口都返回 HTTP 409 和相同的 JSON 错误。状态码本身无法证明事务是否提交，需另行调用 `GET /api/transactions/accounts` 查看：默认规则示例会让 A 减少 `100.00`、B 不变；`rollbackFor` 示例会保持调用前余额。测试没有外层 `@Transactional`，所以验证的是 Spring 代理后的真实 MySQL 提交与回滚。捕获异常并正常返回、或同类自调用绕过代理的情况仍未在本步演示。

## 当前验证与边界

用户提供的截图确认初始账户查询显示 A、B 均为 `1000.00`。用户也已明确手动验证 `transfer-fail`：抛出异常后余额未变。

正常转账的用户手动验证尚未确认。一次较早的数据库查询观察到 A=`900.00`、B=`1100.00`，但这个余额快照本身不能证明由正常转账接口造成。两个受检异常路由也没有手动确认；用户熟悉后跳过了 `transfer-checked-rollback` 的手动检查。自动化测试单独覆盖它们。

自调用绕过代理、隔离级别 RC/RR、MVCC、快照读与当前读目前只作过概念讨论，未实现对应代码实验或验证。阶段六尚未开始，因此本文记录的是阶段五当前已实现和已验证的部分，不代表完整实验计划全部完成。

## 自动化验证

本机忽略的 `src/test/java/com/example/rbacdemo/transaction/TransactionEndpointIntegrationTest.java` 对真实 MySQL 验证匿名转账无需 CSRF、成功提交、运行时异常回滚、受检异常默认提交与 `rollbackFor` 回滚、余额不足，以及原用户 API 仍要求认证和 CSRF。测试先保存 A、B 的真实余额，在 `finally` 中恢复，且没有测试级事务掩盖 Spring 代理行为。测试中的余额重设仅用于建立可重复断言的基准；人工多次调用转账接口会累计改变余额，接口不会自动重置账户。

本机已将此脚本应用到 demo 数据库。执行前账户表不存在，初始播种 A、B 各为 `1000.00`。原有七张 RBAC 表的精确行数执行前后相同：`user=2`、`role=2`、`permission=3`、`menu=2`、`user_role=2`、`role_permission=4`、`role_menu=3`。集成测试每次先保存实际余额，并在 `finally` 中恢复，因此手动转账改变余额后，测试不会将余额重置为初始播种值。

使用 Java 17 执行 `mvn test package`，15 项测试通过（原有 13 项与本阶段 2 项），完整结果见 `target/stage-5-mvn-test-package.log`。最近一次完整测试前后，实际余额都为 A=`800.00`、B=`1100.00`；原有七表行数仍是 `2/2/3/2/2/4/3`。自动化验证证明 MockMvc 请求经过真实 Spring 事务代理并读写本机 MySQL；它不等同于在正在运行的应用上手动操作 Postman。测试各自保存并在 `finally` 中恢复实际余额，不用测试级事务掩盖代理的提交或回滚行为。
