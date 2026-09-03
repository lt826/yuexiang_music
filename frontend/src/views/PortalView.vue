<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Headset, Search, Collection, Star, Mic, DataAnalysis } from '@element-plus/icons-vue'
import { getPortalStats, type PortalStatsVO } from '@/api/portal'

const router = useRouter()

// ===== 实时统计（30s 轮询；失败静默保留上次值） =====
const stats = ref<PortalStatsVO>({
  userCount: 0,
  singerCount: 0,
  activeCount: 0,
  songCount: 0,
  publishedCount: 0,
  playCount: 0,
})
const statsLoaded = ref(false)
let pollTimer: ReturnType<typeof setInterval> | null = null

async function refreshStats() {
  try {
    const vo = await getPortalStats()
    // 字段缺失（个别服务未起）时兜底 0，页面不空
    stats.value = {
      userCount: vo.userCount ?? 0,
      singerCount: vo.singerCount ?? 0,
      activeCount: vo.activeCount ?? 0,
      songCount: vo.songCount ?? 0,
      publishedCount: vo.publishedCount ?? 0,
      playCount: vo.playCount ?? 0,
    }
    statsLoaded.value = true
  } catch {
    // 静默：门户不弹错误打扰访客
  }
}

function formatCount(n: number): string {
  if (n >= 100000000) return (n / 100000000).toFixed(1) + '亿'
  if (n >= 10000) return (n / 10000).toFixed(1) + '万'
  return String(n)
}

/** 数值滚动：数字从当前值平滑滚到目标值（约 1s，ease-out） */
type StatKey = 'userCount' | 'singerCount' | 'songCount' | 'playCount'
const animated = ref<Record<StatKey, number>>({
  userCount: 0,
  singerCount: 0,
  songCount: 0,
  playCount: 0,
})
const animFrames: Partial<Record<StatKey, number>> = {}

function animateTo(key: StatKey, target: number) {
  cancelAnimationFrame(animFrames[key]!)
  const reduce = window.matchMedia('(prefers-reduced-motion: reduce)').matches
  if (reduce || animated.value[key] === target) {
    animated.value[key] = target
    return
  }
  const from = animated.value[key]
  const start = performance.now()
  const duration = 900
  const step = (now: number) => {
    const t = Math.min(1, (now - start) / duration)
    const eased = 1 - Math.pow(1 - t, 3)
    animated.value[key] = Math.round(from + (target - from) * eased)
    if (t < 1) animFrames[key] = requestAnimationFrame(step)
  }
  animFrames[key] = requestAnimationFrame(step)
}

const statTiles = computed(() => [
  { key: 'userCount' as const, label: '注册用户', sub: '累计注册账号', display: formatCount(animated.value.userCount) },
  { key: 'singerCount' as const, label: '驻站歌手', sub: '认证通过的创作者', display: formatCount(animated.value.singerCount) },
  { key: 'songCount' as const, label: '收录歌曲', sub: `已上架 ${stats.value.publishedCount} 首`, display: formatCount(animated.value.songCount) },
  { key: 'playCount' as const, label: '累计播放', sub: '全站已上架作品', display: formatCount(animated.value.playCount) },
])

function playStatsAnimation() {
  animateTo('userCount', stats.value.userCount)
  animateTo('singerCount', stats.value.singerCount)
  animateTo('songCount', stats.value.songCount)
  animateTo('playCount', stats.value.playCount)
}

onMounted(() => {
  refreshStats().then(playStatsAnimation)
  pollTimer = setInterval(() => {
    refreshStats().then(playStatsAnimation)
  }, 30000)
})

onUnmounted(() => {
  if (pollTimer) clearInterval(pollTimer)
  Object.values(animFrames).forEach((id) => cancelAnimationFrame(id!))
})

