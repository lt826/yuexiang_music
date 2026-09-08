<script setup lang="ts">
import { nextTick, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Plus } from '@element-plus/icons-vue'
import TopNav from '@/components/TopNav.vue'
import AddToPlaylistDialog from '@/components/AddToPlaylistDialog.vue'
import { usePlayerStore } from '@/stores/player'
import { useSongActions } from '@/composables/useSongActions'
import { useUserStore } from '@/stores/user'
import { aiChat, type AiChatMessage, type AiSongVO } from '@/api/ai'
import type { SongVO } from '@/api/home'

const route = useRoute()
const router = useRouter()
const playerStore = usePlayerStore()
const userStore = useUserStore()

/** 单条聊天气泡 */
interface ChatItem extends AiChatMessage {
  /** loading 占位中 */
  pending?: boolean
}

/** 历史会话按用户隔离存储（key 带 userId，换账号互不可见，且不落服务端） */
function historyKey(): string {
  return `yuexiang-ai-history:u${userStore.userId || 0}`
}

function loadHistory(): ChatItem[] {
  try {
    const raw = localStorage.getItem(historyKey())
    if (!raw) return []
    const arr = JSON.parse(raw) as ChatItem[]
    // 只恢复完整消息：排除异常数据与 loading 中断的占位
    return Array.isArray(arr)
      ? arr.filter((m) => m && m.content && (m.role === 'user' || m.role === 'assistant') && !m.pending)
      : []
  } catch {
    return []
  }
}

function saveHistory() {
  // 只存 role/content/songs/createdPlaylist，pending 状态不落盘
  const plain = messages.value
    .filter((m) => !m.pending && m.content)
    .map((m) => ({ role: m.role, content: m.content, songs: m.songs, createdPlaylist: m.createdPlaylist }))
  try {
    localStorage.setItem(historyKey(), JSON.stringify(plain))
  } catch {
    // 存储满/隐私模式静默失败，不影响对话
  }
}

function clearHistory() {
  messages.value = []
  try {
    localStorage.removeItem(historyKey())
  } catch {
    // ignore
  }
}

const input = ref('')
// 进入页面恢复本人上次对话；为空时展示引导示例
const messages = ref<ChatItem[]>(loadHistory())

// 未登录游客共用 0 号历史；登录后（或换账号）立即切换到本人历史
watch(
  () => userStore.userId,
  () => {
    messages.value = loadHistory()
  },
)
const sending = ref(false)
const listRef = ref<HTMLElement | null>(null)

/** 引导示例：点击直接提问（与当前曲库对齐，避免示例词查无此歌） */
const suggestions = [
  '我想找一句歌词是"可还是听见了你说不可能"的歌',
  'Justin Bieber有哪些歌',
  '歌手卡卡有几首歌',
]

onMounted(() => {
  loadFavored()
  // 搜索页"没找到"跳转过来时自动带词提问
  const q = String(route.query.q || '').trim()
  if (q) {
    input.value = q
    send()
  } else {
    scrollToBottom()
  }
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
      pendingItem.createdPlaylist = { id: vo.createdPlaylistId, title: vo.createdPlaylistTitle || '我的AI歌单' }
    }
  } catch {
    // 拦截器已 toast，这里兜底把占位气泡变成提示
    pendingItem.content = '抱歉，AI 服务暂时不可用，请稍后再试。'
    pendingItem.songs = []
  } finally {
    // 先清 pending 再持久化：saveHistory 只存非 pending 消息，
    // 顺序反了会把本轮回答整体过滤掉（刷新后回答丢失）
    pendingItem.pending = false
    saveHistory()
    sending.value = false
    scrollToBottom()
  }
}

/** AiSongVO → 播放器 SongVO：url/lyric 等缺省字段由播放器 play/{id} 详情接口兜底 */
function toSong(s: AiSongVO): SongVO {
  return {
    id: s.id,
    name: s.name,
    cover: null,
    url: null,
    lyric: null,
    style: s.style,
    playCount: s.playCount,
    singerId: s.singerId ?? 0,
    singerName: s.singerName,
  }
}

