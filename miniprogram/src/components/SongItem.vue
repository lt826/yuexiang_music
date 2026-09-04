<script setup lang="ts">
import { fullUrl } from '@/config'
import { formatCount } from '@/utils/format'
import type { SongVO } from '@/api/types'

/** 通用歌曲行：序号/封面 + 歌名 + 歌手/播放量，点击交给父级播队列 */
const props = withDefaults(
  defineProps<{
    song: SongVO
    index?: number
    /** 显示序号而非封面（榜单样式） */
    showRank?: boolean
    active?: boolean
    /** 行尾显示"收进歌单"按钮，点击走 add 事件（不冒泡播放） */
    showAdd?: boolean
  }>(),
  { index: 0, showRank: false, active: false, showAdd: false }
)

const emit = defineEmits<{
  (e: 'tap-song', song: SongVO, index: number): void
  (e: 'add', song: SongVO): void
}>()

function onAdd() {
  emit('add', props.song)
}

function onTap() {
  emit('tap-song', props.song, props.index)
}
</script>

<template>
  <view class="song-item" :class="{ 'is-active': active }" @tap="onTap">
    <view v-if="showRank" class="rank" :class="{ 'rank-top': index < 3 }">{{ index + 1 }}</view>
    <image
      v-else
      class="cover"
      :src="fullUrl(song.cover) || '/static/logo.png'"
      mode="aspectFill"
      lazy-load
    />
    <view class="info">
      <view class="name">{{ song.name }}</view>
      <view class="meta">
        <text>{{ song.singerName || '未知歌手' }}</text>
        <text class="dot">·</text>
        <text>{{ formatCount(song.playCount) }}次播放</text>
      </view>
    </view>
    <view v-if="showAdd" class="add-btn" @tap.stop="onAdd">＋</view>
    <view v-if="active" class="playing-mark">♫</view>
  </view>
</template>

<style scoped>
.add-btn {
  width: 56rpx;
  height: 56rpx;
  border-radius: 50%;
  border: 2rpx solid #d6e0f0;
  color: #2a78d6;
  font-size: 32rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.song-item {
  display: flex;
  align-items: center;
  gap: 20rpx;
  padding: 18rpx 24rpx;
  background: #fff;
  border-radius: 16rpx;
  margin-bottom: 14rpx;
}

.song-item.is-active {
  background: #eef4fd;
}

.rank {
  width: 48rpx;
  text-align: center;
  font-size: 30rpx;
  font-weight: 700;
  color: #9aa0ab;
  font-style: italic;
}

.rank-top {
  color: #2a78d6;
}

.cover {
  width: 88rpx;
  height: 88rpx;
  border-radius: 14rpx;
  flex-shrink: 0;
  background: #eef1f6;
}

.info {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 6rpx;
}

.name {
  font-size: 30rpx;
  font-weight: 500;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.is-active .name {
  color: #2a78d6;
}

.meta {
  font-size: 24rpx;
  color: #9aa0ab;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.dot {
  margin: 0 8rpx;
}

.playing-mark {
  color: #2a78d6;
  font-size: 30rpx;
  animation: pulse 1s ease-in-out infinite;
}

@keyframes pulse {
  0%,
  100% {
    opacity: 1;
  }
  50% {
    opacity: 0.4;
  }
}
</style>
