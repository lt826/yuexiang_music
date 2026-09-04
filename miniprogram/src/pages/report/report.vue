<script setup lang="ts">
import { ref, computed } from 'vue'
import { onShow, onShareAppMessage } from '@dcloudio/uni-app'
import { getWeeklyReport } from '@/api/favorite'
import { useUserStore } from '@/stores/user'
import { usePlayerStore } from '@/stores/player'
import { fullUrl } from '@/config'
import type { SongVO, WeeklyReportVO } from '@/api/types'

const userStore = useUserStore()
const player = usePlayerStore()

const report = ref<WeeklyReportVO | null>(null)
const loading = ref(false)

async function load() {
  if (!userStore.isLoggedIn) return
  loading.value = true
  try {
    report.value = await getWeeklyReport()
  } catch {
    // request 层已提示；空态兜底
  } finally {
    loading.value = false
  }
}

const isEmpty = computed(() => !report.value || report.value.playCount === 0)

/** TOP5 组成播放队列（url 缺省由 play/{id} 详情接口兜底） */
function playTop(index: number) {
  if (!report.value?.topSongs.length) return
  const list = report.value.topSongs.map(
    (t) =>
      ({
        id: t.songId,
        name: t.name || '未知歌曲',
        cover: t.cover,
        url: null,
        style: null,
        playCount: 0,
        singerId: 0,
        singerName: t.singerName,
      }) as SongVO
  )
  player.playQueue(list, index)
}

/** 晒报告：分享卡片落到报告页 */
onShareAppMessage(() => {
  const name = userStore.nickname
  const count = report.value?.playCount || 0
  return {
    title: `${name}本周听了 ${count} 次歌，来看看我的听歌周报`,
    path: '/pages/my/my',
  }
})

onShow(() => {
  load()
})
</script>

<template>
  <view class="page">
    <view v-if="loading" class="loading-tip">生成中...</view>

    <template v-else>
      <view class="head">
        <view class="head-title">我的听歌周报</view>
        <view class="head-sub">最近 7 天 · {{ userStore.nickname }}</view>
      </view>

      <!-- 空态 -->
      <view v-if="isEmpty" class="empty card">
        <view class="empty-mark">🎧</view>
        <view class="empty-text">这 7 天还没有听歌记录</view>
        <view class="empty-sub">去首页听听歌，下周这里就有你的故事</view>
      </view>

      <!-- 报告 -->
      <template v-else-if="report">
        <!-- 核心数字 -->
        <view class="stats card">
          <view class="stat">
            <view class="stat-num">{{ report.playCount }}</view>
            <view class="stat-label">播放次数</view>
          </view>
          <view class="stat">
            <view class="stat-num">{{ report.songCount }}</view>
            <view class="stat-label">听过歌曲</view>
          </view>
          <view class="stat">
            <view class="stat-num">{{ report.activeDays }}</view>
            <view class="stat-label">活跃天数</view>
          </view>
        </view>

        <!-- 最爱歌手 -->
        <view v-if="report.topSingers.length" class="section card">
          <view class="sec-title">最爱的歌手</view>
          <view class="singers">
            <view v-for="(s, i) in report.topSingers" :key="s.name" class="singer">
              <view class="singer-rank" :class="`rank-${i + 1}`">{{ i + 1 }}</view>
              <view class="singer-name">{{ s.name }}</view>
              <view class="singer-count">{{ s.count }} 次</view>
            </view>
          </view>
        </view>

        <!-- 单曲 TOP5 -->
        <view v-if="report.topSongs.length" class="section card">
          <view class="sec-title">单曲循环 TOP5</view>
          <view
            v-for="(t, i) in report.topSongs"
            :key="t.songId"
            class="top-item"
            @tap="playTop(i)"
          >
            <view class="top-rank" :class="{ 'is-top3': i < 3 }">{{ i + 1 }}</view>
            <image class="top-cover" :src="fullUrl(t.cover)" mode="aspectFill" />
            <view class="top-info">
              <view class="top-name">{{ t.name || '未知歌曲' }}</view>
              <view class="top-singer">{{ t.singerName || '未知歌手' }}</view>
            </view>
            <view class="top-count">{{ t.count }} 次</view>
          </view>
          <view class="sec-tip">点击歌曲可直接播放</view>
        </view>
      </template>
    </template>

    <view class="bottom-space" />
  </view>
