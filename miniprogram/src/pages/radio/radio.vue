<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { radioTune, toSongVO, type RadioTuneVO, type AiSongVO } from '@/api/ai'
import { usePlayerStore } from '@/stores/player'
import type { SongVO } from '@/api/types'

/**
 * 心情电台（共享时间轴模式）：8 个固定频道，点击即调频。
 * 播放进度是服务器时间的纯函数（当天零点起播、按节目单总时长循环取模），
 * 所有用户同一时刻听到同一首的同一秒——B 进来正好接上 A 听到的位置。
 * 客户端不连播：每首歌播完由 store 自动对时（离开本页也照常收听），
 * 重进本页时从 store 会话恢复"正在收听 XX 频道"画面。
 */

const player = usePlayerStore()

/** 电台频道：key 对应后端节目单映射 */
interface RadioChannel {
  key: string
  /** 虚拟调频频率（签名元素：FM 刻度盘展示用） */
  freq: string
  label: string
  slogan: string
}

const CHANNELS: RadioChannel[] = [
  { key: 'sleep',   freq: '88.4',  label: '睡前',  slogan: '声音调暗，夜就深了' },
  { key: 'study',   freq: '91.7',  label: '学习',  slogan: '专注，是不被打扰的歌' },
  { key: 'commute', freq: '94.0',  label: '通勤',  slogan: '路很远，歌要够长' },
  { key: 'sport',   freq: '97.5',  label: '运动',  slogan: '心跳跟上鼓点' },
  { key: 'emo',     freq: '101.3', label: 'emo',   slogan: '丧的时候，歌懂你' },
  { key: 'happy',   freq: '103.6', label: '开心',  slogan: '好心情要配副歌' },
  { key: 'rain',    freq: '106.2', label: '雨天',  slogan: '雨声和它更配' },
  { key: 'coffee',  freq: '108.9', label: '咖啡',  slogan: '一杯咖啡一首歌' },
]

const PHASE = {
  /** 选台中 */
  idle: 'idle',
  /** 调频中 */
  tuning: 'tuning',
  /** 已开播 */
  playing: 'playing',
} as const
type Phase = (typeof PHASE)[keyof typeof PHASE]

const phase = ref<Phase>(PHASE.idle)
const selected = ref<RadioChannel | null>(null)
const songs = ref<AiSongVO[]>([])
const playlistId = ref(0)
const playlistTitle = ref('')
/** 当前正在播的第几首（0 起） */
const nowPlayingIndex = ref(-1)
/** 调频动画：频率数字滚动时的临时值 */
const tuningFreq = ref('')

const activeFreq = computed(() => {
  if (phase.value === PHASE.playing && selected.value) return selected.value.freq
  if (tuningFreq.value) return tuningFreq.value
  return selected.value ? selected.value.freq : '--.-'
})

