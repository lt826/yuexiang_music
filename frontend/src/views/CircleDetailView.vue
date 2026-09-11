<script setup lang="ts">
import { onMounted, ref, computed, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowLeft, Plus, Headset, Search, Delete, TopRight, Picture } from '@element-plus/icons-vue'
import TopNav from '@/components/TopNav.vue'
import { useUserStore } from '@/stores/user'
import { usePlayerStore } from '@/stores/player'
import type { SongVO } from '@/api/home'
import { searchSongs, getHotChart } from '@/api/song'
import { uploadFile } from '@/api/singer'
import {
  getCircleDetail,
  joinCircle,
  quitCircle,
  dissolveCircle,
  updateCircle,
  getMembers,
  kickMember,
  setMemberRole,
  getPosts,
  createPost,
  deletePost,
  pinPost,
  toggleLike,
  getComments,
  createComment,
  deleteComment,
  createListenRoom,
  getLiveRoom,
  type CircleVO,
  type PostVO,
  type CommentVO,
  type MemberVO,
  type ListenRoomVO,
} from '@/api/circle'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const playerStore = usePlayerStore()

const circleId = computed(() => Number(route.params.id))
const circle = ref<CircleVO | null>(null)
const loading = ref(false)

/** 是否圈内成员（发帖/评论/一起听的前提） */
const isMember = computed(() => !!circle.value?.joined)
/** 圈主本人 */
const isOwner = computed(() => circle.value?.myRole === 'OWNER' || circle.value?.creatorId === userStore.userId)
/** 圈主/圈管理员/平台管理员（管理动作） */
const isManager = computed(
  () => isOwner.value || circle.value?.myRole === 'ADMIN' || userStore.role === 'ADMIN',
)

async function loadCircle() {
  loading.value = true
  try {
    circle.value = await getCircleDetail(circleId.value)
  } catch {
    ElMessage.error('圈子不存在或已解散')
    router.push({ name: 'circle' })
  } finally {
    loading.value = false
  }
}

// ===== 加入/退出/解散 =====
async function join() {
  if (!userStore.isLoggedIn) {
    router.push({ name: 'login', query: { redirect: route.fullPath } })
    return
  }
  try {
    await joinCircle(circleId.value)
    ElMessage.success('已加入圈子')
    await loadCircle()
  } catch {
    // 拦截器已提示
  }
}

async function quit() {
  try {
    await ElMessageBox.confirm(`确认退出「${circle.value?.name}」？`, '退出圈子', { type: 'warning' })
  } catch {
    return
  }
  try {
    await quitCircle(circleId.value)
    ElMessage.success('已退出圈子')
    await loadCircle()
  } catch {
    // 拦截器已提示
  }
}

async function dissolve() {
  try {
    await ElMessageBox.confirm(
      '解散后圈内所有帖子、评论将被删除且不可恢复，确认解散？',
      '解散圈子',
      { type: 'warning', confirmButtonText: '确认解散' },
    )
  } catch {
    return
  }
  try {
    await dissolveCircle(circleId.value)
    ElMessage.success('圈子已解散')
    router.push({ name: 'circle' })
  } catch {
    // 拦截器已提示
  }
}

// ===== 编辑圈子（改名重新审核） =====
const editVisible = ref(false)
const editForm = ref({ name: '', description: '' })

function openEdit() {
  editForm.value = {
    name: circle.value?.name || '',
    description: circle.value?.description || '',
  }
  editVisible.value = true
}

async function submitEdit() {
  if (!editForm.value.name.trim()) {
    ElMessage.warning('请填写圈子名称')
    return
  }
  try {
    await updateCircle(circleId.value, {
      name: editForm.value.name.trim(),
      description: editForm.value.description.trim(),
    })
    editVisible.value = false
    ElMessage.success('已提交，名称审核通过后生效')
    await loadCircle()
  } catch {
    // 拦截器已提示
  }
}

// ===== 成员列表（搜索筛选 + 圈主/管理员操作） =====
const memberVisible = ref(false)
const members = ref<MemberVO[]>([])
const membersLoading = ref(false)
const memberKeyword = ref('')

/** 按昵称筛选（客户端过滤，成员列表本就一次拉全量） */
const filteredMembers = computed(() => {
  const kw = memberKeyword.value.trim()
  if (!kw) return members.value
  return members.value.filter((m) => (m.nickname || '').includes(kw))
})

/** 我是否圈主本人（设管理员仅圈主可用；平台管理员同权） */
const iAmOwner = computed(
  () => circle.value?.creatorId === userStore.userId || userStore.role === 'ADMIN',
)
/** 我是否圈管理员（除圈主身份外的 ADMIN 角色） */
const iAmAdmin = computed(() => circle.value?.myRole === 'ADMIN')

async function openMembers() {
  memberVisible.value = true
  memberKeyword.value = ''
  membersLoading.value = true
  try {
    members.value = await getMembers(circleId.value)
  } catch {
    members.value = []
  } finally {
    membersLoading.value = false
  }
}

/** 能否对该成员"设/撤管理员"：仅圈主，且不能对自己/圈主操作 */
function canSetRole(m: MemberVO): boolean {
  if (!iAmOwner.value) return false
  if (m.userId === userStore.userId) return false
  return m.role !== 'OWNER' && m.userId !== circle.value?.creatorId
}

/** 能否踢出：圈主可踢管理员+成员；圈管理员只能踢普通成员；自己/圈主不可 */
function canKick(m: MemberVO): boolean {
  if (m.userId === userStore.userId) return false
  if (m.role === 'OWNER' || m.userId === circle.value?.creatorId) return false
  if (iAmOwner.value) return true
  if (iAmAdmin.value) return m.role === 'MEMBER'
  return false
}

