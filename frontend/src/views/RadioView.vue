<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import TopNav from '@/components/TopNav.vue'
import { radioTune, type RadioTuneVO, type AiSongVO } from '@/api/ai'
import { usePlayerStore } from '@/stores/player'
import type { SongVO } from '@/api/home'

/**
 * 心情电台（共享时间轴模式）：8 个固定频道，点击即调频。
 * 播放进度是服务器时间的纯函数（当天零点起播、按节目单总时长循环取模），
 * 所有用户同一时刻听到同一首的同一秒——B 进来正好接上 A 听到的位置。
 * 客户端不连播：每首歌播完重新调频对时，误差不跨首累积（真电台也从不等你）。
 */

const router = useRouter()
const playerStore = usePlayerStore()

/** 电台频道：key 对应后端节目单映射，prompt 字段已废弃（节目单固化为官方歌单） */
interface RadioChannel {
  key: string
  /** 虚拟调频频率（签名元素：FM 刻度盘展示用） */
  freq: string
  label: string
  slogan: string
}

const CHANNELS: RadioChannel[] = [
  { key: 'sleep',  freq: '88.4',  label: '睡前',  slogan: '声音调暗，夜就深了' },
  { key: 'study',  freq: '91.7',  label: '学习',  slogan: '专注，是不被打扰的歌' },
  { key: 'commute',freq: '94.0',  label: '通勤',  slogan: '路很远，歌要够长' },
  { key: 'sport',  freq: '97.5',  label: '运动',  slogan: '心跳跟上鼓点' },
  { key: 'emo',    freq: '101.3', label: 'emo',   slogan: '丧的时候，歌懂你' },
  { key: 'happy',  freq: '103.6', label: '开心',  slogan: '好心情要配副歌' },
  { key: 'rain',   freq: '106.2', label: '雨天',  slogan: '雨声和它更配' },
  { key: 'coffee', freq: '108.9', label: '咖啡',  slogan: '一杯咖啡一首歌' },
]

const PHASE = {
  idle: 'idle',
  tuning: 'tuning',
  playing: 'playing',
} as const
type Phase = typeof PHASE[keyof typeof PHASE]

const phase = ref<Phase>(PHASE.idle)
const selected = ref<RadioChannel | null>(null)
const songs = ref<AiSongVO[]>([])
const playlistId = ref(0)
const playlistTitle = ref('')
/** 当前正在播的第几首（0 起） */
const nowPlayingIndex = ref(-1)
/** 调频动画：频率数字滚动时的临时值 */
const tuningFreq = ref('')
const tuningTimer = ref<number | null>(null)

const activeFreq = computed(() => {
  if (phase.value === PHASE.playing && selected.value) return selected.value.freq
  if (tuningFreq.value) return tuningFreq.value
  return selected.value ? selected.value.freq : '--.-'
})

const statusText = computed(() => {
  if (phase.value === PHASE.tuning) return 'TUNING'
  if (phase.value === PHASE.playing) return 'ON AIR'
  return 'STAND BY'
})

onMounted(() => {
  // 回到电台页：若电台仍在收听（store 会话跨页面存活），恢复"正在收听 XX 频道"画面而不是选台页。
  // 节目单从歌单 ID 拉最新（顺带拿到此刻正在播第几首，进度条列表高亮准确）。
  const ch = playerStore.radioChannel
  if (ch && playerStore.radioMode) {
    const known = CHANNELS.find((c) => c.key === ch)
    if (known) {
      selected.value = known
      void restorePlaying(known)
    }
  }
})

/** 恢复收听画面：拉一次 tune 同步节目单与当前位置，但不重启播放（歌本来就在放） */
async function restorePlaying(channel: RadioChannel) {
  try {
    const vo = await radioTune(channel.key)
    if (!vo.songs?.length) return
    songs.value = vo.songs
    playlistId.value = vo.playlistId || 0
    playlistTitle.value = vo.playlistTitle || playerStore.radioPlaylistTitle
    nowPlayingIndex.value = vo.currentIndex
    phase.value = PHASE.playing
  } catch {
    // 拉取失败：仍按 store 里的会话显示"已锁定"，至少不闪回选台页
    phase.value = PHASE.playing
  }
}

