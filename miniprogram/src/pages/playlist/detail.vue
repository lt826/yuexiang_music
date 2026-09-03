<script setup lang="ts">
import { ref } from 'vue'
import { onLoad, onShow, onShareAppMessage } from '@dcloudio/uni-app'
import {
  getPlaylistDetail,
  favoritePlaylist,
  unfavoritePlaylist,
  removeSongFromPlaylist,
} from '@/api/playlist'
import { usePlayerStore } from '@/stores/player'
import { useUserStore } from '@/stores/user'
import { fullUrl } from '@/config'
import { formatCount } from '@/utils/format'
import SongItem from '@/components/SongItem.vue'
import MiniPlayer from '@/components/MiniPlayer.vue'
import AddToPlaylistSheet from '@/components/AddToPlaylistSheet.vue'
import type { PlaylistDetailVO, PlaylistVO, SongVO } from '@/api/types'

const player = usePlayerStore()
const userStore = useUserStore()

/** 收进歌单弹层：歌曲行"＋"与头部"收进歌单"按钮共用 */
const addSheet = ref<InstanceType<typeof AddToPlaylistSheet> | null>(null)
function openAdd(song: SongVO) {
  addSheet.value?.open(song)
}

const id = ref(0)
const detail = ref<PlaylistDetailVO | null>(null)
const loading = ref(false)
const favored = ref(false)
/** 加载失败原因（区别于"歌单不存在"：网络错误/未登录等给可行动的提示） */
const loadFailed = ref('')

async function load() {
  if (!id.value) return
  loading.value = true
  loadFailed.value = ''
  try {
    const d = await getPlaylistDetail(id.value)
    detail.value = d
    favored.value = !!d.playlist.favored
  } catch {
    // request 层已 toast 具体错误；页面态区分"加载失败（可重试）"与"歌单不存在"
    detail.value = null
    loadFailed.value = '加载失败，请检查网络后重试'
  } finally {
    loading.value = false
  }
}

function playAll() {
  if (!detail.value || detail.value.songs.length === 0) return
  player.playQueue(detail.value.songs as SongVO[], 0)
}

async function toggleFavored() {
  if (!userStore.isLoggedIn) {
    uni.showToast({ title: '请先登录', icon: 'none' })
    return
  }
  if (!detail.value) return
  try {
    if (favored.value) {
      await unfavoritePlaylist(id.value)
      favored.value = false
      uni.showToast({ title: '已取消收藏', icon: 'none' })
    } else {
      await favoritePlaylist(id.value)
      favored.value = true
      uni.showToast({ title: '已收藏歌单', icon: 'none' })
    }
  } catch {
    // request 层已提示
  }
}

/** 长按移除歌曲（仅自己的歌单） */
function onSongLongpress(songId: number) {
  const p = detail.value?.playlist as PlaylistVO | undefined
  if (!p || !userStore.isLoggedIn || p.userId !== userStore.user?.userId) return
  uni.showModal({
    title: '移除歌曲',
    content: '确定从该歌单移除这首歌吗？',
    success: async (res) => {
      if (!res.confirm) return
      try {
        await removeSongFromPlaylist(id.value, songId)
        uni.showToast({ title: '已移除', icon: 'none' })
        load()
      } catch {
        // 已提示
      }
    },
  })
}

onLoad((opts) => {
  id.value = Number(opts?.id || 0)
})

onShow(() => {
  if (id.value) load()
})

/** 歌单分享卡片：好友点开直接落到歌单详情页 */
onShareAppMessage(() => {
  const p = detail.value?.playlist
  if (!p) {
    return { title: '悦享音乐，遇见好声音', path: '/pages/index/index' }
  }
  return {
    title: `歌单「${p.title}」- ${detail.value?.songs.length || 0}首好歌`,
    path: `/pages/playlist/detail?id=${p.id}`,
    imageUrl: fullUrl(p.cover) || undefined,
  }
})
</script>

