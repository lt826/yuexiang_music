# 悦享音乐 · 微信小程序端

基于 **uni-app（Vue3 + TypeScript + Pinia）**，与 Web 端共用同一套后端微服务（网关 `http://127.0.0.1:8080`）与账号体系。

## 功能一览

| 页面 | 路径 | 说明 |
|---|---|---|
| 首页 | `pages/index/index` | 轮播图（点击播放关联歌曲）、推荐歌单、热门榜、新歌首发 |
| 搜索 | `pages/search/search` | 关键字搜歌、点歌即播 |
| 歌单广场 | `pages/playlist/square` | 热门歌单 / 乐友歌单 |
| 歌单详情 | `pages/playlist/detail` | 播放全部、收藏歌单、自己的歌单长按移除歌曲 |
| 我的音乐 | `pages/my/my` | 登录态展示、收藏列表、播放历史、我的歌单（可新建） |
| 全屏播放器 | `pages/player/player` | 旋转封面、LRC 歌词滚动、进度拖动、四种循环模式 |
| 登录/注册 | `pages/login/login` | 与 Web 端同账号 |
| 个人中心 | `pages/profile/profile` | 改昵称/签名、改密码、退出登录 |
| 歌手中心 | `pages/singer/center` | 歌手认证申请、查看审核状态与作品 |

播放使用 `uni.getBackgroundAudioManager()`：**退后台/锁屏不断歌，支持系统播放控制**（manifest 已声明 `requiredBackgroundModes: ["audio"]`）。

## 开发运行（3 步）

```bash
# 1. 装依赖（首次）
npm install

# 2. 开发模式（文件改动自动重新编译）
npm run dev:mp-weixin
# 产物在 dist/dev/mp-weixin

# 或正式构建
npm run build:mp-weixin
# 产物在 dist/build/mp-weixin
```

**3. 打开微信开发者工具** → 导入项目 → 目录选
`D:\Desktop\悦享音乐\yuexiang_miniprogram\dist\dev\mp-weixin`（开发）或 `dist\build\mp-weixin`（构建）→ AppID 自动读取（`wx1b2c2c4bf77deb5b`）。

> 开发者工具里务必勾选：**详情 → 本地设置 → 不校验合法域名**（本地 http 后端必需）。

## 后端地址配置

改一个文件即可：`src/config.ts`

```ts
export const BASE_URL = 'http://127.0.0.1:8080'
```

| 场景 | 填什么 |
|---|---|
| 开发者工具模拟器 | `http://127.0.0.1:8080`（默认） |
| 手机真机预览（同一 WiFi） | 你电脑的局域网 IP，如 `http://192.168.1.5:8080`（`ipconfig` 查 IPv4） |
| 发朋友试用（体验版） | 内网穿透公网地址或云服务器地址 |

改完重新 `npm run build:mp-weixin`。

## 发给朋友试用（不发布上线）

1. **确保后端可达**：朋友手机不在你局域网时，用内网穿透（cpolar/natapp）或把 docker compose 部署到云服务器，`BASE_URL` 改成公网地址重新构建。
2. **开发者工具点「上传」**：右上角上传 → 填版本号和备注。
3. **mp.weixin.qq.com 后台**：版本管理 → 开发版本 → 设为**体验版**，得到一个二维码。
4. **添加体验成员**：成员管理 → 体验成员 → 填朋友的微信号（上限约 15 人）。
5. **朋友第一次打开**：右上角「⋯」→ 开发调试 → 打开调试 → 重新进入。**调试模式下不校验域名**，http 地址直接可用，无需 HTTPS 和备案。

## 目录结构

```
src/
├── config.ts          # BASE_URL（唯一要改的环境配置）
├── manifest.json      # 小程序配置（AppID、后台音频权限）
├── pages.json         # 路由与 tabBar
├── api/               # 接口层（与 Web 端 src/api 对齐）
├── stores/            # Pinia：user（登录态）、player（全局播放器）
├── utils/             # request 封装（uni.request + token + 401 处理）
├── components/        # SongItem（歌曲行）、MiniPlayer（底部播放条）
└── pages/             # 9 个页面
```

## 与 Web 端的差异说明

- 登录沿用账号密码，未做微信授权登录（不需要 AppSecret，后端零改动）
- 歌手上传歌曲/头像上传等管理操作请继续用 Web 端，小程序端歌手中心只做申请与展示
- 播放上报、收听历史逻辑与 Web 端一致
