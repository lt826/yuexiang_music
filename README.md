# 悦享音乐 Music Dreamer

基于 **Spring Boot 3 + Spring Cloud Alibaba 微服务**的在线音乐平台，含 Web 管理端/用户端与微信小程序端，深度融合 **DeepSeek 大模型 AI 能力**，提供"搜歌 — 听歌 — 社交 — 创作"一体化音乐体验。

## 一、项目介绍

悦享音乐是一个面向听众、音乐人、管理员三类角色的在线音乐平台：

- **听众端**：关键词/歌词片段找歌、实时热榜、心情电台（全端同秒收听）、AI 智能问答（自然语言找歌/听歌周报/一键建歌单）、歌单管理、点赞评论、乐坊社区、一起听房间
- **音乐人端**：歌手认证、歌曲发布（先审后发）、作品管理、粉丝与作品数据查看
- **管理端**：歌曲/认证/乐坊审核、用户管理、公告群发、平台数据统计

后端采用 **10 个微服务 + 网关** 的架构（用户/歌曲/歌手/歌单/收藏/消息/文件/管理/AI/乐坊），每服务独立数据库，通过 Nacos 注册发现、OpenFeign 同步调用、RabbitMQ 异步解耦、Redis 缓存热点数据、Sentinel 限流熔断，Docker Compose 一键部署。

### 仓库结构（monorepo）

| 目录 | 说明 | 技术栈 |
|---|---|---|
| `backend/` | 后端微服务（10 个服务 + 网关 + Docker 部署） | Java 17 / Spring Boot 3.2 / Spring Cloud Alibaba / MyBatis-Plus / Redis / RabbitMQ / Sentinel / DeepSeek AI |
| `frontend/` | Web 前端（用户门户 + 管理后台） | Vue 3 / Vite / Element Plus / Pinia |
| `miniprogram/` | 微信小程序端 | uni-app / Vue 3 / TypeScript / Pinia |

> 本仓库为三个工程的聚合快照。前后端日常开发仍使用各自的独立仓库（华为云 CodeHub），
> 本仓库用于课程交付/备份展示，推送前在独立仓库提交后需同步本仓库。

## 二、当今形势

- **数字音乐市场持续增长**：中国网络音乐用户规模已超 7 亿，在线音乐付费习惯逐步养成，个性化推荐、社区化互动成为平台竞争的核心抓手，市场对"不只播放器"的综合音乐社区需求明显上升。
- **AI 大模型进入落地爆发期**：以 DeepSeek 为代表的国产大模型 API 成本大幅下降、能力快速提升，"AI + 音乐"正从概念走向产品化——自然语言找歌、智能歌单生成、听歌报告等 AI 原生功能已成为新一代音乐平台的差异化标配。
- **云原生微服务成为企业主流架构**：Spring Cloud Alibaba 生态在国内互联网企业中广泛应用，掌握服务拆分、注册发现、异步消息、限流熔断等微服务治理能力，与行业用人需求高度契合。
- **多端覆盖成为常态**：Web + 微信小程序的双端形态兼顾桌面管理与移动轻量入口，小程序无需安装、即用即走的特性尤其契合音乐类高频消费场景。

## 三、项目优势

1. **AI 原生，而非 AI 贴皮**：AI 问答采用 DeepSeek Function Calling 多轮工具调用（≤8 轮），工具全部实时查库（搜索歌曲/热榜/周报/建歌单），模型只做理解与编排、不编造数据；配合 Sentinel 限流（全局 5 QPS + 单用户热点限流）与超限降级策略，兼顾体验与成本。
2. **全端同步心情电台**：服务端时间取模排轴算法，所有听众同一秒收听同一首歌，实现"云电台"式陪伴体验，进度/切歌锁定、播完自动对时，是同类课程项目中少见的创新点。
3. **真实的企业级微服务架构**：10 服务独立库、网关统一鉴权（JWT 黑名单即时失效）、OpenFeign 同步聚合、RabbitMQ 异步削峰（播放计数、上架粉丝通知）、Redis 实时热榜（ZINCRBY）、FULLTEXT ngram 中文全文检索——架构完整度对齐生产实践。
4. **完整的社区与互动闭环**：乐坊圈子（帖子/挂歌/评论/点赞）、一起听房间（WebSocket 实时同步）、粉丝关注与新歌上架 MQ 级联通知，用户粘性设计完整。
5. **三端一套后端**：Web 用户端、管理后台、微信小程序共享微服务后端，小程序支持后台/锁屏播放（BackgroundAudioManager），工程化程度高。
6. **一键部署，开箱即用**：`deploy.sh` 脚本 + Docker Compose（14 容器）一键拉起全部服务，含 SQL 初始化与音频数据迁移，降低复现门槛。

## 四、运行环境与版本要求

### 后端（backend/）

| 组件 | 要求版本 | 说明 |
|---|---|---|
| JDK | **17** | 必须，Spring Boot 3 最低要求 |
| Spring Boot | **3.2.12** | 父 POM 已锁定 |
| Spring Cloud | **2023.0.3** | 与 Spring Boot 3.2.x 配套 |
| Spring Cloud Alibaba | **2023.0.3.4** | 版本须与 Spring Cloud 前三位一致 |
| Maven | 3.8+ | 仓库自带 `mvnw`，可不装 |
| MySQL | **8.0** | 每服务独立库（共 10 库），需先执行 `sql/init.sql` + seed 脚本 |
| Redis | **7.x** | 热榜/缓存/JWT 黑名单 |
| RabbitMQ | **3.13**（management 镜像） | 播放/上架事件，手动 ACK |
| Nacos | **2.3.x**（推荐 2.3.2） | 注册中心/配置中心，控制台 `localhost:8848/nacos` |
| Sentinel | 随 Spring Cloud Alibaba 引入 | 网关路由限流 + AI 热点限流 |
| MyBatis-Plus | 3.5.7 | boot3 专用 starter |
| DeepSeek API Key | 自备 | AI 问答/电台功能必需，无 Key 时该模块降级 |

### Web 前端（frontend/）

| 组件 | 要求版本 |
|---|---|
| Node.js | **^22.18.0 或 ≥24.12.0**（见 `package.json` engines） |
| Vue | 3.5.x |
| Vite | 8.x |
| Element Plus | 2.14.x |
| Pinia | 4.x |

### 微信小程序（miniprogram/）

| 组件 | 要求版本 |
|---|---|
| Node.js | 18+ |
| uni-app | 3.0（Vue 3 + TypeScript + Pinia） |
| 微信开发者工具 | 最新稳定版 |

### Docker 部署（可选）

- Docker 20+ / Docker Compose v2
- `docker-compose.yml` 默认从宿主机访问 MySQL/Nacos/Redis/RabbitMQ（`host.docker.internal`）

## 五、快速开始

- 后端部署：见 `backend/README.md`（`deploy.sh` 一键部署，含音频数据迁移）
- Web 访问：网关 `http://localhost:8080/api/**`，前端 `http://localhost:8082`
- 小程序：`miniprogram/` 下 `npm run build:mp-weixin`，微信开发者工具导入 `dist/build/mp-weixin`
- 服务启动顺序：**Nacos → MySQL（执行 SQL）→ 业务服务 → 网关**
