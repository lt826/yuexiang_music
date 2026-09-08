<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { CaretRight, Headset, VideoPlay } from '@element-plus/icons-vue'
import TopNav from '@/components/TopNav.vue'
import SongRow from '@/components/SongRow.vue'
import SingerRow from '@/components/SingerRow.vue'
import { useUserStore } from '@/stores/user'
import { usePlayerStore } from '@/stores/player'
import { useAuthPromptStore } from '@/stores/authPrompt'
import {
  getHotChart,
  getNewSongs,
  getRecommendSongs,
  getGuessSongs,
  getHotSingers,
  type SongVO,
  type SingerVO,
} from '@/api/home'
import { addFavorite, removeFavorite, getFavoriteSongIds } from '@/api/favorite'

const router = useRouter()
const userStore = useUserStore()
const playerStore = usePlayerStore()
const authPrompt = useAuthPromptStore()

/** 暮色场景带问候语：按当前时段切换（夜晚来的人看到"晚上好"） */
const hour = new Date().getHours()
const greeting = hour >= 23 || hour < 5
  ? '夜这么深了，听会儿歌吧'
  : hour >= 18
    ? '晚上好，听点什么？'
    : hour >= 12
      ? '下午好，来点音乐'
      : '早上好，从一首歌开始'

/** 场景快捷入口：电台三张代表频道（完整频道在电台页） */
const sceneCards = [
  { key: 'sleep', label: '睡前', freq: 'FM 88.4', desc: '声音调暗，夜就深了', path: '/radio' },
  { key: 'sport', label: '运动', freq: 'FM 97.5', desc: '心跳跟上鼓点', path: '/radio' },
  { key: 'emo', label: 'emo', freq: 'FM 101.3', desc: '丧的时候，歌懂你', path: '/radio' },
]

const loading = ref(false)
const newSongs = ref<SongVO[]>([])
const recommendSongs = ref<SongVO[]>([])
const hotSongs = ref<SongVO[]>([])
const singers = ref<SingerVO[]>([])
const guessSongs = ref<SongVO[]>([])

// 已收藏的歌曲 ID 集合（登录后加载，驱动星标状态）
const favoredIds = ref<Set<number>>(new Set())

async function loadFavorites() {
  if (!userStore.isLoggedIn) {
    favoredIds.value = new Set()
    return
  }
  try {
    const ids = await getFavoriteSongIds()
    favoredIds.value = new Set(ids)
  } catch {
    favoredIds.value = new Set()
  }
}

async function toggleFavorite(song: SongVO) {
  if (!userStore.isLoggedIn) {
    authPrompt.open(() => undefined)
    return
  }
  const wasFavored = favoredIds.value.has(song.id)
  try {
    if (wasFavored) {
      await removeFavorite(song.id)
      favoredIds.value.delete(song.id)
      favoredIds.value = new Set(favoredIds.value)
    } else {
      await addFavorite(song.id)
      favoredIds.value.add(song.id)
      favoredIds.value = new Set(favoredIds.value)
    }
  } catch {
    // 拦截器已提示
  }
}

/** 单击/双击卡片都触发播放 */
function playSong(song: SongVO, list: SongVO[]) {
  const index = list.findIndex((s) => s.id === song.id)
  playerStore.playQueue(list, index < 0 ? 0 : index)
}

/** 各模块"更多"跳转：新歌/推荐/热门 → 榜单页对应 tab；歌手 → 歌手榜 */
function goMore(kind: 'new' | 'recommend' | 'hot' | 'singer') {
  router.push({ path: '/explore', query: { tab: kind } })
}

async function loadHome() {
  loading.value = true
  try {
    // allSettled 容错：单个模块失败不阻塞整页
    const [newRes, recoRes, chartRes, hotSinger, guessRes] = await Promise.allSettled([
      getNewSongs(10),
      getRecommendSongs(10),
      getHotChart(10),
      getHotSingers(10),
      getGuessSongs(10),
    ])
    if (newRes.status === 'fulfilled') newSongs.value = newRes.value
    if (recoRes.status === 'fulfilled') recommendSongs.value = recoRes.value
    if (chartRes.status === 'fulfilled') hotSongs.value = chartRes.value
    if (hotSinger.status === 'fulfilled') singers.value = hotSinger.value
    if (guessRes.status === 'fulfilled') guessSongs.value = guessRes.value
  } finally {
    loading.value = false
  }
}

