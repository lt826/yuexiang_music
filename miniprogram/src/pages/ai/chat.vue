<script setup lang="ts">
import { ref, onMounted, nextTick, watch } from 'vue'
import { aiChat, toSongVO, type AiChatMessage } from '@/api/ai'
import { usePlayerStore } from '@/stores/player'
import { useUserStore } from '@/stores/user'
import type { SongVO } from '@/api/types'

const player = usePlayerStore()
const userStore = useUserStore()

/** 单条聊天气泡 */
interface ChatItem extends AiChatMessage {
  /** loading 占位中 */
  pending?: boolean
}

/** 历史会话按用户隔离存储（key 带 userId，换账号互不可见，且不落服务端） */
function historyKey(): string {
  return `yx_ai_history:u${userStore.user?.userId || 0}`
}

function loadHistory(): ChatItem[] {
  try {
    const raw = uni.getStorageSync(historyKey()) as string
    if (!raw) return []
    const arr = JSON.parse(raw) as ChatItem[]
    // 只恢复完整消息：排除异常数据与 loading 中断的占位
    return Array.isArray(arr)
      ? arr.filter(
          (m) => m && m.content && (m.role === 'user' || m.role === 'assistant') && !m.pending
        )
      : []
  } catch {
    return []
  }
}

function saveHistory() {
  // 只存 role/content/songs/createdPlaylist，pending 状态不落盘
  const plain = messages.value
    .filter((m) => !m.pending && m.content)
    .map((m) => ({
      role: m.role,
      content: m.content,
      songs: m.songs,
      createdPlaylist: m.createdPlaylist,
    }))
  try {
    uni.setStorageSync(historyKey(), JSON.stringify(plain))
  } catch {
    // 存储满/隐私模式静默失败，不影响对话
  }
}

function clearHistory() {
  uni.showModal({
    title: '清空对话',
    content: '确定清空全部对话记录吗？',
    success: (res) => {
      if (!res.confirm) return
      messages.value = []
      try {
        uni.removeStorageSync(historyKey())
      } catch {
        // ignore
      }
    },
  })
}

const input = ref('')
// 进入页面恢复本人上次对话；为空时展示引导示例
const messages = ref<ChatItem[]>(loadHistory())

// 未登录游客共用 0 号历史；登录后（或换账号）立即切换到本人历史
watch(
  () => userStore.user?.userId,
  () => {
    messages.value = loadHistory()
  }
)
const sending = ref(false)

/** 滚动锚点：每次消息变化时指向最后一条气泡 */
const scrollAnchor = ref('')

/** 引导示例：点击直接提问 */
const suggestions = [
  '我想找一句歌词是"故事的小黄花"的歌',
  '帮我看看平台歌手都有哪些歌',
  '最近有什么好听的歌曲推荐吗？',
  '我想听适合跑步的歌，帮我建个歌单',
]

/** 滚到底部（锚点指向末尾占位元素） */
async function scrollToBottom() {
  await nextTick()
  scrollAnchor.value = ''
  setTimeout(() => {
    scrollAnchor.value = 'chat-bottom'
  }, 50)
}

onMounted(() => {
  scrollToBottom()
})

function pickSuggestion(text: string) {
  input.value = text
  send()
}

async function send() {
  const question = input.value.trim()
  if (!question || sending.value) return
  input.value = ''
  messages.value.push({ role: 'user', content: question })
  const pendingItem: ChatItem = { role: 'assistant', content: '', pending: true }
  messages.value.push(pendingItem)
  sending.value = true
  scrollToBottom()

  try {
    // 只带文本历史（歌曲列表不回传），后端截取最近 N 条防超长
    const history = messages.value
      .filter((m) => !m.pending && m.content)
      .slice(0, -1)
      .map((m) => ({ role: m.role as 'user' | 'assistant', content: m.content }))
    const vo = await aiChat(question, history)
    pendingItem.content = vo.reply
    pendingItem.songs = vo.songs || []
    if (vo.createdPlaylistId) {
      pendingItem.createdPlaylist = {
        id: vo.createdPlaylistId,
        title: vo.createdPlaylistTitle || '我的AI歌单',
      }
    }
  } catch {
    // request 层已 toast，这里兜底把占位气泡变成提示
    pendingItem.content = '抱歉，AI 服务暂时不可用，请稍后再试。'
    pendingItem.songs = []
  } finally {
    // 先清 pending 再持久化：saveHistory 只存非 pending 消息，
    // 顺序反了会把本轮回答整体过滤掉（退出页面后回答丢失）
    pendingItem.pending = false
    saveHistory()
    sending.value = false
    scrollToBottom()
  }
}

/** 把本轮 AI 给出的歌曲作为队列从 index 播放（未登录由播放器统一引导登录） */
function playSongs(songs: NonNullable<AiChatMessage['songs']>, index: number) {
  if (!songs.length) return
  player.playQueue(songs.map(toSongVO), index)
}

/** 打开 AI 刚生成的歌单 */
function openPlaylist(id: number) {
  uni.navigateTo({ url: `/pages/playlist/detail?id=${id}` })
}