async function doSetRole(m: MemberVO) {
  const nextRole = m.role === 'ADMIN' ? 'MEMBER' : 'ADMIN'
  const tip = nextRole === 'ADMIN' ? `设 ${m.nickname || '该成员'} 为管理员？` : `取消 ${m.nickname || '该成员'} 的管理员？`
  try {
    await ElMessageBox.confirm(tip, '成员管理', { type: 'warning' })
  } catch {
    return
  }
  try {
    await setMemberRole(circleId.value, m.userId, nextRole)
    ElMessage.success(nextRole === 'ADMIN' ? '已设为管理员' : '已取消管理员')
    members.value = await getMembers(circleId.value)
  } catch {
    // 拦截器已提示
  }
}

async function doKick(m: MemberVO) {
  try {
    await ElMessageBox.confirm(
      `确定将 ${m.nickname || '该成员'} 移出圈子？移出后其帖子保留。`,
      '移出成员',
      { type: 'warning', confirmButtonText: '移出' },
    )
  } catch {
    return
  }
  try {
    await kickMember(circleId.value, m.userId)
    ElMessage.success('已移出该成员')
    members.value = await getMembers(circleId.value)
    await loadCircle()
  } catch {
    // 拦截器已提示
  }
}

function roleLabel(role: string): string {
  if (role === 'OWNER') return '圈主'
  if (role === 'ADMIN') return '管理员'
  return '成员'
}

// ===== 一起听 =====
const liveRoom = ref<ListenRoomVO | null>(null)
const createRoomVisible = ref(false)
const roomForm = ref({ theme: '' })
const pickedRoomSong = ref<SongVO | null>(null)
const roomSubmitting = ref(false)

function pickRoomSong(s: SongVO) {
  pickedRoomSong.value = s
  roomSongKeyword.value = ''
  roomSongResults.value = []
}

function removeRoomSong() {
  pickedRoomSong.value = null
}

async function refreshLive() {
  try {
    liveRoom.value = await getLiveRoom(circleId.value)
  } catch {
    liveRoom.value = null
  }
}

function openCreateRoom() {
  if (!userStore.isLoggedIn) {
    router.push({ name: 'login', query: { redirect: route.fullPath } })
    return
  }
  roomForm.value = { theme: '' }
  pickedRoomSong.value = null
  roomSongKeyword.value = ''
  roomSongResults.value = []
  createRoomVisible.value = true
  loadHotSongs()
}

async function submitCreateRoom() {
  roomSubmitting.value = true
  try {
    const room = await createListenRoom(circleId.value, {
      theme: roomForm.value.theme.trim() || undefined,
      songId: pickedRoomSong.value?.id,
    })
    createRoomVisible.value = false
    ElMessage.success('一起听已发起')
    router.push({ name: 'listen-room', params: { roomId: room.roomId } })
  } catch {
    // 拦截器已提示（如已有房间进行中/实时服务不可用）
  } finally {
    roomSubmitting.value = false
  }
}

function enterRoom() {
  if (liveRoom.value) {
    router.push({ name: 'listen-room', params: { roomId: liveRoom.value.roomId } })
  }
}

// ===== 帖子流 =====
const posts = ref<PostVO[]>([])
const postsLoading = ref(false)
const postPage = ref(1)
const postFinished = ref(false)
const PAGE_SIZE = 10

async function loadPosts(reset = false) {
  if (reset) {
    postPage.value = 1
    postFinished.value = false
  }
  postsLoading.value = true
  try {
    const page = await getPosts(circleId.value, postPage.value, PAGE_SIZE)
    posts.value = reset ? page.items : [...posts.value, ...page.items]
    if (page.items.length < PAGE_SIZE) {
      postFinished.value = true
    } else {
      postPage.value += 1
    }
  } catch {
    // 拦截器已提示
  } finally {
    postsLoading.value = false
  }
}

function loadMore() {
  if (!postFinished.value && !postsLoading.value) {
    loadPosts()
  }
}

// ===== 发帖（搜歌插卡片 + 一张图片） =====
const postVisible = ref(false)
const postSubmitting = ref(false)
const postContent = ref('')
const pickedSong = ref<SongVO | null>(null)
const postImage = ref('')

/** 选歌面板展开态：点搜索框/换歌才展开，选完自动收起（避免常驻列表把发布按钮挤出屏幕） */
const postSongPanelOpen = ref(false)

/** 热门歌曲（选歌列表的默认候选：展开面板即展示，输入关键词后被搜索结果替换） */
const hotSongs = ref<SongVO[]>([])
const hotLoaded = ref(false)

async function loadHotSongs() {
  if (hotLoaded.value) return
  try {
    hotSongs.value = await getHotChart(10)
    hotLoaded.value = true
  } catch {
    hotSongs.value = []
  }
}

/** 发帖选歌：关键词为空展示热门，非空（防抖 400ms）展示搜索结果 */
const postSongKeyword = ref('')
const postSongResults = ref<SongVO[]>([])
const postSongSearching = ref(false)
let postSearchTimer: ReturnType<typeof setTimeout> | null = null

watch(postSongKeyword, (kw) => {
  if (postSearchTimer) clearTimeout(postSearchTimer)
  if (!kw.trim()) {
    postSongResults.value = []
    postSongSearching.value = false
    return
  }
  postSearchTimer = setTimeout(async () => {
    postSongSearching.value = true
    try {
      const r = await searchSongs(kw.trim(), 1, 10)
      postSongResults.value = r.songs
    } catch {
      postSongResults.value = []
    } finally {
      postSongSearching.value = false
    }
  }, 400)
})

/** 选歌列表实际渲染的数据源：有关键词给搜索结果，否则给热门歌曲 */
const postSongOptions = computed(() =>
  postSongKeyword.value.trim() ? postSongResults.value : hotSongs.value,
)

/** 发起一起听选歌：与发帖同一模式（独立状态，互不干扰） */
const roomSongKeyword = ref('')
const roomSongResults = ref<SongVO[]>([])
const roomSongSearching = ref(false)
let roomSearchTimer: ReturnType<typeof setTimeout> | null = null

