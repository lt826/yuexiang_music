<script setup lang="ts">
import { ref, computed } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import {
  applySinger,
  getMyApplication,
  uploadFile,
  publishSong,
  getMySongs,
  offlineSong,
  resubmitSong,
} from '@/api/singer'
import { getSongsBySinger } from '@/api/song'
import { useUserStore } from '@/stores/user'
import { usePlayerStore } from '@/stores/player'
import { formatCount } from '@/utils/format'
import { fullUrl } from '@/config'
import MiniPlayer from '@/components/MiniPlayer.vue'
import type { SingerVO, SongVO } from '@/api/types'

const userStore = useUserStore()
const player = usePlayerStore()

const application = ref<SingerVO | null>(null)
const mySongs = ref<SongVO[]>([])
const loading = ref(false)
const loaded = ref(false)

const isSinger = computed(() => application.value?.status === 1)

const statusText: Record<number, string> = {
  0: '审核中，请耐心等待管理员处理',
  1: '已认证',
  2: '申请被驳回',
  3: '账号已被冻结',
}

/** 歌曲审核状态（对齐 Web 端：0待审核 1已发布 2已下架） */
const songStatusText: Record<number, string> = {
  0: '待审核',
  1: '已发布',
  2: '已下架',
}

// 申请表单
const name = ref('')
const introduction = ref('')
const submitting = ref(false)

// ===== 发布歌曲表单 =====
const STYLE_OPTIONS = ['流行', '摇滚', '民谣', '电子', '后摇', '爵士', '说唱', '古典']
const showPublish = ref(false)
const pubName = ref('')
const pubStyle = ref('流行')
const pubLyric = ref('')
const pubAudioUrl = ref('')
const pubAudioName = ref('')
const pubCoverUrl = ref('')
const audioUploading = ref(false)
const coverUploading = ref(false)
const publishing = ref(false)

async function load() {
  if (!userStore.isLoggedIn) return
  loading.value = true
  try {
    application.value = await getMyApplication().catch(() => null)
    if (isSinger.value) {
      let songs = await getMySongs().catch(() => null)
      // 兜底：/song/my 异常时退化为只看已发布的公开作品
      if (songs === null) {
        songs = (
          await getSongsBySinger(application.value?.id ?? 0).catch(() => [] as SongVO[])
        ).filter((s) => !s.status || s.status === 1)
      }
      mySongs.value = songs
    }
  } finally {
    loading.value = false
    loaded.value = true
  }
}

async function submit() {
  if (!name.value.trim()) {
    uni.showToast({ title: '请填写艺名', icon: 'none' })
    return
  }
  if (submitting.value) return
  submitting.value = true
  try {
    await applySinger({
      name: name.value.trim(),
      introduction: introduction.value.trim() || undefined,
    })
    uni.showToast({ title: '已提交申请', icon: 'success' })
    load()
  } catch {
    // 已提示
  } finally {
    submitting.value = false
  }
}

// ===== 发布歌曲交互 =====

function chooseAudio() {
  uni.chooseMessageFile({
    count: 1,
    type: 'file',
    extension: ['mp3', 'wav', 'flac', 'm4a', 'ogg'],
    success: async (res) => {
      const file = res.tempFiles[0]
      if (!file) return
      if (file.size > 30 * 1024 * 1024) {
        uni.showToast({ title: '音频不能超过 30MB', icon: 'none' })
        return
      }
      audioUploading.value = true
      try {
        const vo = await uploadFile(file.path, 'SONG')
        pubAudioUrl.value = vo.url
        pubAudioName.value = vo.originalName
        if (!pubName.value.trim()) {
          pubName.value = vo.originalName.replace(/\.[^.]+$/, '')
        }
        uni.showToast({ title: '音频上传成功', icon: 'success' })
      } catch {
        // 已提示
      } finally {
        audioUploading.value = false
      }
    },
  })
}

function chooseCover() {
  uni.chooseImage({
    count: 1,
    sizeType: ['compressed'],
    success: async (res) => {
      const path = res.tempFilePaths[0]
      if (!path) return
      coverUploading.value = true
      try {
        const vo = await uploadFile(path, 'COVER')
        pubCoverUrl.value = vo.url
        uni.showToast({ title: '封面上传成功', icon: 'success' })
      } catch {
        // 已提示
      } finally {
        coverUploading.value = false
      }
    },
  })
}

