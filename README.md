# RBAC 与 MySQL 学习项目（阶段 1）

这个项目用 Java 17、Spring Boot 3.5.15 和 JDBC 连接 MySQL 8.4，展示 RBAC 的七张核心表、关联表约束和几条手动 JOIN。当前唯一 HTTP 接口是只读 `GET /api/db-check`；项目尚未实现登录、服务端授权或菜单查询接口。菜单只表示导航数据，不能代替 API 权限检查。

## 环境和首次启动

需要 Docker Desktop、Docker Compose、Maven 3.9+ 和本机 Java 17。此电脑默认 `java` 是 Java 8，项目命令必须显式选择 Java 17；项目不会改动全局 Java 设置。

1. 在全新环境中，仅当 `.env` 不存在时，将 `.env.example` 复制为 `.env`，为 `MYSQL_PASSWORD` 和 `MYSQL_ROOT_PASSWORD` 填入不同的本地随机密码；`SPRING_DATASOURCE_PASSWORD` 必须与 `MYSQL_PASSWORD` 一致。当前工作区已经生成本地 `.env`，不要覆盖它。`.env` 已被忽略且权限应保持为仅本人可读写，真实密码不要贴入聊天、SQL 文件或日志。
2. 启动 MySQL：

   ```sh
   docker compose --env-file .env up -d
   docker compose ps
   ```

   容器使用独立的 `rbac_mysql_demo_data` named volume，只把 `127.0.0.1:3307` 映射到容器的 `3306`。首次创建空数据卷时，镜像按顺序运行 `db/schema.sql` 和 `db/seed.sql`。MySQL 镜像只在初始化空数据目录时自动执行 `/docker-entrypoint-initdb.d`；容器重启不会重跑这些 SQL。

3. 用项目脚本安全读取本地 `.env`，在 Java 17 下构建并运行测试：

   ```sh
   ./scripts/mvn-java17.sh test
   ./scripts/mvn-java17.sh package
   ```

   项目 Maven 依赖缓存隔离在 `target/m2`。如需手动构建并指定该目录，可在 Maven 命令上加 `-Dmaven.repo.local=target/m2`。

4. 启动 Java 应用：

   ```sh
   ./scripts/run-app.sh
   ```

   Compose 会读取 `.env`，但 Java 进程不会自动继承 Compose 的环境变量。启动脚本只解析允许的变量名和值字符，不使用 `source .env`，并将连接凭据直接传给当前 Java 进程。应用绑定 `127.0.0.1:8082`（本机的 8080 和 8081 已被其他服务使用）；访问 <http://127.0.0.1:8082/api/db-check> 可查看 `SELECT 1` 结果和七张表的行数。响应不含用户名、密码哈希或实体数据。

演示用户 `alice`（Alice）拥有 `admin` 角色，`bob`（Bob）拥有 `reader` 角色；两者的 SQL 哈希均由 BCrypt 实际生成，学习用演示密码都是 `learn-only-demo`。这只供本地学习，不可用于真实服务。已初始化的数据卷会保留 MySQL 中的用户密码；直接修改 `.env` 中的 `MYSQL_PASSWORD` 不会自动修改该数据卷里 `piggy` 的密码，需同步执行 MySQL 的用户密码变更，再更新 `SPRING_DATASOURCE_PASSWORD`。

Maven 测试以 alice、bob 两名演示用户及其初始关系作为真实库基线，并检查固定的七表行数。若你之后修改角色或权限关联、增加用户或菜单等数据，测试断言可能与新数据不一致；这表示需要按你的新学习数据调整测试预期，不一定表示应用连库故障。

## Navicat Premium Lite

创建 MySQL 连接并填写：

| 字段 | 值 |
| --- | --- |
| 主机 | `127.0.0.1` |
| 端口 | `3307` |
| 用户名 | `piggy` |
| 密码 | 本机 `.env` 中的 `MYSQL_PASSWORD` |
| 默认数据库 | `rbac_mysql_demo` |

应用也以 `piggy` 连接。MySQL 初始化容器时根据 `.env` 创建该专用账号；MySQL `root` 仅用于首次数据库初始化或管理，不作为应用账号。连接成功后可在 Navicat 中展开七张表，查看用户、角色、权限、菜单及三张关联表。

## 手动执行与重复初始化

在 Navicat 连接 `rbac_mysql_demo` 后，可打开并执行 `db/verification.sql`。其中先展示用户→角色→权限和用户→角色→菜单查询，再给出重复关系和不存在外键目标的失败语句。两个失败实验各自包在事务中，失败后执行 `ROLLBACK`，不留下演示数据。预期分别是 MySQL 错误 1062（复合主键重复）和 1452（外键目标不存在）。如果客户端遇到第一条错误后停止执行，请逐段选中运行，并确认每次失败后执行回滚。

SQL 初始化可重复执行但不清空数据库：`schema.sql` 只创建缺失表；`seed.sql` 按 `username`、`code` 和复合关系查缺补入，不覆盖已经存在的字段。演示用户名为 alice、bob，角色 code 仍为 admin、reader。重复播种会补齐缺失的演示基线关联；若你删掉某个基线关联并重跑，它会被建立回来。因此，修改演示角色权限或关联后，不要再次执行 `seed.sql`。不要通过删除 named volume 来“重置”；若需重新学习空库初始化，请先自行备份并另行确认数据处置。

首次初始化脚本随容器启动自动运行，后续执行需在 Navicat 中手动打开 `db/schema.sql` 与 `db/seed.sql`。手动运行 `seed.sql` 前须先运行 `schema.sql`。种子数据事务若因已有不同约束或手工数据而失败，回滚该事务并检查冲突；脚本不会抹除或覆盖现存数据。

## BCrypt 哈希重新生成

如要为自己更换演示密码，可运行 `./scripts/generate-bcrypt-hash.sh`，在终端输入密码；它只打印新哈希。输入时终端会显示字符，因此选择不易被旁人看到的终端环境。不要把密码放入命令参数或日志。该工具使用项目依赖 `spring-security-crypto` 的 `BCryptPasswordEncoder`，项目不引入完整 Spring Security starter。

## 关闭

停止应用可在运行终端按 `Ctrl-C`。停止 MySQL 容器但保留学习数据：

```sh
docker compose stop
```

## 阶段 1 验收记录

阶段目标、七表模型、演示用户差异及 Navicat 手工验证步骤见 [docs/stage-1/README.md](docs/stage-1/README.md)。真实运行的命令、依赖版本和验收记录见 [docs/stage-1-verification.md](docs/stage-1-verification.md)，实施进度见 [docs/stage-1-progress.md](docs/stage-1-progress.md)。