watch(roomSongKeyword, (kw) => {
  if (roomSearchTimer) clearTimeout(roomSearchTimer)
  if (!kw.trim()) {
    roomSongResults.value = []
    roomSongSearching.value = false
    return
  }
  roomSearchTimer = setTimeout(async () => {
    roomSongSearching.value = true
    try {
      const r = await searchSongs(kw.trim(), 1, 10)
      roomSongResults.value = r.songs
    } catch {
      roomSongResults.value = []
    } finally {
      roomSongSearching.value = false
    }
  }, 400)
})

const roomSongOptions = computed(() =>
  roomSongKeyword.value.trim() ? roomSongResults.value : hotSongs.value,
)

/** 展开选歌面板（首次加载热门歌曲候选） */
function openSongPanel() {
  postSongPanelOpen.value = true
  loadHotSongs()
}

function pickSong(s: SongVO) {
  pickedSong.value = s
  postSongKeyword.value = ''
  postSongResults.value = []
  // 选完自动收起面板，弹窗恢复紧凑
  postSongPanelOpen.value = false
}

function removeSong() {
  pickedSong.value = null
}

/** 试听已选歌曲（不关弹窗：播放走底部迷你条，不弹全屏播放页打断发帖） */
const previewingSongId = ref<number | null>(null)

async function previewPickedSong() {
  if (!pickedSong.value) return
  // 再次点击 = 停止试听
  if (previewingSongId.value === pickedSong.value.id) {
    playerStore.stop()
    previewingSongId.value = null
    return
  }
  const song: SongVO = {
    ...pickedSong.value,
    url: null,
    lyric: null,
  } as unknown as SongVO
  previewingSongId.value = pickedSong.value.id
  try {
    await playerStore.playQueue([song], 0)
    // playQueue 默认开全屏播放页；试听场景收起，让用户继续在弹窗里编辑
    playerStore.fullScreen = false
  } catch {
    previewingSongId.value = null
  }
}

/** 试听被切歌/停止时同步按钮态 */
watch(
  () => playerStore.currentSong?.id,
  (id) => {
    if (previewingSongId.value && id !== previewingSongId.value) {
      previewingSongId.value = null
    }
  },
)

// ===== 发帖图片（最多 1 张） =====
const imageUploading = ref(false)
const imageInputRef = ref<HTMLInputElement | null>(null)

function openImagePicker() {
  imageInputRef.value?.click()
}

async function handleImageChange(e: Event) {
  const input = e.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file) return
  if (file.size > 5 * 1024 * 1024) {
    ElMessage.warning('图片最大 5MB')
    return
  }
  imageUploading.value = true
  try {
    const vo = await uploadFile(file, 'IMAGE')
    postImage.value = vo.url
    ElMessage.success('图片已上传')
  } catch {
    // 拦截器已提示
  } finally {
    imageUploading.value = false
  }
}

function removeImage() {
  postImage.value = ''
}

async function submitPost() {
  if (!postContent.value.trim() && !pickedSong.value && !postImage.value) {
    ElMessage.warning('写点什么，或插入一首歌/一张图片')
    return
  }
  postSubmitting.value = true
  try {
    await createPost(circleId.value, {
      content: postContent.value.trim() || '分享一首歌',
      songId: pickedSong.value?.id,
      image: postImage.value || undefined,
    })
    postVisible.value = false
    postContent.value = ''
    pickedSong.value = null
    postImage.value = ''
    ElMessage.success('帖子已发布')
    await loadPosts(true)
    await loadCircle()
  } catch {
    // 拦截器已提示
  } finally {
    postSubmitting.value = false
  }
}

function openPost() {
  if (!userStore.isLoggedIn) {
    router.push({ name: 'login', query: { redirect: route.fullPath } })
    return
  }
  postVisible.value = true
  postSongPanelOpen.value = false
}

// ===== 帖子操作 =====
async function removePost(p: PostVO) {
  try {
    await ElMessageBox.confirm('确认删除该帖子？', '删除帖子', { type: 'warning' })
  } catch {
    return
  }
  try {
    await deletePost(p.id)
    ElMessage.success('已删除')
    await loadPosts(true)
    await loadCircle()
  } catch {
    // 拦截器已提示
  }
}

async function togglePin(p: PostVO) {
  try {
    await pinPost(p.id, p.pinned !== 1)
    ElMessage.success(p.pinned === 1 ? '已取消置顶' : '已置顶')
    await loadPosts(true)
  } catch {
    // 拦截器已提示（如置顶超限）
  }
}

async function like(p: PostVO) {
  if (!userStore.isLoggedIn) {
    router.push({ name: 'login', query: { redirect: route.fullPath } })
    return
  }
  try {
    await toggleLike(p.id)
    p.liked = !p.liked
    p.likeCount += p.liked ? 1 : -1
  } catch {
    // 拦截器已提示
  }
}

/** 播放帖子歌曲卡片 */
async function playSongCard(p: PostVO) {
  if (!p.song || !p.songId) return
  const song: SongVO = {
    id: p.songId,
    name: p.song.name || '未知歌曲',
    cover: p.song.cover ?? null,
    url: null,
    lyric: null,
    style: p.song.style ?? null,
    playCount: p.song.playCount ?? 0,
    singerId: p.song.singerId ?? 0,
    singerName: p.song.singerName ?? null,
  } as unknown as SongVO
  await playerStore.playQueue([song], 0)
}

// ===== 评论（楼中楼） =====
const commentPanelId = ref<number | null>(null)
const comments = ref<CommentVO[]>([])
const commentsLoading = ref(false)

/** 评论输入：key=帖子ID，value={content, replyTo} */
const commentInputs = ref<Record<number, { content: string; replyTo: CommentVO | null }>>({})

function inputOf(postId: number) {
  if (!commentInputs.value[postId]) {
    commentInputs.value[postId] = { content: '', replyTo: null }
  }
  return commentInputs.value[postId]
}