// ===== 功能介绍（对齐系统真实能力，不写空话） =====
const features = [
  {
    icon: Headset,
    title: '在线听歌',
    desc: '播放器支持歌词同步、上一首/下一首、播放队列与收藏快捷操作，匿名也能听全站上架作品。',
  },
  {
    icon: Search,
    title: '全网搜索',
    desc: '按歌名、歌词、风格或歌手名子串检索，命中结果按相关度与热度排序。',
  },
  {
    icon: Collection,
    title: '歌单广场',
    desc: '浏览官方与用户创建的歌单，登录后可创建自己的歌单并添加喜欢的歌曲。',
  },
  {
    icon: Star,
    title: '收藏与历史',
    desc: '一键收藏歌曲、记录收听历史，首页推荐基于你的偏好持续优化。',
  },
  {
    icon: Mic,
    title: '歌手入驻',
    desc: '提交认证成为驻站歌手，上传原创作品（50MB 内音频），管理作品与上下架。',
  },
  {
    icon: DataAnalysis,
    title: '后台治理',
    desc: '管理员审核歌手认证与歌曲上架，管理用户账号，发布公告与站内通知。',
  },
]

const reducedMotion = computed(() =>
  typeof window !== 'undefined'
    ? window.matchMedia('(prefers-reduced-motion: reduce)').matches
    : false,
)

const spectrumBars = Array.from({ length: 36 }, (_, i) => i)
</script>

<template>
  <div class="portal">
    <!-- ===== 顶栏 ===== -->
    <header class="portal-nav">
      <div class="nav-inner">
        <div class="nav-brand">
          <span class="brand-mark">♪</span>
          <span class="brand-name">悦享音乐</span>
          <span class="brand-tag">系统门户</span>
        </div>
        <nav class="nav-links">
          <a href="#features" class="nav-link">功能</a>
          <a href="#stats" class="nav-link">数据</a>
        </nav>
        <div class="nav-actions">
          <el-button text class="nav-btn" @click="router.push('/login')">登录</el-button>
          <el-button class="nav-btn nav-btn--primary" @click="router.push('/register')">免费注册</el-button>
        </div>
      </div>
    </header>

    <!-- ===== Hero：系统是什么 + 频谱律动 ===== -->
    <section class="hero">
      <div class="hero-inner">
        <p class="hero-eyebrow">微服务架构 · 前后端分离 · 8 大服务</p>
        <h1 class="hero-title">
          听见原创的力量<span class="hero-title-accent">。</span>
        </h1>
        <p class="hero-sub">
          悦享音乐是一个面向原创音乐人与听众的一体化平台：听众发现、收藏与分享音乐，
          歌手认证入驻、上传作品，管理员审核治理——从一首歌的诞生到被万人播放，都在这里完成。
        </p>
        <div class="hero-cta">
          <el-button size="large" class="cta-primary" @click="router.push('/home')">
            开始听歌
          </el-button>
          <el-button size="large" class="cta-ghost" @click="router.push('/admin/login')">
            管理员入口
          </el-button>
        </div>
      </div>
      <!-- 签名元素：音频频谱律动条 -->
      <div class="spectrum" aria-hidden="true">
        <span
          v-for="i in spectrumBars"
          :key="i"
          class="spectrum-bar"
          :class="{ 'is-static': reducedMotion }"
          :style="{
            '--d': (i * 0.13).toFixed(2) + 's',
            '--h': 22 + Math.abs(Math.sin(i * 1.7)) * 60 + '%',
          }"
        />
      </div>
    </section>

    <!-- ===== 实时统计：卡片咬合 hero 底边 ===== -->
    <section id="stats" class="stats-band">
      <div class="stats-inner">
        <header class="stats-head">
          <h2 class="stats-title">平台实时数据</h2>
          <span class="stats-note">
            <span class="live-dot" :class="{ 'is-on': statsLoaded }" />
            每 30 秒自动刷新
          </span>
        </header>
        <div class="stats-grid">
          <div v-for="t in statTiles" :key="t.key" class="stat-tile">
            <span class="stat-label">{{ t.label }}</span>
            <span class="stat-value tabular-nums">
              {{ statsLoaded ? t.display : '—' }}
            </span>
            <span class="stat-sub">{{ t.sub }}</span>
          </div>
        </div>
      </div>
    </section>

    <!-- ===== 功能介绍 ===== -->
    <section id="features" class="features">
      <div class="features-inner">
        <header class="features-head">
          <h2 class="features-title">系统能做什么</h2>
          <p class="features-sub">六端联通：听众、歌手、管理员各得其所</p>
        </header>
        <div class="features-grid">
          <article v-for="f in features" :key="f.title" class="feature-card">
            <div class="feature-icon">
              <el-icon :size="22"><component :is="f.icon" /></el-icon>
            </div>
            <h3 class="feature-title">{{ f.title }}</h3>
            <p class="feature-desc">{{ f.desc }}</p>
          </article>
        </div>
      </div>
    </section>

    <!-- ===== 底部 CTA + 页脚 ===== -->
    <footer class="portal-foot">
      <div class="foot-inner">
        <div class="foot-cta">
          <h2 class="foot-title">现在就加入悦享</h2>
          <p class="foot-sub">注册即听，入驻即唱——你的下一首歌，从今天开始。</p>
          <div class="foot-actions">
            <el-button size="large" class="cta-primary" @click="router.push('/register')">创建账号</el-button>
            <el-button size="large" class="cta-ghost" @click="router.push('/admin/login')">管理员入口</el-button>
          </div>
        </div>
        <div class="foot-meta">
          <span>悦享音乐 Yuexiang Music</span>
          <span class="foot-sep">·</span>
          <span>微服务课程实践项目</span>
          <span class="foot-sep">·</span>
          <router-link to="/home" class="foot-link">用户端</router-link>
          <router-link to="/admin/login" class="foot-link">管理端</router-link>
        </div>
      </div>
    </footer>
  </div>