async function submitPublish() {
  if (!pubName.value.trim()) {
    uni.showToast({ title: '请填写歌曲名', icon: 'none' })
    return
  }
  if (!pubAudioUrl.value) {
    uni.showToast({ title: '请先上传音频文件', icon: 'none' })
    return
  }
  if (publishing.value) return
  publishing.value = true
  try {
    await publishSong({
      name: pubName.value.trim(),
      url: pubAudioUrl.value,
      cover: pubCoverUrl.value || null,
      lyric: pubLyric.value.trim() || null,
      style: pubStyle.value,
    })
    uni.showToast({ title: '已提交，待管理员审核', icon: 'success' })
    // 重置表单
    showPublish.value = false
    pubName.value = ''
    pubStyle.value = '流行'
    pubLyric.value = ''
    pubAudioUrl.value = ''
    pubAudioName.value = ''
    pubCoverUrl.value = ''
    load()
  } catch {
    // 已提示
  } finally {
    publishing.value = false
  }
}

function toggleOffline(song: SongVO) {
  const goingOffline = !song.status || song.status === 1
  uni.showModal({
    title: goingOffline ? '下架歌曲' : '重新上架',
    content: goingOffline ? `确定下架《${song.name}》吗？` : `重新提交《${song.name}》上架审核？`,
    success: async (res) => {
      if (!res.confirm) return
      try {
        if (goingOffline) {
          await offlineSong(song.id)
          uni.showToast({ title: '已下架', icon: 'success' })
        } else {
          await resubmitSong(song.id)
          uni.showToast({ title: '已提交审核', icon: 'success' })
        }
        load()
      } catch {
        // 已提示
      }
    },
  })
}

/** 试听自己的作品（含待审核/已下架，后端对归属歌手放行；试听不计播放量） */
async function previewSong(song: SongVO) {
  try {
    await player.playQueue([song], 0)
  } catch {
    uni.showToast({ title: '试听失败，请稍后重试', icon: 'none' })
  }
}

onShow(load)
</script>

