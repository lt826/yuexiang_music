<script setup lang="ts">
import { ref, computed } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { getFavoriteList, getHistoryList } from '@/api/favorite'
import { getMyPlaylists, createPlaylist } from '@/api/playlist'
import { useUserStore } from '@/stores/user'
import { usePlayerStore } from '@/stores/player'
import { fullUrl } from '@/config'
import MiniPlayer from '@/components/MiniPlayer.vue'
import AddToPlaylistSheet from '@/components/AddToPlaylistSheet.vue'
import { ensureTabBar } from '@/utils/tabbar'
import type { FavoriteItemVO, PlaylistVO, SongVO } from '@/api/types'

const userStore = useUserStore()
const player = usePlayerStore()

/** 添加到歌单弹层（收藏/历史行"＋"） */
const addSheet = ref<InstanceType<typeof AddToPlaylistSheet> | null>(null)
function openAdd(item: FavoriteItemVO) {
  addSheet.value?.open({
    id: item.songId,
    name: item.name || '未知歌曲',
    cover: item.cover,
    url: item.url,
    style: item.style,
    playCount: item.playCount ?? 0,
    singerId: 0,
    singerName: item.singerName,
  } as SongVO)
}

const tab = ref<'fav' | 'history' | 'playlists'>('fav')
const favs = ref<FavoriteItemVO[]>([])
const historyItems = ref<FavoriteItemVO[]>([])
const playlists = ref<PlaylistVO[]>([])
const loading = ref(false)

/** 新建歌单弹层 */
const creating = ref(false)
const newTitle = ref('')

const currentList = computed(() => (tab.value === 'fav' ? favs.value : historyItems.value))

async function load() {
  if (!userStore.isLoggedIn) return
  loading.value = true
  try {
    const [f, h, p] = await Promise.all([
      getFavoriteList().catch(() => [] as FavoriteItemVO[]),
      getHistoryList(1, 30).catch(() => null),
      getMyPlaylists().catch(() => [] as PlaylistVO[]),
    ])
    favs.value = f
    historyItems.value = h?.items ?? []
    playlists.value = p
  } finally {
    loading.value = false
  }
}

/** 收藏/历史转 SongVO 队列播放 */
function playItem(item: FavoriteItemVO, index: number) {
  const list = currentList.value.map(
    (it) =>
      ({
        id: it.songId,
        name: it.name || '未知歌曲',
        cover: it.cover,
        url: it.url,
        style: it.style,
        playCount: it.playCount ?? 0,
        singerId: 0,
        singerName: it.singerName,
      }) as SongVO
  )
  player.playQueue(list, index)
}

async function submitCreate() {
  const title = newTitle.value.trim()
  if (!title) {
    uni.showToast({ title: '请输入歌单名', icon: 'none' })
    return
  }
  try {
    await createPlaylist({ title })
    uni.showToast({ title: '已创建', icon: 'success' })
    creating.value = false
    newTitle.value = ''
    load()
  } catch {
    // 已提示
  }
}

function openPlaylist(id: number) {
  uni.navigateTo({ url: `/pages/playlist/detail?id=${id}` })
}

function goLogin() {
  uni.navigateTo({ url: '/pages/login/login' })
}

function goProfile() {
  uni.navigateTo({ url: '/pages/profile/profile' })
}

function goSingerCenter() {
  uni.navigateTo({ url: '/pages/singer/center' })
}

function goReport() {
  uni.navigateTo({ url: '/pages/report/report' })
}

onShow(() => {
  ensureTabBar()
  load()
})
</script>

