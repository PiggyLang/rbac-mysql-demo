# 阶段二：表单登录与 Session 认证

## 目标与边界

本阶段基于 Spring Security 6.5 与 MySQL 用户表实现认证：表单提交用户名和密码，服务端用 BCrypt 校验数据库中的哈希，并以 HTTP Session 保存登录状态。BCrypt 是单向哈希，登录时通过 `matches(明文, 哈希)` 比较，不能解密哈希。浏览器保存的 `JSESSIONID` 是承载登录会话的凭据，需要妥善保管；CSRF token 用来防止跨站伪造写请求，不代替 Session Cookie，也不是 JWT。

本阶段只回答“请求者是否已登录”，没有根据 `user_role`、`role_permission` 做接口授权。登录用户之间目前没有权限差异；角色、权限和菜单的运行时授权留待后续阶段。用户表的 `enabled=false` 会阻止登录。

演示用户 `alice` 和 `bob` 的密码均为 `960225`，数据库保存各自独立的 BCrypt 哈希。该密码是明确设置的本地教学示例，不可用于真实服务。它与 MySQL 连接账号 `piggy` 的本机 `.env` 密码是两类不同凭据。

本项目没有单独配置 Session 空闲超时；当前默认空闲超时为 30 分钟。CSRF token 没有独立的存活时间，随 Session 一同管理。Cookie 仍在浏览器中不代表服务器端 Session 仍有效。

## 请求流程与代码职责

所有请求先经过 Spring Security 过滤链。CSRF 过滤器对 `GET` 等安全方法跳过校验，对 `POST` 等写请求检查 token；`permitAll` 只表示可以匿名访问，不会跳过 CSRF 校验。`/login` 由表单认证过滤器处理，`/logout` 由 `LogoutFilter` 处理，两者都不是 Controller 方法。

Spring Security 框架负责过滤链、Session 认证上下文、CSRF 校验、会话认证策略、认证提供者和异常处理。项目代码由三个小部分组成：`config/SecurityConfig.java` 配置规则和 JSON 回调；`user/JdbcUserDetailsService.java` 用参数化 JDBC 查询用户、包装 `UserDetails`；`auth/AuthController.java` 提供 CSRF token 与当前用户摘要接口。角色权限的运行时加载和授权不在本阶段。

## HTTP 接口

| 方法与路径 | 认证 | 成功行为 |
| --- | --- | --- |
| `GET /api/csrf` | 公开 | 返回 `headerName`、`parameterName`、`token`，并建立/初始化 CSRF token 与 Session |
| `POST /login` | 公开，表单 CSRF 必需 | `application/x-www-form-urlencoded` 的 `username`、`password`；成功返回仅含用户名的 JSON 摘要 |
| `GET /api/me` | 需要登录 | 返回当前用户名 |
| `GET /api/db-check` | 需要登录 | 返回 `SELECT 1` 与七张表的行数 |
| `POST /logout` | 有效 CSRF token 必需 | 清除当前会话并使其失效；未登录会话也可退出 |

CSRF 默认保持启用。认证失败对不存在用户和错误密码使用相同的 HTTP 401 JSON；未认证访问受保护 API 返回 HTTP 401 JSON，不重定向到 HTML 登录页。缺失或错误的 CSRF token 返回 HTTP 403，不能把 CSRF 失败当作密码错误。登录和退出都会清理旧 CSRF token，所以每次登录或退出后都应重新调用 `GET /api/csrf`，并使用该响应对应的 Session Cookie 与 token。

## Postman 手动操作

