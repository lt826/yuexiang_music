<script setup lang="ts">
/**
 * 添加到歌单弹层：底部弹出我的歌单列表，点选即把目标歌曲收进歌单；
 * 无歌单时可直接新建。用 v-model:show 控制显隐，open(song) 传入目标歌曲。
 */
import { ref } from 'vue'
import { getMyPlaylists, createPlaylist, addSongToPlaylist } from '@/api/playlist'
import { useUserStore } from '@/stores/user'
import { fullUrl } from '@/config'
import type { PlaylistVO, SongVO } from '@/api/types'

const show = ref(false)
const loading = ref(false)
const playlists = ref<PlaylistVO[]>([])
/** 正在加入歌单的歌曲 */
const song = ref<SongVO | null>(null)
/** 已含歌曲的歌单 id：再次点选提示"已在歌单中" */
const addedIds = ref<number[]>([])

const userStore = useUserStore()

/** 新建歌单内联输入 */
const creating = ref(false)
const newTitle = ref('')
const creatingSubmitting = ref(false)

function close() {
  show.value = false
  song.value = null
  creating.value = false
  newTitle.value = ''
}

/** 打开弹层并加载我的歌单 */
async function open(target: SongVO) {
  if (!userStore.isLoggedIn) {
    uni.showToast({ title: '请先登录', icon: 'none' })
    uni.navigateTo({ url: '/pages/login/login' })
    return
  }
  song.value = target
  show.value = true
  loading.value = true
  try {
    playlists.value = await getMyPlaylists()
  } catch {
    // 已提示
  } finally {
    loading.value = false
  }
}

async function pick(p: PlaylistVO) {
  if (!song.value) return
  try {
    await addSongToPlaylist(p.id, song.value.id)
    addedIds.value.push(p.id)
    uni.showToast({ title: `已加入「${p.title}」`, icon: 'none' })
    setTimeout(close, 600)
  } catch {
    // 已提示（含"已在歌单中"）
  }
}

async function submitCreate() {
  const title = newTitle.value.trim()
  if (!title) {
    uni.showToast({ title: '请输入歌单名', icon: 'none' })
    return
  }
  creatingSubmitting.value = true
  try {
    const id = await createPlaylist({ title })
    // 创建成功直接把歌曲收进去
    if (song.value) {
      await addSongToPlaylist(id, song.value.id)
    }
    uni.showToast({ title: `已创建并加入`, icon: 'success' })
    close()
  } catch {
    // 已提示
  } finally {
    creatingSubmitting.value = false
  }
}

defineExpose({ open })
</script>

<template>
  <view v-if="show" class="mask" @tap="close">
    <view class="sheet" @tap.stop>
      <view class="sheet-title">添加「{{ song?.name || '' }}」到歌单</view>

      <!-- 内联新建歌单 -->
      <view v-if="creating" class="new-row">
        <input
          v-model="newTitle"
          class="new-input"
          placeholder="新歌单名称"
          placeholder-class="ph"
          :maxlength="50"
        />
        <view class="new-btn" :class="{ 'is-busy': creatingSubmitting }" @tap="submitCreate"
          >创建</view
        >
        <view class="new-cancel" @tap="creating = false">取消</view>
      </view>
      <view v-else class="new-entry" @tap="creating = true">＋ 新建歌单</view>

      <scroll-view class="pl-scroll" scroll-y>
        <view v-if="loading" class="pl-loading">加载中...</view>
        <template v-else>
          <view v-for="p in playlists" :key="p.id" class="pl-item" @tap="pick(p)">
            <image
              class="pl-cover"
              :src="fullUrl(p.cover) || '/static/logo.png'"
              mode="aspectFill"
            />
            <view class="pl-info">
              <view class="pl-title">{{ p.title }}</view>
              <view class="pl-meta">{{ p.songCount }}首</view>
            </view>
            <view v-if="addedIds.includes(p.id)" class="pl-added">✓ 已加入</view>
          </view>
          <view v-if="playlists.length === 0" class="pl-empty">还没有歌单，先新建一个吧</view>
        </template>
      </scroll-view>
    </view>
  </view>
</template>

<style scoped>
.mask {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.45);
  z-index: 200;
  display: flex;
  align-items: flex-end;
}

.sheet {
  width: 100%;
  max-height: 70vh;
  background: #fff;
  border-radius: 32rpx 32rpx 0 0;
  padding: 34rpx 30rpx calc(30rpx + env(safe-area-inset-bottom));
  display: flex;
  flex-direction: column;
}

.sheet-title {
  font-size: 30rpx;
  font-weight: 600;
  text-align: center;
  margin-bottom: 24rpx;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.new-entry {
  text-align: center;
  padding: 20rpx;
  font-size: 28rpx;
  color: #2a78d6;
  background: #eef4fd;
  border-radius: 16rpx;
  margin-bottom: 20rpx;
}

.new-row {
  display: flex;
  align-items: center;
  gap: 16rpx;
  margin-bottom: 20rpx;
}

.new-input {
  flex: 1;
  height: 76rpx;
  background: #f5f6fa;
  border-radius: 14rpx;
  padding: 0 24rpx;
  font-size: 27rpx;
}

.ph {
  color: #b6bcc7;
}

.new-btn {
  padding: 0 30rpx;
  height: 76rpx;
  line-height: 76rpx;
  border-radius: 999rpx;
  background: linear-gradient(135deg, #3987e5, #1c5cab);
  color: #fff;
  font-size: 27rpx;
}

.new-btn.is-busy {
  opacity: 0.6;
}

.new-cancel {
  font-size: 27rpx;
  color: #9aa0ab;
  padding: 0 8rpx;
}

.pl-scroll {
  max-height: 46vh;
}

.pl-loading,
.pl-empty {
  text-align: center;
  font-size: 26rpx;
  color: #9aa0ab;
  padding: 40rpx 0;
}

.pl-item {
  display: flex;
  align-items: center;
  gap: 20rpx;
  padding: 16rpx 12rpx;
  border-radius: 16rpx;
}

.pl-item:active {
  background: #eef4fd;
}

.pl-cover {
  width: 80rpx;
  height: 80rpx;
  border-radius: 12rpx;
  background: #eef1f6;
  flex-shrink: 0;
}

.pl-info {
  flex: 1;
  min-width: 0;
}

.pl-title {
  font-size: 29rpx;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.pl-meta {
  font-size: 23rpx;
  color: #9aa0ab;
  margin-top: 4rpx;
}

.pl-added {
  font-size: 24rpx;
  color: #0ca30c;
}
</style>