/** 把本轮 AI 给出的歌曲作为队列从 index 播放 */
function playSongs(songs: NonNullable<AiChatMessage['songs']>, index: number) {
  if (!songs.length) return
  playerStore.playQueue(songs.map(toSong), index)
}

/** 打开 AI 刚生成的歌单 */
function openPlaylist(id: number) {
  router.push(`/playlist/${id}`)
}

// ===== 收藏与加入歌单（全站公共逻辑） =====
const songActions = useSongActions()
const { favoredIds, addPanel, loadFavored, toggleFavorite, openAddPanel } = songActions

function formatCount(n: number): string {
  if (n >= 100000000) return (n / 100000000).toFixed(1) + '亿'
  if (n >= 10000) return (n / 10000).toFixed(1) + '万'
  return String(n)
}

async function scrollToBottom() {
  await nextTick()
  if (listRef.value) {
    listRef.value.scrollTop = listRef.value.scrollHeight
  }
}
</script>

<template>
  <div class="page">
    <TopNav />
    <main class="main">
      <div class="ai-shell">
        <header class="ai-head">
          <span class="badge">AI</span>
          <div class="head-text">
            <span class="yx-eyebrow">SMART SEARCH</span>
            <h2 class="yxs-title yx-display">智能搜歌助手</h2>
            <p>用一句话找歌：歌词片段、歌手名、心情场景都行</p>
          </div>
          <el-button
            v-if="messages.length > 0"
            class="clear-btn"
            text
            type="danger"
            @click="clearHistory"
          >
            清空对话
          </el-button>
        </header>

        <!-- 空态引导 -->
        <div v-if="messages.length === 0" class="empty-guide">
          <div class="guide-icon">🎵</div>
          <p class="guide-title">嗨，我是悦享 AI 助手</p>
          <p class="guide-sub">告诉我你想听什么，我来帮你在曲库里翻</p>
          <div class="suggestion-list">
            <button
              v-for="s in suggestions"
              :key="s"
              class="suggestion"
              type="button"
              @click="pickSuggestion(s)"
            >
              {{ s }}
            </button>
          </div>
          <ul class="ability-list">
            <li>🔎 歌词片段找歌：「有一句歌词是 xxx 的歌」</li>
            <li>🎤 查歌手全部作品：「他有什么歌」</li>
            <li>🔥 热门推荐：「最近大家都听什么」</li>
            <li>📊 听歌周报：「我最近听了什么」</li>
            <li>🎶 生成歌单：「我想听适合跑步的歌，帮我建个歌单」</li>
          </ul>
        </div>

        <!-- 对话区 -->
        <div v-show="messages.length > 0" ref="listRef" class="chat-list">
          <template v-for="(msg, i) in messages" :key="i">
            <div class="row" :class="msg.role === 'user' ? 'row-user' : 'row-ai'">
              <div v-if="msg.role === 'assistant'" class="avatar">🤖</div>
              <div class="bubble" :class="{ 'bubble-user': msg.role === 'user' }">
                <span v-if="msg.pending" class="typing"><i /><i /><i /></span>
                <template v-else>
                  <span class="text">{{ msg.content }}</span>
                  <!-- 命中歌曲列表：紧凑行布局，完整歌名+歌手可直接点播 -->
                  <ol v-if="msg.songs && msg.songs.length > 0" class="song-card-list">
                    <li
                      v-for="(song, idx) in msg.songs"
                      :key="song.id"
                      class="song-row"
                      @click="playSongs(msg.songs!, idx)"
                    >
                      <span class="play-dot">▶</span>
                      <span class="song-info">
                        <span class="song-name">{{ song.name }}</span>
                        <span class="song-meta">{{ song.singerName || '未知歌手' }} · {{ song.style || '流行' }}</span>
                      </span>
                      <span class="song-count tabular-nums">{{ formatCount(song.playCount) }}次播放</span>
                      <!-- 操作区：收藏 / 添加到歌单（阻止冒泡避免触发播放） -->
                      <span class="song-actions">
                        <button
                          class="act-btn"
                          :class="{ 'is-favored': favoredIds.has(song.id) }"
                          :title="favoredIds.has(song.id) ? '已收藏' : '收藏'"
                          @click.stop="toggleFavorite(song)"
                        >
                          {{ favoredIds.has(song.id) ? '♥' : '♡' }}
                        </button>
                        <button
                          class="act-btn"
                          title="添加到我创建的歌单"
                          @click.stop="openAddPanel(song)"
                        >
                          <el-icon :size="14"><Plus /></el-icon>
                        </button>
                      </span>
                    </li>
                  </ol>
                  <!-- AI 新建的歌单卡片：点击跳歌单详情 -->
                  <button
                    v-if="msg.createdPlaylist"
                    class="playlist-card"
                    type="button"
                    @click="openPlaylist(msg.createdPlaylist.id)"
                  >
                    <span class="pl-icon">🎶</span>
                    <span class="pl-card-info">
                      <span class="pl-card-title">{{ msg.createdPlaylist.title }}</span>
                      <span class="pl-card-sub">AI 已为你生成歌单 · 点击查看</span>
                    </span>
                    <span class="pl-arrow">›</span>
                  </button>
                </template>
              </div>
            </div>
          </template>
        </div>

        <!-- 输入区 -->
        <footer class="input-bar">
          <el-input
            v-model="input"
            class="chat-input"
            placeholder="试试：想听一首民谣"
            size="large"
            clearable
            :disabled="sending"
            @keyup.enter="send"
          />
          <el-button type="primary" size="large" :loading="sending" @click="send">
            发送
          </el-button>
        </footer>
      </div>

      <!-- 加入歌单弹窗 -->
      <AddToPlaylistDialog :actions="songActions" />
    </main>
  </div>