</template>

<style scoped>
.portal {
  min-height: 100%;
  background: #f7f8fa;
}

/* ===== 顶栏：与用户端区分——深色玻璃拟态浮于 hero ===== */
.portal-nav {
  position: sticky;
  top: 0;
  z-index: 20;
  background: rgba(16, 21, 38, 0.72);
  backdrop-filter: blur(12px);
  -webkit-backdrop-filter: blur(12px);
}

.nav-inner {
  max-width: 1120px;
  margin: 0 auto;
  height: 60px;
  padding: 0 24px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
}

.nav-brand {
  display: flex;
  align-items: center;
  gap: 10px;
}

.brand-mark {
  width: 32px;
  height: 32px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 8px;
  font-size: 18px;
  color: #fff;
  background: var(--yx-gradient);
}

.brand-name {
  font-size: 17px;
  font-weight: 700;
  letter-spacing: 1px;
  color: #fff;
}

.brand-tag {
  font-size: 11px;
  padding: 2px 8px;
  border-radius: 10px;
  color: #9fc0ee;
  background: rgba(57, 135, 229, 0.18);
  border: 1px solid rgba(57, 135, 229, 0.35);
}

.nav-links {
  display: flex;
  gap: 26px;
}

.nav-link {
  font-size: 14px;
  color: #b9c3d8;
  transition: color 0.15s ease;
}

.nav-link:hover {
  color: #fff;
}

.nav-link:focus-visible {
  outline: 2px solid #9fc0ee;
  outline-offset: 3px;
  border-radius: 2px;
}

.nav-actions {
  display: flex;
  gap: 10px;
  align-items: center;
}

.nav-btn {
  color: #dce4f4;
}

.nav-btn:hover {
  color: #fff;
}

/* 注册主按钮：亮品牌蓝，深底上对比充分 */
.nav-btn--primary {
  background: var(--yx-primary);
  border-color: var(--yx-primary);
  color: #fff;
}

.nav-btn--primary:hover {
  background: var(--yx-primary-light);
  border-color: var(--yx-primary-light);
  color: #fff;
}

