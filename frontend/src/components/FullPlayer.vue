<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { CaretLeft, ChatDotRound, Plus, Star, StarFilled, VideoPause, VideoPlay } from '@element-plus/icons-vue'
import { usePlayerStore, PLAY_RATES } from '@/stores/player'
import { useUserStore } from '@/stores/user'
import { addFavorite, removeFavorite, getFavoriteSongIds } from '@/api/favorite'
import { followSinger, getMyFollowIds, unfollowSinger } from '@/api/singer'
import { getInteraction, likeSong, unlikeSong } from '@/api/interaction'
import SongCover from '@/components/SongCover.vue'
import SongCommentPanel from '@/components/SongCommentPanel.vue'

/** 仿网易云全屏播放页：左旋转唱片/右歌词同步滚动 + 点击歌词跳转（固定深色底，不用封面做背景） */
const playerStore = usePlayerStore()
const userStore = useUserStore()
const router = useRouter()

function formatTime(sec: number): string {
  if (!Number.isFinite(sec) || sec < 0) return '00:00'
  const m = Math.floor(sec / 60)
  const s = Math.floor(sec % 60)
  return `${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`
}

/** 倍速弹层 */
const showRate = ref(false)
function pickRate(rate: number) {
  playerStore.setPlayRate(rate)
  showRate.value = false
}

/** 倍速按钮文案：1x 显示为原速标记 */
function rateLabel(r: number): string {
  return r === 1.0 ? '1.0x' : `${r}x`
}

const modeLabel: Record<string, string> = {
  order: '顺序播放',
  loop: '列表循环',
  one: '单曲循环',
  random: '随机播放',
}

/** 收藏状态 */
const favored = ref(false)

async function loadFavored() {
  if (!userStore.isLoggedIn || !playerStore.currentSong) {
    favored.value = false
    return
  }
  try {
    const ids = await getFavoriteSongIds()
    favored.value = ids.includes(playerStore.currentSong.id)
  } catch {
    favored.value = false
  }
}

async function toggleFavorite() {
  const song = playerStore.currentSong
  if (!song) return
  if (!userStore.isLoggedIn) {
    ElMessage.info('登录后即可收藏歌曲')
    return
  }
  try {
    if (favored.value) {
      await removeFavorite(song.id)
      favored.value = false
      ElMessage.success(`已取消收藏《${song.name}》`)
    } else {
      await addFavorite(song.id)
      favored.value = true
      ElMessage.success(`已收藏《${song.name}》`)
    }
  } catch {
    // 拦截器已提示
  }
}

/** ===== 关注歌手：歌手名可点进主页 + 名字右侧关注钮 ===== */
const followed = ref(false)
const followBusy = ref(false)
/** 已关注的歌手ID集合（本会话缓存，关注/取关后就地增删） */
const followedIds = ref<Set<number>>(new Set())
let followedLoaded = false

async function loadFollowedIds() {
  if (!userStore.isLoggedIn) return
  try {
    followedIds.value = new Set(await getMyFollowIds())
    followedLoaded = true
  } catch {
    // 静默：关注态拉取失败不影响播放
  }
}

const currentSingerId = computed(() => playerStore.currentSong?.singerId ?? 0)

/** 当前歌有歌手归属才显示关注钮（关注自己由后端拒绝兜底） */
const showFollowBtn = computed(() => currentSingerId.value > 0)

async function syncFollowed() {
  if (!userStore.isLoggedIn) {
    followed.value = false
    return
  }
  if (!followedLoaded) {
    await loadFollowedIds()
  }
  followed.value = followedIds.value.has(currentSingerId.value)
}

function goSingerHome() {
  if (!currentSingerId.value) return
  // 收起播放页跳转（返回时播放不中断）
  playerStore.fullScreen = false
  router.push(`/singer/${currentSingerId.value}`)
}

async function toggleFollow() {
  const singerId = currentSingerId.value
  if (!singerId || followBusy.value) return
  if (!userStore.isLoggedIn) {
    ElMessage.info('登录后即可关注歌手')
    return
  }
  followBusy.value = true
  try {
    if (followed.value) {
      await unfollowSinger(singerId)
      followed.value = false
      followedIds.value = new Set([...followedIds.value].filter((id) => id !== singerId))
      ElMessage.success('已取消关注')
    } else {
      await followSinger(singerId)
      followed.value = true
      followedIds.value = new Set([...followedIds.value, singerId])
      ElMessage.success('已关注，新歌上架会通知你')
    }
  } catch {
    // 拦截器已提示
  } finally {
    followBusy.value = false
  }
}

