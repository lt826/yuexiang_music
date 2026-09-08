<script setup lang="ts">
import { computed, watch, ref } from 'vue'
import { onLoad, onShow, onShareAppMessage } from '@dcloudio/uni-app'
import { usePlayerStore, PLAY_RATES } from '@/stores/player'
import { getSongPlay } from '@/api/song'
import { fullUrl } from '@/config'
import { formatTime } from '@/utils/format'
import AddToPlaylistSheet from '@/components/AddToPlaylistSheet.vue'
import type { PlayMode } from '@/stores/player'

const player = usePlayerStore()

/** 添加到歌单弹层（控制区"＋歌单"按钮） */
const addSheet = ref<InstanceType<typeof AddToPlaylistSheet> | null>(null)
function openAddSheet() {
  if (!player.currentSong) return
  addSheet.value?.open(player.currentSong)
}

/** 分享落地标记：songId 落地加载中，onShow 不做空判断回退 */
let landing = false

/** 分享落地：卡片携带 songId，重进时单独起播这首 */
onLoad(async (opts) => {
  const songId = Number(opts?.songId || 0)
  if (songId && !player.hasCurrent) {
    landing = true
    try {
      const song = await getSongPlay(songId)
      await player.playQueue([song], 0)
    } catch {
      // request 层已提示；落地失败停留本页即可
    } finally {
      landing = false
    }
  }
})

/** 好友/群聊分享卡片（右上角菜单 + 页面分享按钮共用） */
onShareAppMessage(() => {
  const song = player.currentSong
  if (!song) {
    return { title: '悦享音乐，遇见好声音', path: '/pages/index/index' }
  }
  return {
    title: `《${song.name}》- ${song.singerName || '未知歌手'}`,
    path: `/pages/player/player?songId=${song.id}`,
    imageUrl: fullUrl(song.cover) || undefined,
  }
})

const cover = computed(() => fullUrl(player.currentSong?.cover))
const name = computed(() => player.currentSong?.name || '')
const singer = computed(() => player.currentSong?.singerName || '未知歌手')

/** 拖动进度中：不回写时间显示，避免拖柄被播放进度拽回（对齐 Web 端逻辑） */
const dragging = ref(false)
const dragValue = ref(0)

/** 电台模式：进度与切歌锁在电台时间轴上（store 兜底，UI 同步禁用） */
const radioLocked = computed(() => player.radioMode)

const sliderValue = computed(() => {
  if (dragging.value) return dragValue.value
  if (!player.duration) return 0
  return Math.min(100, (player.currentTime / player.duration) * 100)
})

function onSliderChanging(e: { detail: { value: number } }) {
  if (radioLocked.value) return
  dragging.value = true
  dragValue.value = e.detail.value
}

function onSliderChange(e: { detail: { value: number } }) {
  if (radioLocked.value) return
  const percent = e.detail.value
  player.seek((percent / 100) * player.duration)
  dragging.value = false
}

const modeLabel: Record<PlayMode, string> = {
  order: '顺序',
  loop: '列表循环',
  one: '单曲循环',
  random: '随机',
}
const modeIcon: Record<PlayMode, string> = {
  order: '⇉',
  loop: '🔁',
  one: '🔂',
  random: '🔀',
}

/** 歌词滚动：当前行变化时 scroll-into-view 到当前行 */
const lyricScrollId = ref('')
watch(
  () => player.currentLyricIndex,
  (idx) => {
    lyricScrollId.value = idx >= 0 ? `line-${idx}` : ''
  }
)

// ===== 睡眠定时器 / 倍速弹窗 =====
const showSleep = ref(false)
const showRate = ref(false)
const sleepMinutes = [15, 30, 60]

const sleepLabel = computed(() => {
  if (player.sleepMode === 'song') return '播完停'
  if (player.sleepMode === 'time') {
    const min = Math.max(1, Math.ceil(player.sleepRemaining / 60))
    return `${min}分后停`
  }
  return '定时'
})

function onPickSleepTime(minutes: number) {
  player.setSleep('time', minutes)
  showSleep.value = false
  uni.showToast({ title: `${minutes} 分钟后暂停`, icon: 'none' })
}

function onPickSleepSong() {
  player.setSleep('song')
  showSleep.value = false
  uni.showToast({ title: '播完当前这首后停止', icon: 'none' })
}

function onPickRate(rate: number) {
  player.setPlayRate(rate)
  showRate.value = false
}

onShow(() => {
  if (!player.hasCurrent && !landing) {
    uni.showToast({ title: '当前没有播放中的歌曲', icon: 'none' })
    setTimeout(() => uni.navigateBack(), 600)
  }
})
</script>