<template>
  <view class="page">
    <!-- 未登录 -->
    <view v-if="!userStore.isLoggedIn" class="login-guide card">
      <view class="lg-mark">♪</view>
      <view class="lg-text">登录后同步收藏、历史与歌单</view>
      <button class="btn-primary lg-btn" @tap="goLogin">去登录</button>
    </view>

    <!-- 已登录 -->
    <template v-else>
      <view class="user-card card">
        <view class="avatar">{{ userStore.nickname.slice(0, 1) }}</view>
        <view class="uc-info">
          <view class="uc-name">{{ userStore.nickname }}</view>
          <view class="uc-role">{{ userStore.role === 'SINGER' ? '驻站歌手' : '普通用户' }}</view>
        </view>
        <view class="uc-links">
          <view class="uc-link" @tap="goProfile">个人中心</view>
          <view class="uc-link" @tap="goSingerCenter">歌手中心</view>
        </view>
      </view>

      <!-- 听歌周报入口 -->
      <view class="report-entry card" @tap="goReport">
        <view class="re-mark">📊</view>
        <view class="re-info">
          <view class="re-title">我的听歌周报</view>
          <view class="re-sub">最近 7 天你与音乐的故事</view>
        </view>
        <text class="re-arrow">›</text>
      </view>

      <view class="seg">
        <view class="seg-item" :class="{ 'is-active': tab === 'fav' }" @tap="tab = 'fav'"
          >收藏 {{ favs.length }}</view
        >
        <view class="seg-item" :class="{ 'is-active': tab === 'history' }" @tap="tab = 'history'"
          >最近播放</view
        >
        <view
          class="seg-item"
          :class="{ 'is-active': tab === 'playlists' }"
          @tap="tab = 'playlists'"
          >我的歌单</view
        >
      </view>

      <view v-if="loading" class="loading-tip">加载中...</view>

      <!-- 收藏 / 历史 -->
      <template v-else-if="tab === 'fav' || tab === 'history'">
        <view class="list">
          <view
            v-for="(it, i) in currentList"
            :key="`${it.songId}-${it.time}`"
            class="fav-item"
            @tap="playItem(it, i)"
          >
            <image class="fav-cover" :src="fullUrl(it.cover)" mode="aspectFill" />
            <view class="fav-info">
              <view class="fav-name">{{ it.name || '未知歌曲' }}</view>
              <view class="fav-meta"
                >{{ it.singerName || '未知歌手'
                }}<text v-if="it.time" class="fav-time">{{ it.time.slice(0, 10) }}</text></view
              >
            </view>
            <view class="row-add" @tap.stop="openAdd(it)">＋</view>
            <text class="fav-play">▶</text>
          </view>
        </view>
        <view v-if="currentList.length === 0" class="empty-tip">
          {{ tab === 'fav' ? '还没有收藏歌曲' : '还没有播放记录' }}
        </view>
      </template>

      <!-- 我的歌单 -->
      <template v-else>
        <view class="create-row" @tap="creating = true">
          <view class="create-box">＋</view>
          <view class="create-text">新建歌单</view>
        </view>
        <view class="pl-list">
          <view v-for="p in playlists" :key="p.id" class="pl-item" @tap="openPlaylist(p.id)">
            <image class="pl-cover" :src="fullUrl(p.cover)" mode="aspectFill" />
            <view class="pl-info">
              <view class="pl-title">{{ p.title }}</view>
              <view class="pl-meta">{{ p.songCount }}首</view>
            </view>
            <text class="pl-arrow">›</text>
          </view>
        </view>
        <view v-if="playlists.length === 0" class="empty-tip">还没有自己的歌单</view>
      </template>
    </template>

    <!-- 新建歌单弹层 -->
    <view v-if="creating" class="mask" @tap="creating = false">
      <view class="dialog" @tap.stop>
        <view class="dialog-title">新建歌单</view>
        <input
          v-model="newTitle"
          class="dialog-input"
          placeholder="歌单名称"
          placeholder-class="ph"
          :maxlength="50"
        />
        <view class="dialog-actions">
          <view class="dialog-btn" @tap="creating = false">取消</view>
          <view class="dialog-btn is-primary" @tap="submitCreate">创建</view>
        </view>
      </view>
    </view>

    <AddToPlaylistSheet ref="addSheet" />

    <view class="bottom-space" />
    <MiniPlayer />
  </view>
</template>

<style scoped>
.page {
  padding-bottom: 40rpx;
}

.login-guide {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 20rpx;
  padding: 80rpx 40rpx;
  margin-top: 30rpx;
}

