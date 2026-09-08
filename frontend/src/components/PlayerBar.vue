<script setup lang="ts">
import { nextTick, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { CaretRight, Headset, Mute, Plus, Star, VideoPause, VideoPlay } from '@element-plus/icons-vue'
import { usePlayerStore } from '@/stores/player'
import { useUserStore } from '@/stores/user'
import { addFavorite } from '@/api/favorite'
import { getMyPlaylists, addSongToPlaylist, type PlaylistVO } from '@/api/playlist'
import SongCover from '@/components/SongCover.vue'

const playerStore = usePlayerStore()
const userStore = useUserStore()

const audio = ref<HTMLAudioElement>()

// audio 元素常驻渲染，挂载后注入 store（flush: post 确保在 DOM 更新后执行）
watch(audio, (el) => {
  if (el) {
    playerStore.bindAudio(el)
  }
}, { immediate: true, flush: 'post' })

function formatTime(sec: number): string {
  if (!Number.isFinite(sec) || sec < 0) return '00:00'
  const m = Math.floor(sec / 60)
  const s = Math.floor(sec % 60)
  return `${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`
}

const modeLabel: Record<string, string> = {
  order: '顺序播放',
  loop: '列表循环',
  one: '单曲循环',
  random: '随机播放',
}

async function handleFavorite() {
  const song = playerStore.currentSong
  if (!song) return
  if (!userStore.isLoggedIn) {
    ElMessage.info('登录后即可收藏歌曲')
    return
  }
  try {
    await addFavorite(song.id)
    ElMessage.success(`已收藏《${song.name}》`)
  } catch {
    // 重复收藏等业务错误已由拦截器提示
  }
}

/** 是否显示歌词面板 */
const showLyric = ref(false)

/** 歌词面板容器（滚动控制用） */
const lyricBody = ref<HTMLDivElement>()

/** 歌词行元素引用（点击 seek + 自动滚动定位） */
const lyricLineEls = ref<HTMLParagraphElement[]>([])

/** 用户手动滚动后暂停自动跟随，几秒无操作后恢复 */
const lyricFollowPaused = ref(false)
let followResumeTimer: ReturnType<typeof setTimeout> | null = null

function onLyricWheel() {
  lyricFollowPaused.value = true
  if (followResumeTimer) clearTimeout(followResumeTimer)
  // 3 秒无操作后恢复自动跟随
  followResumeTimer = setTimeout(() => {
    lyricFollowPaused.value = false
  }, 3000)
}

/** 点击歌词行 → 跳转播放进度 */
function seekToLyric(index: number) {
  const line = playerStore.lyricLines[index]
  if (!line) return
  playerStore.seek(line.time)
  // 跳转后立即恢复跟随，避免视觉滞后
  lyricFollowPaused.value = false
}

/** 当前歌词行变化时，把它平滑滚动到面板中央（歌词同步滚动） */
watch(
  () => playerStore.currentLyricIndex,
  async (idx) => {
    if (!showLyric.value || lyricFollowPaused.value || idx < 0) return
    await nextTick()
    const body = lyricBody.value
    const el = lyricLineEls.value[idx]
    if (!body || !el) return
    const target = el.offsetTop - (body.clientHeight - el.offsetHeight) / 2
    body.scrollTo({ top: Math.max(0, target), behavior: 'smooth' })
  },
)

/** 打开面板时先把当前行定位到中央（不动画，直接落位） */
watch(showLyric, async (open) => {
  if (!open) return
  await nextTick()
  const body = lyricBody.value
  const idx = playerStore.currentLyricIndex
  const el = idx >= 0 ? lyricLineEls.value[idx] : undefined
  if (body && el) {
    const target = el.offsetTop - (body.clientHeight - el.offsetHeight) / 2
    body.scrollTop = Math.max(0, target)
  }
})

/** ===== 歌词浮层拖动（标题栏按住拖移，位置存 localStorage 记忆） ===== */
const lyricPanel = ref<HTMLDivElement>()
const panelPos = reactive({ x: 0, y: 0 })
const panelDragging = ref(false)
let dragStartX = 0
let dragStartY = 0
let panelStartX = 0
let panelStartY = 0

// 恢复上次位置（相对右下角的偏移）
const savedPos = localStorage.getItem('lyricPanelPos')
if (savedPos) {
  try {
    const p = JSON.parse(savedPos) as { x: number; y: number }
    panelPos.x = p.x
    panelPos.y = p.y
  } catch {
    // 旧格式忽略
  }
}

function onPanelDragStart(e: MouseEvent) {
  panelDragging.value = true
  dragStartX = e.clientX
  dragStartY = e.clientY
  panelStartX = panelPos.x
  panelStartY = panelPos.y
  window.addEventListener('mousemove', onPanelDragMove)
  window.addEventListener('mouseup', onPanelDragEnd)
}

function onPanelDragMove(e: MouseEvent) {
  if (!panelDragging.value) return
  const nx = panelStartX + (e.clientX - dragStartX)
  const ny = panelStartY + (e.clientY - dragStartY)
  // 限制在视口内（粗略边界，防止拖出屏幕）
  panelPos.x = Math.min(window.innerWidth - 120, Math.max(-window.innerWidth + 240, nx))
  panelPos.y = Math.min(window.innerHeight - 68, Math.max(-window.innerHeight + 160, ny))
}

function onPanelDragEnd() {
  panelDragging.value = false
  window.removeEventListener('mousemove', onPanelDragMove)
  window.removeEventListener('mouseup', onPanelDragEnd)
  localStorage.setItem('lyricPanelPos', JSON.stringify({ x: panelPos.x, y: panelPos.y }))
}

/** 进度条本地拖动值：拖动期间显示它而非播放进度 */
const dragTime = ref(0)

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

/** 加入歌单：下拉列出我的歌单 */
const playlistVisible = ref(false)
const myPlaylists = ref<PlaylistVO[]>([])

async function openPlaylistMenu() {
  if (!userStore.isLoggedIn) {
    ElMessage.info('登录后即可管理歌单')
    return
  }
  try {
    myPlaylists.value = await getMyPlaylists()
    playlistVisible.value = true
  } catch {
    // 拦截器已提示
  }
}

async function handleAddToPlaylist(pl: PlaylistVO) {
  const song = playerStore.currentSong
  if (!song) return
  try {
    await addSongToPlaylist(pl.id, song.id)
    ElMessage.success(`已加入「${pl.title}」`)
    playlistVisible.value = false
  } catch {
    // 拦截器已提示（已在歌单/歌曲不存在等）
  }
}
</script>

<template>
  <!-- audio 常驻（不受 v-if 影响）：首次点歌时元素必须已存在，store 才能立即设置 src 起播。
       注意 volume 不走 attribute 绑定（HTML 无此属性，setAttribute 无效），由 store 统一设置 -->
  <audio
    ref="audio"
    @timeupdate="playerStore.onTimeUpdate"
    @loadedmetadata="playerStore.onLoadedMetadata"
    @ended="playerStore.onEnded"
    @play="playerStore.onPlay"
    @pause="playerStore.onPause"
    @seeked="playerStore.onSeeked"
  />

  <!-- 无歌曲时播放条隐藏，避免占位 -->
  <div v-if="playerStore.hasCurrent" class="player-bar">

    <!-- 左：歌曲信息 + 收藏（封面/歌名可点开全屏播放页） -->
    <div class="track">
      <template v-if="playerStore.currentSong">
        <div class="track-cover" @click="playerStore.openFullScreen()" title="展开全屏播放页">
          <SongCover
            :cover="playerStore.currentSong.cover"
            :name="playerStore.currentSong.name"
            :seed="playerStore.currentSong.id"
            radius="6px"
          />
          <span v-if="playerStore.playing" class="track-wave" aria-hidden="true">
            <i /><i /><i />
          </span>
        </div>
        <div class="track-info" @click="playerStore.openFullScreen()" style="cursor: pointer">
          <span class="track-name">{{ playerStore.currentSong.name }}</span>
          <span class="track-artist">{{ playerStore.currentSong.singerName || '未知歌手' }}</span>
        </div>
        <el-icon class="track-fav" :size="16" @click="handleFavorite"><Star /></el-icon>
        <el-popover v-model:visible="playlistVisible" width="240" trigger="click">
          <template #reference>
            <el-icon class="track-fav" :size="15" @click="openPlaylistMenu"><Plus /></el-icon>
          </template>
          <div class="pl-menu">
            <p class="pl-menu-title">加入歌单</p>
            <p v-if="myPlaylists.length === 0" class="pl-menu-empty">
              还没有歌单，去「我的音乐」创建一个吧
            </p>
            <p v-for="pl in myPlaylists" :key="pl.id" class="pl-menu-item" @click="handleAddToPlaylist(pl)">
              {{ pl.title }}
              <span class="pl-menu-count">{{ pl.songCount ?? 0 }}首</span>
            </p>
          </div>
        </el-popover>
      </template>
    </div>

    <!-- 中：控制 + 进度 -->
    <div class="center">
      <div class="controls">
        <el-icon class="ctl" :class="{ 'is-locked': playerStore.radioMode }" :size="17" @click="playerStore.prev()"><CaretLeft /></el-icon>
        <div class="ctl-main" @click="playerStore.toggle()">
          <el-icon :size="20">
            <VideoPause v-if="playerStore.playing" />
            <VideoPlay v-else />
          </el-icon>
        </div>
        <el-icon class="ctl" :class="{ 'is-locked': playerStore.radioMode }" :size="17" @click="playerStore.next()"><CaretRight /></el-icon>
      </div>
      <div class="progress">
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
    </div>

    <!-- 右：全屏入口 + 歌词开关 + 循环模式 + 音量 -->
    <div class="right">
      <span class="right-btn" title="全屏播放页" @click="playerStore.openFullScreen()">⤢</span>
      <span class="right-btn" :class="{ 'is-on': showLyric }" @click="showLyric = !showLyric">词</span>
      <span class="right-btn" :title="modeLabel[playerStore.playMode]" @click="playerStore.cyclePlayMode()">
        {{ { order: '⇥', loop: '↻', one: '①', random: '⤨' }[playerStore.playMode] }}
      </span>
      <!-- 静音开关：点击切换；音量条拖动实时生效（input 而非 change） -->
      <el-icon
        :size="16"
        class="right-icon vol-icon"
        :title="playerStore.muted ? '取消静音' : '静音'"
        @click="playerStore.toggleMute()"
      >
        <Mute />
      </el-icon>
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
  </div>

  <!-- 全局错误横幅：音频加载失败可见反馈（此前静默，用户以为"没反应"） -->
  <teleport to="body">
    <transition name="banner-fade">
      <div v-if="playerStore.loadError" class="play-error-banner" role="alert">
        <span class="banner-text">⚠ {{ playerStore.loadError }}</span>
        <span class="banner-close" @click="playerStore.loadError = ''">✕</span>
      </div>
    </transition>
  </teleport>

  <!-- 歌词浮层（标题栏可拖动，位置记忆） -->
  <teleport to="body">
    <div
      v-if="showLyric && playerStore.hasCurrent"
      ref="lyricPanel"
      class="lyric-panel"
      :style="{ transform: `translate(${panelPos.x}px, ${panelPos.y}px)` }"
      :class="{ 'is-dragging': panelDragging }"
    >
      <div class="lyric-head" @mousedown.prevent="onPanelDragStart">
        <span class="lyric-title">{{ playerStore.currentSong?.name }} — {{ playerStore.currentSong?.singerName || '未知歌手' }}</span>
        <el-icon :size="16" @click="showLyric = false"><Close /></el-icon>
      </div>
      <div ref="lyricBody" class="lyric-body" @wheel="onLyricWheel" @touchmove="onLyricWheel">
        <p v-if="playerStore.lyricLines.length === 0" class="lyric-empty">暂无歌词</p>
        <p class="lyric-spacer" aria-hidden="true" />
        <p
          v-for="(line, index) in playerStore.lyricLines"
          :key="index"
          :ref="(el) => { if (el) lyricLineEls[index] = el as HTMLParagraphElement }"
          class="lyric-line"
          :class="{ 'is-active': index === playerStore.currentLyricIndex }"
          :title="`跳到 ${Math.floor(line.time / 60)}:${String(Math.floor(line.time % 60)).padStart(2, '0')}`"
          @click="seekToLyric(index)"
        >
          {{ line.text }}
        </p>
        <p class="lyric-spacer" aria-hidden="true" />
      </div>
      <p class="lyric-hint">点击歌词可跳转播放</p>
    </div>
  </teleport>
</template>

<style scoped>
/* ===== 深色玻璃底栏：顶部品牌蓝细节线 ===== */
.player-bar {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  z-index: 200;
  height: 68px;
  display: flex;
  align-items: center;
  gap: 20px;
  padding: 0 20px;
  background: var(--yx-dark-surface);
  backdrop-filter: blur(12px);
  border-top: 1px solid rgba(255, 255, 255, 0.06);
  color: #fff;
  box-shadow: 0 -6px 24px rgba(0, 0, 0, 0.25);
}

/* 左：曲目信息 */
.track {
  width: 250px;
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}

.track-cover {
  position: relative;
  width: 46px;
  height: 46px;
  border-radius: 6px;
  overflow: hidden;
  flex-shrink: 0;
}

/* 播放中的律动标记 */
.track-wave {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: flex-end;
  justify-content: center;
  gap: 2px;
  padding-bottom: 3px;
  background: rgba(0, 0, 0, 0.4);
}

.track-wave i {
  width: 3px;
  height: 6px;
  border-radius: 1px;
  background: var(--yx-accent-on-dark);
  animation: wave 0.9s ease-in-out infinite alternate;
}

.track-wave i:nth-child(2) { animation-delay: 0.2s; }
.track-wave i:nth-child(3) { animation-delay: 0.45s; }

@keyframes wave {
  from { transform: scaleY(0.5); }
  to { transform: scaleY(1.8); }
}

.track-info {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 3px;
}

.track-name {
  font-size: 14px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.track-artist {
  font-size: 12px;
  color: rgba(255, 255, 255, 0.55);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.track-fav {
  color: rgba(255, 255, 255, 0.6);
  cursor: pointer;
  flex-shrink: 0;
}

.track-fav:hover {
  color: var(--yx-accent);
}

/* 加歌菜单 */
.pl-menu {
  margin: -12px;
}

.pl-menu-title {
  padding: 10px 14px 8px;
  font-size: 13px;
  font-weight: 600;
  border-bottom: 1px solid var(--yx-hairline);
}

.pl-menu-empty {
  padding: 16px 14px;
  font-size: 12px;
  color: var(--yx-text-secondary);
}

.pl-menu-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 9px 14px;
  font-size: 13px;
  cursor: pointer;
}

.pl-menu-item:hover {
  background: var(--yx-accent-weak);
  color: var(--yx-accent);
}

.pl-menu-count {
  font-size: 11px;
  color: var(--yx-text-disabled);
}

/* 中：控制与进度 */
.center {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.controls {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 24px;
}

.ctl {
  cursor: pointer;
  color: rgba(255, 255, 255, 0.72);
}

.ctl:hover {
  color: #fff;
}

/* 电台模式：切歌/进度锁定态（store 已兜底拦截，这里只做视觉提示） */
.ctl.is-locked {
  cursor: not-allowed;
  opacity: 0.35;
}

.ctl.is-locked:hover {
  color: rgba(255, 255, 255, 0.72);
}

.slider.is-locked :deep(.el-slider__runway) {
  cursor: not-allowed;
}

.ctl-main {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  background: #fff;
  color: #3a3a3e;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: transform 0.12s ease, color 0.12s ease;
}

.ctl-main:hover {
  transform: scale(1.06);
  color: #1c5cab;
}

.progress {
  display: flex;
  align-items: center;
  gap: 10px;
}

.time {
  font-size: 11px;
  color: rgba(255, 255, 255, 0.55);
  width: 38px;
  text-align: center;
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
  width: 11px;
  height: 11px;
  background: #fff;
  border: none;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.4);
}

/* 右：功能键 */
.right {
  width: 250px;
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 14px;
}

.right-btn {
  width: 26px;
  height: 26px;
  display: flex;
  align-items: center;
  justify-content: center;
  border: 1px solid rgba(255, 255, 255, 0.25);
  border-radius: 4px;
  font-size: 13px;
  cursor: pointer;
  color: rgba(255, 255, 255, 0.7);
}

.right-btn:hover,
.right-btn.is-on {
  color: #fff;
  border-color: var(--yx-accent-on-dark);
}

.right-icon {
  color: rgba(255, 255, 255, 0.6);
}

.vol-icon {
  cursor: pointer;
  transition: color 0.15s ease;
}

.vol-icon:hover {
  color: #fff;
}

.vol-slider {
  width: 80px;
}

.vol-slider :deep(.el-slider__runway) {
  height: 4px;
  background: rgba(255, 255, 255, 0.18);
  border-radius: 2px;
}

.vol-slider :deep(.el-slider__button) {
  width: 11px;
  height: 11px;
  background: #fff;
  border: none;
}

/* 歌词浮层 */
.lyric-panel {
  position: fixed;
  right: 18px;
  bottom: 82px;
  z-index: 210;
  width: 400px;
  display: flex;
  flex-direction: column;
  background: rgba(30, 34, 46, 0.98);
  border: 1px solid rgba(255, 255, 255, 0.1);
  border-radius: 12px;
  color: #fff;
  overflow: hidden;
  box-shadow: 0 12px 40px rgba(0, 0, 0, 0.35);
}

.lyric-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 13px 16px;
  font-size: 13.5px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.1);
  cursor: grab;
  flex-shrink: 0;
  user-select: none;
}

