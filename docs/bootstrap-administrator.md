# 首次管理员一次性自举

> 当前生产库状态（2026-08-15）：`young` 管理员已完成自举，并已验证登录、管理员角色、登出和会话失效。
> 不得在当前生产库再次执行本文流程。清空本地 `TUTOR_BOOTSTRAP_PASSWORD`，并保持
> `TUTOR_BOOTSTRAP_ENABLED=false`。

首次部署时还没有已激活管理员，因此不能调用受管理员权限保护的激活码发行接口。项目提供只在
`bootstrap-admin` profile 下运行的一次性离线自举流程。它不开放公共 bootstrap endpoint，成功后立即关闭应用。

## 前置条件

1. 已备份数据库并完成当前全部 Flyway migration（目前为 V1 至 V5）。
2. 数据库尚未存在管理员账户。自举会创建一个不绑定 `student` 的首位管理员账户；如果同名账户已被学员使用，
   或数据库已经存在管理员，自举会失败且回滚。
3. 云服务器防火墙没有开放本次进程的临时监听端口。
4. 准备至少 6 个字符的管理员密码。

这个流程的授权边界是：受控服务器、数据库连接凭据、显式启用的 `bootstrap-admin` profile 和
`TUTOR_BOOTSTRAP_ENABLED=true`。它不是面向公网的第二套认证协议，也不使用一个与本进程配置相互比较的伪门禁码。
请只允许运维人员在应用尚未对外监听时执行。

## PowerShell 执行示例

```powershell
$env:SPRING_PROFILES_ACTIVE='bootstrap-admin'
$env:TUTOR_BOOTSTRAP_ENABLED='true'
$env:TUTOR_BOOTSTRAP_USERNAME='young'
$env:TUTOR_BOOTSTRAP_PASSWORD='<管理员初始密码>'
$env:DATABASE_URL='jdbc:postgresql://127.0.0.1:5432/tutor_base'
$env:DATABASE_USERNAME='<数据库应用账户>'
$env:DATABASE_PASSWORD='<数据库密码>'

.\mvnw.cmd spring-boot:run '-Dspring-boot.run.arguments=--server.address=127.0.0.1 --server.port=0'
```

管理员创建与激活在同一数据库事务内完成；任一步失败都不会留下半激活账户。日志出现
`One-time administrator provisioning completed` 后，进程会自行退出。然后立即关闭当前终端或清除上述
`TUTOR_BOOTSTRAP_*` 环境变量，使用正常的 `production` profile 启动服务。

## 后续管理员

首位管理员自举完成后，系统支持多名管理员并行使用；学员目录提供「认领」归属，避免多人重复处理同一批学员。

新增管理员仍走运维路径，不开放公共注册接口：

1. 由运维在受控数据库连接中插入待激活管理员账户（用户名必须已 trim，规范化列为小写）：

```sql
INSERT INTO public.account (username, username_normalized, role, status)
VALUES ('<管理员用户名>', lower(btrim('<管理员用户名>')), 'administrator', 'pending_activation')
RETURNING id;
```

2. 现有管理员调用 `POST /api/v1/admin/account-activations`（body：`{"accountId": <上一步返回的账户 ID>}`）签发一次性激活码；
   明文激活码只在响应中出现一次，通过可信离线渠道交给新管理员。
3. 新管理员调用 `POST /api/v1/account-activations/complete`（body：`username`、`activationCode`、`password`）完成激活，
   之后按普通管理员登录。

新管理员自动进入「指挥中心」，可以认领未认领学员、释放自己的认领；管理员账户不会出现在学员目录中，也不能被认领或删除。
不要为管理员复用学习者账户，也不要在日志、聊天记录或 issue 中保存激活码与密码。

## 验证

```sql
SELECT username, status, role, activated_at
FROM public.account
WHERE username_normalized = 'young';
```

期望结果为 `status = 'active'`、`role = 'administrator'` 且 `activated_at` 非空。不得查询、记录或分享
`password_hash`。再次运行自举会失败，因为账户已经不再处于待激活状态。