/* ===== Hero：深夜舞台 ===== */
.hero {
  position: relative;
  padding: 96px 24px 150px;
  text-align: center;
  overflow: hidden;
  background:
    radial-gradient(1100px 460px at 50% -80px, rgba(57, 135, 229, 0.28), transparent 65%),
    radial-gradient(700px 300px at 82% 30%, rgba(74, 58, 167, 0.16), transparent 60%),
    linear-gradient(180deg, #141a2e 0%, #18203a 100%);
  color: #fff;
}

.hero-inner {
  max-width: 720px;
  margin: 0 auto;
  position: relative;
  z-index: 1;
}

.hero-eyebrow {
  display: inline-block;
  font-size: 12.5px;
  letter-spacing: 2px;
  color: #9fc0ee;
  padding: 6px 14px;
  border-radius: 14px;
  background: rgba(57, 135, 229, 0.14);
  border: 1px solid rgba(57, 135, 229, 0.3);
}

.hero-title {
  margin-top: 22px;
  font-size: clamp(38px, 6vw, 60px);
  font-weight: 800;
  line-height: 1.18;
  letter-spacing: 1px;
}

/* 句点是唯一的强调字符，不用渐变字 */
.hero-title-accent {
  color: var(--yx-primary-light);
}

.hero-sub {
  margin: 20px auto 0;
  max-width: 560px;
  font-size: 15.5px;
  line-height: 1.85;
  color: #aeb9d2;
}

.hero-cta {
  margin-top: 34px;
  display: flex;
  justify-content: center;
  gap: 14px;
  flex-wrap: wrap;
}

.cta-primary {
  background: var(--yx-primary);
  border-color: var(--yx-primary);
  color: #fff;
}

.cta-primary:hover {
  background: var(--yx-primary-light);
  border-color: var(--yx-primary-light);
  color: #fff;
}

.cta-ghost {
  background: transparent;
  border-color: rgba(255, 255, 255, 0.32);
  color: #e6ecf8;
}

.cta-ghost:hover {
  border-color: rgba(255, 255, 255, 0.6);
  background: rgba(255, 255, 255, 0.06);
  color: #fff;
}

/* ===== 签名元素：频谱律动 ===== */
.spectrum {
  position: absolute;
  left: 0;
  right: 0;
  bottom: 0;
  height: 90px;
  display: flex;
  align-items: flex-end;
  justify-content: center;
  gap: 6px;
  padding: 0 6vw;
  opacity: 0.85;
  mask-image: linear-gradient(90deg, transparent, #000 12%, #000 88%, transparent);
  -webkit-mask-image: linear-gradient(90deg, transparent, #000 12%, #000 88%, transparent);
}

.spectrum-bar {
  flex: 1;
  max-width: 14px;
  height: var(--h);
  border-radius: 3px 3px 0 0;
  background: linear-gradient(180deg, #3987e5 0%, rgba(57, 135, 229, 0.25) 100%);
  transform-origin: bottom;
  animation: eq-bounce 1.6s ease-in-out infinite alternate;
  animation-delay: var(--d);
}

@keyframes eq-bounce {
  from {
    transform: scaleY(0.35);
  }
  to {
    transform: scaleY(1);
  }
}

@media (prefers-reduced-motion: reduce) {
  .spectrum-bar.is-static {
    animation: none;
    transform: scaleY(0.7);
  }
}

/* ===== 实时统计带：卡片咬合 hero 底边 ===== */
.stats-band {
  background: #f7f8fa;
  padding: 0 24px;
}

.stats-inner {
  max-width: 1120px;
  margin: -72px auto 0;
  background: #fff;
  border: 1px solid var(--yx-hairline);
  border-radius: 14px;
  box-shadow: 0 12px 40px rgba(16, 26, 56, 0.12);
  padding: 26px 28px 24px;
  position: relative;
  z-index: 2;
}

.stats-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  margin-bottom: 18px;
}

.stats-title {
  font-size: 17px;
  font-weight: 700;
  color: var(--yx-text-main);
}

.stats-note {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  font-size: 12.5px;
  color: var(--yx-text-secondary);
}

/* 呼吸红点：数据已接入的信号，而非装饰 */
.live-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--yx-text-disabled);
  transition: background 0.3s ease;
}

.live-dot.is-on {
  background: #1baf7a;
  animation: live-pulse 2.4s ease-in-out infinite;
}

@keyframes live-pulse {
  0%,
  100% {
    box-shadow: 0 0 0 0 rgba(27, 175, 122, 0.4);
  }
  50% {
    box-shadow: 0 0 0 5px rgba(27, 175, 122, 0);
  }
}

.stats-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 14px;
}

