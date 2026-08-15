# 首次管理员一次性自举

> 当前生产库状态（2026-08-15）：`young` 管理员已完成自举，并已验证登录、管理员角色、登出和会话失效。
> 不得在当前生产库再次执行本文流程。清空本地 `TUTOR_BOOTSTRAP_PASSWORD`，并保持
> `TUTOR_BOOTSTRAP_ENABLED=false`。

首次部署时还没有已激活管理员，因此不能调用受管理员权限保护的激活码发行接口。项目提供只在
`bootstrap-admin` profile 下运行的一次性离线自举流程。它不开放公共 bootstrap endpoint，成功后立即关闭应用。

## 前置条件

1. 已备份数据库并完成 Flyway V1 至 V3 migration。
2. 数据库尚未存在管理员账户。自举会创建一个不绑定 `student` 的首位管理员账户；如果同名账户已被学员使用，
   或数据库已经存在管理员，自举会失败且回滚。
3. 云服务器防火墙没有开放本次进程的临时监听端口。
4. 准备至少 12 个字符的管理员密码。

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

## 验证

```sql
SELECT username, status, role, activated_at
FROM public.account
WHERE username_normalized = 'young';
```

期望结果为 `status = 'active'`、`role = 'administrator'` 且 `activated_at` 非空。不得查询、记录或分享
`password_hash`。再次运行自举会失败，因为账户已经不再处于待激活状态。
