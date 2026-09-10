<script setup lang="ts">
import { onMounted, onUnmounted, ref, computed, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowLeft, Delete, Position } from '@element-plus/icons-vue'
import TopNav from '@/components/TopNav.vue'
import SongCover from '@/components/SongCover.vue'
import { useUserStore } from '@/stores/user'
import { getSongPlay } from '@/api/song'
import type { SongVO } from '@/api/home'
import {
  listenWsUrl,
  endListenRoom,
  muteListenMember,
  kickListenMember,
  type ListenMemberVO,
} from '@/api/circle'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const roomId = computed(() => Number(route.params.roomId))
const connected = ref(false)
const connecting = ref(true)
const roomClosed = ref(false)

/** 进入用户公开主页（聊天/成员列表昵称可点击） */
function goUserHome(userId?: number) {
  if (userId) {
    router.push({ name: 'user-home', params: { id: userId } })
  }
}

/** 房间状态 */
const theme = ref('')
const creatorId = ref(0)
const members = ref<ListenMemberVO[]>([])
const memberCount = ref(0)

/** 播放状态（同步时间轴） */
const song = ref<Partial<SongVO> | null>(null)
const playing = ref(false)
const position = ref(0)
const duration = ref(0)

/** 聊天 */
interface ChatItem {
  kind: 'chat' | 'system'
  userId?: number
  username?: string
  avatar?: string
  content: string
  time: number
}
const chatList = ref<ChatItem[]>([])
const chatInput = ref('')
const chatBox = ref<HTMLElement | null>(null)

/** 我是否管理员（发起人/圈主/圈管理员/平台 ADMIN，服务端下发的成员列表里有 isManager 标记） */
const amManager = computed(
  () => userStore.userId === creatorId.value || userStore.role === 'ADMIN'
    || members.value.some((m) => m.userId === userStore.userId && m.isManager),
)

// ===== 音频 =====
const audio = new Audio()
audio.preload = 'auto'
let audioReady = false

/** WS */
let ws: WebSocket | null = null
let reconnectAttempts = 0
let heartbeatTimer: ReturnType<typeof setInterval> | null = null
let reconnectTimer: ReturnType<typeof setTimeout> | null = null
const MAX_RECONNECT = 5
let intentionalClose = false

function connect() {
  connecting.value = true
  const url = listenWsUrl(roomId.value)
  ws = new WebSocket(url)

  ws.onopen = () => {
    reconnectAttempts = 0
    connected.value = true
    connecting.value = false
    send({ type: 'JOIN_ROOM', roomId: roomId.value })
    // 心跳保活 30s（对齐需求文档）
    if (heartbeatTimer) clearInterval(heartbeatTimer)
    heartbeatTimer = setInterval(() => send({ type: 'HEARTBEAT' }), 30000)
  }

  ws.onmessage = (ev) => {
    try {
      const msg = JSON.parse(ev.data as string)
      handleMessage(msg.type, msg.data || {})
    } catch {
      // 非 JSON 消息忽略
    }
  }

  ws.onclose = () => {
    connected.value = false
    connecting.value = false
    if (heartbeatTimer) {
      clearInterval(heartbeatTimer)
      heartbeatTimer = null
    }
    stopAudio()
    // 指数退避重连（房间未主动退出且未被解散）
    if (!intentionalClose && !roomClosed.value && reconnectAttempts < MAX_RECONNECT) {
      const delay = Math.min(1000 * 2 ** reconnectAttempts, 30000)
      reconnectAttempts += 1
      reconnectTimer = setTimeout(connect, delay)
    }
  }

  ws.onerror = () => {
    // onclose 会随后触发，重连逻辑在那里
  }
}

function send(payload: object) {
  if (ws && ws.readyState === WebSocket.OPEN) {
    ws.send(JSON.stringify(payload))
  }
}