/** 进度条拖动 */
const dragTime = ref(0)

/** ===== 点赞 + 评论（互动数据） ===== */
const likeCount = ref(0)
const liked = ref(false)
const commentCount = ref(0)
/** 点赞爱心 pop 动画开关 */
const likeBump = ref(false)
let likeBumpTimer: ReturnType<typeof setTimeout> | null = null
const showComments = ref(false)

function formatCount(n: number): string {
  if (n >= 100000000) return `${(n / 100000000).toFixed(1)}亿`
  if (n >= 10000) return `${(n / 10000).toFixed(1)}万`
  return String(n)
}

async function loadInteraction() {
  const song = playerStore.currentSong
  if (!song) {
    likeCount.value = 0
    liked.value = false
    commentCount.value = 0
    return
  }
  try {
    const vo = await getInteraction(song.id)
    likeCount.value = vo.likeCount
    liked.value = vo.liked
    commentCount.value = vo.commentCount
  } catch {
    // 拦截器已提示
  }
}

async function toggleLike() {
  const song = playerStore.currentSong
  if (!song) return
  if (!userStore.isLoggedIn) {
    ElMessage.info('登录后即可点赞')
    return
  }
  try {
    if (liked.value) {
      likeCount.value = await unlikeSong(song.id)
      liked.value = false
    } else {
      likeCount.value = await likeSong(song.id)
      liked.value = true
      // 点亮时的弹跳动画
      likeBump.value = false
      await nextTick()
      likeBump.value = true
      if (likeBumpTimer) clearTimeout(likeBumpTimer)
      likeBumpTimer = setTimeout(() => (likeBump.value = false), 450)
    }
  } catch {
    // 拦截器已提示（重复点赞等）
  }
}

/** 评论面板回调：发评/删评后同步顶栏计数 */
function onCommentChange(delta: number) {
  commentCount.value = Math.max(0, commentCount.value + delta)
}

/** 互动计数 10s 轮询：所有人看到的点赞/评论数是活的 */
let interactionTimer: ReturnType<typeof setInterval> | null = null

/** 切歌刷新互动数据（照收藏的 watch 模式） */
watch(
  () => playerStore.currentSong?.id,
  () => {
    liked.value = false
    loadInteraction()
  },
)

function onDragStart() {
  dragTime.value = playerStore.currentTime
  playerStore.setSeeking(true)
}

function onDragInput(v: number | number[]) {
  dragTime.value = Array.isArray(v) ? v[0]! : v
}

function onDragChange(v: number | number[]) {
  const t = Array.isArray(v) ? v[0]! : v
  // seek() 内部置 seeking 并保持到 audio seeked 事件——不能在这里提前解除，
  // 否则 timeupdate 会在浏览器异步 seek 完成前把进度条拽回旧位置
  playerStore.seek(t)
}

/** ===== 歌词同步滚动 ===== */
const lyricBody = ref<HTMLDivElement>()
const lyricLineEls = ref<HTMLParagraphElement[]>([])
const lyricFollowPaused = ref(false)
let followResumeTimer: ReturnType<typeof setTimeout> | null = null

function onLyricWheel() {
  lyricFollowPaused.value = true
  if (followResumeTimer) clearTimeout(followResumeTimer)
  followResumeTimer = setTimeout(() => {
    lyricFollowPaused.value = false
  }, 3000)
}

/** 当前行滚动到视口中央（网易云式居中） */
async function scrollLyricTo(idx: number, smooth = true) {
  await nextTick()
  const body = lyricBody.value
  const el = lyricLineEls.value[idx]
  if (!body || !el) return
  const target = el.offsetTop - (body.clientHeight - el.offsetHeight) / 2
  body.scrollTo({ top: Math.max(0, target), behavior: smooth ? 'smooth' : 'auto' })
}

watch(
  () => playerStore.currentLyricIndex,
  (idx) => {
    if (lyricFollowPaused.value || idx < 0) return
    scrollLyricTo(idx)
  },
)

watch(
  () => playerStore.currentSong?.id,
  () => {
    favored.value = false
    loadFavored()
    // 切歌后歌词回顶，待新解析完成再定位
    lyricLineEls.value = []
    nextTick(() => {
      if (playerStore.currentLyricIndex >= 0) {
        scrollLyricTo(playerStore.currentLyricIndex, false)
      }
    })
  },
)

/** 切歌同步关注态（歌手名/关注钮跟随当前歌） */
watch(currentSingerId, () => {
  followed.value = false
  syncFollowed()
})