/** 播放整个猜你喜欢板块（可整体当歌单听） */
function playGuessAll() {
  if (guessSongs.value.length) {
    playerStore.playQueue(guessSongs.value, 0)
  }
}

onMounted(() => {
  loadHome()
  loadFavorites()
})
</script>

<template>
  <div class="page">
    <TopNav />

    <!-- ===== 暮色场景带（签名元素）：深色带 + 衬线问候 + 电台场景卡 ===== -->
    <section class="dusk-band">
      <div class="dusk-inner">
        <div class="dusk-copy">
          <span class="yx-eyebrow dusk-eyebrow">MOOD RADIO</span>
          <h1 class="dusk-title yx-display">{{ greeting }}</h1>
          <p class="dusk-sub">
            <span class="dusk-lamp" />AI 心情电台已开播 · 选个频道，歌替你串好
          </p>
          <el-button size="large" class="dusk-cta" @click="router.push('/radio')">
            进入心情电台
          </el-button>
        </div>
        <div class="scene-row">
          <button
            v-for="sc in sceneCards"
            :key="sc.key"
            type="button"
            class="scene-card"
            @click="router.push(sc.path)"
          >
            <span class="scene-lamp" />
            <span class="scene-freq tabular-nums">{{ sc.freq }}</span>
            <span class="scene-label">{{ sc.label }}</span>
            <span class="scene-desc">{{ sc.desc }}</span>
          </button>
        </div>
      </div>
    </section>

    <!-- ===== 四模块（左图右文榜单行）：新歌首发 / 推荐歌曲 / 热门歌曲 / 推荐歌手 ===== -->
    <main v-loading="loading" class="main">
      <!-- 猜你喜欢：风格画像个性化推荐，可整块当歌单播放 -->
      <section v-if="guessSongs.length" class="module">
        <header class="module-head">
          <div class="guess-head">
            <div>
              <span class="yx-eyebrow">FOR YOU</span>
              <h2 class="module-title yx-display">猜你喜欢</h2>
            </div>
            <span class="guess-sub">
              <el-icon :size="12"><Headset /></el-icon>
              按你的口味画像推荐
            </span>
          </div>
          <el-button
            class="guess-play-all"
            type="primary"
            round
            :icon="VideoPlay"
            @click="playGuessAll"
          >
            播放全部（{{ guessSongs.length }}首）
          </el-button>
        </header>
        <div class="row-grid">
          <SongRow
            v-for="(song, i) in guessSongs.slice(0, 10)"
            :key="song.id"
            :song="song"
            :rank="i + 1"
            :favored="favoredIds.has(song.id)"
            @play="playSong(song, guessSongs)"
            @toggle-fav="toggleFavorite"
          />
        </div>
      </section>

      <!-- 新歌首发 -->
      <section class="module">
        <header class="module-head">
          <div>
            <span class="yx-eyebrow">FRESH</span>
            <h2 class="module-title yx-display">新歌首发</h2>
          </div>
          <a class="module-more" @click="goMore('new')">
            更多
            <el-icon :size="12"><CaretRight /></el-icon>
          </a>
        </header>
        <div class="row-grid">
          <SongRow
            v-for="(song, i) in newSongs.slice(0, 10)"
            :key="song.id"
            :song="song"
            :rank="i + 1"
            :favored="favoredIds.has(song.id)"
            @play="playSong(song, newSongs)"
            @toggle-fav="toggleFavorite"
          />
        </div>
      </section>

      <!-- 推荐歌曲 -->
      <section class="module">
        <header class="module-head">
          <div>
            <span class="yx-eyebrow">MADE FOR YOU</span>
            <h2 class="module-title yx-display">{{ userStore.isLoggedIn ? '为你推荐' : '推荐歌曲' }}</h2>
          </div>
          <a class="module-more" @click="goMore('recommend')">
            更多
            <el-icon :size="12"><CaretRight /></el-icon>
          </a>
        </header>
        <div class="row-grid">
          <SongRow
            v-for="(song, i) in recommendSongs.slice(0, 10)"
            :key="song.id"
            :song="song"
            :rank="i + 1"
            :favored="favoredIds.has(song.id)"
            @play="playSong(song, recommendSongs)"
            @toggle-fav="toggleFavorite"
          />
        </div>
      </section>

      <!-- 热门歌曲 -->
      <section class="module">
        <header class="module-head">
          <div>
            <span class="yx-eyebrow">TOP CHART</span>
            <h2 class="module-title yx-display">热门歌曲</h2>
          </div>
          <a class="module-more" @click="goMore('hot')">
            更多
            <el-icon :size="12"><CaretRight /></el-icon>
          </a>
        </header>
        <div class="row-grid">
          <SongRow
            v-for="(song, i) in hotSongs.slice(0, 10)"
            :key="song.id"
            :song="song"
            :rank="i + 1"
            :favored="favoredIds.has(song.id)"
            @play="playSong(song, hotSongs)"
            @toggle-fav="toggleFavorite"
          />
        </div>
      </section>

      <!-- 推荐歌手 -->
      <section class="module">
        <header class="module-head">
          <div>
            <span class="yx-eyebrow">ARTISTS</span>
            <h2 class="module-title yx-display">推荐歌手</h2>
          </div>
          <a class="module-more" @click="goMore('singer')">
            更多
            <el-icon :size="12"><CaretRight /></el-icon>
          </a>
        </header>
        <div class="row-grid">
          <SingerRow
            v-for="(singer, i) in singers.slice(0, 10)"
            :key="singer.id"
            :singer="singer"
            :rank="i + 1"
            @open="router.push(`/singer/${singer.id}`)"
          />
        </div>
      </section>
    </main>

    <footer class="footer">
      <div class="footer-inner">
        <p>悦享音乐 — 音乐分享与播放平台</p>
        <p class="footer-sub">歌曲、歌手与歌单内容由本系统用户发布，仅用于课程演示。</p>
      </div>
    </footer>
  </div>
