# 悦享音乐 Music Dreamer

基于 Spring Boot 3 + Spring Cloud Alibaba 微服务的在线音乐平台，含 Web 管理端/用户端与微信小程序端。

## 仓库结构（monorepo）

| 目录 | 说明 | 技术栈 |
|---|---|---|
| `backend/` | 后端微服务（10 个服务 + 网关 + Docker 部署） | Java 17 / Spring Boot 3.2 / Spring Cloud Alibaba / MyBatis-Plus / Redis / RabbitMQ / Sentinel / DeepSeek AI |
| `frontend/` | Web 前端（用户门户 + 管理后台） | Vue 3 / Vite / Element Plus / Pinia |
| `miniprogram/` | 微信小程序端 | uni-app / Vue 3 / TypeScript / Pinia |

> 本仓库为三个工程的聚合快照。前后端日常开发仍使用各自的独立仓库（华为云 CodeHub），
> 本仓库用于课程交付/备份展示，推送前在独立仓库提交后需同步本仓库。

## 快速了解

- 后端部署：见 `backend/README.md`（`deploy.sh` 一键部署，含音频数据迁移）
- Web 访问：网关 `http://localhost:8080/api/**`，前端 `http://localhost:8082`
- 小程序：`miniprogram/` 下 `npm run build:mp-weixin`，微信开发者工具导入 `dist/build/mp-weixin`