<template>
  <view class="player-page">
    <!-- 封面 -->
    <view class="cover-wrap">
      <image
        class="cover"
        :class="{ 'is-paused': !player.playing }"
        :src="cover || '/static/logo.png'"
        mode="aspectFill"
      />
    </view>

    <!-- 歌名 -->
    <view class="song-name">{{ name }}</view>
    <view class="song-singer">{{ singer }}</view>
    <view v-if="player.loadError" class="error-tip">{{ player.loadError }}</view>

    <!-- 歌词 -->
    <scroll-view
      v-if="player.lyricLines.length"
      class="lyric"
      scroll-y
      :scroll-into-view="lyricScrollId"
      scroll-with-animation
    >
      <view
        v-for="(line, i) in player.lyricLines"
        :id="`line-${i}`"
        :key="i"
        class="lyric-line"
        :class="{ 'is-current': i === player.currentLyricIndex }"
      >
        {{ line.text }}
      </view>
      <view class="lyric-tail" />
    </scroll-view>
    <view v-else class="lyric-empty">纯音乐，请欣赏</view>

    <!-- 进度条 -->
    <view class="progress">
      <text class="time">{{ formatTime(player.currentTime) }}</text>
      <slider
        class="slider"
        :value="sliderValue"
        :disabled="radioLocked"
        activeColor="#3987e5"
        backgroundColor="#3a4258"
        block-size="20"
        block-color="#ffffff"
        @changing="onSliderChanging"
        @change="onSliderChange"
      />
      <text class="time">{{ formatTime(player.duration) }}</text>
    </view>

    <!-- 倍速 + 收进歌单入口 -->
    <view class="rate-row">
      <view class="rate-btn" @tap="showRate = true"
        >{{ player.playRate.toFixed(2).replace(/0+$/, '').replace(/\.$/, '') }}x</view
      >
      <view class="rate-btn add-pl-btn" @tap="openAddSheet">＋ 歌单</view>
      <!-- 收进歌单：弹出我的歌单选择 -->
    </view>

    <!-- 控制区 -->
    <view class="controls">
      <view class="ctrl mode" @tap="player.cyclePlayMode">
        <text class="mode-icon">{{ modeIcon[player.playMode] }}</text>
        <text class="mode-text">{{ modeLabel[player.playMode] }}</text>
      </view>
      <view class="ctrl big" :class="{ 'is-locked': radioLocked }" @tap="player.prev">⏮</view>
      <view class="ctrl play" @tap="player.toggle">
        <text>{{ player.loading ? '…' : player.playing ? '❚❚' : '▶' }}</text>
      </view>
      <view class="ctrl big" :class="{ 'is-locked': radioLocked }" @tap="player.next(true)">⏭</view>
      <view class="ctrl mode" @tap="showSleep = true">
        <text class="mode-icon">🌙</text>
        <text class="mode-text">{{ sleepLabel }}</text>
      </view>
      <button class="ctrl queue-btn share-btn" open-type="share">
        <text class="mode-icon">↗</text>
        <text class="mode-text">分享</text>
      </button>
    </view>

    <!-- 电台模式提示条 -->
    <view v-if="radioLocked" class="radio-tip">电台模式：进度与切歌由电台时间轴控制，每首歌播完自动接上电台</view>

    <!-- 睡眠定时器弹窗 -->
    <view v-if="showSleep" class="mask" @tap="showSleep = false">
      <view class="sheet" @tap.stop>
        <view class="sheet-title">睡眠定时</view>
        <view class="sheet-options">
          <view v-for="m in sleepMinutes" :key="m" class="sheet-option" @tap="onPickSleepTime(m)"
            >{{ m }} 分钟</view
          >
          <view class="sheet-option" @tap="onPickSleepSong">播完当前这首</view>
        </view>
        <view class="sheet-option cancel" @tap="showSleep = false">取消</view>
      </view>
    </view>

    <!-- 添加到歌单弹层 -->
    <AddToPlaylistSheet ref="addSheet" />

    <!-- 倍速弹窗 -->
    <view v-if="showRate" class="mask" @tap="showRate = false">
      <view class="sheet" @tap.stop>
        <view class="sheet-title">播放倍速</view>
        <view class="sheet-options">
          <view
            v-for="r in PLAY_RATES"
            :key="r"
            class="sheet-option"
            :class="{ 'is-active': player.playRate === r }"
            @tap="onPickRate(r)"
            >{{ r }}x</view
          >
        </view>
        <view class="sheet-option cancel" @tap="showRate = false">取消</view>
      </view>
    </view>
  </view>
</template>

