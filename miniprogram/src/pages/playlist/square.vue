<script setup lang="ts">
import { ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { getHotPlaylists, getUserCreatedPlaylists } from '@/api/playlist'
import { fullUrl } from '@/config'
import { useUserStore } from '@/stores/user'
import MiniPlayer from '@/components/MiniPlayer.vue'
import { ensureTabBar } from '@/utils/tabbar'
import type { PlaylistVO } from '@/api/types'

const userStore = useUserStore()

const tab = ref<'hot' | 'user'>('hot')
const hot = ref<PlaylistVO[]>([])
const userCreated = ref<PlaylistVO[]>([])
const loading = ref(false)

async function load() {
  loading.value = true
  try {
    const [h, u] = await Promise.all([
      getHotPlaylists(30).catch(() => [] as PlaylistVO[]),
      getUserCreatedPlaylists(30).catch(() => [] as PlaylistVO[]),
    ])
    // 热门歌单 Top3；乐友歌单展示全部开放（投稿）歌单（与 Web 端口径一致）
    hot.value = h.slice(0, 3)
    userCreated.value = u
  } finally {
    loading.value = false
  }
}

function openDetail(id: number) {
  uni.navigateTo({ url: `/pages/playlist/detail?id=${id}` })
}

onShow(() => {
  ensureTabBar()
  load()
})
</script>

<template>
  <view class="page">
    <view class="seg">
      <view class="seg-item" :class="{ 'is-active': tab === 'hot' }" @tap="tab = 'hot'"
        >热门歌单</view
      >
      <view class="seg-item" :class="{ 'is-active': tab === 'user' }" @tap="tab = 'user'"
        >乐友歌单</view
      >
    </view>

    <view v-if="loading" class="loading-tip">加载中...</view>

    <view v-else class="grid">
      <view
        v-for="p in tab === 'hot' ? hot : userCreated"
        :key="p.id"
        class="grid-card"
        @tap="openDetail(p.id)"
      >
        <image class="grid-cover" :src="fullUrl(p.cover)" mode="aspectFill" />
        <view v-if="p.isOfficial" class="official-badge">官方</view>
        <view class="grid-title">{{ p.title }}</view>
        <view class="grid-meta">{{ p.songCount }}首 · {{ p.playCount }}次播放</view>
      </view>
    </view>

    <view v-if="!loading && (tab === 'hot' ? hot : userCreated).length === 0" class="empty-tip">
      暂无歌单
    </view>

    <view class="bottom-space" />
    <MiniPlayer />
  </view>
</template>

<style scoped>
.page {
  padding: 20rpx 24rpx 40rpx;
}

.seg {
  display: flex;
  background: #e9edf4;
  border-radius: 999rpx;
  padding: 6rpx;
  margin-bottom: 24rpx;
}

.seg-item {
  flex: 1;
  text-align: center;
  font-size: 28rpx;
  color: #66708a;
  height: 64rpx;
  line-height: 64rpx;
  border-radius: 999rpx;
}

.seg-item.is-active {
  background: #fff;
  color: #2a78d6;
  font-weight: 600;
}

.grid {
  display: flex;
  flex-wrap: wrap;
  gap: 22rpx;
}

.grid-card {
  width: calc((100% - 44rpx) / 3);
  position: relative;
}

.grid-cover {
  width: 100%;
  height: 218rpx;
  border-radius: 16rpx;
  background: #eef1f6;
}

.official-badge {
  position: absolute;
  top: 10rpx;
  left: 10rpx;
  background: linear-gradient(135deg, #3987e5, #1c5cab);
  color: #fff;
  font-size: 20rpx;
  padding: 4rpx 12rpx;
  border-radius: 8rpx;
}

.grid-title {
  font-size: 26rpx;
  margin-top: 10rpx;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.grid-meta {
  font-size: 22rpx;
  color: #9aa0ab;
  margin-top: 4rpx;
}

.bottom-space {
  height: 160rpx;
}
</style>