// ===== 消息分发 =====
function handleMessage(type: string, data: Record<string, unknown>) {
  switch (type) {
    case 'ROOM_STATE':
      applyRoomState(data)
      break
    case 'USER_JOIN':
      pushChat({ kind: 'system', content: `${data.username} 加入了房间`, time: Date.now() })
      break
    case 'USER_LEAVE':
      pushChat({ kind: 'system', content: `${data.username} 离开了房间`, time: Date.now() })
      break
    case 'CHAT_MESSAGE':
      pushChat({
        kind: 'chat',
        userId: Number(data.userId),
        username: String(data.username || ''),
        avatar: String(data.avatar || ''),
        content: String(data.content || ''),
        time: Number(data.time || Date.now()),
      })
      break
    case 'SYSTEM':
      pushChat({ kind: 'system', content: String(data.content || ''), time: Date.now() })
      break
    case 'PLAYBACK_CTRL':
      applyPlayback(data)
      break
    case 'MEMBER_MUTED':
      pushChat({ kind: 'system', content: `${data.username} 已被禁言`, time: Date.now() })
      break
    case 'MEMBER_UNMUTED':
      pushChat({ kind: 'system', content: `${data.username} 已被解除禁言`, time: Date.now() })
      break
    case 'KICKED':
      roomClosed.value = true
      ElMessage.warning('您已被移出房间')
      cleanup()
      break
    case 'ROOM_CLOSED':
      roomClosed.value = true
      ElMessage.info('一起听已结束')
      cleanup()
      break
    case 'ERROR':
      ElMessage.warning(String(data.message || '操作失败'))
      break
  }
}

/** 房间全量状态（进入/重连/SYNC_STATE 时下发） */
function applyRoomState(data: Record<string, unknown>) {
  theme.value = String(data.theme || '')
  creatorId.value = Number(data.creatorId || 0)
  members.value = (data.members as ListenMemberVO[]) || []
  memberCount.value = Number(data.memberCount || members.value.length)
  const newSong = (data.song as Partial<SongVO> | undefined) || null
  const status = String(data.status || 'idle')
  const actionAt = Number(data.actionAt || Date.now())
  const serverPosition = Number(data.position || 0)

  const songChanged = !!newSong && newSong.id !== song.value?.id
  song.value = newSong

  if (status === 'idle' || !newSong) {
    playing.value = false
    stopAudio()
    return
  }

  // 按服务器时间轴推算应播位置：position + (本地now - actionAt)/1000
  // （本机时钟与服务器可能有偏差，但课设场景内网偏差毫秒级可接受）
  const expected = Math.max(0, serverPosition + (Date.now() - actionAt) / 1000)
  position.value = expected

  // 首次或切歌：拉播放地址
  if (songChanged || !audioReady) {
    void loadAndSync(expected, status === 'playing')
  } else {
    syncAudio(expected, status === 'playing')
  }
}

/** 播放控制广播 */
function applyPlayback(data: Record<string, unknown>) {
  const action = String(data.action || '')
  const now = Number(data.timestamp || Date.now())
  const pos = Number(data.position || 0)

  if (action === 'playSong') {
    const newSong = (data.song as Partial<SongVO> | undefined) || null
    song.value = newSong
    position.value = 0
    playing.value = true
    if (newSong?.id) void loadAndSync(0, true)
    return
  }
  if (action === 'stopSong') {
    song.value = null
    playing.value = false
    stopAudio()
    return
  }
  if (action === 'play') {
    playing.value = true
    position.value = Math.max(0, pos + (Date.now() - now) / 1000)
    syncAudio(position.value, true)
    return
  }
  if (action === 'pause') {
    playing.value = false
    position.value = pos
    syncAudio(pos, false)
    return
  }
  if (action === 'seek') {
    position.value = pos
    syncAudio(pos, playing.value)
  }
}

/** 拉播放地址并按时间轴起播 */
async function loadAndSync(at: number, autoplay: boolean) {
  if (!song.value?.id) return
  try {
    const detail = await getSongPlay(song.value.id)
    if (!detail.url) {
      ElMessage.warning('该歌曲没有可播放的音频文件')
      return
    }
    song.value = detail
    if (audio.src !== detail.url) {
      audio.src = detail.url
      audioReady = true
    }
    syncAudio(at, autoplay)
  } catch {
    // 拦截器已提示
  }
}

/** 同步音频位置与播放态 */
function syncAudio(at: number, autoplay: boolean) {
  const apply = () => {
    if (Math.abs(audio.currentTime - at) > 1.5) {
      audio.currentTime = at
    }
    if (autoplay && audio.paused) {
      audio.play().catch(() => undefined)
    } else if (!autoplay && !audio.paused) {
      audio.pause()
    }
  }
  if (audio.readyState >= 1) {
    apply()
  } else {
    audio.addEventListener('loadedmetadata', apply, { once: true })
  }
}