async function openComments(p: PostVO) {
  if (commentPanelId.value === p.id) {
    commentPanelId.value = null
    return
  }
  commentPanelId.value = p.id
  commentsLoading.value = true
  try {
    comments.value = await getComments(p.id)
  } catch {
    comments.value = []
  } finally {
    commentsLoading.value = false
  }
}

/** 当前面板的评论列表（按帖子过滤） */
const panelComments = computed(() =>
  commentPanelId.value ? comments.value.filter((c) => c.postId === commentPanelId.value) : [],
)

function setReply(p: PostVO, c: CommentVO) {
  inputOf(p.id).replyTo = c
}

async function submitComment(p: PostVO) {
  if (!userStore.isLoggedIn) {
    router.push({ name: 'login', query: { redirect: route.fullPath } })
    return
  }
  const input = inputOf(p.id)
  if (!input.content.trim()) {
    ElMessage.warning('请输入评论内容')
    return
  }
  try {
    await createComment(p.id, {
      content: input.content.trim(),
      parentId: input.replyTo?.id,
    })
    input.content = ''
    input.replyTo = null
    p.commentCount += 1
    if (commentPanelId.value === p.id) {
      comments.value = await getComments(p.id)
    }
    ElMessage.success('评论成功')
  } catch {
    // 拦截器已提示（如非圈内成员）
  }
}

async function removeComment(p: PostVO, c: CommentVO) {
  try {
    await ElMessageBox.confirm('确认删除该评论？', '删除评论', { type: 'warning' })
  } catch {
    return
  }
  try {
    await deleteComment(c.id)
    if (commentPanelId.value === p.id) {
      comments.value = await getComments(p.id)
    }
    p.commentCount = Math.max(0, p.commentCount - 1)
  } catch {
    // 拦截器已提示
  }
}

function formatTime(t: string): string {
  if (!t) return ''
  return t.slice(5, 16).replace('T', ' ')
}

/** 进入用户公开主页（昵称/头像可点击） */
function goUserHome(userId: number) {
  router.push({ name: 'user-home', params: { id: userId } })
}

onMounted(async () => {
  await loadCircle()
  await loadPosts(true)
  refreshLive()
})
</script>