/** 点击歌词行 → 跳转播放（电台模式下不跳，进度锁在时间轴上） */
function seekToLyric(index: number) {
  if (playerStore.radioMode) return
  const line = playerStore.lyricLines[index]
  if (!line) return
  playerStore.seek(line.time)
  lyricFollowPaused.value = false
  scrollLyricTo(index)
}

/** 空格暂停 / 左右方向键快进快退（电台模式下快退快进与切歌被锁） */
function onKeydown(e: KeyboardEvent) {
  if ((e.target as HTMLElement)?.tagName === 'INPUT' || (e.target as HTMLElement)?.tagName === 'TEXTAREA') return
  if (e.code === 'Space') {
    e.preventDefault()
    playerStore.toggle()
  } else if (playerStore.radioMode) {
    // 电台模式：方向键切歌/快进会偏离共享时间轴，忽略
    return
  } else if (e.code === 'ArrowRight') {
    playerStore.seek(Math.min(playerStore.duration, playerStore.currentTime + 5))
  } else if (e.code === 'ArrowLeft') {
    playerStore.seek(Math.max(0, playerStore.currentTime - 5))
  } else if (e.code === 'ArrowUp') {
    e.preventDefault()
    playerStore.next()
  } else if (e.code === 'ArrowDown') {
    e.preventDefault()
    playerStore.prev()
  }
}

onMounted(() => {
  window.addEventListener('keydown', onKeydown)
  loadFavored()
  loadInteraction()
  loadFollowedIds().then(() => syncFollowed())
  interactionTimer = setInterval(() => {
    // 评论面板打开时面板自己轮询列表，此处只刷顶栏计数
    if (!showComments.value) loadInteraction()
  }, 10000)
  nextTick(() => {
    if (playerStore.currentLyricIndex >= 0) {
      scrollLyricTo(playerStore.currentLyricIndex, false)
    }
  })
})

onUnmounted(() => {
  window.removeEventListener('keydown', onKeydown)
  if (followResumeTimer) clearTimeout(followResumeTimer)
  if (likeBumpTimer) clearTimeout(likeBumpTimer)
  if (interactionTimer) clearInterval(interactionTimer)
})
</script>

