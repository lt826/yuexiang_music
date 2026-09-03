<script setup lang="ts">
import { ref } from 'vue'
import { onLoad, onShow } from '@dcloudio/uni-app'
import { searchSongs } from '@/api/song'
import { usePlayerStore } from '@/stores/player'
import SongItem from '@/components/SongItem.vue'
import AddToPlaylistSheet from '@/components/AddToPlaylistSheet.vue'
import MiniPlayer from '@/components/MiniPlayer.vue'
import { ensureTabBar } from '@/utils/tabbar'
import type { SongVO } from '@/api/types'

const player = usePlayerStore()

/** 添加到歌单弹层（搜索结果行"＋"） */
const addSheet = ref<InstanceType<typeof AddToPlaylistSheet> | null>(null)
function openAdd(song: SongVO) {
  addSheet.value?.open(song)
}

const keyword = ref('')
const results = ref<SongVO[]>([])
const total = ref(0)
const searching = ref(false)
const searched = ref(false)

async function doSearch() {
  const kw = keyword.value.trim()
  if (!kw) {
    uni.showToast({ title: '请输入搜索关键字', icon: 'none' })
    return
  }
  searching.value = true
  searched.value = true
  try {
    const res = await searchSongs(kw, 1, 50)
    results.value = res.songs
    total.value = res.total
  } catch {
    results.value = []
    total.value = 0
  } finally {
    searching.value = false
  }
}

function clearKw() {
  keyword.value = ''
  results.value = []
  total.value = 0
  searched.value = false
}

function goAiChat() {
  uni.navigateTo({ url: '/pages/ai/chat' })
}

onShow(() => {
  ensureTabBar()
})

onLoad((opts) => {
  if (opts?.keyword) keyword.value = decodeURIComponent(String(opts.keyword))
})
</script>

<template>
  <view class="page">
    <view class="search-bar">
      <view class="search-box">
        <text class="search-icon">🔍</text>
        <input
          v-model="keyword"
          class="search-input"
          placeholder="搜索歌曲、歌手"
          confirm-type="search"
          @confirm="doSearch"
        />
        <text v-if="keyword" class="clear" @tap="clearKw">✕</text>
      </view>
      <view class="search-btn" @tap="doSearch">搜索</view>
    </view>

    <!-- AI 找歌入口：搜不准、想用一句话描述时用 -->
    <view class="ai-entry" @tap="goAiChat">
      <text class="ai-entry-icon">🤖</text>
      <view class="ai-entry-text">
        <view class="ai-entry-title">AI 智能搜歌</view>
        <view class="ai-entry-sub">一句话找歌：歌词片段、心情场景都行</view>
      </view>
      <text class="ai-entry-arrow">›</text>
    </view>

    <view v-if="searched && !searching" class="result-head">
      共找到 {{ total }} 首「{{ keyword }}」
    </view>

    <view v-if="searching" class="loading-tip">搜索中...</view>

    <view v-else-if="searched && results.length" class="list">
      <SongItem
        v-for="(s, i) in results"
        :key="s.id"
        :song="s"
        :index="i"
        :active="player.currentSong?.id === s.id"
        show-add
        @tap-song="(song, idx) => player.playQueue(results, idx)"
        @add="openAdd"
      />
    </view>

    <view v-else-if="searched" class="empty-tip">没有找到相关歌曲</view>

    <view v-else class="empty-tip">输入关键字开始搜索</view>

    <view class="bottom-space" />
    <AddToPlaylistSheet ref="addSheet" />
    <MiniPlayer />
  </view>
</template>

<style scoped>
.page {
  padding: 20rpx 24rpx 40rpx;
}

.search-bar {
  display: flex;
  align-items: center;
  gap: 20rpx;
  margin-bottom: 24rpx;
}

.search-box {
  flex: 1;
  height: 76rpx;
  background: #fff;
  border-radius: 999rpx;
  display: flex;
  align-items: center;
  padding: 0 28rpx;
  gap: 14rpx;
}

.search-icon {
  font-size: 28rpx;
}

.search-input {
  flex: 1;
  font-size: 28rpx;
}

.clear {
  color: #b6bcc7;
  font-size: 28rpx;
  padding: 10rpx;
}

.search-btn {
  color: #2a78d6;
  font-size: 30rpx;
  font-weight: 500;
}

/* AI 找歌入口卡片 */
.ai-entry {
  display: flex;
  align-items: center;
  gap: 18rpx;
  background: linear-gradient(135deg, #eaf2fd, #f4f8ff);
  border: 1rpx solid #d8e6fa;
  border-radius: 16rpx;
  padding: 20rpx 24rpx;
  margin-bottom: 24rpx;
}

.ai-entry-icon {
  font-size: 44rpx;
}

.ai-entry-text {
  flex: 1;
  min-width: 0;
}

.ai-entry-title {
  font-size: 28rpx;
  font-weight: 600;
  color: #2a78d6;
}

.ai-entry-sub {
  font-size: 22rpx;
  color: #8a93ab;
  margin-top: 4rpx;
}

.ai-entry-arrow {
  color: #b6c6de;
  font-size: 40rpx;
}

.result-head {
  font-size: 26rpx;
  color: #9aa0ab;
  margin-bottom: 20rpx;
}

.list {
  margin: 0;
}

.bottom-space {
  height: 160rpx;
}
</style>