/**
 * 调频：拿节目单与当前位置，从第 index 首的 offset 秒起播。
 * 频道元数据写入 store 会话（跨页面存活）；离开电台页后歌播完由 store 自动对时。
 */
async function tune(channel: RadioChannel) {
  if (phase.value === PHASE.tuning) return
  phase.value = PHASE.tuning
  spinFreq(channel.freq)
  try {
    const vo: RadioTuneVO = await radioTune(channel.key)
    if (!vo.songs?.length) {
      phase.value = selected.value ? PHASE.playing : PHASE.idle
      ElMessage.warning('这个频道的节目单暂时是空的')
      return
    }
    selected.value = channel
    songs.value = vo.songs
    playlistId.value = vo.playlistId || 0
    playlistTitle.value = vo.playlistTitle || `悦享电台 · ${channel.label} FM ${channel.freq}`
    nowPlayingIndex.value = vo.currentIndex
    phase.value = PHASE.playing
    playerStore.setRadioChannel({
      channel: channel.key,
      freq: channel.freq,
      playlistId: playlistId.value,
      title: playlistTitle.value,
    })
    playerStore.playQueueAt(songs.value.map(toSong), vo.currentIndex, vo.offsetSeconds)
  } catch {
    phase.value = selected.value && selected.value !== channel ? PHASE.playing : PHASE.idle
    // 拦截器已 toast，落回原状态
  }
}

/** AiSongVO → 播放器 SongVO：url/lyric 等缺省字段由播放器 play/{id} 详情接口兜底 */
function toSong(s: AiSongVO): SongVO {
  return {
    id: s.id,
    name: s.name,
    cover: null,
    url: null,
    lyric: null,
    style: s.style,
    playCount: s.playCount,
    singerId: s.singerId ?? 0,
    singerName: s.singerName,
  }
}

/** 频率数字快速跳动一下再落定（调频手感） */
function spinFreq(target: string) {
  if (tuningTimer.value) clearInterval(tuningTimer.value)
  const seq: string[] = ['92.5', '97.8', '103.2', target]
  let i = 0
  tuningFreq.value = seq[0] ?? target
  tuningTimer.value = window.setInterval(() => {
    i++
    const next = seq[i]
    if (next === undefined) {
      if (tuningTimer.value) clearInterval(tuningTimer.value)
      tuningTimer.value = null
      tuningFreq.value = ''
      return
    }
    tuningFreq.value = next
  }, 140)
}

/** 重新对时：重新调频拿最新位置（同频道的"换一批"替代品——节目单是公共的，不重新生成） */
function resync() {
  if (phase.value === PHASE.tuning || !selected.value) return
  void tune(selected.value)
}

/** 回到选台：同时退出电台监听（store 解锁，恢复普通播放器；正在放的歌不受影响） */
function backToIdle() {
  if (phase.value === PHASE.tuning) return
  playerStore.setRadioChannel(null)
  phase.value = PHASE.idle
  selected.value = null
  songs.value = []
  nowPlayingIndex.value = -1
}

function openPlaylist() {
  if (playlistId.value) router.push(`/playlist/${playlistId.value}`)
}

function formatCount(n: number): string {
  if (n >= 100000000) return (n / 100000000).toFixed(1) + '亿'
  if (n >= 10000) return (n / 10000).toFixed(1) + '万'
  return String(n)
}
</script>