.stat-tile {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 16px 18px;
  border-radius: 10px;
  background: #f7f9fc;
  border: 1px solid #edf0f5;
}

.stat-label {
  font-size: 13px;
  color: var(--yx-text-secondary);
}

/* 大数值用比例数字，字号充分放大（stat tile 契约） */
.stat-value {
  font-size: 34px;
  font-weight: 700;
  line-height: 1.15;
  color: var(--yx-text-main);
}

.stat-sub {
  font-size: 12px;
  color: var(--yx-text-disabled);
}

/* ===== 功能介绍 ===== */
.features {
  padding: 72px 24px 80px;
}

.features-inner {
  max-width: 1120px;
  margin: 0 auto;
}

.features-head {
  text-align: center;
  margin-bottom: 40px;
}

.features-title {
  font-size: 28px;
  font-weight: 700;
  color: var(--yx-text-main);
}

.features-sub {
  margin-top: 10px;
  font-size: 14.5px;
  color: var(--yx-text-secondary);
}

.features-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 16px;
}

.feature-card {
  background: #fff;
  border: 1px solid var(--yx-hairline);
  border-radius: 12px;
  padding: 24px 22px;
  transition: box-shadow 0.18s ease, transform 0.18s ease;
}

.feature-card:hover {
  box-shadow: var(--yx-shadow-card);
  transform: translateY(-2px);
}

.feature-icon {
  width: 44px;
  height: 44px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 10px;
  color: var(--yx-red-deep);
  background: var(--yx-accent-weak);
  margin-bottom: 14px;
}

.feature-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--yx-text-main);
}

.feature-desc {
  margin-top: 8px;
  font-size: 13.5px;
  line-height: 1.75;
  color: var(--yx-text-secondary);
}

/* ===== 页脚 CTA ===== */
.portal-foot {
  background:
    radial-gradient(900px 300px at 50% 120%, rgba(57, 135, 229, 0.22), transparent 70%),
    linear-gradient(180deg, #171d33 0%, #12172a 100%);
  color: #fff;
  padding: 64px 24px 28px;
}

.foot-inner {
  max-width: 1120px;
  margin: 0 auto;
}

.foot-cta {
  text-align: center;
}

.foot-title {
  font-size: 26px;
  font-weight: 700;
}

.foot-sub {
  margin-top: 10px;
  font-size: 14px;
  color: #aeb9d2;
}

.foot-actions {
  margin-top: 24px;
  display: flex;
  justify-content: center;
  gap: 14px;
  flex-wrap: wrap;
}

.foot-meta {
  margin-top: 56px;
  padding-top: 18px;
  border-top: 1px solid rgba(255, 255, 255, 0.08);
  display: flex;
  justify-content: center;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
  font-size: 12.5px;
  color: #7f8aa3;
}

.foot-sep {
  color: #4a5470;
}

.foot-link {
  color: #9fc0ee;
}

.foot-link:hover {
  color: #fff;
}

/* ===== 响应式 ===== */
@media (max-width: 960px) {
  .features-grid {
    grid-template-columns: repeat(2, 1fr);
  }
  .nav-links {
    display: none;
  }
}

@media (max-width: 720px) {
  .hero {
    padding: 64px 20px 130px;
  }
  .stats-grid {
    grid-template-columns: repeat(2, 1fr);
  }
  .features-grid {
    grid-template-columns: 1fr;
  }
  .nav-actions .nav-btn:first-child {
    display: none;
  }
}
</style>
