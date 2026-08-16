# 云服务器 staging 部署

当前方案只部署 Java API，不替换 GitHub Pages 前端，也不修改现有 `be-young.top` 博客。服务器使用宿主机已有的
Nginx 终止 TLS，并反向代理到仅监听 `127.0.0.1:8080` 的后端容器。

## 前置条件

1. `staging-api.be-young.top` 的 A 记录指向服务器公网 IPv4。
2. 安全组开放 80、443 和受限来源的 SSH；不要开放 8080 或数据库端口。
3. Docker Engine 与 Docker Compose 插件已经安装。
4. 已按 `../docs/runtime-database-role.md` 创建并验证 `tutor_base_app`。
5. 后端镜像已经通过 `mvnw clean verify`，并使用不可变 commit SHA 标签发布到 GHCR。

## 获取部署文件

在服务器使用当前开发分支：

```shell
git clone --branch codex/java-backend-migration --single-branch \
  https://github.com/by-be-young/tutor-base.git
cd tutor-base/deploy
```

如果目录已经存在，使用 `git pull --ff-only` 更新，不要覆盖本地环境变量文件。

## 配置后端

```shell
cp .env.example .env
cp backend.env.example backend.env
chmod 600 .env backend.env
```

`.env` 只填写已经验证的不可变镜像标签：

```text
BACKEND_IMAGE=ghcr.io/by-be-young/tutor-base-backend:sha-<完整提交 SHA>
```

`backend.env` 填写 Supabase Session Pooler 连接、独立运行账户密码、随机 CSRF secret 和精确的 HTTPS 前端
origin。CSRF secret 可以用 `openssl rand -base64 48` 生成，不要发送给他人或提交到 Git。

必须保持：

```text
SPRING_PROFILES_ACTIVE=production
DATABASE_MIGRATIONS_ENABLED=false
TUTOR_BOOTSTRAP_ENABLED=false
TUTOR_BOOTSTRAP_PASSWORD=
```

生产服务器不执行 Flyway，也不使用 Supabase 管理员或项目数据库密码。

## 启动后端

```shell
sudo docker compose config --quiet
sudo docker compose up -d backend
sudo docker compose ps
sudo docker compose logs --tail=100 backend
curl --fail http://127.0.0.1:8080/actuator/health/liveness
curl --fail http://127.0.0.1:8080/actuator/health/readiness
curl --fail http://127.0.0.1:8080/api/v1/system/status
```

Compose 对容器设置 768 MiB 内存限制，JVM 最大使用其中约 60%。宿主机 8080 只绑定回环地址。

## 配置现有 Nginx

仓库提供独立的 `nginx/staging-api.be-young.top.conf`，不会修改博客配置：

```shell
sudo install -m 0644 nginx/staging-api.be-young.top.conf \
  /etc/nginx/conf.d/staging-api.be-young.top.conf
sudo nginx -t
sudo systemctl reload nginx
curl --fail http://staging-api.be-young.top/actuator/health/readiness
```

确认 HTTP 代理正常后安装 Certbot 并申请证书：

```shell
sudo apt update
sudo apt install -y certbot python3-certbot-nginx
sudo certbot --nginx -d staging-api.be-young.top --redirect
sudo nginx -t
curl --fail https://staging-api.be-young.top/actuator/health/liveness
curl --fail https://staging-api.be-young.top/actuator/health/readiness
curl --fail https://staging-api.be-young.top/api/v1/system/status
sudo certbot renew --dry-run
```

## 身份功能 smoke test

在 Windows 管理机上执行；密码通过隐藏提示输入，不写入命令历史：

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
sudo docker compose up -d backend
sudo docker compose ps
curl --fail http://127.0.0.1:8080/actuator/health/readiness
```

只允许向后兼容的数据库 migration 与应用一起发布。破坏性 migration 不得依靠切回旧镜像回滚。