function formatCount(n: number): string {
  if (n >= 100000000) return (n / 100000000).toFixed(1) + '亿'
  if (n >= 10000) return (n / 10000).toFixed(1) + '万'
  return String(n)
}
</script>

<template>
  <view class="page">
    <!-- 空态引导 -->
    <view v-if="messages.length === 0" class="empty-guide">
      <view class="guide-icon">🎵</view>
      <view class="guide-title">嗨，我是悦享 AI 助手</view>
      <view class="guide-sub">告诉我你想听什么，我来帮你在曲库里翻</view>
      <view class="suggestion-list">
        <view v-for="s in suggestions" :key="s" class="suggestion" @tap="pickSuggestion(s)">
          {{ s }}
        </view>
      </view>
      <view class="ability-list">
        <view>🔎 歌词片段找歌：「有一句歌词是 xxx 的歌」</view>
        <view>🎤 查歌手全部作品：「他有什么歌」</view>
        <view>🔥 热门推荐：「最近大家都听什么」</view>
        <view>📊 听歌周报：「我最近听了什么」</view>
        <view>🎶 生成歌单：「我想听适合跑步的歌，帮我建个歌单」</view>
      </view>
    </view>

    <!-- 对话区 -->
    <scroll-view
      v-show="messages.length > 0"
      class="chat-list"
      scroll-y
      :scroll-into-view="scrollAnchor"
      scroll-with-animation
    >
      <view class="chat-inner">
        <view class="head-bar">
          <view class="head-badge">AI</view>
          <view class="head-text">智能搜歌助手 · 歌词片段 / 歌手 / 心情都能找</view>
          <view class="clear-btn" @tap="clearHistory">清空</view>
        </view>

        <view
          v-for="(msg, i) in messages"
          :key="i"
          class="row"
          :class="msg.role === 'user' ? 'row-user' : 'row-ai'"
        >
          <view v-if="msg.role === 'assistant'" class="avatar">🤖</view>
          <view class="bubble" :class="{ 'bubble-user': msg.role === 'user' }">
            <view v-if="msg.pending" class="typing"><text /><text /><text /></view>
            <template v-else>
              <text class="text">{{ msg.content }}</text>
              <!-- 命中歌曲列表：完整歌名+歌手，点行从该首起播整组 -->
              <view v-if="msg.songs && msg.songs.length > 0" class="song-card-list">
                <view
                  v-for="(song, idx) in msg.songs"
                  :key="song.id"
                  class="song-row"
                  @tap="playSongs(msg.songs!, idx)"
                >
                  <view class="play-dot">▶</view>
                  <view class="song-info">
                    <view class="song-name">{{ song.name }}</view>
                    <view class="song-meta"
                      >{{ song.singerName || '未知歌手' }} · {{ song.style || '流行' }}</view
                    >
                  </view>
                  <view class="song-count">{{ formatCount(song.playCount) }}次播放</view>
                </view>
              </view>
              <!-- AI 新建的歌单卡片：点击跳歌单详情 -->
              <view
                v-if="msg.createdPlaylist"
                class="playlist-card"
                @tap="openPlaylist(msg.createdPlaylist.id)"
              >
                <view class="pl-icon">🎶</view>
                <view class="pl-info">
                  <view class="pl-title">{{ msg.createdPlaylist.title }}</view>
                  <view class="pl-sub">AI 已为你生成歌单 · 点击查看</view>
                </view>
                <text class="pl-arrow">›</text>
              </view>
            </template>
          </view>
        </view>
        <view id="chat-bottom" class="chat-bottom-anchor" />
      </view>
    </scroll-view>

    <!-- 输入区 -->
    <view class="input-bar">
      <input
        v-model="input"
        class="chat-input"
        placeholder="试试：想找一首关于夏天的民谣…"
        confirm-type="send"
        :disabled="sending"
        :adjust-position="true"
        :cursor-spacing="20"
        @confirm="send"
      />
      <view class="send-btn" :class="{ 'is-sending': sending }" @tap="send">
        {{ sending ? '...' : '发送' }}
      </view>
    </view>
  </view>
</template>

<style scoped>
.page {
  height: 100vh;
  display: flex;
  flex-direction: column;
  background: #f5f6fa;
}

/* ===== 空态引导 ===== */
.empty-guide {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 40rpx 60rpx;
  gap: 12rpx;
  text-align: center;
}

.guide-icon {
  font-size: 88rpx;
}

.guide-title {
  font-size: 36rpx;
  font-weight: 600;
}

.guide-sub {
  color: #9aa0ab;
  font-size: 26rpx;
}

.suggestion-list {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 16rpx;
  margin-top: 28rpx;
}

.suggestion {
  padding: 14rpx 28rpx;
  border: 1rpx solid #e3e8f0;
  border-radius: 999rpx;
  background: #fff;
  font-size: 24rpx;
  color: #445;
}

.suggestion:active {
  border-color: #2a78d6;
  background: #eef4fd;
}