<template>
  <div class="page">
    <TopNav />
    <main class="main" v-loading="loading">
      <button class="back" @click="router.push({ name: 'circle' })">
        <el-icon :size="14"><ArrowLeft /></el-icon>
        返回乐坊
      </button>

      <!-- 圈子头部 -->
      <section v-if="circle" class="hero">
        <div class="hero-cover" :style="{ background: `linear-gradient(135deg, #86b6ef, #2a78d6)` }">
          <img v-if="circle.cover" :src="circle.cover" :alt="circle.name" class="hero-img" />
          <span v-else class="hero-glyph">{{ circle.name.slice(0, 1) }}</span>
        </div>
        <div class="hero-info">
          <div class="hero-top">
            <h1 class="hero-name yx-display">{{ circle.name }}</h1>
            <el-tag v-if="circle.myRole === 'OWNER'" type="warning" effect="plain">圈主</el-tag>
            <el-tag v-else-if="circle.myRole === 'ADMIN'" type="warning" effect="plain">管理员</el-tag>
            <el-tag v-if="circle.status === 0" type="info" effect="light">名称审核中</el-tag>
          </div>
          <p class="hero-desc">{{ circle.description || '暂无简介' }}</p>
          <p v-if="circle.status === 2" class="hero-reject">
            <el-tag size="small" type="danger" effect="light">
              名称未通过审核：{{ circle.rejectReason }}（可在编辑中改名重新提交）
            </el-tag>
          </p>
          <div class="hero-meta">
            <span class="tabular-nums">{{ circle.memberCount }} 成员</span>
            <span class="dot">·</span>
            <span class="tabular-nums">{{ circle.postCount }} 帖子</span>
            <span class="dot">·</span>
            <span>圈主 {{ circle.creatorName || `用户${circle.creatorId}` }}</span>
          </div>
          <div class="hero-actions">
            <template v-if="isMember">
              <el-button
                v-if="isManager"
                type="primary"
                :icon="Headset"
                @click="liveRoom ? enterRoom() : openCreateRoom()"
              >
                {{ liveRoom ? '回到一起听' : '发起一起听' }}
              </el-button>
              <el-button v-if="liveRoom" type="success" :icon="Headset" @click="enterRoom">
                加入一起听
              </el-button>
              <el-button @click="openPost">发帖</el-button>
              <el-button @click="openMembers">成员</el-button>
              <el-button v-if="isOwner" @click="openEdit">编辑</el-button>
              <el-button v-if="!isOwner" @click="quit">退出</el-button>
              <el-button v-if="isOwner" type="danger" plain @click="dissolve">解散</el-button>
            </template>
            <template v-else>
              <el-button type="primary" @click="join">加入圈子</el-button>
              <el-button @click="openMembers">成员</el-button>
            </template>
          </div>
        </div>
      </section>

      <!-- 帖子流 -->
      <section class="posts">
        <div class="posts-head">
          <h2 class="posts-title">帖子</h2>
          <el-button v-if="isMember" type="primary" size="small" :icon="Plus" @click="openPost">发帖</el-button>
        </div>

        <div v-loading="postsLoading && posts.length === 0" class="post-list">
          <article v-for="p in posts" :key="p.id" class="post">
            <div class="post-side">
              <el-avatar
                :size="38"
                :src="p.avatar || undefined"
                class="user-link"
                @click="goUserHome(p.userId)"
              >
                {{ (p.nickname || '友').slice(0, 1) }}
              </el-avatar>
            </div>
            <div class="post-main">
              <div class="post-head">
                <span class="post-author user-link" @click="goUserHome(p.userId)">
                  {{ p.nickname || `用户${p.userId}` }}
                </span>
                <span class="post-time">{{ formatTime(p.createTime) }}</span>
                <el-tag v-if="p.pinned === 1" size="small" type="warning" effect="light" class="pin-tag">
                  <el-icon :size="11"><TopRight /></el-icon>
                  置顶
                </el-tag>
                <span class="flex-spacer" />
                <el-dropdown v-if="p.canDelete || p.canPin" trigger="click">
                  <span class="post-more">···</span>
                  <template #dropdown>
                    <el-dropdown-menu>
                      <el-dropdown-item v-if="p.canPin" @click="togglePin(p)">
                        {{ p.pinned === 1 ? '取消置顶' : '置顶' }}
                      </el-dropdown-item>
                      <el-dropdown-item v-if="p.canDelete" @click="removePost(p)">删除</el-dropdown-item>
                    </el-dropdown-menu>
                  </template>
                </el-dropdown>
              </div>
              <p class="post-content">{{ p.content }}</p>

              <!-- 帖子图片（最多 1 张，点击预览大图） -->
              <el-image
                v-if="p.image"
                :src="p.image"
                :preview-src-list="[p.image]"
                fit="cover"
                hide-on-click-modal
                preview-teleported
                loading="lazy"
                class="post-image"
              />

              <!-- 歌曲卡片 -->
              <div v-if="p.song && p.songId" class="song-card" @click="playSongCard(p)">
                <div class="song-card-cover">
                  <img v-if="p.song.cover" :src="p.song.cover" :alt="p.song.name" />
                  <span v-else>♪</span>
                </div>
                <div class="song-card-info">
                  <p class="song-card-name">{{ p.song.name }}</p>
                  <p class="song-card-singer">{{ p.song.singerName || '未知歌手' }}</p>
                </div>
                <span class="song-card-play">▶ 播放</span>
              </div>

              <div class="post-foot">
                <button class="foot-btn" :class="{ 'foot-btn--active': p.liked }" @click="like(p)">
                  {{ p.liked ? '❤' : '♡' }} <span class="tabular-nums">{{ p.likeCount }}</span>
                </button>
                <button class="foot-btn" @click="openComments(p)">
                  💬 <span class="tabular-nums">{{ p.commentCount }}</span>
                </button>
              </div>

              <!-- 评论区（楼中楼） -->
              <div v-if="commentPanelId === p.id" class="comments" v-loading="commentsLoading">
                <div v-for="c in panelComments" :key="c.id" class="comment">
                  <el-avatar
                    :size="26"
                    :src="c.avatar || undefined"
                    class="user-link"
                    @click="goUserHome(c.userId)"
                  >
                    {{ (c.nickname || '友').slice(0, 1) }}
                  </el-avatar>
                  <div class="comment-body">
                    <p class="comment-head">
                      <span class="comment-author user-link" @click="goUserHome(c.userId)">
                        {{ c.nickname || `用户${c.userId}` }}
                      </span>
                      <span class="comment-time">{{ formatTime(c.createTime) }}</span>
                      <span class="flex-spacer" />
                      <button class="comment-op" @click="setReply(p, c)">回复</button>
                      <button v-if="c.canDelete" class="comment-op comment-op--danger" @click="removeComment(p, c)">
                        <el-icon :size="12"><Delete /></el-icon>
                      </button>
                    </p>
                    <p class="comment-content">{{ c.content }}</p>
                    <!-- 楼中楼回复 -->
                    <div v-if="c.replies?.length" class="replies">
                      <div v-for="r in c.replies" :key="r.id" class="reply">
                        <p class="comment-head">
                          <span class="comment-author user-link" @click="goUserHome(r.userId)">
                            {{ r.nickname || `用户${r.userId}` }}
                          </span>
                          <span class="comment-time">{{ formatTime(r.createTime) }}</span>
                          <span class="flex-spacer" />
                          <button class="comment-op" @click="setReply(p, c)">回复</button>
                          <button
                            v-if="r.canDelete"
                            class="comment-op comment-op--danger"
                            @click="removeComment(p, r)"
                          >
                            <el-icon :size="12"><Delete /></el-icon>
                          </button>
                        </p>
                        <p class="comment-content">{{ r.content }}</p>
                      </div>
                    </div>
                  </div>
                </div>
                <el-empty
                  v-if="!commentsLoading && panelComments.length === 0"
                  description="还没有评论"
                  :image-size="50"
                />

                <!-- 评论输入 -->
                <div v-if="isMember" class="comment-input">
                  <p v-if="inputOf(p.id).replyTo" class="replying">
                    回复 @{{ inputOf(p.id).replyTo?.nickname || `用户${inputOf(p.id).replyTo?.userId}` }}
                    <button class="comment-op" @click="inputOf(p.id).replyTo = null">取消</button>
                  </p>
                  <div class="comment-input-row">
                    <el-input
                      v-model="inputOf(p.id).content"
                      placeholder="友善评论，理性交流"
                      maxlength="500"
                      @keyup.enter="submitComment(p)"
                    />
                    <el-button type="primary" @click="submitComment(p)">发送</el-button>
                  </div>
                </div>
                <p v-else class="comment-login-hint">加入圈子后可评论</p>
              </div>
            </div>
          </article>
        </div>

        <div v-if="!postFinished && posts.length > 0" class="load-more">
          <el-button :loading="postsLoading" @click="loadMore">加载更多</el-button>
        </div>
        <el-empty
          v-if="!postsLoading && posts.length === 0"
          :description="isMember ? '还没有帖子，来发第一帖吧' : '该圈子还没有帖子'"
        />
      </section>
    </main>

    <!-- 发帖弹窗：body 区滚动，footer 发布按钮始终可见 -->
    <el-dialog
      v-model="postVisible"
      title="发布帖子"
      width="560px"
      class="post-dialog"
      @closed="previewingSongId = null"
    >
      <div class="post-dialog-body">
        <el-input
          v-model="postContent"
          type="textarea"
          :rows="4"
          maxlength="2000"
          show-word-limit
          placeholder="分享你的音乐心情，可插入一首歌/一张图片…"
        />

        <!-- 图片预览（最多 1 张） -->
        <div v-if="postImage" class="pick-card">
          <div class="img-pick">
            <img :src="postImage" alt="帖子图片" />
            <div class="pick-meta">
              <p class="pick-meta-title">图片已就绪</p>
              <p class="pick-meta-sub">发布后将展示在帖子正文下方</p>
              <el-button text type="danger" size="small" @click="removeImage">移除图片</el-button>
            </div>
          </div>
        </div>

        <!-- 已选歌曲卡片：封面 + 歌名 + 歌手 + 试听，发布前即可预览帖子里的卡片形态 -->
        <div v-if="pickedSong" class="pick-card">
          <div
            class="song-card"
            :class="{ 'is-previewing': previewingSongId === pickedSong.id }"
          >
            <div class="song-card-cover">
              <img v-if="pickedSong.cover" :src="pickedSong.cover" :alt="pickedSong.name" />
              <span v-else>♪</span>
            </div>
            <div class="song-card-info" @click="previewPickedSong">
              <p class="song-card-name">{{ pickedSong.name }}</p>
              <p class="song-card-singer">{{ pickedSong.singerName || '未知歌手' }}</p>
            </div>
            <el-button
              text
              size="small"
              type="primary"
              class="song-card-preview"
              @click="previewPickedSong"
            >
              {{ previewingSongId === pickedSong.id ? '停止' : '试听' }}
            </el-button>
            <el-button text type="danger" size="small" @click="removeSong">移除</el-button>
          </div>
        </div>

        <!-- 搜歌 + 传图工具行 -->
        <div class="song-search">
          <div class="post-toolbar">
            <el-input
              v-model="postSongKeyword"
              placeholder="搜索歌曲插入卡片（可选）"
              clearable
              :prefix-icon="Search"
              @focus="openSongPanel"
            />
            <el-button
              :icon="Picture"
              :loading="imageUploading"
              :disabled="!!postImage"
              @click="openImagePicker"
            >
              {{ postImage ? '已传图' : '添加图片' }}
            </el-button>
            <el-button
              v-if="pickedSong"
              text
              type="primary"
              @click="openSongPanel"
            >
              换一首
            </el-button>
            <input
              ref="imageInputRef"
              type="file"
              accept=".jpg,.jpeg,.png,.webp,.gif"
              hidden
              @change="handleImageChange"
            />
          </div>

          <!-- 选歌候选：仅面板展开时渲染（点搜索框/换一首展开，选完自动收起） -->
          <template v-if="postSongPanelOpen">
            <p class="song-search-hint">
              {{ postSongKeyword.trim() ? '搜索结果' : '热门歌曲（点击插入卡片）' }}
            </p>
            <div v-if="postSongSearching" class="song-search-loading" v-loading="true" />
            <div v-if="postSongOptions.length" class="song-results song-results--static">
              <div v-for="s in postSongOptions" :key="s.id" class="song-result" @click="pickSong(s)">
                <span class="song-result-cover">
                  <img v-if="s.cover" :src="s.cover" :alt="s.name" />
                  <span v-else>♪</span>
                </span>
                <span class="song-result-name">{{ s.name }}</span>
                <span class="song-result-singer">{{ s.singerName }}</span>
              </div>
            </div>
            <p v-else-if="!postSongSearching" class="song-search-hint">暂无候选歌曲</p>
            <el-button text size="small" class="song-panel-collapse" @click="postSongPanelOpen = false">
              收起列表
            </el-button>
          </template>
        </div>
      </div>

      <template #footer>
        <el-button @click="postVisible = false">取消</el-button>
        <el-button type="primary" :loading="postSubmitting" @click="submitPost">发布</el-button>
      </template>
    </el-dialog>

    <!-- 编辑圈子弹窗 -->
    <el-dialog v-model="editVisible" title="编辑圈子" width="480px">
      <el-alert type="info" :closable="false" show-icon title="修改名称将重新进入审核，审核期间圈子仍显示旧名称" class="create-tip" />
      <el-form label-position="top">
        <el-form-item label="圈子名称" required>
          <el-input v-model="editForm.name" maxlength="50" show-word-limit />
        </el-form-item>
        <el-form-item label="圈子简介">
          <el-input v-model="editForm.description" type="textarea" :rows="3" maxlength="500" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editVisible = false">取消</el-button>
        <el-button type="primary" @click="submitEdit">保存</el-button>
      </template>
    </el-dialog>

    <!-- 成员列表弹窗（搜索筛选 + 成员管理） -->
    <el-dialog v-model="memberVisible" title="圈内成员" width="460px">
      <el-input
        v-model="memberKeyword"
        placeholder="搜索成员昵称"
        clearable
        :prefix-icon="Search"
        class="member-search"
      />
      <div v-loading="membersLoading" class="member-list">
        <div v-for="m in filteredMembers" :key="m.userId" class="member">
          <div class="member-main user-link" @click="goUserHome(m.userId)">
            <el-avatar :size="34" :src="m.avatar || undefined">{{ (m.nickname || '友').slice(0, 1) }}</el-avatar>
            <span class="member-name">{{ m.nickname || `用户${m.userId}` }}</span>
          </div>
          <span class="flex-spacer" />
          <el-tag size="small" :type="m.role === 'OWNER' ? 'warning' : m.role === 'ADMIN' ? 'danger' : 'info'" effect="plain">
            {{ roleLabel(m.role) }}
          </el-tag>
          <template v-if="canSetRole(m) || canKick(m)">
            <button
              v-if="canSetRole(m)"
              class="member-op"
              :title="m.role === 'ADMIN' ? '取消管理员' : '设为管理员'"
              @click="doSetRole(m)"
            >
              {{ m.role === 'ADMIN' ? '撤管理员' : '设管理员' }}
            </button>
            <button
              v-if="canKick(m)"
              class="member-op member-op--danger"
              title="移出圈子"
              @click="doKick(m)"
            >
              移出
            </button>
          </template>
        </div>
        <el-empty
          v-if="!membersLoading && filteredMembers.length === 0"
          :description="memberKeyword.trim() ? '没有匹配的成员' : '暂无成员'"
          :image-size="50"
        />
      </div>
    </el-dialog>

    <!-- 发起一起听弹窗 -->
    <el-dialog v-model="createRoomVisible" title="发起一起听" width="480px">
      <el-form label-position="top">
        <el-form-item label="房间主题（选填）">
          <el-input v-model="roomForm.theme" maxlength="100" placeholder="如：本周新歌鉴赏" />
        </el-form-item>
        <el-form-item label="初始歌曲（选填，发起后可在房间内切歌）">
          <!-- 已选初始歌曲 -->
          <div v-if="pickedRoomSong" class="room-pick">
            <span class="room-pick-name">{{ pickedRoomSong.name }} - {{ pickedRoomSong.singerName || '未知歌手' }}</span>
            <el-button text type="danger" size="small" @click="removeRoomSong">移除</el-button>
          </div>
          <template v-else>
            <el-input
              v-model="roomSongKeyword"
              placeholder="搜索歌曲（可选）"
              clearable
              :prefix-icon="Search"
            />
            <p class="song-search-hint">
              {{ roomSongKeyword.trim() ? '搜索结果' : '热门歌曲' }}
            </p>
            <div v-if="roomSongSearching" class="song-search-loading" v-loading="true" />
            <div v-if="roomSongOptions.length" class="song-results">
              <div
                v-for="s in roomSongOptions"
                :key="s.id"
                class="song-result"
                @click="pickRoomSong(s)"
              >
                <span class="song-result-name">{{ s.name }}</span>
                <span class="song-result-singer">{{ s.singerName }}</span>
              </div>
            </div>
            <p v-else-if="!roomSongSearching" class="song-search-hint">暂无候选歌曲</p>
          </template>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createRoomVisible = false">取消</el-button>
        <el-button type="primary" :loading="roomSubmitting" @click="submitCreateRoom">发起</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.main {
  max-width: 860px;
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

/* ===== 圈子头部 ===== */
.hero {
  display: flex;
  gap: 20px;
  background: #fff;
  border: 1px solid var(--yx-hairline);
  border-radius: 12px;
  padding: 20px;
  margin-bottom: 24px;
}

.hero-cover {
  width: 120px;
  height: 120px;
  border-radius: 12px;
  overflow: hidden;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
}

.hero-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.hero-glyph {
  font-size: 44px;
  color: rgba(255, 255, 255, 0.9);
  font-weight: 700;
}

.hero-info {
  flex: 1;
  min-width: 0;
}

.hero-top {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.hero-name {
  font-size: 24px;
}

.hero-desc {
  color: var(--yx-text-secondary);
  font-size: 13.5px;
  margin: 6px 0;
}

.hero-reject {
  margin: 4px 0;
}

.hero-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  color: var(--yx-text-secondary);
  margin: 6px 0 12px;
}

.hero-meta .dot {
  color: var(--yx-text-disabled);
}

.hero-actions {
  display: flex;
  gap: 4px;
  flex-wrap: wrap;
}

/* ===== 帖子 ===== */
.posts-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}

