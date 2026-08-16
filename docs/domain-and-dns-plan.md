# be-young.top 域名与 DNS 配置

博客继续使用 `be-young.top`，学习平台使用独立子域名，不修改博客现有的 `@`、`www` 或其他记录。

| 用途 | 地址 | DNS |
| --- | --- | --- |
| 现有博客 | `http://be-young.top/` | 保持现状；建议后续单独启用 HTTPS |
| 学习平台前端 | `https://learn.be-young.top` | CNAME 到 `by-be-young.github.io` |
| Java API | `https://api.be-young.top` | 云服务器确定后，A 记录到公网 IPv4 |

## 前端域名：现在可以配置

按照 GitHub 的安全顺序，先在 GitHub 配置 custom domain，再在阿里云添加 DNS：

1. 在 GitHub 个人账户设置中验证 `be-young.top`，按 GitHub 给出的内容在阿里云添加 TXT 记录。
2. 打开仓库 `by-be-young/tutor-base` → Settings → Pages。
3. 在 Custom domain 填写 `learn.be-young.top` 并保存。
4. 打开阿里云云解析 DNS → `be-young.top` → 解析设置 → 添加记录：

   | 字段 | 值 |
   | --- | --- |
   | 记录类型 | `CNAME` |
   | 主机记录 | `learn` |
   | 解析请求来源 | 默认 |
   | 记录值 | `by-be-young.github.io` |
   | TTL | 默认或 10 分钟 |

5. 不要把记录值写成 `by-be-young.github.io/tutor-base`，DNS 记录不能包含路径。
6. DNS 生效并且 GitHub 证书签发后，在 Pages 设置中启用 Enforce HTTPS。
7. 不添加 `*.be-young.top` 泛域名记录，避免未使用子域被接管。

当前仓库只维护一个 Pages 站点。`main` 更新后，`.github/workflows/deploy.yml` 自动构建并发布前端。
工作流读取 GitHub Pages 返回的 `base_path`：未绑定自定义域名时使用 `/tutor-base/`，绑定
`learn.be-young.top` 后自动使用 `/`，不需要手工修改 Vite base。

当前联调阶段连接已经部署的测试 API：

```text
VITE_API_BASE_URL=https://staging-api.be-young.top/api/v1
```

Java API 切换到正式域名后，再把工作流中的地址改为 `https://api.be-young.top/api/v1`。

## API 域名：购买并初始化服务器后配置

云服务器得到公网 IPv4 后，在阿里云添加：

| 字段 | 值 |
| --- | --- |
| 记录类型 | `A` |
| 主机记录 | `api` |
| 解析请求来源 | 默认 |
| 记录值 | `<云服务器公网 IPv4>` |
| TTL | 默认或 10 分钟 |

服务器复用现有 Nginx，通过 Certbot 为 `api.be-young.top` 申请 TLS 证书并代理到只监听
`127.0.0.1:8080` 的 Java container。API-only staging 先使用 `staging-api.be-young.top`。后端环境变量使用：

```text
TUTOR_WEB_ALLOWED_ORIGINS=https://learn.be-young.top,https://by-be-young.github.io
```

只允许完整的 HTTPS origin；不要填写裸域名、路径、尾随 `/` 或 `*`。

使用默认 `by-be-young.github.io` 地址只能做页面加载和基础接口检查。密码登录依赖服务端 Cookie，浏览器可能拦截
跨站 Cookie，因此完整验收必须使用 `https://learn.be-young.top`。

## 验证命令

Windows PowerShell：

```powershell
Resolve-DnsName learn.be-young.top -Type CNAME
Resolve-DnsName api.be-young.top -Type A
```

预期前端 CNAME 最终指向 `by-be-young.github.io`。API A 记录只有在云服务器准备完成后才应存在。
