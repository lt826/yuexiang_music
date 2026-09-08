<script setup lang="ts">
import { ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { getActiveBanners, getHotChart, getNewSongs, getGuessSongs } from '@/api/home'
import { getRecommendPlaylists } from '@/api/playlist'
import { usePlayerStore } from '@/stores/player'
import { fullUrl } from '@/config'
import SongItem from '@/components/SongItem.vue'
import MiniPlayer from '@/components/MiniPlayer.vue'
import { ensureTabBar } from '@/utils/tabbar'
import type { ActiveBannerVO, PlaylistVO, SongVO } from '@/api/types'

const player = usePlayerStore()

const banners = ref<ActiveBannerVO[]>([])
const hotSongs = ref<SongVO[]>([])
const newSongs = ref<SongVO[]>([])
const playlists = ref<PlaylistVO[]>([])
const guessSongs = ref<SongVO[]>([])
const loading = ref(false)

async function load() {
  loading.value = true
  try {
    const [b, hot, fresh, rec, guess] = await Promise.all([
      getActiveBanners().catch(() => [] as ActiveBannerVO[]),
      getHotChart(8).catch(() => [] as SongVO[]),
      getNewSongs(6).catch(() => [] as SongVO[]),
      getRecommendPlaylists(6).catch(() => [] as PlaylistVO[]),
      getGuessSongs(10).catch(() => [] as SongVO[]),
    ])
    banners.value = b
    hotSongs.value = hot
    newSongs.value = fresh
    playlists.value = rec
    guessSongs.value = guess
  } finally {
    loading.value = false
  }
}

/** 播放整个猜你喜欢板块（可整体当歌单听） */
function playGuessAll() {
  if (guessSongs.value.length) {
    player.playQueue(guessSongs.value, 0)
  }
}

/** 点轮播图：关联了歌曲则单曲播放 */
function onBannerTap(banner: ActiveBannerVO) {
  if (!banner.songId) return
  player.playQueue([{ id: banner.songId, name: banner.title } as SongVO], 0)
}

function openPlaylist(id: number) {
  uni.navigateTo({ url: `/pages/playlist/detail?id=${id}` })
}

function goAiChat() {
  uni.navigateTo({ url: '/pages/ai/chat' })
}

function goRadio() {
  uni.navigateTo({ url: '/pages/radio/radio' })
}

onShow(() => {
  ensureTabBar()
  load()
})
</script>

<template>
  <view class="page">
    <!-- 轮播 -->
    <swiper
      v-if="banners.length"
      class="banner-swiper"
      indicator-dots
      autoplay
      circular
      :interval="4500"
      indicator-color="rgba(255,255,255,0.4)"
      indicator-active-color="#ffffff"
    >
      <swiper-item v-for="b in banners" :key="b.id" @tap="onBannerTap(b)">
        <image class="banner-img" :src="fullUrl(b.imageUrl)" mode="aspectFill" />
        <view class="banner-caption">
          <view class="banner-title">{{ b.title }}</view>
          <view v-if="b.subtitle" class="banner-subtitle">{{ b.subtitle }}</view>
        </view>
      </swiper-item>
    </swiper>

    <!-- 猜你喜欢：风格标签画像加权混采推荐（收听历史+收藏双信号），可整块当歌单播放 -->
    <view v-if="guessSongs.length" class="section">
      <view class="section-head">
        <view class="section-title-wrap">
          <view class="section-title">猜你喜欢</view>
          <view class="section-sub">根据你的口味画像推荐</view>
        </view>
        <view class="play-all" @tap="playGuessAll">▶ 播放全部</view>
      </view>
      <view class="list">
        <SongItem
          v-for="(s, i) in guessSongs"
          :key="s.id"
          :song="s"
          :index="i"
          show-rank
          :active="player.currentSong?.id === s.id"
          @tap-song="(song, idx) => player.playQueue(guessSongs, idx)"
        />
      </view>
    </view>

    <!-- 心情电台入口：深夜电台色，与 AI 卡片成"手动找歌/自动串歌"一对 -->
    <view class="radio-entry" @tap="goRadio">
      <view class="radio-lamp" />
      <view class="radio-entry-text">
        <view class="radio-entry-title"
          >心情电台 <text class="radio-entry-freq">FM 88.4</text></view
        >
        <view class="radio-entry-sub">睡前 / 学习 / emo…选频道，AI 为你串好歌</view>
      </view>
      <text class="radio-entry-arrow">›</text>
    </view>

    <!-- AI 找歌入口：一句话找歌 -->
    <view class="ai-entry" @tap="goAiChat">
      <text class="ai-entry-icon">🤖</text>
      <view class="ai-entry-text">
        <view class="ai-entry-title">AI 智能搜歌</view>
        <view class="ai-entry-sub">一句话找歌：歌词片段、心情场景都行</view>
      </view>
      <text class="ai-entry-arrow">›</text>
    </view>

    <!-- 推荐歌单 -->
    <view v-if="playlists.length" class="section-title">推荐歌单</view>
    <scroll-view v-if="playlists.length" scroll-x class="pl-scroll" :show-scrollbar="false">
      <view class="pl-row">
        <view v-for="p in playlists" :key="p.id" class="pl-card" @tap="openPlaylist(p.id)">
          <image class="pl-cover" :src="fullUrl(p.cover)" mode="aspectFill" />
          <view class="pl-title">{{ p.title }}</view>
          <view class="pl-count">{{ p.songCount }}首</view>
        </view>
      </view>
    </scroll-view>

    <!-- 热门榜 -->
    <view class="section-title">热门榜 TOP{{ hotSongs.length }}</view>
    <view class="list">
      <SongItem
        v-for="(s, i) in hotSongs"
        :key="s.id"
        :song="s"
        :index="i"
        show-rank
        :active="player.currentSong?.id === s.id"
        @tap-song="(song, idx) => player.playQueue(hotSongs, idx)"
      />
    </view>

    <!-- 新歌首发 -->
    <view class="section-title">新歌首发</view>
    <view class="list">
      <SongItem
        v-for="(s, i) in newSongs"
        :key="s.id"
        :song="s"
        :index="i"
        :active="player.currentSong?.id === s.id"
        @tap-song="(song, idx) => player.playQueue(newSongs, idx)"
      />
    </view>

    <view v-if="!loading && !hotSongs.length && !newSongs.length" class="empty-tip">
      暂无歌曲数据，请检查后端服务
    </view>
    <view v-if="loading" class="loading-tip">加载中...</view>

    <view class="bottom-space" />
    <MiniPlayer />
  </view>
</template>

<style scoped>
.page {
  padding-bottom: 40rpx;
}

/* 猜你喜欢板块头部：标题+来源说明在左，播放全部在右 */
.section-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 24rpx;
  margin-bottom: 16rpx;
}