</template>

<style scoped>
.page {
  min-height: 100%;
  display: flex;
  flex-direction: column;
}

.main {
  flex: 1;
  width: 100%;
  max-width: 1280px;
  margin: 0 auto;
  padding: 24px 24px 40px;
}

/* 模块间距统一 24px */
.module {
  margin-bottom: 24px;
}

.module:last-child {
  margin-bottom: 0;
}

.module-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  margin-bottom: 14px;
}

/* 猜你喜欢头部：标题+来源说明并排，右侧播放全部 */
.guess-head {
  display: flex;
  align-items: baseline;
  gap: 14px;
  min-width: 0;
}

.guess-sub {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  color: var(--yx-text-secondary);
  white-space: nowrap;
}

.guess-play-all {
  flex-shrink: 0;
}

/* 章节标题：眉标在上（.yx-eyebrow 全局），衬线大标题在下，替换原 border-left 色条 */
.module-title {
  font-size: 21px;
  line-height: 1.3;
  color: var(--yx-text-main);
}

.guess-head > div,
.module-head > div {
  display: flex;
  flex-direction: column;
  gap: 3px;
}

.guess-head .guess-sub {
  align-self: flex-end;
}

/* ===== 暮色场景带（签名元素） =====
 * 全站唯一的深色+渐变区：夜空渐变自上而下沉入夜幕，衬线大字 + 琥珀信号灯 */
.dusk-band {
  background: var(--yx-dusk-gradient);
}

.dusk-inner {
  max-width: 1280px;
  margin: 0 auto;
  padding: 56px 24px 48px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 40px;
}

.dusk-copy {
  min-width: 0;
}

