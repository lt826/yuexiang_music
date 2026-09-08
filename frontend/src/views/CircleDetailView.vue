<script setup lang="ts">
import { onMounted, ref, computed, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowLeft, Plus, Headset, Search, Delete, TopRight } from '@element-plus/icons-vue'
import TopNav from '@/components/TopNav.vue'
import { useUserStore } from '@/stores/user'
import { usePlayerStore } from '@/stores/player'
import type { SongVO } from '@/api/home'
import { searchSongs } from '@/api/song'
import {
  getCircleDetail,
  joinCircle,
  quitCircle,
  dissolveCircle,
  updateCircle,
  getMembers,
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

// ===== 成员列表 =====
const memberVisible = ref(false)
const members = ref<MemberVO[]>([])
const membersLoading = ref(false)

async function openMembers() {
  memberVisible.value = true
  membersLoading.value = true
  try {
    members.value = await getMembers(circleId.value)
  } catch {
    members.value = []
  } finally {
    membersLoading.value = false
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
const roomForm = ref({ theme: '', songId: null as number | null })

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
  roomForm.value = { theme: '', songId: null }
  createRoomVisible.value = true
}

async function submitCreateRoom() {
  try {
    const room = await createListenRoom(circleId.value, {
      theme: roomForm.value.theme.trim() || undefined,
      songId: roomForm.value.songId ?? undefined,
    })
    createRoomVisible.value = false
    ElMessage.success('一起听已发起')
    router.push({ name: 'listen-room', params: { roomId: room.roomId } })
  } catch {
    // 拦截器已提示（如已有房间进行中）
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

// ===== 发帖（搜歌插卡片） =====
const postVisible = ref(false)
const postSubmitting = ref(false)
const postContent = ref('')
const pickedSong = ref<SongVO | null>(null)

/** 歌曲搜索（防抖） */
const songKeyword = ref('')
const songResults = ref<SongVO[]>([])
const songSearching = ref(false)
let searchTimer: ReturnType<typeof setTimeout> | null = null

watch(songKeyword, (kw) => {
  if (searchTimer) clearTimeout(searchTimer)
  if (!kw.trim()) {
    songResults.value = []
    return
  }
  searchTimer = setTimeout(async () => {
    songSearching.value = true
    try {
      const r = await searchSongs(kw.trim(), 1, 10)
      songResults.value = r.songs
    } catch {
      songResults.value = []
    } finally {
      songSearching.value = false
    }
  }, 400)
})

function pickSong(s: SongVO) {
  pickedSong.value = s
  songKeyword.value = ''
  songResults.value = []
}

function removeSong() {
  pickedSong.value = null
}

async function submitPost() {
  if (!postContent.value.trim() && !pickedSong.value) {
    ElMessage.warning('写点什么，或插入一首歌')
    return
  }
  postSubmitting.value = true
  try {
    await createPost(circleId.value, {
      content: postContent.value.trim() || '分享一首歌',
      songId: pickedSong.value?.id,
    })
    postVisible.value = false
    postContent.value = ''
    pickedSong.value = null
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
              <el-avatar :size="38" :src="p.avatar || undefined">
                {{ (p.nickname || '友').slice(0, 1) }}
              </el-avatar>
            </div>
            <div class="post-main">
              <div class="post-head">
                <span class="post-author">{{ p.nickname || `用户${p.userId}` }}</span>
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
                  <el-avatar :size="26" :src="c.avatar || undefined">
                    {{ (c.nickname || '友').slice(0, 1) }}
                  </el-avatar>
                  <div class="comment-body">
                    <p class="comment-head">
                      <span class="comment-author">{{ c.nickname || `用户${c.userId}` }}</span>
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
                          <span class="comment-author">{{ r.nickname || `用户${r.userId}` }}</span>
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

    <!-- 发帖弹窗 -->
    <el-dialog v-model="postVisible" title="发布帖子" width="560px">
      <el-input
        v-model="postContent"
        type="textarea"
        :rows="4"
        maxlength="2000"
        show-word-limit
        placeholder="分享你的音乐心情，可插入一首歌…"
      />

      <!-- 已选歌曲卡片 -->
      <div v-if="pickedSong" class="pick-card">
        <div class="song-card">
          <div class="song-card-cover">
            <img v-if="pickedSong.cover" :src="pickedSong.cover" :alt="pickedSong.name" />
            <span v-else>♪</span>
          </div>
          <div class="song-card-info">
            <p class="song-card-name">{{ pickedSong.name }}</p>
            <p class="song-card-singer">{{ pickedSong.singerName || '未知歌手' }}</p>
          </div>
          <el-button text type="danger" size="small" @click="removeSong">移除</el-button>
        </div>
      </div>

      <!-- 搜歌 -->
      <div v-else class="song-search">
        <el-input v-model="songKeyword" placeholder="搜索歌曲插入卡片（可选）" clearable :prefix-icon="Search" />
        <div v-if="songSearching" class="song-search-loading" v-loading="true" />
        <div v-if="songResults.length" class="song-results">
          <div v-for="s in songResults" :key="s.id" class="song-result" @click="pickSong(s)">
            <span class="song-result-name">{{ s.name }}</span>
            <span class="song-result-singer">{{ s.singerName }}</span>
          </div>
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

    <!-- 成员列表弹窗 -->
    <el-dialog v-model="memberVisible" title="圈内成员" width="420px">
      <div v-loading="membersLoading" class="member-list">
        <div v-for="m in members" :key="m.userId" class="member">
          <el-avatar :size="34" :src="m.avatar || undefined">{{ (m.nickname || '友').slice(0, 1) }}</el-avatar>
          <span class="member-name">{{ m.nickname || `用户${m.userId}` }}</span>
          <span class="flex-spacer" />
          <el-tag size="small" :type="m.role === 'OWNER' ? 'warning' : m.role === 'ADMIN' ? 'danger' : 'info'" effect="plain">
            {{ roleLabel(m.role) }}
          </el-tag>
        </div>
        <el-empty v-if="!membersLoading && members.length === 0" description="暂无成员" :image-size="50" />
      </div>
    </el-dialog>

    <!-- 发起一起听弹窗 -->
    <el-dialog v-model="createRoomVisible" title="发起一起听" width="480px">
      <el-form label-position="top">
        <el-form-item label="房间主题（选填）">
          <el-input v-model="roomForm.theme" maxlength="100" placeholder="如：本周新歌鉴赏" />
        </el-form-item>
        <el-form-item label="初始歌曲（选填，发起后可在房间内切歌）">
          <el-select
            v-model="roomForm.songId"
            filterable
            remote
            clearable
            :remote-method="(kw: string) => (songKeyword = kw)"
            :loading="songSearching"
            placeholder="搜索歌曲"
            style="width: 100%"
          >
            <el-option
              v-for="s in songResults"
              :key="s.id"
              :label="`${s.name} - ${s.singerName || '未知歌手'}`"
              :value="s.id"
            />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createRoomVisible = false">取消</el-button>
        <el-button type="primary" @click="submitCreateRoom">发起</el-button>
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

.song-search {
  margin-top: 10px;
  position: relative;
}

.song-search-loading {
  height: 40px;
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

.song-result-name {
  font-weight: 500;
}

.song-result-singer {
  color: var(--yx-text-secondary);
  font-size: 12px;
}

/* 成员列表 */
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

.member-name {
  font-size: 14px;
}

@media (max-width: 640px) {
  .hero {
    flex-direction: column;
  }
}
</style>