.section-title-wrap {
  display: flex;
  align-items: baseline;
  gap: 14rpx;
  min-width: 0;
}

.section-sub {
  font-size: 22rpx;
  color: #9aa0ab;
  white-space: nowrap;
}

.play-all {
  flex-shrink: 0;
  font-size: 24rpx;
  color: #fff;
  background: linear-gradient(135deg, #3987e5, #1c5cab);
  padding: 10rpx 24rpx;
  border-radius: 999rpx;
}

/* 心情电台入口卡片：深夜电台色（对齐电台页 #1b2233），信号灯为唯一暖色 */
.radio-entry {
  display: flex;
  align-items: center;
  gap: 18rpx;
  margin: 24rpx 24rpx 0;
  background: linear-gradient(135deg, #232c45, #1b2233);
  border: 1rpx solid #33406b;
  border-radius: 16rpx;
  padding: 20rpx 24rpx;
}

.radio-lamp {
  width: 16rpx;
  height: 16rpx;
  border-radius: 50%;
  background: #f0b34e;
  box-shadow: 0 0 12rpx rgba(240, 179, 78, 0.7);
  flex-shrink: 0;
}

.radio-entry-text {
  flex: 1;
  min-width: 0;
}

.radio-entry-title {
  font-size: 28rpx;
  font-weight: 600;
  color: #e8edf7;
}

.radio-entry-freq {
  font-size: 20rpx;
  font-weight: 400;
  color: #f0b34e;
  margin-left: 8rpx;
  letter-spacing: 1rpx;
}

.radio-entry-sub {
  font-size: 22rpx;
  color: #8fa0c2;
  margin-top: 4rpx;
}

.radio-entry-arrow {
  color: #5b7095;
  font-size: 40rpx;
}

/* AI 找歌入口卡片 */
.ai-entry {
  display: flex;
  align-items: center;
  gap: 18rpx;
  margin: 24rpx;
  background: linear-gradient(135deg, #eaf2fd, #f4f8ff);
  border: 1rpx solid #d8e6fa;
  border-radius: 16rpx;
  padding: 20rpx 24rpx;
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

.banner-swiper {
  height: 300rpx;
  margin: 20rpx 24rpx;
  border-radius: 20rpx;
  overflow: hidden;
}

.banner-img {
  width: 100%;
  height: 100%;
}

.banner-caption {
  position: absolute;
  left: 0;
  right: 0;
  bottom: 0;
  padding: 40rpx 28rpx 20rpx;
  background: linear-gradient(180deg, transparent, rgba(0, 0, 0, 0.55));
  display: flex;
  flex-direction: column;
  gap: 4rpx;
}

.banner-title {
  color: #fff;
  font-size: 32rpx;
  font-weight: 600;
}

.banner-subtitle {
  color: rgba(255, 255, 255, 0.85);
  font-size: 24rpx;
}

.pl-scroll {
  white-space: nowrap;
}

.pl-row {
  display: inline-flex;
  gap: 20rpx;
  padding: 0 24rpx;
}

.pl-card {
  width: 200rpx;
  flex-shrink: 0;
}

.pl-cover {
  width: 200rpx;
  height: 200rpx;
  border-radius: 16rpx;
  background: #eef1f6;
}

.pl-title {
  font-size: 26rpx;
  margin-top: 10rpx;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.pl-count {
  font-size: 22rpx;
  color: #9aa0ab;
}

.list {
  padding: 0 24rpx;
}

.bottom-space {
  height: 160rpx;
}
</style>