.dusk-eyebrow {
  color: var(--yx-amber);
}

.dusk-title {
  margin: 10px 0 14px;
  font-size: 40px;
  line-height: 1.2;
  color: var(--yx-night-text);
}

.dusk-sub {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 15px;
  color: var(--yx-night-text-dim);
}

/* 琥珀信号灯：暗处一盏暖灯（与电台页同一元素语言） */
.dusk-lamp {
  width: 9px;
  height: 9px;
  border-radius: 50%;
  background: var(--yx-amber);
  box-shadow: 0 0 10px rgba(240, 179, 78, 0.8);
  flex-shrink: 0;
  animation: dusk-lamp-breathe 1.8s infinite ease-in-out;
}

@media (prefers-reduced-motion: reduce) {
  .dusk-lamp {
    animation: none;
  }
}

@keyframes dusk-lamp-breathe {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.4; }
}

.dusk-cta {
  margin-top: 22px;
  border: none;
  color: #1b2233;
  background: var(--yx-amber);
  font-weight: 600;
}

.dusk-cta:hover {
  color: #1b2233;
  filter: brightness(1.08);
  background: var(--yx-amber);
  border: none;
}

/* 场景卡：暮色玻璃面，hover 灯亮上浮；渐变统一在蓝紫夜空色相内 */
.scene-row {
  display: flex;
  gap: 16px;
  flex-shrink: 0;
}

.scene-card {
  position: relative;
  width: 150px;
  padding: 18px 16px 16px;
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 4px;
  background: rgba(27, 34, 51, 0.55);
  border: 1px solid rgba(143, 160, 194, 0.25);
  border-radius: 14px;
  cursor: pointer;
  text-align: left;
  transition: transform 0.18s ease, border-color 0.18s ease, background 0.18s ease;
}

.scene-card:hover {
  transform: translateY(-4px);
  border-color: rgba(240, 179, 78, 0.55);
  background: rgba(27, 34, 51, 0.75);
}

.scene-lamp {
  position: absolute;
  top: 16px;
  right: 14px;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #3a4666;
  transition: background 0.18s ease, box-shadow 0.18s ease;
}

.scene-card:hover .scene-lamp {
  background: var(--yx-amber);
  box-shadow: 0 0 8px rgba(240, 179, 78, 0.8);
}

.scene-freq {
  font-size: 11px;
  letter-spacing: 2px;
  color: var(--yx-night-text-dim);
  font-variant-numeric: tabular-nums;
}

.scene-label {
  font-size: 20px;
  font-weight: 700;
  color: var(--yx-night-text);
}

.scene-desc {
  font-size: 12px;
  color: var(--yx-night-text-dim);
}

@media (max-width: 900px) {
  .dusk-inner {
    flex-direction: column;
    align-items: flex-start;
    padding: 40px 20px 36px;
  }

  .scene-row {
    width: 100%;
  }

  .scene-card {
    flex: 1;
  }

  .dusk-title {
    font-size: 30px;
  }
}

/* 更多按钮 */
.module-more {
  display: inline-flex;
  align-items: center;
  gap: 2px;
  font-size: 13px;
  color: var(--yx-text-secondary);
  cursor: pointer;
  transition: color 0.15s ease, gap 0.15s ease;
}

.module-more:hover {
  color: var(--yx-accent);
  gap: 5px;
}

/* 榜单行布局：双列行式列表，窄屏合并为单列 */
.row-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 4px 24px;
}

@media (max-width: 760px) {
  .row-grid {
    grid-template-columns: minmax(0, 1fr);
  }
}

/* 页脚 */
.footer {
  background: #fff;
  border-top: 1px solid var(--yx-hairline);
}

.footer-inner {
  max-width: 1160px;
  margin: 0 auto;
  padding: 22px 20px;
  text-align: center;
  font-size: 13px;
  color: var(--yx-text-secondary);
}

.footer-sub {
  margin-top: 6px;
  font-size: 12px;
  color: var(--yx-text-disabled);
}
</style>