<template>
  <div class="radio-page">
    <TopNav />
    <main class="main">
      <!-- 台标 + 调频盘（签名元素）：深夜电台色，与全站浅色页面形成反差 -->
      <section class="dial-shell">
        <header class="masthead">
          <span class="mast-eyebrow">MOOD RADIO · 悦享电台</span>
          <h1 class="mast-title">心情电台</h1>
        </header>

        <div class="dial" :class="{ 'is-tuning': phase === 'tuning' }">
          <div class="onair" :class="{ 'is-on': phase !== 'idle' }">
            <span class="onair-lamp" />
            <span>{{ statusText }}</span>
          </div>
          <div class="freq tabular-nums">{{ activeFreq }}</div>
          <div class="freq-unit">FM · MHz</div>
          <p class="dial-slogan">
            {{
              phase === 'playing' && selected
                ? `已锁定 ${selected.label}频道 · ${songs[nowPlayingIndex]?.name || ''}`
                : selected
                  ? selected.slogan
                  : '选个频道，和所有人听同一秒的歌'
            }}
          </p>
        </div>
      </section>

      <!-- 选台态 -->
      <section v-if="phase === 'idle'" class="panel">
        <div class="channel-grid">
          <button
            v-for="c in CHANNELS"
            :key="c.key"
            type="button"
            class="channel"
            @click="tune(c)"
          >
            <span class="channel-label">{{ c.label }}</span>
            <span class="channel-freq tabular-nums">{{ c.freq }}</span>
          </button>
        </div>
      </section>

      <!-- 调频态 -->
      <section v-else-if="phase === 'tuning'" class="panel tuning-panel">
        <p class="tuning-text">正在为你调频…</p>
        <p class="tuning-sub">锁定 FM 频率，接上电台时间轴</p>
      </section>

      <!-- 开播态：节目单列表 -->
      <section v-else class="panel">
        <button v-if="playlistId" type="button" class="pl-card" @click="openPlaylist">
          <span class="pl-badge">歌单</span>
          <span class="pl-name">{{ playlistTitle }}</span>
          <span class="pl-arrow">›</span>
        </button>
        <ol class="song-list">
          <!-- 节目单纯展示：电台是共享时间轴，不支持点歌跳播（换歌由时间轴推进） -->
          <li
            v-for="(s, idx) in songs"
            :key="s.id"
            class="song-row"
            :class="{ 'is-now': idx === nowPlayingIndex }"
          >
            <span class="song-idx tabular-nums">
              {{ idx === nowPlayingIndex ? '♪' : idx + 1 }}
            </span>
            <span class="song-info">
              <span class="song-name">{{ s.name }}</span>
              <span class="song-meta">{{ s.singerName || '未知歌手' }} · {{ s.style || '流行' }}</span>
            </span>
            <span v-if="idx === nowPlayingIndex" class="song-live">正在播出</span>
            <span v-else class="song-count tabular-nums">{{ formatCount(s.playCount) }}次播放</span>
          </li>
        </ol>
        <div class="onair-actions">
          <button type="button" class="action-btn action-main" @click="resync">重新对时</button>
          <button type="button" class="action-btn" @click="backToIdle">重新选台</button>
        </div>
      </section>
    </main>
  </div>
</template>

<style scoped>
/* 整页深色：电台是一个"房间"，与全站浅色页面形成 entering-the-room 的反差 */
.radio-page {
  min-height: 100vh;
  background: #1b2233;
}

.main {
  max-width: 720px;
  margin: 0 auto;
  padding: 32px 20px 56px;
}

/* ===== 台标 ===== */
.mast-eyebrow {
  font-size: 12px;
  letter-spacing: 4px;
  color: #5b7095;
}

.mast-title {
  margin: 6px 0 20px;
  font-size: 30px;
  font-weight: 700;
  color: #e8edf7;
  letter-spacing: 2px;
}

/* ===== 调频盘（签名元素） ===== */
.dial {
  background: #232c45;
  border: 1px solid #33406b;
  border-radius: 16px;
  padding: 34px 28px 28px;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
}

/* ON AIR 信号灯：全页唯一暖色 */
.onair {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
  letter-spacing: 3px;
  color: #5b7095;
}

.onair-lamp {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #3a4666;
}

.onair.is-on {
  color: #f0b34e;
}

.onair.is-on .onair-lamp {
  background: #f0b34e;
  box-shadow: 0 0 8px rgba(240, 179, 78, 0.7);
  animation: lamp-breathe 1.6s infinite ease-in-out;
}