function stopAudio() {
  audio.pause()
}

// 音频进度（仅用于本地进度条显示；管理员 seek 走广播）
audio.addEventListener('timeupdate', () => {
  if (playing.value) {
    position.value = audio.currentTime
  }
})
audio.addEventListener('loadedmetadata', () => {
  duration.value = audio.duration || 0
})
audio.addEventListener('ended', () => {
  // 播完静候管理员切歌（同步听歌以管理员时间轴为准）
  playing.value = false
})

// ===== 管理员控制 =====
function ctrlPlay() {
  send({ type: 'PLAYBACK_CTRL', action: 'play', payload: { time: position.value } })
}

function ctrlPause() {
  send({ type: 'PLAYBACK_CTRL', action: 'pause' })
}

function ctrlSeek(t: number) {
  send({ type: 'PLAYBACK_CTRL', action: 'seek', payload: { time: t } })
}

function ctrlStop() {
  send({ type: 'PLAYBACK_CTRL', action: 'stopSong' })
}

function switchSong(songId: number) {
  send({ type: 'PLAYBACK_CTRL', action: 'playSong', payload: { songId } })
}

async function endRoom() {
  try {
    await ElMessageBox.confirm('结束后所有成员将退出房间，确认结束一起听？', '结束一起听', {
      type: 'warning',
    })
  } catch {
    return
  }
  try {
    await endListenRoom(roomId.value)
    roomClosed.value = true
    cleanup()
    ElMessage.success('一起听已结束')
  } catch {
    // 拦截器已提示
  }
}

async function toggleMute(m: ListenMemberVO) {
  try {
    await muteListenMember(roomId.value, m.userId, !m.muted)
  } catch {
    // 拦截器已提示
  }
}

async function kick(m: ListenMemberVO) {
  try {
    await ElMessageBox.confirm(`确认将「${m.nickname}」移出房间？`, '移出成员', { type: 'warning' })
  } catch {
    return
  }
  try {
    await kickListenMember(roomId.value, m.userId)
  } catch {
    // 拦截器已提示
  }
}

// ===== 聊天 =====
function sendChat() {
  const content = chatInput.value.trim()
  if (!content) return
  send({ type: 'SEND_MESSAGE', content })
  chatInput.value = ''
}

function pushChat(item: ChatItem) {
  chatList.value.push(item)
  if (chatList.value.length > 200) {
    chatList.value = chatList.value.slice(-200)
  }
  nextTick(() => {
    chatBox.value?.scrollTo({ top: chatBox.value.scrollHeight, behavior: 'smooth' })
  })
}

