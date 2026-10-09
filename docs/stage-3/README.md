# 阶段三：按权限码控制用户 API

## 本步范围

登录时，`JdbcUserDetailsService` 从 `user` 表读取账号，再通过 `user_role`、`role_permission` 和 `permission` 加载去重并排序的权限码。权限码以原始 `GrantedAuthority` 字符串放入登录时的 `SecurityContext`，例如 alice 有 `user:read`、`user:create`、`user:delete`，bob 只有 `user:read`。

安全规则按 HTTP 方法和路径检查权限，并位于通用的“已认证即可访问”规则之前：

| 请求 | 所需权限 | 行为 |
| --- | --- | --- |
| `GET /api/users` | `user:read` | 按 `id` 顺序返回仅含用户名的 JSON 数组，不查询或返回密码哈希 |
| `POST /api/users` | `user:create` | 返回 `{ "message": "user_create_allowed", "demo": true }`，仅演示授权，不写数据库 |
| `DELETE /api/users/{id}` | `user:delete` | 返回 `{ "message": "user_delete_allowed", "demo": true, "id": 37 }` 一类结果，仅演示授权，不删除数据 |

创建和删除演示不需要请求体，也不会修改任何表。Session/form login、CSRF、未认证时的 JSON 401、权限拒绝时的 JSON 403 保持原有行为。

## 验收矩阵

| 身份 | GET 用户 | POST 创建（有效 CSRF） | DELETE 用户（有效 CSRF） |
| --- | ---: | ---: | ---: |
| alice | 200 | 200 | 200 |
| bob | 200 | 403 | 403 |
| 未登录 | 401 | 401 | 401 |

已登录请求若缺少 CSRF token，POST 和 DELETE 会先被 CSRF 过滤器拒绝并返回 403，即使当前用户有对应权限也一样。bob 携带有效 CSRF token 时的写入 403 则表示权限检查拒绝；排查 403 时应先确认请求包含登录后的有效 token，再判断用户权限。

## Postman 手动步骤

1. 在同一个 Postman cookie jar 中调用 `GET /api/csrf`，保留返回的 Session cookie，并记录响应中的 `headerName` 与 `token`。
2. 调用 `POST /login`，使用 `application/x-www-form-urlencoded`，提交 `username` 和 `password`，并将第一步的 token 放到返回的 header 名称中。Postman 会保存登录后的 Session cookie。
3. 登录成功后再次调用 `GET /api/csrf`，使用新 token；登录会轮换 Session 和 CSRF token。
4. 用同一 cookie jar 调用 `GET /api/users`。分别以 bob、alice 登录，可检查各自都能读取用户名列表。
5. 用登录后的新 token 调用 `POST /api/users` 和 `DELETE /api/users/2`。alice 应收到 200 演示响应；bob 应收到 JSON 403。删除演示不会删除 id 为 2 的记录。

本地忽略的真实 MySQL 集成测试已覆盖以上状态码、返回内容、仅用户名输出、CSRF 和请求前后七张表的行数一致。自动化测试覆盖完整矩阵。手动 Postman 检查中，bob 登录已确认；bob 的 `GET /api/users` 使用有效登录 Session 返回 200 和用户名列表，`POST /api/users` 携带有效 CSRF token 返回 403。用户跳过了 bob 的 DELETE 请求，以及 alice 的所有接口手动检查；这些结果仅由自动化测试覆盖。权限查询调试时已确认 alice 加载三项权限；bob 的权限查询调试已跳过。

## 权限快照与重启

权限在登录时查询，并保存在当次 Session 的 `SecurityContext` 快照中。修改用户角色或权限关系后，需要退出并重新登录，才能让新会话加载最新权限。代码或安全配置变更需要重启正在运行的应用；然后重新登录以创建使用新配置和权限快照的 Session。现有 IDEA 运行会话不会自动载入这些代码变更。