@keyframes lamp-breathe {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.35; }
}

@media (prefers-reduced-motion: reduce) {
  .onair.is-on .onair-lamp {
    animation: none;
  }
}

.freq {
  font-size: 64px;
  font-weight: 700;
  line-height: 1.15;
  color: #e8edf7;
  font-variant-numeric: tabular-nums;
}

.freq-unit {
  font-size: 12px;
  letter-spacing: 4px;
  color: #5b7095;
}

.dial-slogan {
  margin: 12px 0 0;
  font-size: 15px;
  color: #8fa0c2;
  text-align: center;
}

/* ===== 面板 ===== */
.panel {
  margin-top: 20px;
  background: #232c45;
  border: 1px solid #33406b;
  border-radius: 16px;
  padding: 24px;
}

/* 频道胶囊 */
.channel-grid {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  justify-content: center;
}

.channel {
  display: inline-flex;
  align-items: baseline;
  gap: 8px;
  padding: 10px 22px;
  background: #2c3859;
  border: 1px solid transparent;
  border-radius: 999px;
  color: #c6d2ea;
  cursor: pointer;
  font-size: 15px;
  transition: background 0.15s ease, border-color 0.15s ease;
}

.channel:hover {
  background: #33406b;
}

.channel-label {
  font-weight: 600;
}

.channel-freq {
  font-size: 12px;
  color: #7d90b8;
  font-variant-numeric: tabular-nums;
}

/* ===== 调频态 ===== */
.tuning-panel {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  padding: 48px 24px;
}

.tuning-text {
  margin: 0;
  font-size: 18px;
  color: #e8edf7;
  font-weight: 600;
}

.tuning-sub {
  margin: 0;
  font-size: 14px;
  color: #7d90b8;
}

/* ===== 开播态 ===== */
.pl-card {
  display: flex;
  align-items: center;
  gap: 12px;
  width: 100%;
  padding: 14px 16px;
  background: #2a4a86;
  border: none;
  border-radius: 10px;
  cursor: pointer;
  margin-bottom: 14px;
}

.pl-card:hover {
  filter: brightness(1.08);
}

.pl-badge {
  font-size: 12px;
  color: #fff;
  background: #5b8fe8;
  border-radius: 5px;
  padding: 2px 8px;
  flex-shrink: 0;
}

.pl-name {
  flex: 1;
  min-width: 0;
  text-align: left;
  font-size: 15px;
  color: #e8edf7;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.pl-arrow {
  color: #a8c4f5;
  font-size: 20px;
}

.song-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.song-row {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 10px 12px;
  border-radius: 8px;
  /* 节目单纯展示不可点：不给手型光标与 hover 反馈 */
}

.song-row.is-now {
  background: #2a4a86;
}

.song-idx {
  width: 26px;
  font-size: 13px;
  color: #5b7095;
  text-align: center;
  flex-shrink: 0;
  font-variant-numeric: tabular-nums;
}

.song-row.is-now .song-idx {
  color: #f0b34e;
}

.song-info {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.song-name {
  font-size: 15px;
  color: #e8edf7;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.song-meta {
  font-size: 12px;
  color: #7d90b8;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.song-count {
  flex-shrink: 0;
  font-size: 12px;
  color: #5b7095;
  font-variant-numeric: tabular-nums;
}

.song-live {
  flex-shrink: 0;
  font-size: 12px;
  letter-spacing: 1px;
  color: #f0b34e;
}

.onair-actions {
  margin-top: 20px;
  display: flex;
  gap: 12px;
}

.action-btn {
  flex: 1;
  height: 44px;
  border: none;
  border-radius: 999px;
  background: #2c3859;
  color: #c6d2ea;
  font-size: 15px;
  cursor: pointer;
  transition: filter 0.15s ease;
}

.action-btn:hover {
  filter: brightness(1.15);
}

.action-main {
  background: linear-gradient(135deg, #5b8fe8, #2a5cb8);
  color: #fff;
}
</style>