.lg-mark {
  width: 110rpx;
  height: 110rpx;
  border-radius: 50%;
  background: linear-gradient(135deg, #3987e5, #1c5cab);
  color: #fff;
  font-size: 56rpx;
  display: flex;
  align-items: center;
  justify-content: center;
}

.lg-text {
  font-size: 28rpx;
  color: #66708a;
}

.lg-btn {
  width: 320rpx;
}

.user-card {
  display: flex;
  align-items: center;
  gap: 22rpx;
  margin-top: 20rpx;
}

.avatar {
  width: 96rpx;
  height: 96rpx;
  border-radius: 50%;
  background: linear-gradient(135deg, #3987e5, #1c5cab);
  color: #fff;
  font-size: 40rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.uc-info {
  flex: 1;
  min-width: 0;
}

.uc-name {
  font-size: 32rpx;
  font-weight: 600;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.uc-role {
  font-size: 24rpx;
  color: #9aa0ab;
  margin-top: 4rpx;
}

.uc-links {
  display: flex;
  flex-direction: column;
  gap: 10rpx;
  align-items: flex-end;
}

.uc-link {
  font-size: 24rpx;
  color: #2a78d6;
  background: #eef4fd;
  padding: 6rpx 20rpx;
  border-radius: 999rpx;
}

/* 听歌周报入口 */
.report-entry {
  margin-top: 24rpx;
  display: flex;
  align-items: center;
  gap: 20rpx;
  padding: 26rpx 28rpx;
  background: linear-gradient(135deg, #eef4fd, #f7f0fd);
}

.re-mark {
  font-size: 44rpx;
}

.re-info {
  flex: 1;
  min-width: 0;
}

.re-title {
  font-size: 28rpx;
  font-weight: 700;
}

.re-sub {
  margin-top: 4rpx;
  color: #9aa0ab;
  font-size: 22rpx;
}

.re-arrow {
  color: #b4bac6;
  font-size: 40rpx;
}

.seg {
  display: flex;
  background: #e9edf4;
  border-radius: 999rpx;
  padding: 6rpx;
  margin: 24rpx;
}

.seg-item {
  flex: 1;
  text-align: center;
  font-size: 26rpx;
  color: #66708a;
  height: 60rpx;
  line-height: 60rpx;
  border-radius: 999rpx;
}

.seg-item.is-active {
  background: #fff;
  color: #2a78d6;
  font-weight: 600;
}

.list {
  padding: 0 24rpx;
}

.fav-item {
  display: flex;
  align-items: center;
  gap: 20rpx;
  padding: 16rpx 24rpx;
  background: #fff;
  border-radius: 16rpx;
  margin-bottom: 14rpx;
}

.fav-cover {
  width: 88rpx;
  height: 88rpx;
  border-radius: 14rpx;
  background: #eef1f6;
  flex-shrink: 0;
}

.fav-info {
  flex: 1;
  min-width: 0;
}

.fav-name {
  font-size: 30rpx;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.fav-meta {
  font-size: 24rpx;
  color: #9aa0ab;
  margin-top: 6rpx;
}

.fav-time {
  margin-left: 14rpx;
}

.fav-play {
  color: #2a78d6;
  font-size: 30rpx;
}

.row-add {
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

.create-row {
  display: flex;
  align-items: center;
  gap: 20rpx;
  margin: 0 24rpx 20rpx;
  padding: 16rpx 24rpx;
  background: #fff;
  border-radius: 16rpx;
}

.create-box {
  width: 88rpx;
  height: 88rpx;
  border-radius: 14rpx;
  border: 2rpx dashed #b6c6de;
  color: #2a78d6;
  font-size: 40rpx;
  display: flex;
  align-items: center;
  justify-content: center;
}

.create-text {
  font-size: 28rpx;
  color: #2a78d6;
}

.pl-list {
  padding: 0 24rpx;
}

.pl-item {
  display: flex;
  align-items: center;
  gap: 20rpx;
  padding: 16rpx 24rpx;
  background: #fff;
  border-radius: 16rpx;
  margin-bottom: 14rpx;
}

.pl-cover {
  width: 88rpx;
  height: 88rpx;
  border-radius: 14rpx;
  background: #eef1f6;
  flex-shrink: 0;
}

.pl-info {
  flex: 1;
  min-width: 0;
}

.pl-title {
  font-size: 30rpx;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.pl-meta {
  font-size: 24rpx;
  color: #9aa0ab;
  margin-top: 6rpx;
}

.pl-arrow {
  color: #c2c8d2;
  font-size: 36rpx;
}

.mask {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.45);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 100;
}

.dialog {
  width: 580rpx;
  background: #fff;
  border-radius: 24rpx;
  padding: 40rpx 36rpx 30rpx;
}

.dialog-title {
  font-size: 32rpx;
  font-weight: 600;
  text-align: center;
  margin-bottom: 30rpx;
}

.dialog-input {
  height: 88rpx;
  background: #f5f6fa;
  border-radius: 16rpx;
  padding: 0 26rpx;
  font-size: 28rpx;
}

.ph {
  color: #b6bcc7;
}

.dialog-actions {
  display: flex;
  margin-top: 34rpx;
}

.dialog-btn {
  flex: 1;
  text-align: center;
  font-size: 30rpx;
  height: 84rpx;
  line-height: 84rpx;
  border-radius: 999rpx;
  color: #66708a;
}

.dialog-btn.is-primary {
  background: linear-gradient(135deg, #3987e5, #1c5cab);
  color: #fff;
  margin-left: 20rpx;
}

.bottom-space {
  height: 160rpx;
}
</style>