onMounted(() => {
  // 回到电台页：若电台仍在收听（store 会话跨页面存活），恢复"正在收听 XX 频道"画面而不是选台页
  const ch = player.radioChannel
  if (ch && player.radioMode) {
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
    playlistTitle.value = vo.playlistTitle || player.radioPlaylistTitle
    nowPlayingIndex.value = vo.currentIndex
    phase.value = PHASE.playing
  } catch {
    // 拉取失败：仍按 store 里的会话显示"已锁定"，至少不闪回选台页
    phase.value = PHASE.playing
  }
}

/**
 * 调频：拿节目单与当前位置，从第 index 首的 offset 秒起播。
 * 频道元数据写入 store 会话（跨页面存活）；离开本页后歌播完由 store 自动对时。
 */
async function tune(channel: RadioChannel) {
  if (phase.value === PHASE.tuning) return
  phase.value = PHASE.tuning
  spinFreq(channel.freq)
  try {
    const vo: RadioTuneVO = await radioTune(channel.key)
    if (!vo.songs?.length) {
      phase.value = selected.value ? PHASE.playing : PHASE.idle
      uni.showToast({ title: '这个频道的节目单暂时是空的', icon: 'none' })
      return
    }
    selected.value = channel
    songs.value = vo.songs
    playlistId.value = vo.playlistId || 0
    playlistTitle.value = vo.playlistTitle || `悦享电台 · ${channel.label} FM ${channel.freq}`
    nowPlayingIndex.value = vo.currentIndex
    phase.value = PHASE.playing
    player.setRadioChannel({
      channel: channel.key,
      freq: channel.freq,
      playlistId: playlistId.value,
      title: playlistTitle.value,
    })
    // 先 setRadioChannel（置电台模式）再 playQueueAt；playQueueAt 内不清模式，
    // 清模式只发生在普通入口 playQueue，见 store
    await player.playQueueAt(songs.value.map(toSongVO), vo.currentIndex, vo.offsetSeconds)
  } catch {
    phase.value = selected.value && selected.value !== channel ? PHASE.playing : PHASE.idle
    // request 层已 toast，落回原状态
  }
}

/** 列表点选其他歌：从那首的电台当前位置听起（仍在同一时间轴上） */
function jumpTo(index: number) {
  if (phase.value !== PHASE.playing) return
  nowPlayingIndex.value = index
  player.playAt(index)
}

/** 频率数字快速跳动一下再落定（调频手感） */
function spinFreq(target: string) {
  const seq = ['92.5', '97.8', '103.2', target]
  let i = 0
  tuningFreq.value = seq[0]
  const timer = setInterval(() => {
    i++
    if (i >= seq.length) {
      clearInterval(timer)
      tuningFreq.value = ''
      return
    }
    tuningFreq.value = seq[i]
  }, 140)
}

/** 重新对时：重新调频拿最新位置（节目单是公共的，不重新生成） */
function resync() {
  if (phase.value === PHASE.tuning || !selected.value) return
  tune(selected.value)
}

/** 回到选台：同时退出电台监听（store 解锁，恢复普通播放器；正在放的歌不受影响） */
function backToIdle() {
  if (phase.value === PHASE.tuning) return
  player.setRadioChannel(null)
  phase.value = PHASE.idle
  selected.value = null
  songs.value = []
  nowPlayingIndex.value = -1
}

function openPlaylist() {
  if (playlistId.value) {
    uni.navigateTo({ url: `/pages/playlist/detail?id=${playlistId.value}` })
  }
}

function formatCount(n: number): string {
  if (n >= 100000000) return (n / 100000000).toFixed(1) + '亿'
  if (n >= 10000) return (n / 10000).toFixed(1) + '万'
  return String(n)
}
</script>

<template>
  <view class="page">
    <!-- 电台台标 -->
    <view class="masthead">
      <view class="mast-eyebrow">MOOD RADIO · 悦享电台</view>
      <view class="mast-title">心情电台</view>
    </view>

    <!-- 调频盘：签名元素 -->
    <view class="dial" :class="{ 'is-tuning': phase === 'tuning' }">
      <view class="onair" :class="{ 'is-on': phase !== 'idle' }">
        <view class="onair-lamp" />
        <text>{{
          phase === 'tuning' ? 'TUNING' : phase === 'playing' ? 'ON AIR' : 'STAND BY'
        }}</text>
      </view>
      <view class="freq" :class="{ 'freq-spin': tuningFreq }">{{ activeFreq }}</view>
      <view class="freq-unit">FM · MHz</view>
      <view class="dial-slogan">
        {{
          phase === 'playing' && selected
            ? `已锁定 ${selected.label}频道 · ${songs[nowPlayingIndex]?.name || ''}`
            : selected
              ? selected.slogan
              : '选个频道，和所有人听同一秒的歌'
        }}
      </view>
    </view>

    <!-- 选台态 -->
    <template v-if="phase !== 'tuning'">
      <view v-if="phase === 'idle'" class="panel">
        <view class="channel-grid">
          <view
            v-for="c in CHANNELS"
            :key="c.key"
            class="channel"
            @tap="tune(c)"
          >
            <text class="channel-label">{{ c.label }}</text>
            <text class="channel-freq">{{ c.freq }}</text>
          </view>
        </view>
      </view>

      <!-- 开播态：节目单列表 -->
      <view v-else class="panel onair-panel">
        <view v-if="playlistId" class="pl-card" @tap="openPlaylist">
          <view class="pl-badge">歌单</view>
          <view class="pl-name">{{ playlistTitle }}</view>
          <text class="pl-arrow">›</text>
        </view>
        <view class="song-list">
          <view
            v-for="(s, idx) in songs"
            :key="s.id"
            class="song-row"
            :class="{ 'is-now': idx === nowPlayingIndex }"
            @tap="jumpTo(idx)"
          >
            <view class="song-idx">{{ idx === nowPlayingIndex ? '♪' : idx + 1 }}</view>
            <view class="song-info">
              <view class="song-name">{{ s.name }}</view>
              <view class="song-meta"
                >{{ s.singerName || '未知歌手' }} · {{ s.style || '流行' }}</view
              >
            </view>
            <view v-if="idx === nowPlayingIndex" class="song-live">正在播出</view>
            <view v-else class="song-count">{{ formatCount(s.playCount) }}次播放</view>
          </view>
        </view>
        <view class="onair-actions">
          <button class="action-btn action-main" @tap="resync">重新对时</button>
          <button class="action-btn" @tap="backToIdle">重新选台</button>
        </view>
      </view>
    </template>

    <!-- 调频态 -->
    <view v-else class="panel tuning-panel">
      <view class="tuning-text">正在为你调频…</view>
      <view class="tuning-sub">锁定 FM 频率，接上电台时间轴</view>
    </view>
  </view>
</template>

<style scoped>
.page {
  min-height: 100vh;
  padding: 30rpx 24rpx calc(40rpx + env(safe-area-inset-bottom));
  background: #1b2233;
}

/* ===== 台标 ===== */
.masthead {
  padding: 10rpx 8rpx 26rpx;
}

.mast-eyebrow {
  font-size: 20rpx;
  letter-spacing: 4rpx;
  color: #5b7095;
}

.mast-title {
  margin-top: 8rpx;
  font-size: 44rpx;
  font-weight: 700;
  color: #e8edf7;
  letter-spacing: 2rpx;
}

/* ===== 调频盘（签名元素） ===== */
.dial {
  position: relative;
  background: #232c45;
  border: 1rpx solid #33406b;
  border-radius: 24rpx;
  padding: 40rpx 30rpx 34rpx;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10rpx;
}

/* ON AIR 信号灯：全页唯一暖色 */
.onair {
  display: flex;
  align-items: center;
  gap: 10rpx;
  font-size: 20rpx;
  letter-spacing: 3rpx;
  color: #5b7095;
}

.onair-lamp {
  width: 14rpx;
  height: 14rpx;
  border-radius: 50%;
  background: #3a4666;
}

.onair.is-on {
  color: #f0b34e;
}

.onair.is-on .onair-lamp {
  background: #f0b34e;
  animation: lamp-breathe 1.6s infinite ease-in-out;
}

@keyframes lamp-breathe {
  0%,
  100% {
    opacity: 1;
  }
  50% {
    opacity: 0.35;
  }
}

.freq {
  font-size: 96rpx;
  font-weight: 700;
  line-height: 1.1;
  color: #e8edf7;
  font-variant-numeric: tabular-nums;
}

.freq-spin {
  animation: freq-pop 0.14s;
}

@keyframes freq-pop {
  0% {
    transform: translateY(-6rpx);
    opacity: 0.4;
  }
  100% {
    transform: translateY(0);
    opacity: 1;
  }
}

.freq-unit {
  font-size: 20rpx;
  letter-spacing: 4rpx;
  color: #5b7095;
}

.dial-slogan {
  margin-top: 14rpx;
  font-size: 24rpx;
  color: #8fa0c2;
  text-align: center;
}

/* ===== 面板 ===== */
.panel {
  margin-top: 24rpx;
  background: #232c45;
  border: 1rpx solid #33406b;
  border-radius: 24rpx;
  padding: 28rpx 24rpx;
}

/* 频道胶囊 */
.channel-grid {
  display: flex;
  flex-wrap: wrap;
  gap: 18rpx;
  justify-content: center;
}

.channel {
  display: flex;
  align-items: baseline;
  gap: 8rpx;
  padding: 16rpx 28rpx;
  background: #2c3859;
  border: 1rpx solid transparent;
  border-radius: 999rpx;
  color: #c6d2ea;
}

.channel:active {
  background: #33406b;
}

.channel-label {
  font-size: 27rpx;
  font-weight: 600;
}

.channel-freq {
  font-size: 20rpx;
  color: #7d90b8;
}

/* ===== 开播态 ===== */
.pl-card {
  display: flex;
  align-items: center;
  gap: 14rpx;
  padding: 20rpx 22rpx;
  background: #2a4a86;
  border-radius: 16rpx;
  margin-bottom: 20rpx;
}

.pl-card:active {
  opacity: 0.85;
}

.pl-badge {
  font-size: 20rpx;
  color: #fff;
  background: #5b8fe8;
  border-radius: 8rpx;
  padding: 4rpx 12rpx;
  flex-shrink: 0;
}

.pl-name {
  flex: 1;
  min-width: 0;
  font-size: 26rpx;
  color: #e8edf7;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.pl-arrow {
  color: #a8c4f5;
  font-size: 34rpx;
}

.song-list {
  display: flex;
  flex-direction: column;
  gap: 8rpx;
}

.song-row {
  display: flex;
  align-items: center;
  gap: 18rpx;
  padding: 16rpx 18rpx;
  border-radius: 14rpx;
}

.song-row:active {
  background: #2c3859;
}

.song-row.is-now {
  background: #2a4a86;
}

.song-idx {
  width: 44rpx;
  font-size: 24rpx;
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
}

.song-name {
  font-size: 27rpx;
  color: #e8edf7;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.song-meta {
  margin-top: 4rpx;
  font-size: 22rpx;
  color: #7d90b8;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.song-count {
  flex-shrink: 0;
  font-size: 20rpx;
  color: #5b7095;
}

.song-live {
  flex-shrink: 0;
  font-size: 20rpx;
  letter-spacing: 1rpx;
  color: #f0b34e;
}

.onair-actions {
  margin-top: 26rpx;
  display: flex;
  gap: 18rpx;
}

.action-btn {
  flex: 1;
  height: 80rpx;
  line-height: 80rpx;
  border-radius: 999rpx;
  background: #2c3859;
  color: #c6d2ea;
  font-size: 27rpx;
}

.action-main {
  background: linear-gradient(135deg, #5b8fe8, #2a5cb8);
  color: #fff;
}

/* ===== 调频态 ===== */
.tuning-panel {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12rpx;
  padding: 60rpx 24rpx;
}

.tuning-text {
  font-size: 30rpx;
  color: #e8edf7;
  font-weight: 600;
}

.tuning-sub {
  font-size: 24rpx;
  color: #7d90b8;
}
</style>