function formatClock(t: number): string {
  const d = new Date(t)
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${pad(d.getHours())}:${pad(d.getMinutes())}`
}

function formatPos(t: number): string {
  const m = Math.floor(t / 60)
  const s = Math.floor(t % 60)
  return `${m}:${String(s).padStart(2, '0')}`
}

// ===== 切歌搜索（管理员） =====
const switchKeyword = ref('')
const switchResults = ref<SongVO[]>([])
const switchSearching = ref(false)
let switchTimer: ReturnType<typeof setTimeout> | null = null

function onSwitchInput(kw: string) {
  if (switchTimer) clearTimeout(switchTimer)
  if (!kw.trim()) {
    switchResults.value = []
    return
  }
  switchTimer = setTimeout(async () => {
    switchSearching.value = true
    try {
      const r = await import('@/api/song').then((m) => m.searchSongs(kw.trim(), 1, 8))
      switchResults.value = r.songs
    } catch {
      switchResults.value = []
    } finally {
      switchSearching.value = false
    }
  }, 400)
}

function leave() {
  router.push({ name: 'circle-detail', params: { id: roomIdToCircle() } })
}

let lastCircleId = 0
function roomIdToCircle(): number {
  return lastCircleId
}

function cleanup() {
  intentionalClose = true
  try {
    ws?.close()
  } catch {
    // 忽略
  }
  ws = null
  stopAudio()
  if (heartbeatTimer) {
    clearInterval(heartbeatTimer)
    heartbeatTimer = null
  }
  if (reconnectTimer) {
    clearTimeout(reconnectTimer)
    reconnectTimer = null
  }
}

onMounted(() => {
  if (!userStore.isLoggedIn) {
    router.push({ name: 'login', query: { redirect: route.fullPath } })
    return
  }
  connect()
})

onUnmounted(cleanup)
</script>

<template>
  <div class="page">
    <TopNav />
    <main class="main">
      <button class="back" @click="router.push({ name: 'circle' })">
        <el-icon :size="14"><ArrowLeft /></el-icon>
        返回乐坊
      </button>

      <!-- 已结束/被踢 -->
      <div v-if="roomClosed" class="closed-panel">
        <p class="closed-title">一起听已结束</p>
        <el-button type="primary" @click="router.push({ name: 'circle' })">回乐坊逛逛</el-button>
      </div>

      <template v-else>
        <header class="room-head">
          <div>
            <span class="yx-eyebrow">LISTEN TOGETHER</span>
            <h1 class="room-title yx-display">{{ theme || '一起听' }}</h1>
            <p class="room-sub">
              <span class="live-dot" :class="{ 'live-dot--ok': connected }" />
              {{ connecting ? '连接中…' : connected ? `已连接 · ${memberCount} 人在线` : '连接断开，正在重连…' }}
            </p>
          </div>
          <el-button v-if="amManager" type="danger" plain @click="endRoom">结束一起听</el-button>
        </header>

        <div class="layout">
          <!-- 播放器区 -->
          <section class="player-panel">
            <template v-if="song">
              <SongCover :cover="song.cover ?? null" :name="song.name ?? ''" :seed="Number(song.id || 0)" radius="12px" class="player-cover" :class="{ spinning: playing }" />
              <p class="player-name">{{ song.name }}</p>
              <p class="player-singer">{{ song.singerName || '未知歌手' }}</p>

              <!-- 管理员进度条 -->
              <template v-if="amManager">
                <el-slider
                  :model-value="position"
                  :max="duration || 100"
                  :format-tooltip="formatPos"
                  @change="ctrlSeek($event)"
                />
              </template>
              <template v-else>
                <el-slider :model-value="position" :max="duration || 100" disabled />
              </template>
              <div class="player-times tabular-nums">
                <span>{{ formatPos(position) }}</span>
                <span>{{ formatPos(duration) }}</span>
              </div>

              <!-- 管理员控制 -->
              <div v-if="amManager" class="player-ctrls">
                <el-button circle size="large" @click="ctrlStop">⏹</el-button>
                <el-button
                  circle
                  size="large"
                  type="primary"
                  class="play-btn"
                  @click="playing ? ctrlPause() : ctrlPlay()"
                >
                  {{ playing ? '⏸' : '▶' }}
                </el-button>
              </div>
              <p v-else class="player-hint">{{ playing ? '正在同步收听' : '已暂停，等待管理员继续' }}</p>

              <!-- 管理员切歌 -->
              <div v-if="amManager" class="switch-box">
                <el-input
                  v-model="switchKeyword"
                  placeholder="搜索并切换下一首"
                  clearable
                  :prefix-icon="Position"
                  @input="onSwitchInput"
                />
                <div v-if="switchResults.length" class="switch-results">
                  <div v-for="s in switchResults" :key="s.id" class="switch-item" @click="switchSong(s.id); switchResults = []">
                    <span class="switch-name">{{ s.name }}</span>
                    <span class="switch-singer">{{ s.singerName }}</span>
                  </div>
                </div>
              </div>
            </template>
            <div v-else class="player-empty">
              <p class="player-empty-icon">♪</p>
              <p class="player-hint">{{ amManager ? '还没有播放歌曲，搜索切一首开始吧' : '等待管理员选歌…' }}</p>
              <div v-if="amManager" class="switch-box switch-box--center">
                <el-input
                  v-model="switchKeyword"
                  placeholder="搜索歌曲开始播放"
                  clearable
                  @input="onSwitchInput"
                />
                <div v-if="switchResults.length" class="switch-results">
                  <div v-for="s in switchResults" :key="s.id" class="switch-item" @click="switchSong(s.id); switchResults = []">
                    <span class="switch-name">{{ s.name }}</span>
                    <span class="switch-singer">{{ s.singerName }}</span>
                  </div>
                </div>
              </div>
            </div>
          </section>

          <!-- 聊天 + 成员 -->
          <section class="chat-panel">
            <div class="panel-tabs">
              <span>聊天室</span>
            </div>
            <div ref="chatBox" class="chat-box">
              <div v-for="(c, i) in chatList" :key="i" class="chat-item">
                <!-- 系统消息 -->
                <p v-if="c.kind === 'system'" class="chat-system">{{ c.content }}</p>
                <!-- 聊天消息 -->
                <div v-else class="chat-msg" :class="{ 'chat-msg--mine': c.userId === userStore.userId }">
                  <el-avatar :size="26" :src="c.avatar || undefined" class="user-link" @click="goUserHome(c.userId)">{{ (c.username || '友').slice(0, 1) }}</el-avatar>
                  <div class="chat-bubble-wrap">
                    <p class="chat-meta">
                      <span class="chat-author user-link" @click="goUserHome(c.userId)">{{ c.username }}</span>
                      <span class="chat-time">{{ formatClock(c.time) }}</span>
                    </p>
                    <div class="chat-bubble">{{ c.content }}</div>
                  </div>
                </div>
              </div>
            </div>
            <div class="chat-input-row">
              <el-input
                v-model="chatInput"
                placeholder="说点什么…（Enter 发送）"
                maxlength="300"
                :disabled="!connected"
                @keyup.enter="sendChat"
              />
              <el-button type="primary" :disabled="!connected" @click="sendChat">发送</el-button>
            </div>

            <!-- 成员列表（含管理操作） -->
            <div class="member-box">
              <p class="member-head">在线成员（{{ memberCount }}）</p>
              <div class="member-scroll">
                <div v-for="m in members" :key="m.userId" class="member-row user-link" @click="goUserHome(m.userId)">
                  <el-avatar :size="26" :src="m.avatar || undefined">{{ (m.nickname || '友').slice(0, 1) }}</el-avatar>
                  <span class="member-nick">{{ m.nickname }}</span>
                  <el-tag v-if="m.userId === creatorId" size="small" type="warning" effect="plain">房主</el-tag>
                  <el-tag v-if="m.muted" size="small" type="danger" effect="light">禁言</el-tag>
                  <span class="flex-spacer" />
                  <template v-if="amManager && m.userId !== userStore.userId">
                    <el-button text size="small" @click="toggleMute(m)">
                      {{ m.muted ? '解除禁言' : '禁言' }}
                    </el-button>
                    <el-button text size="small" type="danger" @click="kick(m)">
                      <el-icon><Delete /></el-icon>
                    </el-button>
                  </template>
                </div>
              </div>
            </div>
          </section>
        </div>
      </template>
    </main>
  </div>
</template>

<style scoped>
.main {
  max-width: 1080px;
  margin: 0 auto;
  padding: 24px 20px 60px;
}

.back {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 13px;
  color: var(--yx-text-secondary);
  background: none;
  border: none;
  cursor: pointer;
  margin-bottom: 14px;
  padding: 0;
}

.back:hover {
  color: var(--yx-accent);
}

.room-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  margin-bottom: 18px;
  gap: 12px;
}

.room-title {
  font-size: 26px;
  margin: 4px 0;
}

.room-sub {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  color: var(--yx-text-secondary);
}

.live-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: #c0c4cc;
}

.live-dot--ok {
  background: #19be6b;
  box-shadow: 0 0 6px rgba(25, 190, 107, 0.7);
}

.layout {
  display: grid;
  grid-template-columns: minmax(320px, 5fr) minmax(360px, 6fr);
  gap: 16px;
  align-items: start;
}

/* ===== 播放器 ===== */
.player-panel {
  background: #fff;
  border: 1px solid var(--yx-hairline);
  border-radius: 12px;
  padding: 28px 24px;
  text-align: center;
}

.player-cover {
  width: 200px;
  height: 200px;
  margin: 0 auto 16px;
  display: block;
  box-shadow: 0 10px 30px rgba(28, 92, 171, 0.18);
}

.spinning {
  animation: spin 20s linear infinite;
  animation-play-state: paused;
}

.spinning:global(.spinning) {
  /* 占位：scoped 下动画由 playing 类控制 */
}

@keyframes spin {
  from {
    transform: rotate(0deg);
  }
  to {
    transform: rotate(360deg);
  }
}

.player-name {
  font-size: 18px;
  font-weight: 700;
}

.player-singer {
  font-size: 13px;
  color: var(--yx-text-secondary);
  margin: 2px 0 18px;
}

.player-times {
  display: flex;
  justify-content: space-between;
  font-size: 12px;
  color: var(--yx-text-disabled);
  margin-top: -8px;
}

.player-ctrls {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 18px;
  margin-top: 14px;
}

.play-btn {
  width: 56px;
  height: 56px;
  font-size: 20px;
}

.player-hint {
  font-size: 13px;
  color: var(--yx-text-secondary);
  margin-top: 14px;
}

.player-empty {
  padding: 60px 20px;
}

.player-empty-icon {
  font-size: 56px;
  color: var(--yx-text-disabled);
  margin-bottom: 10px;
}

.switch-box {
  position: relative;
  margin-top: 18px;
  text-align: left;
}

.switch-box--center {
  max-width: 320px;
  margin-left: auto;
  margin-right: auto;
}

.switch-results {
  position: absolute;
  z-index: 20;
  left: 0;
  right: 0;
  top: 44px;
  background: #fff;
  border: 1px solid var(--yx-hairline);
  border-radius: 8px;
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.08);
  max-height: 220px;
  overflow-y: auto;
}

.switch-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 14px;
  cursor: pointer;
  font-size: 13.5px;
}

.switch-item:hover {
  background: #f5f8fc;
}

.switch-name {
  font-weight: 500;
}

.switch-singer {
  color: var(--yx-text-secondary);
  font-size: 12px;
}

/* ===== 聊天面板 ===== */
.chat-panel {
  background: #fff;
  border: 1px solid var(--yx-hairline);
  border-radius: 12px;
  padding: 16px;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.panel-tabs {
  font-size: 15px;
  font-weight: 700;
}

.chat-box {
  height: 300px;
  overflow-y: auto;
  padding: 4px 2px;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.chat-system {
  text-align: center;
  font-size: 12px;
  color: var(--yx-text-disabled);
}

.chat-msg {
  display: flex;
  gap: 8px;
  align-items: flex-start;
}

.chat-msg--mine {
  flex-direction: row-reverse;
}

.chat-bubble-wrap {
  max-width: 75%;
}

.chat-meta {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 2px;
}

.chat-msg--mine .chat-meta {
  justify-content: flex-end;
}

.chat-author {
  font-size: 12px;
  font-weight: 600;
}

.chat-time {
  font-size: 11px;
  color: var(--yx-text-disabled);
}

.chat-bubble {
  background: #f2f6fb;
  border-radius: 4px 12px 12px 12px;
  padding: 8px 12px;
  font-size: 13.5px;
  line-height: 1.5;
  word-break: break-word;
}

.chat-msg--mine .chat-bubble {
  background: var(--yx-accent-weak, #e8f1fb);
  border-radius: 12px 4px 12px 12px;
}

.chat-input-row {
  display: flex;
  gap: 8px;
}

/* ===== 成员 ===== */
.member-box {
  border-top: 1px solid var(--yx-hairline);
  padding-top: 10px;
}

.member-head {
  font-size: 13px;
  font-weight: 600;
  color: var(--yx-text-secondary);
  margin-bottom: 8px;
}

.member-scroll {
  max-height: 160px;
  overflow-y: auto;
}

.member-row {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 5px 0;
}

.member-nick {
  font-size: 13px;
  max-width: 120px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* 昵称/头像可点击进入用户公开主页 */
.user-link {
  cursor: pointer;
}

.chat-author.user-link:hover,
.member-row.user-link:hover .member-nick {
  color: var(--yx-accent);
}

.flex-spacer {
  flex: 1;
}

.closed-panel {
  background: #fff;
  border: 1px solid var(--yx-hairline);
  border-radius: 12px;
  padding: 60px 20px;
  text-align: center;
}

.closed-title {
  font-size: 18px;
  font-weight: 700;
  margin-bottom: 16px;
}

@media (max-width: 860px) {
  .layout {
    grid-template-columns: 1fr;
  }
}
</style>