<template>
  <view class="page">
    <view
      v-if="!userStore.isLoggedIn"
      class="empty-tip card"
      @tap="uni.navigateTo({ url: '/pages/login/login' })"
    >
      请先登录后再使用歌手中心
    </view>

    <view v-else-if="loading && !loaded" class="loading-tip">加载中...</view>

    <template v-else>
      <!-- 未申请：申请表单 -->
      <view v-if="!application" class="card">
        <view class="form-title">申请成为驻站歌手</view>
        <view class="form-desc">认证通过后即可在本页直接发布歌曲，无需再去 Web 端。</view>
        <view class="field-label">艺名 *</view>
        <input v-model="name" class="form-input" :maxlength="50" placeholder="听众看到的名字" />
        <view class="field-label">简介</view>
        <textarea
          v-model="introduction"
          class="form-textarea"
          :maxlength="200"
          placeholder="介绍一下自己"
        />
        <button class="btn-primary submit" :disabled="submitting" @tap="submit">
          {{ submitting ? '提交中...' : '提交申请' }}
        </button>
      </view>

      <!-- 已有申请 -->
      <template v-else>
        <view class="card status-card">
          <view class="status-name">{{ application.name }}</view>
          <view v-if="application.introduction" class="status-intro">{{
            application.introduction
          }}</view>
          <view class="status-badge" :class="`st-${application.status}`">
            {{ statusText[application.status] || '状态未知' }}
          </view>
          <view v-if="application.status === 2 && application.rejectReason" class="reject-reason">
            驳回原因：{{ application.rejectReason }}
          </view>
        </view>

        <!-- 我的作品 -->
        <template v-if="isSinger">
          <view class="section-title">我的作品 {{ mySongs.length }}</view>
          <view class="publish-entry" @tap="showPublish = !showPublish">
            <view class="pe-icon">{{ showPublish ? '−' : '＋' }}</view>
            <view class="pe-text">{{ showPublish ? '收起发布表单' : '发布新歌曲' }}</view>
          </view>

          <!-- 发布表单 -->
          <view v-if="showPublish" class="card publish-form">
            <view class="field-label">歌曲名 *</view>
            <input
              v-model="pubName"
              class="form-input"
              :maxlength="80"
              placeholder="上传音频后自动填充，可修改"
            />
            <view class="field-label">风格</view>
            <view class="style-row">
              <view
                v-for="st in STYLE_OPTIONS"
                :key="st"
                class="style-tag"
                :class="{ 'is-active': pubStyle === st }"
                @tap="pubStyle = st"
                >{{ st }}</view
              >
            </view>
            <view class="field-label">音频文件 *</view>
            <view class="upload-row" @tap="chooseAudio">
              <view class="upload-box">
                {{ audioUploading ? '上传中...' : pubAudioUrl ? '重新选择' : '选择文件' }}
              </view>
              <view class="upload-name">{{ pubAudioName || '支持 mp3/wav/flac/m4a，≤30MB' }}</view>
            </view>
            <view class="field-label">封面（选填）</view>
            <view class="upload-row" @tap="chooseCover">
              <image
                v-if="pubCoverUrl"
                class="cover-preview"
                :src="fullUrl(pubCoverUrl)"
                mode="aspectFill"
              />
              <view v-else class="upload-box">{{ coverUploading ? '上传中...' : '选择图片' }}</view>
              <view v-if="!pubCoverUrl" class="upload-name">建议 1:1 方图</view>
            </view>
            <view class="field-label">歌词 LRC（选填）</view>
            <textarea
              v-model="pubLyric"
              class="form-textarea lyric-area"
              :maxlength="8000"
              placeholder="粘贴带 [mm:ss] 时间轴的歌词；留空则显示“纯音乐，请欣赏”"
            />
            <button
              class="btn-primary submit"
              :disabled="publishing || audioUploading"
              @tap="submitPublish"
            >
              {{ publishing ? '提交中...' : '提交发布' }}
            </button>
            <view class="publish-tip">提交后管理员审核通过即上架</view>
          </view>

          <view class="list">
            <view
              v-for="(s, i) in mySongs"
              :key="s.id"
              class="work-item"
              @longpress="toggleOffline(s)"
            >
              <view class="work-index">{{ i + 1 }}</view>
              <view class="work-info" @tap="previewSong(s)">
                <view class="work-name">{{ s.name }}</view>
                <view class="work-meta">
                  {{ formatCount(s.playCount) }}次播放 · {{ s.style || '未分类' }}
                  <text class="work-status" :class="`ws-${s.status ?? 1}`">{{
                    songStatusText[s.status ?? 1]
                  }}</text>
                </view>
              </view>
              <view class="work-op work-play" @tap.stop="previewSong(s)">试听</view>
              <view class="work-op" @tap.stop="toggleOffline(s)">
                {{ !s.status || s.status === 1 ? '下架' : '上架' }}
              </view>
            </view>
          </view>
          <view v-if="mySongs.length === 0 && !showPublish" class="empty-tip"
            >还没有作品，点上方「发布新歌曲」上传吧</view
          >
        </template>
      </template>
    </template>

    <view class="bottom-space" />
    <MiniPlayer />
  </view>
</template>

<style scoped>
.page {
  padding: 20rpx 0 40rpx;
}

.form-title {
  font-size: 34rpx;
  font-weight: 700;
}

.form-desc {
  font-size: 24rpx;
  color: #9aa0ab;
  margin-top: 10rpx;
}

.field-label {
  font-size: 26rpx;
  color: #66708a;
  margin: 28rpx 0 12rpx;
}

.form-input {
  height: 84rpx;
  background: #f5f6fa;
  border-radius: 12rpx;
  padding: 0 20rpx;
  font-size: 28rpx;
}

.form-textarea {
  height: 160rpx;
  background: #f5f6fa;
  border-radius: 12rpx;
  padding: 16rpx 20rpx;
  font-size: 26rpx;
  width: 100%;
  box-sizing: border-box;
}

