# RBAC + MySQL 阶段 1 设计说明

日期：2026-10-07
状态：设计稿，尚未实施

## 目标与边界

阶段 1 建立可运行的 Java/MySQL 学习项目骨架，创建 RBAC 核心七表、少量演示数据，提供数据库连通性只读检查和可手动执行的 SQL 验证。重点学习多对多关系、关系约束与范式，以及菜单数据和接口权限的区别。

本阶段不实现登录、授权或菜单查询接口，不引入事务业务逻辑、索引优化、库存等后续内容。唯一演示端点建议为只读 `GET /api/db-check`，返回 `SELECT 1` 结果和非敏感计数，不返回用户信息、密码哈希或完整用户实体。

## 技术选择

- Java 17；Maven；Spring Boot 3.5 系列；使用 JDBC 直接学习 SQL，不使用 ORM。
- 实施时核对可获取的 Spring Boot 补丁版，并使用 Spring Boot 依赖管理，不在多个位置重复固定受管理依赖版本。
- 本机 Java 17 位于 `/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home`；机器默认 `java` 为 Java 8。项目启动命令显式设置 `JAVA_HOME`（并让 `PATH` 优先使用其 `bin`），不修改全局 Java 配置。
- Docker Compose 使用 MySQL 8.4。为本项目设置独立 service/container、专用 named volume 和 `127.0.0.1:3307:3306` 端口映射。
- 数据库名 `rbac_mysql_demo`，数据库连接用户名 `piggy`，应用不使用 root 连接。连接凭据由本机忽略的 `.env` 提供；`.env.example` 只给出变量名称与占位说明，不能放入或提交真实密码。
- `Navicat Premium Lite` 通过 `127.0.0.1:3307` 连接 `rbac_mysql_demo`，使用数据库连接用户名 `piggy`；README 说明如何查看七张表、浏览演示数据和执行验证 SQL。
- 使用 `spring-security-crypto` 的 BCrypt 能力生成演示账户的真实 `password_hash`；无需引入完整 Spring Security starter。禁止保存明文密码。

## 数据模型

七张表：`user`、`role`、`permission`、`menu`、`user_role`、`role_permission`、`role_menu`。`user` 在 SQL 中必须使用反引号引用。`username`、`role.code`、`permission.code` 等业务标识设置唯一约束。三张关联表使用复合主键，并以外键引用两端实体，阻止重复关系和孤立关系；不要用可重复插入的无约束关联行代替。

种子数据包括 `admin` 与 `reader` 角色、少量演示用户，以及 `user:read`、`user:create`、`user:delete` 权限。`admin` 拥有全部三项权限，`reader` 仅拥有 `user:read`。菜单通过 `role_menu` 单独关联角色；菜单项不是 API 权限，也不能据此推断服务端已实施授权。

用户表保留 `password_hash` 字段，并只存储 BCrypt 哈希。演示用户的哈希应由 BCrypt 实际生成，不能用明文、伪造示例值或把哈希写入客户端响应。

## 初始化与安全约束

数据库初始化 SQL 与 Java 代码分离，放入 `db/`。禁止脚本自动 `DROP` 表、删除数据或清库。脚本若无法在保留用户修改的同时可靠地重复初始化，必须明确定位为“仅用于空库的首次初始化”，并说明再次运行会如何处理；不得宣称可重复初始化。若实现可重复执行，也不得覆盖用户后续修改，并应为唯一键冲突、外键顺序及部分初始化失败定义清楚行为。

Compose 使用专用 named volume。项目 `.gitignore` 忽略 `.env`，仓库仅提供无秘密的 `.env.example`。README 指导用户复制示例文件、在本机填写自己的凭据，并明确真实密码不应提交、粘贴进示例或写入 SQL/日志。

## 预期交付结构

```text
pom.xml
compose.yaml
.env.example
.gitignore
README.md
db/
  schema.sql
  seed.sql
  verification.sql
src/main/java/com/example/rbacdemo/...
src/main/resources/application.yml
docs/stage-1-verification.md
```

以上为预期结构；阶段 1 设计任务本身只交付本说明，不创建这些实施文件。

## 手动验证计划

1. 记录实际使用的 Java、Maven、Spring Boot 和 MySQL 版本；执行 `mvn test` 与 `mvn package`，只在真实运行后记录结果。
2. 按 README 启动 Compose，确认 MySQL 健康、端口可连接；以应用账户从 Java 应用连入实际 MySQL，并访问 `/api/db-check`。
3. 使用 `Navicat Premium Lite` 按 README 提供的主机、端口、数据库名 `rbac_mysql_demo` 及连接用户名 `piggy` 连接，浏览表和演示数据。
4. 在 `verification.sql` 展示用户→角色→权限、用户→角色→菜单的关联查询；验证重复关联被复合主键拒绝，并验证不存在的外键目标被外键拒绝。
5. 失败约束实验使用事务并回滚，避免留下演示残留。SQL 与约束验证必须针对实际 MySQL 8.4 执行，不能用 H2 代替。
6. 在 `docs/stage-1-verification.md` 记录真实命令、版本、连库结果与 SQL 证据；未实际执行的步骤标记为未验证，不能写成通过。

## 操作说明与风险

README 应提供 Docker Compose 启停步骤、空库首次初始化顺序、Java 17 项目启动命令、Navicat Lite 连接参数和手动 SQL 操作说明。数据库连接失败时检查 `127.0.0.1:3307` 是否被占用、容器是否健康、Java 进程是否收到 `.env` 中的变量；Compose 的 `.env` 插值不应被误认为会自动注入 Java 进程。Navicat 应使用数据库连接用户名 `piggy` 而非 root。演示连接只绑定本机回环地址，凭据不得进入版本控制。