1. 请求 `GET http://127.0.0.1:8082/api/csrf`。复制响应中的 `headerName` 与 `token`；Postman Cookie Jar 会自动保存 `JSESSIONID`。
2. 请求 `POST http://127.0.0.1:8082/login`，Body 选择 `x-www-form-urlencoded`，填写 `username=alice`、`password=960225`。在 Headers 中把 `headerName` 的值设为上一步的 `token`。成功为 HTTP 200，响应只包含 `username`。Postman 应保留登录响应的新 Session Cookie。
3. 登录成功后重新请求 `GET /api/csrf`，获得当前 Session 对应的新 token。随后请求 `GET /api/me`，应返回当前用户名；`GET /api/db-check` 应返回七表行数。
4. 用新的 CSRF header/token 请求 `POST http://127.0.0.1:8082/logout`。成功为 HTTP 200；之后 `/api/me` 返回 401。退出后再次 GET `/api/csrf` 才能执行下一次登录。Spring Security 默认 logout filter 会处理带有效 CSRF token 的退出请求，即使当前会话未登录。
5. 可分别使用错误密码与不存在的用户名验证两个登录响应一致为 401。省略 CSRF header 的 `POST /login` 或 `POST /logout` 返回 403。

状态码观察：

| 场景 | 预期状态 |
| --- | --- |
| 公开获取 CSRF token | 200 |
| 登录成功 | 200 |
| 用户名不存在或密码错误 | 401，相同 JSON |
| 未登录访问 `/api/me` 或 `/api/db-check` | 401 JSON，无重定向 |
| 写请求缺少/携带无效 CSRF token | 403 JSON |
| 有效 CSRF token 退出（已登录或匿名会话） | 200，当前 Session 清除 |
| 退出后访问 `/api/me` | 401 |

## 历史自动化验证

此前记录的真实 MySQL `mvn test package` 结果为 6 项测试、0 失败、0 错误。测试包括 alice/bob BCrypt 哈希匹配与既有 RBAC/菜单关系、数据库检查接口认证、缺少 CSRF 返回 403、登录后 Session ID 轮换、旧 token 被拒绝、logout 清除 Session、匿名 `/api/me` 为 401，以及不存在用户和错误密码使用一致错误响应。测试源位于本机忽略的 `src/test/`，不会提交到仓库。本次文档更新未重新运行测试。

此前本地 CookieJar HTTP 验收还观察到 alice、bob 登录成功、未知用户名与错误密码同为 401、旧 CSRF token 被拒绝、匿名会话携有效 token 可以退出。以下为独立的用户手动验证记录；这些条目只描述用户此次操作，不改变上面的历史测试记录。

## 用户手动验证（2026-10-09）

用户确认的核心流程为：未登录 `GET /api/me` 返回 401；`GET /api/csrf` 返回 200 并建立 Cookie；缺失 CSRF token 的 `POST /login` 返回 403；携有效 token 使用错误密码登录返回 401；另一个正确密码流程中，alice 使用 `960225` 登录返回 200，Session ID 从示例值 `001` 轮换到 `002`，之后 `GET /api/me` 返回 200 和用户名 alice；重新获取 CSRF token `BBB` 后退出返回 200，退出后 `/api/me` 返回 401。

用户此次没有手动验证 bob 登录、未知用户名登录或旧 token 写请求；这些行为见上面的历史自动化与 CookieJar 验收记录。之前另一次 IDEA 运行配置未提供数据源密码，登录失败回调也会把数据库查询异常映射成同样的 `401 invalid_credentials`。该次数据库异常与用户之后独立的错误密码手验不能混作同一次证据；不能仅凭 401 断定密码错误，还应结合账号状态、输入和服务端异常排查。

IDEA 不会自动读取项目 `.env`。使用 IDEA 时，在 Run | Edit Configurations | Environment variables 中设置 `SPRING_DATASOURCE_PASSWORD=960225`，然后重启应用；每次新登录前先重新获取 CSRF token。此处 `960225` 在本机示例中既用作演示用户密码，也用作 MySQL 应用账号 `piggy` 的连接密码，但它们用途不同。连接密码配置缺失会让用户查询异常也走统一认证失败回调。

真实数据库仅更新了 alice、bob 两行的 `password_hash`；`id`、`display_name`、`enabled` 均保持原值，七表计数仍为 `2/2/3/2/2/4/3`。演示密码 `960225` 与各自 BCrypt 哈希匹配。
