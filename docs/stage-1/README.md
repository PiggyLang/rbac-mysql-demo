# 阶段一：项目骨架与 RBAC 表结构验证

## 目标与边界

阶段一建立可本机运行的 Java / Spring Boot / MySQL 学习项目，用真实 MySQL 展示 RBAC 七表、角色权限与菜单关联、约束行为，以及只读数据库连通检查。当前应用只有 `GET /api/db-check`；登录、服务端授权和菜单查询 API 尚未实现。菜单是导航数据，不能代替 API 权限检查。

## 环境与运行基线

| 项目 | 基线或实测值 |
| --- | --- |
| Java | OpenJDK 17.0.20.1 |
| Maven | 3.9.16 |
| Spring Boot | 3.5.15 |
| Spring Security Crypto | 6.5.11 |
| MySQL Connector/J | 9.7.0 |
| Docker Compose | 5.5.1 |
| MySQL | 8.4.11（Compose 镜像 `mysql:8.4`） |
| 数据库 / 应用账号 | `rbac_mysql_demo` / `piggy` |
| 端口 | MySQL `127.0.0.1:3307`；应用 `127.0.0.1:8082` |
| 七表行数 | `2 / 2 / 3 / 2 / 2 / 4 / 3` |

## 七张表与演示数据

七表为 `user`、`role`、`permission`、`menu`、`user_role`、`role_permission` 和 `role_menu`。三张关联表以两列组成复合主键，防止重复分配；外键保证关联目标存在。

当前演示用户为 `alice`（Alice）和 `bob`（Bob）：`alice → admin`，拥有 `user:read`、`user:create`、`user:delete` 三项权限，并关联 `users`、`reports` 两个菜单；`bob → reader`，只有 `user:read` 权限，并关联 `reports` 菜单。菜单关系与权限关系分开查询。演示密码 `learn-only-demo` 仅用于本地学习，不可用于真实服务；数据库中存储 BCrypt 哈希。

## JOIN 与约束验证

`db/verification.sql` 可在 Navicat 中执行。用户到角色再到权限的查询：

```sql
SELECT u.username, r.code AS role_code, p.code AS permission_code
FROM `user` u
JOIN user_role ur ON ur.user_id = u.id
JOIN `role` r ON r.id = ur.role_id
JOIN role_permission rp ON rp.role_id = r.id
JOIN permission p ON p.id = rp.permission_id
ORDER BY u.username, p.code;
```

用户到角色再到菜单的查询：

```sql
SELECT u.username, r.code AS role_code, m.code AS menu_code, m.path
FROM `user` u
JOIN user_role ur ON ur.user_id = u.id
JOIN `role` r ON r.id = ur.role_id
JOIN role_menu rm ON rm.role_id = r.id
JOIN menu m ON m.id = rm.menu_id
ORDER BY u.username, m.sort_order;
```

用户已在 Navicat 确认两条 JOIN 的结果。重复 `user_role` 复合主键返回 MySQL `1062`，向 `user_role.user_id` 插入不存在的用户返回 `1452`；用户确认两次失败后均执行 `ROLLBACK`。客户端若在错误后停止脚本，应分块运行并手动回滚。验证前后七表行数均为 `2 / 2 / 3 / 2 / 2 / 4 / 3`。

## 自动化与接口验收

阶段一实现期间，基于真实 MySQL 的 4 项集成测试及 `mvn package` 均曾通过。测试覆盖 BCrypt 密码匹配、admin/reader 权限差异、独立菜单关系、HTTP 检查接口的七表计数与敏感字段排除。测试源码保留在开发者本机，不纳入仓库。

应用曾在 `127.0.0.1:8082` 实际启动，`GET /api/db-check` 返回 HTTP 200、`SELECT 1` 成功和七表行数 `2 / 2 / 3 / 2 / 2 / 4 / 3`，响应不含账户名称、密码哈希或用户实体字段。种子脚本重播后行数不变，且已有业务字段不会被覆盖；删除的基线关联会在重播时补回。

## 后续阶段

登录认证、服务端权限检查、菜单 API 和其他后续阶段功能尚未实现。阶段一只提供关系模型、示例数据、手动验证 SQL 与数据库连通性检查。