</template>

<style scoped>
.page {
  padding: 20rpx 0 40rpx;
}

.loading-tip,
.empty-tip {
  text-align: center;
  color: #9aa0ab;
  font-size: 26rpx;
  padding: 60rpx 0;
}

.head {
  padding: 20rpx 32rpx 10rpx;
}

.head-title {
  font-size: 44rpx;
  font-weight: 700;
}

.head-sub {
  margin-top: 8rpx;
  color: #9aa0ab;
  font-size: 24rpx;
}

/* 空态 */
.empty {
  margin: 30rpx 24rpx 0;
  padding: 80rpx 40rpx;
  text-align: center;
}

.empty-mark {
  font-size: 72rpx;
}

.empty-text {
  margin-top: 20rpx;
  font-size: 30rpx;
  font-weight: 600;
}

.empty-sub {
  margin-top: 12rpx;
  color: #9aa0ab;
  font-size: 24rpx;
}

/* 核心数字 */
.stats {
  margin: 30rpx 24rpx 0;
  display: flex;
  padding: 36rpx 0;
}

.stat {
  flex: 1;
  text-align: center;
}

.stat-num {
  font-size: 52rpx;
  font-weight: 700;
  color: #2a78d6;
}

.stat-label {
  margin-top: 8rpx;
  color: #9aa0ab;
  font-size: 24rpx;
}

/* 区块 */
.section {
  margin: 24rpx 24rpx 0;
  padding: 28rpx 24rpx;
}

.sec-title {
  font-size: 30rpx;
  font-weight: 700;
  margin-bottom: 22rpx;
}

.sec-tip {
  margin-top: 16rpx;
  color: #b4bac6;
  font-size: 22rpx;
  text-align: center;
}

/* 歌手榜 */
.singers {
  display: flex;
  flex-direction: column;
  gap: 20rpx;
}

.singer {
  display: flex;
  align-items: center;
  gap: 18rpx;
}

.singer-rank {
  width: 44rpx;
  height: 44rpx;
  border-radius: 10rpx;
  background: #eef1f6;
  color: #9aa0ab;
  font-size: 24rpx;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.singer-rank.rank-1 {
  background: #fdecd2;
  color: #d98b1f;
}

.singer-rank.rank-2 {
  background: #e8edf5;
  color: #7d8aa5;
}

.singer-rank.rank-3 {
  background: #f9e8dd;
  color: #c4713a;
}

.singer-name {
  flex: 1;
  font-size: 28rpx;
  font-weight: 600;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.singer-count {
  color: #9aa0ab;
  font-size: 24rpx;
}

/* 单曲 TOP */
.top-item {
  display: flex;
  align-items: center;
  gap: 18rpx;
  padding: 14rpx 0;
}

.top-rank {
  width: 40rpx;
  text-align: center;
  color: #b4bac6;
  font-size: 28rpx;
  font-weight: 700;
  flex-shrink: 0;
}

.top-rank.is-top3 {
  color: #2a78d6;
}

.top-cover {
  width: 76rpx;
  height: 76rpx;
  border-radius: 12rpx;
  background: #eef1f6;
  flex-shrink: 0;
}

.top-info {
  flex: 1;
  min-width: 0;
}

.top-name {
  font-size: 28rpx;
  font-weight: 600;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.top-singer {
  margin-top: 4rpx;
  color: #9aa0ab;
  font-size: 24rpx;
}

.top-count {
  color: #9aa0ab;
  font-size: 24rpx;
  flex-shrink: 0;
}

.bottom-space {
  height: 40rpx;
}
</style>
