# 云服务器 staging 部署

当前 Compose 只部署 Java API 和 Caddy，不会替换 GitHub Pages 上仍在使用 Supabase 的生产前端。

## 前置条件

1. `staging-api.be-young.top` 的阿里云 A 记录指向服务器公网 IPv4。
2. 安全组只开放 80、443 和受限来源的 SSH；不要开放 8080 或数据库端口。
3. 已按 `../docs/runtime-database-role.md` 创建并验证 `tutor_base_app`。
4. 后端镜像已经通过 `mvnw clean verify`，并使用不可变 commit SHA 标签发布到 GHCR。

## 配置

```shell
cp .env.example .env
cp backend.env.example backend.env
chmod 600 .env backend.env
```

填写 `.env` 的镜像标签和 API 域名；填写 `backend.env` 的数据库连接、独立运行账户密码、随机 CSRF secret
和精确 HTTPS 前端 origin。必须保持：

```text
SPRING_PROFILES_ACTIVE=production
DATABASE_MIGRATIONS_ENABLED=false
TUTOR_BOOTSTRAP_ENABLED=false
TUTOR_BOOTSTRAP_PASSWORD=
```

## 启动和验证

```shell
docker compose config
docker compose pull
docker compose up -d
docker compose ps
docker compose logs --tail=100 backend caddy
curl --fail https://staging-api.be-young.top/actuator/health/liveness
curl --fail https://staging-api.be-young.top/actuator/health/readiness
curl --fail https://staging-api.be-young.top/api/v1/system/status
```

后端不映射宿主机 8080，只有同一内部 Docker 网络中的 Caddy 可以访问它。Caddy 自动申请和续期 TLS 证书。

在 Windows 管理机上执行完整身份 smoke test；密码通过隐藏提示输入，不写入命令历史：

```powershell
.\api-smoke-test.ps1 -BaseUrl https://staging-api.be-young.top
```

本地后端默认地址可以直接执行：

```powershell
.\api-smoke-test.ps1
```

## 回滚

把 `.env` 中的 `BACKEND_IMAGE` 改为上一个已验证的 commit SHA 标签，然后执行：

```shell
docker compose pull backend
docker compose up -d backend
docker compose ps
```

只允许向后兼容的数据库 migration 与应用一起发布。破坏性 migration 不得依靠切回旧镜像回滚。
