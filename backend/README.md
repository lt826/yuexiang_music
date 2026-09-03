# Music Dreamer 悦享音乐 — 后端微服务

基于 **Spring Boot 3 + Spring Cloud Alibaba** 的微服务架构，前端（Vue）位于 `../yuexiang_fronted`。

## 版本矩阵

| 组件 | 版本 | 说明 |
|---|---|---|
| JDK | 17 | |
| Spring Boot | 3.2.12 | |
| Spring Cloud | 2023.0.3 | |
| Spring Cloud Alibaba | 2023.0.3.4 | 前三位须与 Spring Cloud 一致 |
| Nacos | 2.3.x | 注册中心 |
| MyBatis-Plus | 3.5.7 | boot3 专用 starter |
| MySQL | 8.0 | 每服务独立库 |

## 模块与端口

| 模块 | 端口 | 职责 | 数据库 |
|---|---|---|---|
| yuexiang-gateway | 8080 | 统一入口，`/api/{service}/**` 转发 | - |
| yuexiang-user | 8081 | 登录注册、用户信息设置 | yuexiang_user |
| yuexiang-song | 8082 | 歌曲搜索推荐、歌曲管理 | yuexiang_song |
| yuexiang-singer | 8083 | 歌手管理 | yuexiang_singer |
| yuexiang-playlist | 8084 | 歌单 | yuexiang_playlist |
| yuexiang-favorite | 8085 | 歌曲/歌单收藏、收听历史 | yuexiang_favorite |
| yuexiang-message | 8086 | 消息通知 | yuexiang_message |
| yuexiang-file | 8087 | 文件上传下载 | yuexiang_file |
| yuexiang-admin | 8088 | 后台管理（Feign 调用各服务） | yuexiang_admin |
| yuexiang-common | - | 统一响应/异常/JWT 工具 | - |

前端统一通过网关访问：`http://localhost:8080/api/{service}/{接口路径}`，
例如 `GET http://localhost:8080/api/user/ping` → 转发到 `yuexiang-user` 的 `/ping`。

## 首次启动

### 1. 安装并启动 Nacos（注册中心）

1. 下载 Nacos 2.3.2：https://github.com/alibaba/nacos/releases/tag/2.3.2
2. 解压后单机模式启动：
   ```cmd
   cd nacos\bin
   startup.cmd -m standalone
   ```
3. 控制台：http://localhost:8848/nacos （默认账号密码 nacos/nacos）

### 2. 初始化 MySQL

1. 确保 MySQL 8 服务已启动
2. 执行 `sql/init.sql`（在 IDEA 数据源中打开执行，或 `mysql -uroot -p < sql/init.sql`）
3. 修改各服务 `application.yml` 中的 `spring.datasource.password`（当前占位 `123456`）
4. 后台默认管理员：`admin / admin123`

### 3. 启动服务（顺序：Nacos → 业务服务 → 网关）

IDEA 中依次运行各模块的 `Yuexiang*Application`，或命令行：

```bash
./mvnw -pl yuexiang-user spring-boot:run
```

### 4. 验证

```bash
# 直连服务
curl http://localhost:8081/ping
# 经网关（验证 注册→发现→路由 全链路）
curl http://localhost:8080/api/user/ping
```

均应返回 `{"code":200,...,"data":"pong from yuexiang-user"}`。

## 构建

```bash
./mvnw clean package -DskipTests   # 全模块打包
```

## 后续迭代规划（当前仅骨架）

- [ ] user：注册/登录（JWT）、资料修改、头像上传
- [ ] song：搜索（FULLTEXT ngram）、推荐（按风格/播放量）、审核流
- [ ] singer：申请成为歌手、歌手主页
- [ ] playlist：创建/收藏歌单、官方推荐歌单
- [ ] favorite：收藏、收听历史
- [ ] message：系统/审核/粉丝消息
- [ ] file：音乐与图片上传下载（本地磁盘起步，可换 MinIO/OSS）
- [ ] admin：用户/歌手/歌曲/歌单管理（Feign 聚合）
- [ ] 网关鉴权过滤器、Redis 缓存、Nacos 配置中心

---

## Sentinel 限流熔断（2026-08-30 接入）

**接入范围**：gateway（网关路由限流）、ai（接口限流+热点参数限流）、song（Feign 熔断降级）

**规则配置**（代码声明，重启生效）：
- 网关路由限流：`GatewaySentinelConfig` —— ai-route 20 QPS / user-route 10 QPS / song-route 10+20 burst / file-route 15 QPS
- AI 服务：`AiSentinelConfig` —— ai-chat 资源 5 QPS + 热点参数限流（单 userId 2 次/秒）
- Feign 熔断：song 服务 `feign.sentinel.enabled=true` + `SingerClientFallback`（singer 服务故障时降级返回空数据，主流程不报错）

**Sentinel Dashboard**（演示用）：
```bash
cd sentinel-dashboard
java -Dserver.port=8858 -Dsentinel.dashboard.auth.username=sentinel -Dsentinel.dashboard.auth.password=sentinel -jar sentinel-dashboard.jar
# 浏览器访问 http://localhost:8858，账号密码 sentinel/sentinel
```

**Docker 注意**：容器通过 `SPRING_CLOUD_SENTINEL_TRANSPORT_DASHBOARD: host.docker.internal:8858` 环境变量连 dashboard（yml 里 `${SENTINEL_DASHBOARD:localhost:8858}` 的默认值在容器内不可达）。

---

## 服务器部署（生产模式）

**文件说明**：
- `.env` — 环境变量（密码/key），已 gitignore，**服务器上必须改成强密码**；`DEEPSEEK_API_KEY` 缺失时 ai 服务无法启动（compose 用 `:?` 强校验）
- `.env.example` — 模板（无真实值）
- `docker-compose.yml` — 所有 Java 服务已加 `extra_hosts: host.docker.internal:host-gateway`，Windows 开发机与 Linux 服务器通用，无需改网络配置
- `docker-compose.prod.yml` — 生产覆盖：对外只留 80/8082，Redis/RabbitMQ/AMQP 不暴露公网，RabbitMQ 管理界面仅 127.0.0.1（SSH 隧道访问），Java 服务内存上限
- `deploy.sh` — 一键部署脚本（含音频数据检查 + 音频可达性冒烟）
- `scripts/export-media.sh` — 开发机导出歌曲/封面/头像（file 服务 volume → media.tar.gz）

**服务器部署步骤**：
```bash
# 1. 上传代码（git clone 或 scp）
# 2. 宿主机 MySQL（先执行 sql/init.sql + 各 seed 脚本）与 Nacos 已运行，
#    安全组/防火墙只放行 80、8082、22
# 3. 配置环境变量
cp .env.example .env && vi .env   # 改密码和 DeepSeek key
# 4. （一次性）迁移音频/封面：在开发机执行并上传
#    bash scripts/export-media.sh && scp media.tar.gz user@服务器:项目目录/
#    （deploy.sh 检测到 media.tar.gz 会自动解包进 file volume；不传则播放 404）
# 5. 一键部署
chmod +x deploy.sh && ./deploy.sh
# 后续更新代码后：./deploy.sh fast
```

**本地开发**：照旧 `docker compose up -d`，不加载 prod 文件，行为不变。
