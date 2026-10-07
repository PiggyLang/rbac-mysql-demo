# 阶段 1 实施进度

更新时间：2026-10-07 16:02 Asia/Shanghai

## 演示用户名更新（2026-10-07）

当前演示用户名为 alice（Alice）和 bob（Bob），分别拥有 admin、reader 角色。只更新了原用户 id=1、id=2 的 username 与 display_name；角色、权限、菜单关联及七表数量未变。早期进度条目中的 admin/reader 用户名描述属于改名之前的记录。

更新后的 seed 已重播成功，没有新建旧用户名记录。数据库 JOIN、BCrypt 全量集成测试（4 项通过）及运行中接口 HTTP 200 均已复核；七表计数保持 `2/2/3/2/2/4/3`。

## 已完成

- 已核对阶段设计：Java 17、Spring Boot 3.5.15、JDBC、MySQL 8.4、七表模型、只读检查接口。
- 已确认项目默认 Java 是 8；目标 Java 17 为 17.0.20.1，Maven 为 3.9.16，Docker Compose 为 5.5.1。
- 已用 Java 17 / Maven 创建本地隔离依赖缓存 `target/m2`；首次无提权 Maven 调用被沙箱 DNS 限制，提权后从 Maven Central 下载依赖成功。
- 已创建本地 `.env`，密码分别随机生成，权限为 600；没有输出凭据。
- 已创建 `pom.xml`、七表 schema、可重复补齐的 seed、JOIN 与约束实验 SQL、Compose 配置、接口实现、测试雏形、启动脚本和中文 README。

## 当前验证

- 有效 TDD 记录：临时移除控制器路由注解后，HTTP 合同测试期望 200、实得 404（1 failure）；恢复注解后通过。之后接口测试已调整为真实 MySQL MockMvc 集成，不使用 service mock。
- `./scripts/mvn-java17.sh test` 在实际 MySQL 上通过 4 项（0 failures、0 errors）；`./scripts/mvn-java17.sh package` 通过。
- Compose `mysql:8.4` 已拉取，服务健康；实际服务版本和账号已核验。
- 实际 SQL JOIN 返回 admin 的三项权限、reader 的 read 权限；独立菜单 JOIN 返回 admin 两个菜单、reader 的 reports 菜单。约束语句分别返回 MySQL 1062 和 1452。种子重复执行前后记录数保持 2/2/3/2/2/4/3，并且临时改名的 admin 角色字段未被覆盖；已恢复原始名称。
- BCrypt `matches` 两个用户的学习密码均通过集成测试。
- 应用正运行于 `127.0.0.1:8082`，PID 记录在 `target/app.pid`，日志在 `target/app.log`；真实 curl 返回 `SELECT 1` 成功和 7 张表计数，响应无敏感字段。

## 人工验收

用户已在 Navicat Premium Lite 亲自验证用户→角色→权限和用户→角色→菜单 JOIN 结果，并确认重复复合主键返回 MySQL 1062 且回滚（有截图）、不存在外键目标返回 1452 且回滚。截图由用户确认存在，未保存在仓库。详细步骤和预期见 `docs/stage-1/README.md` 与 `docs/stage-1-verification.md`。

## 注意事项

- `8080` 和 `8081` 已被系统中另一个服务占用；本应用使用 `127.0.0.1:8082`。
- `.env` 数据卷只在首次空数据目录初始化时由镜像执行 init SQL，重启不会重新播种。
- 应用停服时可在运行会话按 Ctrl-C；MySQL 容器可用 `docker compose stop` 停止并保留 named volume。