<style scoped>
.player-page {
  min-height: 100vh;
  background: linear-gradient(180deg, #232b40 0%, #1b2233 60%, #141927 100%);
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 40rpx 40rpx calc(40rpx + env(safe-area-inset-bottom));
  box-sizing: border-box;
}

.cover-wrap {
  margin-top: 30rpx;
}

.cover {
  width: 480rpx;
  height: 480rpx;
  border-radius: 50%;
  border: 10rpx solid rgba(255, 255, 255, 0.08);
  box-shadow: 0 20rpx 80rpx rgba(0, 0, 0, 0.5);
  background: #2a3248;
  animation: rotate 24s linear infinite;
}

.cover.is-paused {
  animation-play-state: paused;
}

@keyframes rotate {
  from {
    transform: rotate(0deg);
  }
  to {
    transform: rotate(360deg);
  }
}

.song-name {
  margin-top: 44rpx;
  color: #fff;
  font-size: 38rpx;
  font-weight: 700;
  max-width: 640rpx;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.song-singer {
  margin-top: 10rpx;
  color: #8a93ab;
  font-size: 26rpx;
}

.error-tip {
  margin-top: 12rpx;
  color: #eb6834;
  font-size: 24rpx;
}

/* 电台模式：切歌键锁定态 + 底部提示条 */
.ctrl.is-locked {
  opacity: 0.35;
}

.radio-tip {
  margin-top: 20rpx;
  text-align: center;
  color: #7d90b8;
  font-size: 22rpx;
  line-height: 1.6;
  padding: 0 40rpx;
}

.lyric {
  margin-top: 30rpx;
  height: 280rpx;
  width: 100%;
}

.lyric-line {
  text-align: center;
  color: #6a7390;
  font-size: 26rpx;
  line-height: 64rpx;
  transition: color 0.2s ease;
}

.lyric-line.is-current {
  color: #fff;
  font-size: 28rpx;
  font-weight: 600;
}

.lyric-tail {
  height: 220rpx;
}

.lyric-empty {
  margin-top: 40rpx;
  color: #6a7390;
  font-size: 26rpx;
  height: 260rpx;
}

.progress {
  width: 100%;
  display: flex;
  align-items: center;
  gap: 16rpx;
  margin-top: 10rpx;
}

.time {
  color: #8a93ab;
  font-size: 22rpx;
  width: 84rpx;
  text-align: center;
  flex-shrink: 0;
}

.slider {
  flex: 1;
  margin: 0;
}

.rate-row {
  width: 100%;
  display: flex;
  justify-content: flex-end;
  margin-top: 6rpx;
  padding: 0 30rpx;
  box-sizing: border-box;
}

.rate-btn {
  font-size: 22rpx;
  color: #8a93ab;
  border: 2rpx solid #3a4258;
  border-radius: 999rpx;
  padding: 4rpx 20rpx;
}

.add-pl-btn {
  margin-left: 24rpx;
}

.mask {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.5);
  z-index: 100;
  display: flex;
  align-items: flex-end;
}

.sheet {
  width: 100%;
  background: #222a3e;
  border-radius: 24rpx 24rpx 0 0;
  padding: 30rpx 30rpx calc(30rpx + env(safe-area-inset-bottom));
  box-sizing: border-box;
}

.sheet-title {
  text-align: center;
  color: #8a93ab;
  font-size: 24rpx;
  margin-bottom: 20rpx;
}

.sheet-options {
  display: flex;
  flex-wrap: wrap;
  gap: 20rpx;
  justify-content: center;
  margin-bottom: 20rpx;
}

.sheet-option {
  min-width: 160rpx;
  text-align: center;
  padding: 18rpx 30rpx;
  border-radius: 12rpx;
  background: #2c3550;
  color: #dfe4f2;
  font-size: 28rpx;
}

.sheet-option.is-active {
  background: #3987e5;
  color: #fff;
}

.sheet-option.cancel {
  width: 100%;
  margin-top: 6rpx;
  background: transparent;
  border-top: 2rpx solid #323c58;
  border-radius: 0;
  color: #8a93ab;
}

.controls {
  width: 100%;
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 50rpx;
  padding: 0 20rpx;
  box-sizing: border-box;
}

.ctrl {
  color: #aab3cc;
  font-size: 44rpx;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4rpx;
}

.ctrl.big {
  font-size: 56rpx;
}

.ctrl.play {
  width: 130rpx;
  height: 130rpx;
  border-radius: 50%;
  background: linear-gradient(135deg, #3987e5, #1c5cab);
  color: #fff;
  font-size: 48rpx;
  justify-content: center;
  box-shadow: 0 12rpx 40rpx rgba(57, 135, 229, 0.45);
}

.mode,
.queue-btn {
  font-size: 26rpx;
  gap: 6rpx;
}

/* 覆盖 button 默认样式，融入控制区（open-type=share 必须用 button） */
.share-btn {
  margin: 0;
  padding: 0;
  background: transparent;
  line-height: inherit;
  border-radius: 0;
}

.share-btn::after {
  border: none;
}

.mode-icon {
  font-size: 34rpx;
}

.mode-text {
  font-size: 20rpx;
  color: #8a93ab;
}
</style>