.lyric-head:active {
  cursor: grabbing;
}

.lyric-panel.is-dragging {
  transition: none;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.5);
}

.lyric-head .el-icon {
  cursor: pointer;
  color: rgba(255, 255, 255, 0.6);
}

.lyric-head .el-icon:hover {
  color: #fff;
}

.lyric-body {
  flex: 1;
  max-height: 380px;
  overflow-y: auto;
  padding: 8px 18px;
  display: flex;
  flex-direction: column;
  gap: 12px;
  /* 顶部/底部渐隐，当前行居中更聚焦 */
  mask-image: linear-gradient(180deg, transparent 0, #000 28px, #000 calc(100% - 28px), transparent 100%);
  -webkit-mask-image: linear-gradient(180deg, transparent 0, #000 28px, #000 calc(100% - 28px), transparent 100%);
  scrollbar-width: thin;
  scrollbar-color: rgba(255, 255, 255, 0.25) transparent;
}

/* 首尾占位：让第一行/最后一行也能滚动到中央 */
.lyric-spacer {
  min-height: 150px;
}

.lyric-empty {
  font-size: 13px;
  color: rgba(255, 255, 255, 0.4);
  text-align: center;
}

.lyric-line {
  font-size: 15px;
  line-height: 1.65;
  color: rgba(255, 255, 255, 0.45);
  cursor: pointer;
  transition: color 0.25s ease, transform 0.25s ease, opacity 0.25s ease;
  transform-origin: left center;
}

.lyric-line:hover {
  color: rgba(255, 255, 255, 0.8);
}

.lyric-line.is-active {
  color: var(--yx-accent-on-dark);
  font-size: 17px;
  font-weight: 700;
  transform: scale(1.02);
}

.lyric-hint {
  padding: 8px 0 10px;
  text-align: center;
  font-size: 11px;
  color: rgba(255, 255, 255, 0.3);
  flex-shrink: 0;
}

@media (max-width: 760px) {
  .right {
    display: none;
  }
  .track {
    width: 160px;
  }
}

/* ===== 播放错误横幅 ===== */
.play-error-banner {
  position: fixed;
  top: 16px;
  left: 50%;
  transform: translateX(-50%);
  z-index: 400;
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 10px 18px;
  border-radius: 8px;
  background: #2b2016;
  border: 1px solid rgba(250, 178, 25, 0.4);
  color: #fab219;
  font-size: 13.5px;
  box-shadow: 0 8px 30px rgba(0, 0, 0, 0.35);
}

.banner-close {
  cursor: pointer;
  color: rgba(250, 178, 25, 0.6);
  font-size: 12px;
}

.banner-close:hover {
  color: #fab219;
}

.banner-fade-enter-active,
.banner-fade-leave-active {
  transition: opacity 0.25s ease, transform 0.25s ease;
}

.banner-fade-enter-from,
.banner-fade-leave-to {
  opacity: 0;
  transform: translateX(-50%) translateY(-8px);
}
</style>