</template>

<style scoped>
.main {
  max-width: 900px;
  margin: 0 auto;
  padding: 24px 20px 40px;
}

.ai-shell {
  display: flex;
  flex-direction: column;
  height: calc(100vh - 140px);
  min-height: 480px;
  background: #fff;
  border: 1px solid var(--yx-hairline);
  border-radius: var(--yx-radius);
  box-shadow: var(--yx-shadow-card);
  overflow: hidden;
}

.ai-head {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 16px 20px;
  border-bottom: 1px solid var(--yx-hairline);
}

.badge {
  width: 40px;
  height: 40px;
  border-radius: 10px;
  background: var(--yx-accent-weak);
  color: var(--yx-accent);
  font-weight: 700;
  font-size: 15px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.yxs-title {
  font-size: 16px;
  margin: 0;
}

.head-text p {
  margin: 2px 0 0;
  font-size: 12px;
  color: var(--yx-text-secondary);
}

.clear-btn {
  margin-left: auto;
}

/* ===== 空态引导 ===== */
.empty-guide {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 24px;
  gap: 8px;
  text-align: center;
}

.guide-icon {
  font-size: 44px;
}

.guide-title {
  font-size: 18px;
  font-weight: 600;
  margin: 0;
}

.guide-sub {
  color: var(--yx-text-secondary);
  font-size: 13px;
  margin: 0;
}

.suggestion-list {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 10px;
  margin-top: 14px;
}

.suggestion {
  padding: 8px 16px;
  border: 1px solid var(--yx-hairline);
  border-radius: 999px;
  background: #fff;
  cursor: pointer;
  font-size: 13px;
  transition: all 0.2s ease;
}

.suggestion:hover {
  border-color: var(--yx-accent);
  background: var(--yx-accent-weak);
}

.ability-list {
  list-style: none;
  padding: 0;
  margin: 18px 0 0;
  display: flex;
  flex-direction: column;
  gap: 6px;
  font-size: 12px;
  color: var(--yx-text-disabled);
}

/* ===== 对话区 ===== */
.chat-list {
  flex: 1;
  overflow-y: auto;
  padding: 20px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.row {
  display: flex;
  gap: 10px;
  align-items: flex-start;
}

.row-user {
  justify-content: flex-end;
}

.avatar {
  width: 34px;
  height: 34px;
  border-radius: 50%;
  background: var(--yx-accent-weak);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 18px;
  flex-shrink: 0;
}

.bubble {
  max-width: 76%;
  background: #f6f7fa;
  border-radius: 12px;
  padding: 10px 14px;
  font-size: 14px;
  line-height: 1.7;
}

.bubble-user {
  background: var(--yx-accent);
  color: #fff;
}

.text {
  white-space: pre-wrap;
  word-break: break-word;
}

.typing {
  display: inline-flex;
  gap: 4px;
  padding: 6px 0;
}

.typing i {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--yx-text-disabled);
  animation: typing-bounce 1.2s infinite ease-in-out;
}

.typing i:nth-child(2) {
  animation-delay: 0.15s;
}

.typing i:nth-child(3) {
  animation-delay: 0.3s;
}

@keyframes typing-bounce {
  0%, 60%, 100% { transform: translateY(0); opacity: 0.4; }
  30% { transform: translateY(-4px); opacity: 1; }
}

/* ===== 气泡内歌曲卡片 ===== */
.song-card-list {
  list-style: none;
  margin: 10px 0 2px;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.song-row {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 12px;
  background: #fff;
  border: 1px solid var(--yx-hairline);
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.2s ease;
}

.song-row:hover {
  border-color: var(--yx-accent);
  background: var(--yx-accent-weak);
}

.play-dot {
  width: 26px;
  height: 26px;
  border-radius: 50%;
  background: var(--yx-accent-weak);
  color: var(--yx-accent);
  font-size: 11px;
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
  gap: 2px;
}

.song-name {
  font-size: 13px;
  font-weight: 600;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.song-meta {
  font-size: 12px;
  color: var(--yx-text-secondary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.song-count {
  font-size: 11px;
  color: var(--yx-text-disabled);
  flex-shrink: 0;
}

/* 行尾操作区：收藏 / 加号，hover 行时浮现 */
.song-actions {
  display: flex;
  gap: 4px;
  flex-shrink: 0;
}

.act-btn {
  width: 26px;
  height: 26px;
  border: none;
  border-radius: 50%;
  background: transparent;
  color: var(--yx-text-secondary);
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 14px;
  line-height: 1;
  transition: all 0.15s ease;
}

.act-btn:hover {
  background: var(--yx-accent-weak);
  color: var(--yx-accent);
}

.act-btn.is-favored {
  color: #eb5757;
}

.act-btn.is-favored:hover {
  background: rgba(235, 87, 87, 0.1);
  color: #eb5757;
}

/* ===== 气泡内歌单卡片（AI 生成歌单） ===== */
.playlist-card {
  display: flex;
  align-items: center;
  gap: 10px;
  width: 100%;
  margin-top: 10px;
  padding: 12px 14px;
  background: linear-gradient(135deg, #eef4fd, #f7f0fd);
  border: 1px solid #d8e6fb;
  border-radius: 8px;
  cursor: pointer;
  text-align: left;
  transition: all 0.15s ease;
}

.playlist-card:hover {
  border-color: var(--yx-accent);
}

.pl-icon {
  font-size: 22px;
}

.pl-card-info {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.pl-card-title {
  font-size: 13px;
  font-weight: 600;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.pl-card-sub {
  font-size: 12px;
  color: var(--yx-accent);
}

.pl-arrow {
  color: var(--yx-text-disabled);
  font-size: 18px;
}

/* ===== 添加到歌单弹窗 ===== */
.add-target {
  margin: 0 0 12px;
  font-size: 13px;
  color: var(--yx-text-secondary);
}

.playlist-options {
  min-height: 80px;
  max-height: 320px;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.playlist-option {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  border: 1px solid var(--yx-hairline);
  border-radius: 8px;
  background: #fff;
  cursor: pointer;
  font-size: 13px;
  text-align: left;
  transition: all 0.15s ease;
}

.playlist-option:hover {
  border-color: var(--yx-accent);
  background: var(--yx-accent-weak);
}

.playlist-option:disabled {
  opacity: 0.6;
  cursor: wait;
}

.pl-title {
  flex: 1;
  min-width: 0;
  font-weight: 600;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.pl-count {
  font-size: 12px;
  color: var(--yx-text-secondary);
}

.pl-adding {
  color: var(--yx-accent);
}

/* ===== 输入区 ===== */
.input-bar {
  display: flex;
  gap: 12px;
  padding: 14px 16px;
  border-top: 1px solid var(--yx-hairline);
  background: #fff;
}

.chat-input {
  flex: 1;
}
</style>