.posts-title {
  font-size: 18px;
  font-weight: 700;
}

.post {
  display: flex;
  gap: 12px;
  background: #fff;
  border: 1px solid var(--yx-hairline);
  border-radius: 10px;
  padding: 16px;
  margin-bottom: 12px;
}

.post-main {
  flex: 1;
  min-width: 0;
}

.post-head {
  display: flex;
  align-items: center;
  gap: 8px;
}

.post-author {
  font-size: 14px;
  font-weight: 600;
}

/* 用户昵称/头像可点击进入公开主页 */
.user-link {
  cursor: pointer;
}

.post-author.user-link:hover,
.comment-author.user-link:hover {
  color: var(--yx-accent);
}

.post-time {
  font-size: 12px;
  color: var(--yx-text-disabled);
}

.pin-tag {
  display: inline-flex;
  align-items: center;
  gap: 2px;
}

.post-more {
  cursor: pointer;
  color: var(--yx-text-secondary);
  letter-spacing: 1px;
  padding: 0 6px;
}

.post-more:hover {
  color: var(--yx-accent);
}

.post-content {
  font-size: 14.5px;
  line-height: 1.7;
  margin: 8px 0;
  white-space: pre-wrap;
  word-break: break-word;
}

/* 帖子图片（最多 1 张） */
.post-image {
  width: 220px;
  max-height: 220px;
  border-radius: 10px;
  margin: 4px 0 8px;
  cursor: zoom-in;
  display: block;
}

