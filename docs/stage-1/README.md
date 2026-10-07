# 阶段一：项目骨架与 RBAC 表结构验证

## 目标与边界

阶段一建立可本机运行的 Java / Spring Boot / MySQL 学习项目，用真实 MySQL 展示 RBAC 七表、角色权限与菜单关联、约束行为，以及只读数据库连通检查。当前应用只有 `GET /api/db-check`；登录、服务端授权和菜单查询 API 均属于后续工作，尚未实现。菜单是导航数据，不能代替 API 权限检查。

## 环境与运行基线

| 项目 | 当前基线 |
| --- | --- |
| Java | 17（项目脚本选择 Java 17） |
| Spring Boot | 3.5.15 |
| MySQL | 8.4.11（Compose 使用 `mysql:8.4`） |
| 数据库 | `rbac_mysql_demo` |
| MySQL 应用账号 | `piggy`（密码只保存在本机 `.env`） |
| MySQL 映射端口 | `127.0.0.1:3307` → 容器 `3306` |
| 应用地址 | `127.0.0.1:8082` |
| 当前七表行数 | `2 / 2 / 3 / 2 / 2 / 4 / 3` |

版本、构建和 HTTP 验证的详细记录见 [阶段一验收记录](../stage-1-verification.md)。

## 七张表与演示数据

七表为：

1. `user`：用户身份及 BCrypt 密码哈希。
2. `role`：角色，使用唯一 `code` 标识。
3. `permission`：权限，使用唯一 `code` 标识。
4. `menu`：导航菜单。
5. `user_role`：用户与角色关联。
6. `role_permission`：角色与权限关联。
7. `role_menu`：角色与菜单关联。

三张关联表以两列组成复合主键，并使用外键指向实体表。当前演示用户为 `alice`（Alice）和 `bob`（Bob）：`alice → admin`，有 `user:read`、`user:create`、`user:delete` 三项权限，并关联 `users`、`reports` 两个菜单；`bob → reader`，只有 `user:read` 权限，并关联 `reports` 菜单。菜单关系与权限关系分开查询。演示密码 `learn-only-demo` 仅用于本地学习，不可用于真实服务；数据库内存储的是 BCrypt 哈希。

## JOIN 查询

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

用户已在 Navicat 亲自验证两条 JOIN 的结果，以及下列 1062 错误截图和 1452 错误回滚结果。截图由用户确认存在，未作为项目文件保存。

## 约束失败与回滚

在 `user_role` 中重复插入已有的 `(user_id, role_id)`，预期因复合主键返回 MySQL `1062`。在 Navicat 中逐块运行：

```sql
START TRANSACTION;
INSERT INTO user_role (user_id, role_id)
SELECT user_id, role_id FROM user_role LIMIT 1;
-- 预期：ERROR 1062（重复复合主键）
ROLLBACK;
```

向 `user_role.user_id` 插入不存在的用户，预期因外键返回 MySQL `1452`。失败后执行回滚：

```sql
START TRANSACTION;
INSERT INTO user_role (user_id, role_id)
SELECT 18446744073709551615, id FROM `role` WHERE code = 'reader';
-- 预期：ERROR 1452（外键目标不存在）
ROLLBACK;
```

有些 SQL 客户端会在单条语句报错后停止执行脚本。应分块运行；每次失败后确认执行 `ROLLBACK`。用户确认 1062 截图包含失败与回滚，1452 也已确认失败后回滚。库内计数保持 `2 / 2 / 3 / 2 / 2 / 4 / 3`。

## 自动化与人工验收

运行 Maven 测试：

```sh
./scripts/mvn-java17.sh test
```

阶段一验收还记录了 `./scripts/mvn-java17.sh package`、真实 MySQL 连接、`/api/db-check` HTTP 200、七表计数和两个 BCrypt `matches` 检查。自动化结果以仓库当前记录及本次复跑为准。Navicat 图形界面中的 JOIN 和约束结果由用户人工确认；本仓库没有截图文件或截图路径。

## 后续阶段

登录认证、服务端权限检查、菜单 API 和其他后续阶段功能尚未实现。阶段一只提供关系模型、示例数据、手动验证 SQL 与数据库连通性检查。