.ability-list {
  margin-top: 36rpx;
  display: flex;
  flex-direction: column;
  gap: 12rpx;
  font-size: 22rpx;
  color: #b0b6c2;
}

/* ===== 对话区 ===== */
.chat-list {
  flex: 1;
  min-height: 0;
}

.chat-inner {
  padding: 20rpx 24rpx 30rpx;
  display: flex;
  flex-direction: column;
  gap: 20rpx;
}

.head-bar {
  display: flex;
  align-items: center;
  gap: 14rpx;
  background: #fff;
  border-radius: 16rpx;
  padding: 18rpx 24rpx;
}

.head-badge {
  width: 56rpx;
  height: 56rpx;
  border-radius: 14rpx;
  background: #eef4fd;
  color: #2a78d6;
  font-weight: 700;
  font-size: 24rpx;
  display: flex;
  align-items: center;
  justify-content: center;
}

.head-text {
  flex: 1;
  font-size: 24rpx;
  color: #66708a;
}

.clear-btn {
  font-size: 24rpx;
  color: #d03b3b;
  padding: 6rpx 16rpx;
}

.row {
  display: flex;
  gap: 16rpx;
  align-items: flex-start;
}

.row-user {
  justify-content: flex-end;
}

.avatar {
  width: 60rpx;
  height: 60rpx;
  border-radius: 50%;
  background: #eef4fd;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 32rpx;
  flex-shrink: 0;
}

.bubble {
  max-width: 78%;
  background: #fff;
  border-radius: 20rpx;
  padding: 20rpx 24rpx;
  font-size: 27rpx;
  line-height: 1.7;
}

.bubble-user {
  background: linear-gradient(135deg, #3987e5, #1c5cab);
  color: #fff;
}

.text {
  white-space: pre-wrap;
  word-break: break-all;
}

.typing {
  display: inline-flex;
  gap: 8rpx;
  padding: 8rpx 0;
}

.typing text {
  width: 12rpx;
  height: 12rpx;
  border-radius: 50%;
  background: #b0b6c2;
  animation: typing-bounce 1.2s infinite ease-in-out;
}

.typing text:nth-child(2) {
  animation-delay: 0.15s;
}

.typing text:nth-child(3) {
  animation-delay: 0.3s;
}

@keyframes typing-bounce {
  0%,
  60%,
  100% {
    transform: translateY(0);
    opacity: 0.4;
  }
  30% {
    transform: translateY(-8rpx);
    opacity: 1;
  }
}

/* ===== 气泡内歌曲卡片 ===== */
.song-card-list {
  margin-top: 16rpx;
  display: flex;
  flex-direction: column;
  gap: 12rpx;
}

.song-row {
  display: flex;
  align-items: center;
  gap: 16rpx;
  padding: 14rpx 20rpx;
  background: #f6f7fa;
  border-radius: 14rpx;
}

.song-row:active {
  background: #eef4fd;
}

.play-dot {
  width: 48rpx;
  height: 48rpx;
  border-radius: 50%;
  background: #eef4fd;
  color: #2a78d6;
  font-size: 20rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.song-info {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 4rpx;
}

.song-name {
  font-size: 26rpx;
  font-weight: 600;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.song-meta {
  font-size: 22rpx;
  color: #9aa0ab;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.song-count {
  font-size: 20rpx;
  color: #b0b6c2;
  flex-shrink: 0;
}

/* ===== 气泡内歌单卡片（AI 生成歌单） ===== */
.playlist-card {
  margin-top: 16rpx;
  display: flex;
  align-items: center;
  gap: 16rpx;
  padding: 20rpx 24rpx;
  background: linear-gradient(135deg, #eef4fd, #f7f0fd);
  border: 1rpx solid #d8e6fb;
  border-radius: 14rpx;
}

.playlist-card:active {
  opacity: 0.8;
}

.pl-icon {
  font-size: 40rpx;
}

.pl-info {
  flex: 1;
  min-width: 0;
}

.pl-title {
  font-size: 26rpx;
  font-weight: 600;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.pl-sub {
  margin-top: 4rpx;
  font-size: 22rpx;
  color: #2a78d6;
}

.pl-arrow {
  color: #b0b6c2;
  font-size: 36rpx;
}

.chat-bottom-anchor {
  height: 10rpx;
}

/* ===== 输入区 ===== */
.input-bar {
  display: flex;
  gap: 16rpx;
  padding: 16rpx 24rpx calc(16rpx + env(safe-area-inset-bottom));
  background: #fff;
  border-top: 1rpx solid #eef0f4;
}

.chat-input {
  flex: 1;
  height: 76rpx;
  background: #f5f6fa;
  border-radius: 999rpx;
  padding: 0 30rpx;
  font-size: 27rpx;
}

.send-btn {
  flex-shrink: 0;
  min-width: 130rpx;
  height: 76rpx;
  line-height: 76rpx;
  text-align: center;
  background: linear-gradient(135deg, #3987e5, #1c5cab);
  color: #fff;
  font-size: 27rpx;
  border-radius: 999rpx;
  padding: 0 24rpx;
}

.send-btn.is-sending {
  opacity: 0.6;
}
</style>