/* 歌曲卡片 */
.song-card {
  display: flex;
  align-items: center;
  gap: 10px;
  border: 1px solid var(--yx-hairline);
  background: #fafbfd;
  border-radius: 8px;
  padding: 8px 12px;
  margin: 8px 0;
  cursor: pointer;
  transition: border-color 0.15s ease;
  max-width: 420px;
}

.song-card:hover {
  border-color: var(--yx-accent);
}

.song-card-cover {
  width: 42px;
  height: 42px;
  border-radius: 6px;
  overflow: hidden;
  background: var(--yx-gradient);
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  flex-shrink: 0;
}

.song-card-cover img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.song-card-info {
  flex: 1;
  min-width: 0;
}

.song-card-name {
  font-size: 13.5px;
  font-weight: 600;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.song-card-singer {
  font-size: 12px;
  color: var(--yx-text-secondary);
}

.song-card-play {
  font-size: 12px;
  color: var(--yx-accent);
  flex-shrink: 0;
}

.post-foot {
  display: flex;
  gap: 16px;
  margin-top: 6px;
}

.foot-btn {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 13px;
  color: var(--yx-text-secondary);
  background: none;
  border: none;
  cursor: pointer;
  padding: 0;
}

.foot-btn:hover {
  color: var(--yx-accent);
}

.foot-btn--active {
  color: #eb6834;
}

/* 评论区 */
.comments {
  margin-top: 12px;
  padding-top: 12px;
  border-top: 1px solid var(--yx-hairline);
  min-height: 40px;
}

.comment {
  display: flex;
  gap: 8px;
  margin-bottom: 12px;
}

.comment-body {
  flex: 1;
  min-width: 0;
}

.comment-head {
  display: flex;
  align-items: center;
  gap: 8px;
}

.comment-author {
  font-size: 13px;
  font-weight: 600;
}

.comment-time {
  font-size: 11px;
  color: var(--yx-text-disabled);
}

.comment-op {
  font-size: 12px;
  color: var(--yx-text-secondary);
  background: none;
  border: none;
  cursor: pointer;
  padding: 0;
}

.comment-op:hover {
  color: var(--yx-accent);
}

.comment-op--danger:hover {
  color: #f56c6c;
}

.comment-content {
  font-size: 13.5px;
  line-height: 1.6;
  margin-top: 2px;
  word-break: break-word;
}

.replies {
  margin-top: 8px;
  padding: 8px 10px;
  background: #fafbfd;
  border-radius: 8px;
}

.reply {
  margin-bottom: 8px;
}

.reply:last-child {
  margin-bottom: 0;
}

.comment-input {
  margin-top: 10px;
}

.replying {
  font-size: 12px;
  color: var(--yx-text-secondary);
  margin-bottom: 6px;
  display: flex;
  align-items: center;
  gap: 8px;
}

.comment-input-row {
  display: flex;
  gap: 8px;
}

.comment-login-hint {
  font-size: 12.5px;
  color: var(--yx-text-disabled);
  margin-top: 8px;
}

.load-more {
  display: flex;
  justify-content: center;
  margin: 16px 0;
}

.flex-spacer {
  flex: 1;
}

/* 弹窗内 */
.pick-card {
  margin-top: 10px;
}

.pick-card .song-card {
  margin: 0;
}

/* 发帖弹窗：限高 + body 滚动，footer 发布按钮始终可见 */
.post-dialog-body {
  max-height: 60vh;
  overflow-y: auto;
  padding-right: 4px;
}

.song-search {
  margin-top: 10px;
  position: relative;
}

.song-search-loading {
  height: 40px;
}

/* 发帖弹窗：搜歌 + 传图工具行 */
.post-toolbar {
  display: flex;
  gap: 10px;
  align-items: center;
}

/* 热门歌曲/搜索结果 标签 */
.song-search-hint {
  margin: 8px 0 4px;
  font-size: 12px;
  color: var(--yx-text-secondary);
}

/* 发帖已传图片预览 */
.img-pick {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px;
  border: 1px solid var(--yx-hairline);
  border-radius: 10px;
  background: #fafbfd;
}

.img-pick img {
  width: 96px;
  height: 96px;
  object-fit: cover;
  border-radius: 8px;
  border: 1px solid var(--yx-hairline);
  flex-shrink: 0;
}

.pick-meta-title {
  margin: 0;
  font-size: 13.5px;
  font-weight: 600;
}

.pick-meta-sub {
  margin: 3px 0 8px;
  font-size: 12px;
  color: var(--yx-text-secondary);
}

/* 已选歌曲试听按钮 */
.song-card-preview {
  flex-shrink: 0;
}

/* 试听中的卡片高亮 */
.song-card.is-previewing {
  border-color: var(--yx-accent);
  background: var(--yx-accent-weak, #f0f6ff);
}

/* 发起一起听：已选初始歌曲 */
.room-pick {
  width: 100%;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  padding: 8px 12px;
  border: 1px solid var(--yx-hairline);
  border-radius: 8px;
}

.room-pick-name {
  font-size: 13px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.song-results {
  position: absolute;
  z-index: 10;
  left: 0;
  right: 0;
  top: 44px;
  background: #fff;
  border: 1px solid var(--yx-hairline);
  border-radius: 8px;
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.08);
  max-height: 240px;
  overflow-y: auto;
}

/* 发帖弹窗内：候选列表走文档流（跟随 body 滚动，不悬浮遮挡发布按钮） */
.song-results--static {
  position: static;
  box-shadow: none;
}

.song-result {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 14px;
  cursor: pointer;
  font-size: 13.5px;
}

.song-result:hover {
  background: #f5f8fc;
}

/* 候选行封面（具象化：选歌前就能看到封面） */
.song-result-cover {
  width: 30px;
  height: 30px;
  border-radius: 5px;
  overflow: hidden;
  background: var(--yx-gradient);
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-size: 13px;
  flex-shrink: 0;
}

.song-result-cover img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.song-result-name {
  font-weight: 500;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.song-result-singer {
  color: var(--yx-text-secondary);
  font-size: 12px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* 收起候选列表 */
.song-panel-collapse {
  margin-top: 6px;
}

/* 成员列表 */
.member-search {
  margin-bottom: 10px;
}

.member-list {
  max-height: 420px;
  overflow-y: auto;
}

.member {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 0;
}

.member-main {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}

.member-name {
  font-size: 14px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* 成员管理操作按钮 */
.member-op {
  border: 1px solid var(--yx-hairline);
  background: #fff;
  border-radius: 6px;
  padding: 3px 8px;
  font-size: 12px;
  color: var(--yx-text-secondary);
  cursor: pointer;
  flex-shrink: 0;
}

.member-op:hover {
  color: var(--yx-accent);
  border-color: var(--yx-accent);
}

.member-op--danger:hover {
  color: #d94b3f;
  border-color: #d94b3f;
}

@media (max-width: 640px) {
  .hero {
    flex-direction: column;
  }
}
</style>