<template>
  <view class="page">
    <view v-if="loading" class="loading-tip">加载中...</view>

    <template v-else-if="detail">
      <!-- 歌单头部 -->
      <view class="head card">
        <image class="head-cover" :src="fullUrl(detail.playlist.cover)" mode="aspectFill" />
        <view class="head-info">
          <view class="head-title">{{ detail.playlist.title }}</view>
          <view class="head-meta"
            >{{ detail.songs.length }}首 · {{ formatCount(detail.playlist.playCount) }}次播放</view
          >
          <view v-if="detail.playlist.description" class="head-desc">{{
            detail.playlist.description
          }}</view>
          <view class="head-actions">
            <button class="btn-primary play-all" @tap="playAll">▶ 播放全部</button>
            <button class="share-btn" open-type="share">↗ 分享</button>
            <view class="fav-btn" :class="{ 'is-favored': favored }" @tap="toggleFavored">
              {{ favored ? '♥ 已收藏' : '♡ 收藏' }}
            </view>
          </view>
        </view>
      </view>

      <!-- 歌曲列表 -->
      <view class="list">
        <view v-for="(s, i) in detail.songs" :key="s.id" @longpress="onSongLongpress(s.id)">
          <SongItem
            :song="s as unknown as SongVO"
            :index="i"
            :active="player.currentSong?.id === s.id"
            show-add
            @tap-song="(song, idx) => player.playQueue(detail!.songs as unknown as SongVO[], idx)"
            @add="openAdd"
          />
        </view>
      </view>

      <view v-if="detail.songs.length === 0" class="empty-tip">歌单还是空的</view>
    </template>

    <view v-else class="empty-tip">
      {{ loadFailed || '歌单不存在' }}
      <view v-if="loadFailed" class="retry-btn" @tap="load">重试</view>
    </view>

    <AddToPlaylistSheet ref="addSheet" />

    <view class="bottom-space" />
    <MiniPlayer />
  </view>
</template>

<style scoped>
.retry-btn {
  margin: 24rpx auto 0;
  width: 200rpx;
  height: 68rpx;
  line-height: 68rpx;
  border-radius: 999rpx;
  background: linear-gradient(135deg, #3987e5, #1c5cab);
  color: #fff;
  font-size: 26rpx;
}

.page {
  padding: 20rpx 0 40rpx;
}

.head {
  display: flex;
  gap: 24rpx;
}

.head-cover {
  width: 200rpx;
  height: 200rpx;
  border-radius: 16rpx;
  flex-shrink: 0;
  background: #eef1f6;
}

.head-info {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 10rpx;
}

.head-title {
  font-size: 34rpx;
  font-weight: 700;
}

.head-meta {
  font-size: 24rpx;
  color: #9aa0ab;
}

.head-desc {
  font-size: 24rpx;
  color: #66708a;
  overflow: hidden;
  text-overflow: ellipsis;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}

.head-actions {
  display: flex;
  align-items: center;
  gap: 20rpx;
  margin-top: 6rpx;
}

.play-all {
  flex: 1;
  font-size: 26rpx;
  height: 68rpx;
  line-height: 68rpx;
}

.share-btn {
  margin: 0;
  padding: 0 24rpx;
  height: 68rpx;
  line-height: 68rpx;
  border-radius: 999rpx;
  border: 2rpx solid #dfe4ee;
  background: #fff;
  color: #4a5164;
  font-size: 26rpx;
  flex-shrink: 0;
}

.share-btn::after {
  border: none;
}

.fav-btn {
  padding: 0 28rpx;
  height: 68rpx;
  line-height: 68rpx;
  border-radius: 999rpx;
  border: 2rpx solid #2a78d6;
  color: #2a78d6;
  font-size: 26rpx;
}

.fav-btn.is-favored {
  background: #fdeeee;
  border-color: #d03b3b;
  color: #d03b3b;
}

.list {
  padding: 0 24rpx;
  margin-top: 20rpx;
}

.bottom-space {
  height: 160rpx;
}
</style>
