<script setup lang="ts">
import { computed } from 'vue'
import { usePlayerStore } from '@/stores/player'
import { fullUrl } from '@/config'

/** 底部迷你播放条：tab 页通用，点击进全屏播放页 */
const player = usePlayerStore()

const cover = computed(() => fullUrl(player.currentSong?.cover))
const name = computed(() => player.currentSong?.name || '未在播放')

function openPlayer() {
  if (!player.hasCurrent) return
  uni.navigateTo({ url: '/pages/player/player' })
}

function onTapPlay() {
  player.toggle()
}

function onNext() {
  player.next()
}
</script>

<template>
  <view v-if="player.hasCurrent" class="mini-player" @tap="openPlayer">
    <image class="mp-cover" :src="cover || '/static/logo.png'" mode="aspectFill" />
    <view class="mp-info">
      <view class="mp-name">{{ name }}</view>
      <view class="mp-singer">{{ player.currentSong?.singerName || '未知歌手' }}</view>
    </view>
    <view class="mp-btn" @tap.stop="onTapPlay">
      <text>{{ player.playing ? '❚❚' : '▶' }}</text>
    </view>
    <view class="mp-btn" @tap.stop="onNext">
      <text>⏭</text>
    </view>
  </view>
</template>

<style scoped>
.mini-player {
  position: fixed;
  left: 24rpx;
  right: 24rpx;
  bottom: 24rpx;
  height: 100rpx;
  display: flex;
  align-items: center;
  gap: 18rpx;
  padding: 0 20rpx;
  background: rgba(255, 255, 255, 0.96);
  border-radius: 999rpx;
  box-shadow: 0 8rpx 30rpx rgba(28, 92, 171, 0.22);
  z-index: 50;
}

.mp-cover {
  width: 72rpx;
  height: 72rpx;
  border-radius: 50%;
  flex-shrink: 0;
  background: #eef1f6;
}

.mp-info {
  flex: 1;
  min-width: 0;
}

.mp-name {
  font-size: 28rpx;
  font-weight: 500;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.mp-singer {
  font-size: 22rpx;
  color: #9aa0ab;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.mp-btn {
  width: 68rpx;
  height: 68rpx;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #3987e5, #1c5cab);
  color: #fff;
  font-size: 28rpx;
  flex-shrink: 0;
}
</style>