<template>
  <div class="full-player">
    <!-- 顶栏：返回 + 标题 -->
    <header class="fp-head">
      <button class="back-btn" title="收起播放页" @click="playerStore.closeFullScreen()">
        <el-icon :size="20"><CaretLeft /></el-icon>
      </button>
      <div class="fp-title-wrap">
        <h1 class="fp-title">{{ playerStore.currentSong?.name || '未在播放' }}</h1>
        <p class="fp-artist-row">
          <!-- 歌手名可点进歌手主页 -->
          <span
            class="fp-artist"
            :class="{ 'is-link': !!playerStore.currentSong?.singerId }"
            title="查看歌手主页"
            @click="goSingerHome"
          >
            {{ playerStore.currentSong?.singerName || '未知歌手' }}
          </span>
          <!-- 关注钮：有歌手ID且已登录时展示 -->
          <button
            v-if="showFollowBtn && userStore.isLoggedIn"
            class="follow-btn"
            :class="{ 'is-followed': followed }"
            :disabled="followBusy"
            :title="followed ? '取消关注' : '关注歌手'"
            @click.stop="toggleFollow"
          >
            <el-icon :size="11"><Plus v-if="!followed" /><StarFilled v-else /></el-icon>
            {{ followed ? '已关注' : '关注' }}
          </button>
        </p>
      </div>
      <div class="fp-actions">
        <!-- 点赞：爱心 + 数量，点赞后变红 -->
        <div
          class="head-action"
          :class="{ 'is-liked': liked, 'is-bump': likeBump }"
          :title="liked ? '取消点赞' : '点赞'"
          @click="toggleLike"
        >
          <svg v-if="liked" class="heart-icon" viewBox="0 0 24 24" aria-hidden="true">
            <path
              d="M12 21.35l-1.45-1.32C5.4 15.36 2 12.28 2 8.5 2 5.42 4.42 3 7.5 3c1.74 0 3.41.81 4.5 2.09C13.09 3.81 14.76 3 16.5 3 19.58 3 22 5.42 22 8.5c0 3.78-3.4 6.86-8.55 11.54L12 21.35z"
              fill="currentColor"
            />
          </svg>
          <svg v-else class="heart-icon" viewBox="0 0 24 24" fill="none" aria-hidden="true">
            <path
              d="M12 21.35l-1.45-1.32C5.4 15.36 2 12.28 2 8.5 2 5.42 4.42 3 7.5 3c1.74 0 3.41.81 4.5 2.09C13.09 3.81 14.76 3 16.5 3 19.58 3 22 5.42 22 8.5c0 3.78-3.4 6.86-8.55 11.54L12 21.35z"
              stroke="currentColor"
              stroke-width="1.8"
              stroke-linejoin="round"
            />
          </svg>
          <span class="head-count tabular-nums">{{ formatCount(likeCount) }}</span>
        </div>

        <!-- 收藏（原有） -->
        <el-icon
          class="fav-btn"
          :size="20"
          :class="{ 'is-favored': favored }"
          @click="toggleFavorite"
        >
          <StarFilled v-if="favored" />
          <Star v-else />
        </el-icon>

        <!-- 评论：气泡 + 数量，点击进入评论页 -->
        <div
          class="head-action"
          :class="{ 'is-active': showComments }"
          title="查看评论"
          @click="showComments = true"
        >
          <el-icon :size="18"><ChatDotRound /></el-icon>
          <span class="head-count tabular-nums">{{ formatCount(commentCount) }}</span>
        </div>
      </div>
    </header>

    <!-- 主体：左唱片 + 右歌词 -->
    <main class="fp-body">
      <!-- 左：黑胶唱片 + 唱针 -->
      <section class="disc-zone">
        <div class="needle" :class="{ 'is-playing': playerStore.playing }" aria-hidden="true">
          <span class="needle-arm" />
        </div>
        <div class="disc" :class="{ 'is-paused': !playerStore.playing }">
          <div class="disc-ring" aria-hidden="true" />
          <div class="disc-cover">
            <SongCover
              v-if="playerStore.currentSong"
              :cover="playerStore.currentSong.cover"
              :name="playerStore.currentSong.name"
              :seed="playerStore.currentSong.id"
              radius="50%"
            />
          </div>
        </div>
      </section>

      <!-- 右：歌词（居中滚动，点击跳转） -->
      <section class="lyric-zone">
        <!-- 加载/错误反馈层：覆盖在歌词区上方 -->
        <div v-if="playerStore.loading || playerStore.loadError" class="lyric-feedback">
          <template v-if="playerStore.loading">
            <span class="feedback-spinner" aria-hidden="true" />
            <p class="feedback-text">正在加载…</p>
          </template>
          <template v-else-if="playerStore.loadError">
            <p class="feedback-icon" aria-hidden="true">⚠</p>
            <p class="feedback-text">{{ playerStore.loadError }}</p>
            <button class="feedback-retry" @click="playerStore.playAt(playerStore.currentIndex)">重试</button>
          </template>
        </div>

        <div v-show="!playerStore.loading && !playerStore.loadError" ref="lyricBody" class="lyrics" @wheel="onLyricWheel" @touchmove="onLyricWheel">
          <div class="lyrics-pad" aria-hidden="true" />
          <p v-if="playerStore.lyricLines.length === 0" class="lyrics-empty">
            暂无歌词
          </p>
          <p
            v-for="(line, index) in playerStore.lyricLines"
            :key="index"
            :ref="(el) => { if (el) lyricLineEls[index] = el as HTMLParagraphElement }"
            class="lyrics-line"
            :class="{
              'is-active': index === playerStore.currentLyricIndex,
              'is-near': Math.abs(index - playerStore.currentLyricIndex) === 1,
            }"
            :title="`跳到 ${formatTime(line.time)}`"
            @click="seekToLyric(index)"
          >
            {{ line.text }}
          </p>
          <div class="lyrics-pad" aria-hidden="true" />
        </div>
        <p class="lyrics-tip">
          {{ playerStore.radioMode ? '电台模式：进度与切歌由电台时间轴控制 · 空格暂停' : '点击歌词跳转播放 · 空格暂停 · ←→ 快退快进' }}
        </p>
      </section>
    </main>

    <!-- 底部：进度 + 控制（仿网易云控制条） -->
    <footer class="fp-foot">
      <div class="progress-row">
        <span class="time tabular-nums">{{ formatTime(playerStore.seeking ? dragTime : playerStore.currentTime) }}</span>
        <el-slider
          class="slider"
          :class="{ 'is-locked': playerStore.radioMode }"
          :model-value="playerStore.seeking ? dragTime : playerStore.currentTime"
          :max="playerStore.duration || 1"
          :show-tooltip="false"
          :disabled="playerStore.radioMode"
          title="电台模式：进度由电台时间轴控制"
          @mousedown.capture="onDragStart"
          @touchstart.capture="onDragStart"
          @input="onDragInput"
          @change="onDragChange"
        />
        <span class="time tabular-nums">{{ formatTime(playerStore.duration) }}</span>
      </div>

      <div class="controls-row">
        <button
          class="mode-btn"
          :title="modeLabel[playerStore.playMode]"
          @click="playerStore.cyclePlayMode()"
        >
          {{ { order: '⇥', loop: '↻', one: '①', random: '⤨' }[playerStore.playMode] }}
        </button>
        <button class="ctl-side" :class="{ 'is-locked': playerStore.radioMode }" title="上一曲（↓）" @click="playerStore.prev()">
          <el-icon :size="22"><CaretLeft /></el-icon>
        </button>
        <button class="ctl-main" :title="playerStore.playing ? '暂停（空格）' : '播放（空格）'" @click="playerStore.toggle()">
          <span v-if="playerStore.loading" class="ctl-loading" aria-hidden="true" />
          <el-icon v-else :size="26">
            <VideoPause v-if="playerStore.playing" />
            <VideoPlay v-else />
          </el-icon>
        </button>
        <button class="ctl-side" :class="{ 'is-locked': playerStore.radioMode }" title="下一曲（↑）" @click="playerStore.next()">
          <el-icon :size="22" style="transform: scaleX(-1)"><CaretLeft /></el-icon>
        </button>
        <!-- 倍速：点开弹层选档 -->
        <button
          class="mode-btn rate-btn"
          :class="{ 'is-active': playerStore.playRate !== 1.0 }"
          title="播放倍速"
          @click="showRate = true"
        >
          {{ playerStore.playRate.toFixed(2).replace(/0+$/, '').replace(/\.$/, '') }}x
        </button>
        <!-- 音量：静音开关 + 拖动实时调节 -->
        <div class="vol-group">
          <span
            class="vol-mute"
            :title="playerStore.muted ? '取消静音' : '静音'"
            @click="playerStore.toggleMute()"
          >
            {{ playerStore.muted || playerStore.volume === 0 ? '🔇' : '🔊' }}
          </span>
          <el-slider
            class="vol-slider"
            :model-value="playerStore.muted ? 0 : playerStore.volume"
            :min="0"
            :max="1"
            :step="0.05"
            :show-tooltip="false"
            @input="playerStore.setVolume($event as number)"
          />
        </div>
        <span class="queue-count tabular-nums">队列 {{ playerStore.queue.length }} 首</span>
      </div>
    </footer>

    <!-- 倍速选择弹层 -->
    <transition name="rate-fade">
      <div v-if="showRate" class="rate-mask" @click="showRate = false">
        <div class="rate-sheet" @click.stop>
          <p class="rate-title">播放倍速</p>
          <div class="rate-options">
            <button
              v-for="r in PLAY_RATES"
              :key="r"
              class="rate-option"
              :class="{ 'is-active': playerStore.playRate === r }"
              @click="pickRate(r)"
            >
              {{ rateLabel(r) }}
            </button>
          </div>
        </div>
      </div>
    </transition>

    <!-- 评论页：覆盖在播放页之上的全屏浮层 -->
    <SongCommentPanel
      v-if="showComments"
      @close="showComments = false"
      @comment-change="onCommentChange"
    />
  </div>