.submit {
  margin-top: 36rpx;
}

.status-card {
  display: flex;
  flex-direction: column;
  gap: 12rpx;
}

.status-name {
  font-size: 34rpx;
  font-weight: 700;
}

.status-intro {
  font-size: 26rpx;
  color: #66708a;
}

.status-badge {
  align-self: flex-start;
  font-size: 24rpx;
  padding: 8rpx 24rpx;
  border-radius: 999rpx;
}

.st-0 {
  background: #fff7e8;
  color: #c07b1a;
}
.st-1 {
  background: #e8f6ee;
  color: #1baf7a;
}
.st-2 {
  background: #fdeeee;
  color: #d03b3b;
}
.st-3 {
  background: #f0f1f5;
  color: #66708a;
}

.reject-reason {
  font-size: 24rpx;
  color: #d03b3b;
}

.publish-entry {
  display: flex;
  align-items: center;
  gap: 16rpx;
  margin: 0 24rpx 20rpx;
  padding: 20rpx 24rpx;
  background: linear-gradient(135deg, #eaf3fe, #f3f8ff);
  border-radius: 16rpx;
}

.pe-icon {
  width: 56rpx;
  height: 56rpx;
  border-radius: 50%;
  background: linear-gradient(135deg, #3987e5, #1c5cab);
  color: #fff;
  font-size: 36rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.pe-text {
  font-size: 28rpx;
  color: #1c5cab;
  font-weight: 600;
}

.publish-form {
  padding-bottom: 30rpx;
}

.style-row {
  display: flex;
  flex-wrap: wrap;
  gap: 14rpx;
}

.style-tag {
  font-size: 24rpx;
  color: #66708a;
  background: #f0f2f6;
  padding: 10rpx 26rpx;
  border-radius: 999rpx;
}

.style-tag.is-active {
  color: #fff;
  background: linear-gradient(135deg, #3987e5, #1c5cab);
}

.upload-row {
  display: flex;
  align-items: center;
  gap: 18rpx;
}

.upload-box {
  height: 72rpx;
  line-height: 72rpx;
  padding: 0 30rpx;
  border-radius: 12rpx;
  border: 2rpx dashed #9db9dc;
  color: #2a78d6;
  font-size: 25rpx;
  flex-shrink: 0;
}

.upload-name {
  flex: 1;
  min-width: 0;
  font-size: 23rpx;
  color: #9aa0ab;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.cover-preview {
  width: 120rpx;
  height: 120rpx;
  border-radius: 14rpx;
  background: #eef1f6;
}

.lyric-area {
  height: 220rpx;
}

.publish-tip {
  margin-top: 18rpx;
  font-size: 22rpx;
  color: #9aa0ab;
  text-align: center;
}

.work-status {
  margin-left: 12rpx;
  padding: 2rpx 12rpx;
  border-radius: 999rpx;
  font-size: 20rpx;
}

.ws-0 {
  background: #fff7e8;
  color: #c07b1a;
}
.ws-1 {
  background: #e8f6ee;
  color: #1baf7a;
}
.ws-2 {
  background: #fdeeee;
  color: #d03b3b;
}

.work-op {
  flex-shrink: 0;
  font-size: 24rpx;
  color: #2a78d6;
  background: #eef4fd;
  padding: 8rpx 22rpx;
  border-radius: 999rpx;
}

/* 试听按钮：绿色区分下架/上架操作 */
.work-play {
  color: #199e70;
  background: #e8f7f0;
}

.list {
  padding: 0 24rpx;
}

.work-item {
  display: flex;
  align-items: center;
  gap: 20rpx;
  background: #fff;
  border-radius: 16rpx;
  padding: 18rpx 24rpx;
  margin-bottom: 14rpx;
}

.work-index {
  width: 40rpx;
  color: #9aa0ab;
  font-style: italic;
  font-weight: 700;
}

.work-info {
  flex: 1;
  min-width: 0;
}

.work-name {
  font-size: 28rpx;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.work-meta {
  font-size: 22rpx;
  color: #9aa0ab;
  margin-top: 4rpx;
}

.bottom-space {
  height: 160rpx;
}
</style>
