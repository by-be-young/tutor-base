# 云服务器部署手册

生产形态为 GitHub Pages 前端 + 云服务器 Java API + Supabase PostgreSQL。宿主机 Nginx 负责 HTTPS，后端容器只监听
`127.0.0.1:8080`。当前 staging 地址是 `https://staging-learn.be-young.top` 和
`https://staging-api.be-young.top`。

## 服务器文件

```shell
cd /opt/tutor-base/repository
git fetch origin main
git switch main
git pull --ff-only origin main
cd deploy

cp -n .env.example .env
cp -n backend.env.example backend.env
cp -n migration.env.example migration.env
chmod 600 .env backend.env migration.env
```

- `.env`：`BACKEND_IMAGE=ghcr.io/by-be-young/tutor-base-backend:sha-<40位提交SHA>`。
- `backend.env`：使用受限运行账户 `tutor_base_app`，保持 `DATABASE_MIGRATIONS_ENABLED=false`、
  `TUTOR_BOOTSTRAP_ENABLED=false`，并精确配置前端 HTTPS origin。
- `migration.env`：仅供一次性 migration 容器使用，填写 schema owner/migration 账户；不得使用运行账户。
- 三个真实文件均被 Git 忽略，不要把内容发到聊天、日志或仓库。

先检查配置：

```shell
sudo docker compose config --quiet
```

## 首次发布 V5

在 Supabase SQL Editor 先运行 `database/audit/pre_v5_rewards_checks.sql`。所有 `anomaly_count` 为 0 后，在服务器执行：

```shell
sudo docker compose pull backend migration
sudo docker compose --profile release run --rm migration
sudo docker compose up -d --no-deps backend
```

migration 是一次性非 Web 容器：Flyway 成功后以 0 退出；失败则后端不会被替换。V5 完成后，在 Supabase SQL Editor
重新运行：

1. `database/operations/provision_runtime_role.sql`
2. `database/audit/runtime_role_checks.sql`

第二份报告的全部 `anomaly_count` 必须为 0。然后重启后端，让连接池使用新权限：

```shell
sudo docker compose restart backend
```

## 每次发布后的验证

```shell
sudo docker compose ps
sudo docker compose logs --tail=150 backend
curl --fail http://127.0.0.1:8080/actuator/health/liveness
curl --fail http://127.0.0.1:8080/actuator/health/readiness
curl --fail https://staging-api.be-young.top/api/v1/system/status
```

在 Windows 管理机执行完整身份 smoke：

```powershell
.\api-smoke-test.ps1 -BaseUrl https://staging-api.be-young.top
```

还应手工验证管理员建号/授权/设密、学习者作答、批阅、错题、签到和奖励页面。

## GitHub 自动部署

`main` 的集中 `CI` 全部通过后，`Backend CD` 自动启动：先发布或复用 `sha-<commit>` 不可变镜像，随后通过
GitHub Environment `staging` 审批，再执行迁移、替换容器和健康检查。新容器的本地 readiness 或公网 smoke
失败时，工作流会把 `.env` 恢复为上一镜像并重新启动旧后端。开发分支和失败的 `main` CI 都不会触发自动部署。
需在该 Environment 配置：

- `DEPLOY_HOST`：服务器地址。
- `DEPLOY_USER`：部署用户。
- `DEPLOY_SSH_PRIVATE_KEY`：专用部署私钥。
- `DEPLOY_KNOWN_HOSTS`：预先人工核验的服务器 host key，禁止运行时 `ssh-keyscan` 自动信任。

服务器必须预先准备 `.env`、`backend.env`、`migration.env`，并允许部署用户无交互执行所需 Docker 命令。

## 前端发布与权限收口

同一 `main` CI 通过后，`Pages CD` 从相同 commit 构建前端。工作流从仓库变量 `VITE_API_BASE_URL`
读取 API 地址；staging 应设为：

```text
https://staging-api.be-young.top/api/v1
```

确认新版 Pages 的全部关键旅程可用后，才在 Supabase SQL Editor 运行：

1. `database/operations/revoke_browser_table_access.sql`
2. `database/audit/browser_table_access_checks.sql`

第二份报告全为 0 才表示浏览器直连权限已安全关闭。此步骤不可在旧前端仍在线时提前执行。

## Nginx 与回滚

`nginx/staging-api.be-young.top.conf` 反向代理到 `127.0.0.1:8080`。修改后始终先执行：

```shell
sudo nginx -t
sudo systemctl reload nginx
```

应用回滚只需把 `.env` 的 `BACKEND_IMAGE` 改为上一已验证的 commit SHA，再执行：

```shell
sudo docker compose up -d --no-deps backend
curl --fail http://127.0.0.1:8080/actuator/health/readiness
```

数据库 migration 只允许向后兼容；V5 新表和约束保留时旧镜像仍可运行。不要通过删除表或修改 Flyway 历史回滚。