</template>

<style scoped>
.full-player {
  position: fixed;
  inset: 0;
  z-index: 300;
  display: flex;
  flex-direction: column;
  /* 固定深色渐变底：不随封面变化，保证歌词始终清晰可读 */
  background: linear-gradient(160deg, #1e2433, #171c28);
  color: #fff;
  animation: fp-in 0.25s ease;
}

@keyframes fp-in {
  from { opacity: 0; }
  to { opacity: 1; }
}

.full-player > * {
  position: relative;
  z-index: 1;
}

/* ===== 顶栏 ===== */
.fp-head {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 18px 28px 8px;
}

.back-btn {
  width: 40px;
  height: 40px;
  border: none;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.1);
  color: #fff;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: background 0.15s ease, transform 0.15s ease;
}

.back-btn:hover {
  background: rgba(255, 255, 255, 0.2);
  transform: scale(1.05);
}

.fp-title-wrap {
  flex: 1;
  min-width: 0;
  text-align: center;
}

.fp-title {
  font-size: 22px;
  font-weight: 700;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.fp-artist-row {
  margin-top: 3px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
}

.fp-artist {
  font-size: 13px;
  color: rgba(255, 255, 255, 0.6);
}

/* 歌手名可点：hover 变亮并出现下划线（进歌手主页） */
.fp-artist.is-link {
  cursor: pointer;
  transition: color 0.15s ease;
}

.fp-artist.is-link:hover {
  color: var(--yx-accent-on-dark, #3987e5);
  text-decoration: underline;
  text-underline-offset: 3px;
}

/* 关注钮：小胶囊，未关注蓝底、已关注描边灰 */
.follow-btn {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  height: 20px;
  padding: 0 9px;
  border: none;
  border-radius: 999px;
  background: var(--yx-accent-on-dark, #3987e5);
  color: #fff;
  font-size: 11px;
  cursor: pointer;
  transition: all 0.15s ease;
}

.follow-btn:hover {
  filter: brightness(1.15);
}

.follow-btn.is-followed {
  background: transparent;
  border: 1px solid rgba(255, 255, 255, 0.3);
  color: rgba(255, 255, 255, 0.65);
}

.follow-btn.is-followed:hover {
  border-color: rgba(255, 255, 255, 0.55);
  color: #fff;
}

.follow-btn:disabled {
  opacity: 0.6;
  cursor: wait;
}

.fav-btn {
  color: rgba(255, 255, 255, 0.6);
  cursor: pointer;
}

.fav-btn:hover {
  color: #fff;
}

.fav-btn.is-favored {
  color: #fab219;
}

/* ===== 顶栏互动按钮（点赞/评论）：图标+计数的小组件 ===== */
.fp-actions {
  display: flex;
  align-items: center;
  gap: 18px;
}

.head-action {
  display: flex;
  align-items: center;
  gap: 5px;
  color: rgba(255, 255, 255, 0.6);
  cursor: pointer;
  transition: color 0.15s ease;
  user-select: none;
}

.head-action:hover {
  color: #fff;
}

.head-count {
  font-size: 13px;
}

.heart-icon {
  width: 19px;
  height: 19px;
  display: block;
}

.head-action.is-liked {
  color: #ff5c7a;
}

.head-action.is-liked .head-count {
  color: #ff8aa1;
}

/* 点亮瞬间的弹跳 */
.head-action.is-bump .heart-icon {
  animation: heart-bump 0.45s ease;
}

@keyframes heart-bump {
  0% { transform: scale(1); }
  35% { transform: scale(1.35); }
  65% { transform: scale(0.92); }
  100% { transform: scale(1); }
}

@media (prefers-reduced-motion: reduce) {
  .head-action.is-bump .heart-icon { animation: none; }
}

/* 评论按钮激活态（面板打开时高亮） */
.head-action.is-active {
  color: var(--yx-accent-on-dark, #3987e5);
}

/* ===== 主体 ===== */
.fp-body {
  flex: 1;
  min-height: 0;
  display: grid;
  grid-template-columns: minmax(300px, 42%) 1fr;
  gap: 40px;
  align-items: center;
  padding: 10px 6vw 20px;
}

@media (max-width: 880px) {
  .fp-body {
    grid-template-columns: 1fr;
    overflow-y: auto;
  }

  .disc-zone {
    display: none;
  }
}

/* 左：唱片区 */
.disc-zone {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  padding-top: 40px;
}

.disc {
  position: relative;
  width: min(300px, 26vw);
  aspect-ratio: 1;
  border-radius: 50%;
  background: repeating-radial-gradient(circle at center, #1c1f28 0 3px, #262a35 3px 6px);
  box-shadow: 0 24px 70px rgba(0, 0, 0, 0.55);
  /* 20 秒一圈匀速旋转；暂停时定格在当前角度 */
  animation: disc-rotate 20s linear infinite;
  animation-play-state: running;
}

.disc.is-paused {
  animation-play-state: paused;
}

@keyframes disc-rotate {
  to { transform: rotate(360deg); }
}

@media (prefers-reduced-motion: reduce) {
  .disc { animation: none; }
}

.disc-ring {
  position: absolute;
  inset: 8%;
  border-radius: 50%;
  border: 1px solid rgba(255, 255, 255, 0.06);
}

.disc-cover {
  position: absolute;
  inset: 17%;
  border-radius: 50%;
  overflow: hidden;
  box-shadow: 0 0 0 8px #10131b, 0 0 30px rgba(0, 0, 0, 0.5);
}

/* 唱针：播放压下 / 暂停抬起 -30°，锚点在针根 */
.needle {
  position: absolute;
  top: 0;
  left: 50%;
  z-index: 2;
  width: 90px;
  height: 140px;
  transform-origin: 18px 18px;
  transform: translateX(-6px) rotate(-30deg);
  transition: transform 0.4s ease;
  pointer-events: none;
}

.needle.is-playing {
  transform: translateX(-6px) rotate(0deg);
}

.needle-arm {
  position: absolute;
  top: 6px;
  left: 8px;
  width: 6px;
  height: 104px;
  border-radius: 3px;
  background: linear-gradient(180deg, #d8dbe2, #9aa0ac);
  box-shadow: 2px 2px 6px rgba(0, 0, 0, 0.4);
}

.needle-arm::before {
  content: '';
  position: absolute;
  top: -8px;
  left: -7px;
  width: 20px;
  height: 20px;
  border-radius: 50%;
  background: radial-gradient(circle at 35% 35%, #e6e9ef, #767c88);
}

.needle-arm::after {
  content: '';
  position: absolute;
  bottom: -8px;
  left: -3px;
  width: 12px;
  height: 20px;
  border-radius: 4px;
  background: #c9cdd6;
}

/* 右：歌词区 */
.lyric-zone {
  position: relative;
  min-height: 0;
  height: 100%;
  display: flex;
  flex-direction: column;
}

.lyrics {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 0 24px;
  display: flex;
  flex-direction: column;
  gap: 18px;
  mask-image: linear-gradient(180deg, transparent 0, #000 60px, #000 calc(100% - 60px), transparent 100%);
  -webkit-mask-image: linear-gradient(180deg, transparent 0, #000 60px, #000 calc(100% - 60px), transparent 100%);
  scrollbar-width: thin;
  scrollbar-color: rgba(255, 255, 255, 0.2) transparent;
}

.lyrics-pad {
  min-height: 40vh;
}

.lyrics-empty {
  text-align: center;
  padding: 60px 0;
  font-size: 15px;
  color: rgba(255, 255, 255, 0.4);
}

/* 加载/错误反馈层 */
.lyric-feedback {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 14px;
  z-index: 2;
}

.feedback-spinner {
  width: 34px;
  height: 34px;
  border-radius: 50%;
  border: 3px solid rgba(255, 255, 255, 0.2);
  border-top-color: #86b6ef;
  animation: spin 0.9s linear infinite;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

.feedback-icon {
  font-size: 34px;
}

.feedback-text {
  font-size: 15px;
  color: rgba(255, 255, 255, 0.75);
}

.feedback-retry {
  padding: 7px 22px;
  border: 1px solid rgba(255, 255, 255, 0.35);
  border-radius: 999px;
  background: transparent;
  color: #fff;
  font-size: 13px;
  cursor: pointer;
  transition: border-color 0.15s ease, background 0.15s ease;
}

.feedback-retry:hover {
  border-color: #86b6ef;
  background: rgba(134, 182, 239, 0.15);
}

.lyrics-line {
  font-size: 18px;
  line-height: 1.6;
  color: rgba(255, 255, 255, 0.38);
  cursor: pointer;
  transition: color 0.3s ease, transform 0.3s ease, font-size 0.3s ease;
  transform-origin: left center;
  user-select: none;
}

.lyrics-line:hover {
  color: rgba(255, 255, 255, 0.75);
}

.lyrics-line.is-near {
  color: rgba(255, 255, 255, 0.55);
  font-size: 19px;
}

.lyrics-line.is-active {
  color: #fff;
  font-size: 23px;
  font-weight: 700;
  transform: scale(1.02);
}

.lyrics-tip {
  padding: 10px 24px 0;
  font-size: 11.5px;
  color: rgba(255, 255, 255, 0.28);
  text-align: center;
}

/* ===== 底部控制 ===== */
.fp-foot {
  flex-shrink: 0;
  padding: 8px 6vw 26px;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.progress-row {
  display: flex;
  align-items: center;
  gap: 14px;
  max-width: 720px;
  width: 100%;
  margin: 0 auto;
}

.time {
  font-size: 12px;
  color: rgba(255, 255, 255, 0.6);
  width: 42px;
  text-align: center;
  flex-shrink: 0;
}

.slider {
  flex: 1;
}

.slider :deep(.el-slider__runway) {
  height: 4px;
  background: rgba(255, 255, 255, 0.18);
  border-radius: 2px;
}

.slider :deep(.el-slider__bar) {
  background: linear-gradient(90deg, #3987e5, #86b6ef);
  border-radius: 2px;
}

.slider :deep(.el-slider__button) {
  width: 12px;
  height: 12px;
  background: #fff;
  border: none;
  box-shadow: 0 1px 6px rgba(0, 0, 0, 0.5);
}

.controls-row {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 34px;
}

.mode-btn {
  width: 38px;
  height: 38px;
  border: 1px solid rgba(255, 255, 255, 0.25);
  border-radius: 8px;
  background: transparent;
  color: rgba(255, 255, 255, 0.75);
  font-size: 16px;
  cursor: pointer;
  transition: border-color 0.15s ease, color 0.15s ease;
}

.mode-btn:hover {
  color: #fff;
  border-color: var(--yx-accent-on-dark);
}

.ctl-side {
  width: 46px;
  height: 46px;
  border: none;
  border-radius: 50%;
  background: transparent;
  color: rgba(255, 255, 255, 0.85);
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: transform 0.15s ease, color 0.15s ease;
}

.ctl-side:hover {
  transform: scale(1.08);
  color: #fff;
}

/* 电台模式：切歌锁定态（store 已兜底，这里做视觉提示） */
.ctl-side.is-locked {
  cursor: not-allowed;
  opacity: 0.35;
}

.ctl-side.is-locked:hover {
  transform: none;
  color: rgba(255, 255, 255, 0.85);
}

.slider.is-locked :deep(.el-slider__runway) {
  cursor: not-allowed;
}

.ctl-main {
  width: 64px;
  height: 64px;
  border: none;
  border-radius: 50%;
  background: #fff;
  color: #232733;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 8px 26px rgba(0, 0, 0, 0.45);
  transition: transform 0.15s ease;
}

.ctl-main:hover {
  transform: scale(1.06);
}

.ctl-main:active {
  transform: scale(0.97);
}

.ctl-loading {
  width: 24px;
  height: 24px;
  border-radius: 50%;
  border: 3px solid rgba(35, 39, 51, 0.2);
  border-top-color: #232733;
  animation: ctl-spin 0.8s linear infinite;
}

@keyframes ctl-spin {
  to { transform: rotate(360deg); }
}

.queue-count {
  position: absolute;
  right: 6vw;
  font-size: 12px;
  color: rgba(255, 255, 255, 0.5);
}

/* 音量组：静音钮 + 音量条 */
.vol-group {
  display: flex;
  align-items: center;
  gap: 8px;
}

.vol-mute {
  font-size: 15px;
  cursor: pointer;
  opacity: 0.75;
  transition: opacity 0.15s ease;
  user-select: none;
}

.vol-mute:hover {
  opacity: 1;
}

.vol-slider {
  width: 90px;
}

.vol-slider :deep(.el-slider__runway) {
  height: 4px;
  background: rgba(255, 255, 255, 0.2);
  border-radius: 2px;
}

.vol-slider :deep(.el-slider__bar) {
  background: var(--yx-accent-on-dark, #ffc457);
}

.vol-slider :deep(.el-slider__button) {
  width: 11px;
  height: 11px;
  background: #fff;
  border: none;
}
</style>

<style scoped>
/* 倍速按钮激活态（非 1x 时高亮） */
.rate-btn.is-active {
  color: var(--yx-accent-on-dark);
  border-color: var(--yx-accent-on-dark);
}

/* 倍速弹层：底部浮出 */
.rate-mask {
  position: absolute;
  inset: 0;
  background: rgba(0, 0, 0, 0.5);
  display: flex;
  align-items: flex-end;
  justify-content: center;
  z-index: 20;
}

.rate-sheet {
  width: min(420px, 92vw);
  background: #232a3a;
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 16px 16px 0 0;
  padding: 20px 22px calc(22px + env(safe-area-inset-bottom));
}

.rate-title {
  text-align: center;
  font-size: 14px;
  color: rgba(255, 255, 255, 0.6);
  margin-bottom: 14px;
}

.rate-options {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 10px;
}

.rate-option {
  height: 42px;
  border: 1px solid rgba(255, 255, 255, 0.12);
  border-radius: 8px;
  background: transparent;
  color: rgba(255, 255, 255, 0.85);
  font-size: 14px;
  cursor: pointer;
  transition: all 0.15s ease;
}

.rate-option:hover {
  border-color: rgba(255, 255, 255, 0.3);
}

.rate-option.is-active {
  background: rgba(57, 135, 229, 0.18);
  border-color: var(--yx-accent-on-dark);
  color: var(--yx-accent-on-dark);
  font-weight: 700;
}

.rate-fade-enter-active,
.rate-fade-leave-active {
  transition: opacity 0.18s ease;
}

.rate-fade-enter-from,
.rate-fade-leave-to {
  opacity: 0;
}
</style>