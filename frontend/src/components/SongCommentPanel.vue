<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { CaretLeft, CaretTop, CaretBottom } from '@element-plus/icons-vue'
import { usePlayerStore } from '@/stores/player'
import { useUserStore } from '@/stores/user'
import {
  deleteComment,
  getComments,
  getReplies,
  likeComment,
  postComment,
  unlikeComment,
  type CommentSort,
  type CommentVO,
} from '@/api/interaction'

/**
 * 歌曲评论面板：默认底部半屏抽屉，一键向上展开全屏（看长评论列表更直观）。
 * 排序：推荐（热度+时间衰减）/ 最热 / 最新；楼中楼两级回复 + 评论点赞。
 * 打开期间每 10s 轮询实现"准实时"（新评论/新回复/点赞数变化其他人也能看到）。
 */
const playerStore = usePlayerStore()
const userStore = useUserStore()
const emit = defineEmits<{ close: []; commentChange: [delta: number] }>()

/**
 * 半屏抽屉下抽屉上方仍能看到播放器，用户可能切歌——songId 必须响应式跟随，
 * 切歌即重置整个评论区（楼中楼/回复态/点赞缓存一并清掉），防止展示错歌的评论
 */
const songId = computed(() => playerStore.currentSong?.id ?? 0)

/** ===== 半屏抽屉 ↔ 全屏 ===== */
const expanded = ref(false)

/** ===== 排序 ===== */
const sort = ref<CommentSort>('recommend')
const SORT_TABS: { key: CommentSort; label: string }[] = [
  { key: 'recommend', label: '推荐' },
  { key: 'hot', label: '最热' },
  { key: 'time', label: '最新' },
]

/** ===== 根评论列表状态 ===== */
const list = ref<CommentVO[]>([])
const total = ref(0)
const page = ref(1)
const size = 20
const loading = ref(false)
const noMore = ref(false)

/** 新评论提示条（用户已往下翻时，轮询到新评论不直接插入，顶部提示点击查看） */
const pendingCount = ref(0)

const listBody = ref<HTMLDivElement>()

/** ===== 楼中楼：展开回复的根评论状态表 ===== */
const replyStore = ref<Record<number, { items: CommentVO[]; total: number; page: number; loading: boolean; noMore: boolean }>>({})

/** 头像兜底：无头像用昵称首字 + 按 userId 取色渐变圆 */
const AVATAR_GRADIENTS = [
  'linear-gradient(135deg, #3987e5, #1c5cab)',
  'linear-gradient(135deg, #7a5cff, #4a2fb8)',
  'linear-gradient(135deg, #e5578a, #a02a58)',
  'linear-gradient(135deg, #0ca38a, #06705d)',
  'linear-gradient(135deg, #e59b3b, #a85f10)',
  'linear-gradient(135deg, #5cb85c, #2f7d32)',
]
function avatarStyle(userId: number) {
  return { background: AVATAR_GRADIENTS[userId % AVATAR_GRADIENTS.length] }
}
function firstChar(nickname: string) {
  return (nickname || '友').slice(0, 1).toUpperCase()
}

/** ===== 根评论加载 ===== */
async function loadFirstPage(reset = true) {
  loading.value = true
  try {
    const data = await getComments(songId.value, 1, size, sort.value)
    if (reset) {
      list.value = data.items
      page.value = 1
      noMore.value = data.items.length >= data.total
    } else {
      // 轮询合并：保留用户已加载的历史页，仅把新评论插到顶部
      const known = new Set(list.value.map((c) => c.id))
      const fresh = data.items.filter((c) => !known.has(c.id))
      if (fresh.length) {
        list.value = [...fresh, ...list.value]
        total.value = data.total
      }
    }
    total.value = data.total
    pendingCount.value = 0
  } catch {
    // 拦截器已提示
  } finally {
    loading.value = false
  }
}

async function loadMore() {
  if (loading.value || noMore.value) return
  loading.value = true
  try {
    const next = page.value + 1
    const data = await getComments(songId.value, next, size, sort.value)
    const known = new Set(list.value.map((c) => c.id))
    list.value = [...list.value, ...data.items.filter((c) => !known.has(c.id))]
    page.value = next
    noMore.value = list.value.length >= data.total
  } catch {
    // 拦截器已提示
  } finally {
    loading.value = false
  }
}

/** 切排序：重查第一页，收起全部展开的回复 */
function switchSort(key: CommentSort) {
  if (sort.value === key) return
  sort.value = key
  replyStore.value = {}
  loadFirstPage(true)
  pendingCount.value = 0
  nextTick(() => listBody.value?.scrollTo({ top: 0 }))
}

/** 滚动到底自动加载下一页 */
function onScroll() {
  const el = listBody.value
  if (!el) return
  if (el.scrollTop + el.clientHeight >= el.scrollHeight - 80) {
    loadMore()
  }
}

/** 查看新评论（点提示条）：滚回顶部并刷新 */
async function showPending() {
  await loadFirstPage(true)
  await nextTick()
  listBody.value?.scrollTo({ top: 0, behavior: 'smooth' })
}

/** ===== 实时轮询（10s）：新根评论 / 回复数与点赞数变化 ===== */
let pollTimer: ReturnType<typeof setInterval> | null = null
async function poll() {
  if (loading.value || !songId.value) return
  try {
    const data = await getComments(songId.value, 1, size, sort.value)
    total.value = data.total
    // 同步首页可见评论的计数（点赞数/回复数是活的）
    const byId = new Map(data.items.map((c) => [c.id, c]))
    if (userStore.isLoggedIn && page.value === 1) {
      syncLikeState(data.items)
    }
    const known = new Set(list.value.map((c) => c.id))
    const fresh = data.items.filter((c) => !known.has(c.id))
    if (!fresh.length) {
      // 无新评论也要同步计数与回复预览
      for (const c of list.value) {
        const srv = byId.get(c.id)
        if (srv) {
          c.likeCount = srv.likeCount
          c.replyCount = srv.replyCount
          if (srv.replyPreview?.length) c.replyPreview = srv.replyPreview
        }
      }
      return
    }
    const el = listBody.value
    const nearTop = !el || el.scrollTop < 120
    if (nearTop) {
      list.value = [...fresh, ...list.value]
      pendingCount.value = 0
    } else {
      pendingCount.value += fresh.length
    }
  } catch {
    // 静默：轮询失败不打扰用户
  }
}

/** 轮询时同步自己点过赞的评论的点赞态（后端只回计数，liked 以本端操作为准） */
const myLikeIds = ref<Set<number>>(new Set())
function syncLikeState(items: CommentVO[]) {
  for (const c of items) {
    if (myLikeIds.value.has(c.id)) c.liked = true
  }
}

/** ===== 楼中楼展开 ===== */
function replyState(id: number) {
  if (!replyStore.value[id]) {
    replyStore.value[id] = { items: [], total: 0, page: 0, loading: false, noMore: false }
  }
  return replyStore.value[id]
}

async function toggleReplies(root: CommentVO) {
  const st = replyState(root.id)
  if (st.page > 0) {
    // 已展开 → 收起（保留数据，再点继续看）
    delete replyStore.value[root.id]
    return
  }
  await loadReplies(root.id)
}

async function loadReplies(rootId: number) {
  const st = replyState(rootId)
  if (st.loading || st.noMore) return
  st.loading = true
  try {
    const next = st.page + 1
    const data = await getReplies(rootId, next, 20)
    const known = new Set(st.items.map((c) => c.id))
    st.items = [...st.items, ...data.items.filter((c) => !known.has(c.id))]
    st.total = data.total
    st.page = next
    st.noMore = st.items.length >= data.total
    // 根评论本体（含最新点赞态）回写主列表
    const rootInList = list.value.find((c) => c.id === rootId)
    if (rootInList) {
      rootInList.likeCount = data.root.likeCount
      rootInList.liked = data.root.liked
      rootInList.replyCount = data.total
    }
  } catch {
    // 拦截器已提示
  } finally {
    st.loading = false
  }
}

/** ===== 评论点赞（根评论 + 回复通用） ===== */
const likeBusy = ref<Set<number>>(new Set())
async function toggleLike(c: CommentVO) {
  if (!userStore.isLoggedIn) {
    ElMessage.info('登录后即可点赞评论')
    return
  }
  if (likeBusy.value.has(c.id)) return
  likeBusy.value.add(c.id)
  try {
    if (c.liked) {
      c.likeCount = Math.max(0, await unlikeComment(c.id))
      c.liked = false
      myLikeIds.value.delete(c.id)
    } else {
      c.likeCount = await likeComment(c.id)
      c.liked = true
      myLikeIds.value.add(c.id)
    }
  } catch {
    // 拦截器已提示（重复点赞等）
  } finally {
    likeBusy.value.delete(c.id)
  }
}

/** ===== 发表评论 / 回复 ===== */
const draft = ref('')
const posting = ref(false)
/** 回复态：目标根评论 + 被回复人（可空=直接回复根作者） */
const replyTarget = ref<{ root: CommentVO; to: CommentVO } | null>(null)

const inputPlaceholder = computed(() => {
  if (!userStore.isLoggedIn) return '登录后即可评论'
  if (replyTarget.value) {
    return `回复 @${replyTarget.value.to.nickname}：`
  }
  return '说点什么吧…'
})

/** 点"回复"：进入回复态（回复根评论作者） */
function startReply(root: CommentVO, to?: CommentVO) {
  if (!userStore.isLoggedIn) {
    ElMessage.info('登录后即可回复')
    return
  }
  replyTarget.value = { root, to: to ?? root }
  inputEl.value?.focus()
}

function cancelReply() {
  replyTarget.value = null
}

/** 发布（enter 提交；输入法组词中的回车只选词不发布） */
function onInputEnter(e: KeyboardEvent) {
  if (e.isComposing) return
  submit()
}

async function submit() {
  const content = draft.value.trim()
  if (!content) {
    ElMessage.warning('说点什么再发布吧')
    return
  }
  if (!userStore.isLoggedIn) {
    ElMessage.info('登录后即可评论')
    return
  }
  posting.value = true
  try {
    const target = replyTarget.value
    if (target) {
      // 回复：插到对应楼中楼，回复数 +1
      const vo = await postComment(
        songId.value,
        content,
        target.root.id,
        target.to.id === target.root.id ? undefined : target.to.id,
      )
      const st = replyState(target.root.id)
      if (st.page > 0) {
        st.items = [...st.items, vo]
        st.total += 1
      }
      target.root.replyCount += 1
      replyTarget.value = null
      ElMessage.success('回复成功')
      draft.value = ''
    } else {
      // 根评论：插到列表顶部
      const vo = await postComment(songId.value, content)
      // 最热/推荐排序下新评论不一定在顶部，直接插入最前也符合"我刚发的"预期
      list.value = [vo, ...list.value]
      total.value += 1
      draft.value = ''
      emit('commentChange', 1)
      await nextTick()
      listBody.value?.scrollTo({ top: 0, behavior: 'smooth' })
    }
  } catch {
    // 拦截器已提示
  } finally {
    posting.value = false
  }
}

/** 删除自己的评论（根评论删除连带楼中楼） */
async function removeComment(c: CommentVO) {
  try {
    await deleteComment(c.id)
    if (c.rootId) {
      // 删回复：楼中楼里移除，根计数 -1
      const st = replyStore.value[c.rootId]
      if (st) {
        st.items = st.items.filter((x) => x.id !== c.id)
        st.total -= 1
      }
      const root = list.value.find((x) => x.id === c.rootId)
      if (root) root.replyCount = Math.max(0, root.replyCount - 1)
    } else {
      // 删根评论：整层移除（含展开的楼中楼）
      list.value = list.value.filter((x) => x.id !== c.id)
      total.value -= 1
      delete replyStore.value[c.id]
      emit('commentChange', -1)
    }
    ElMessage.success('已删除')
  } catch {
    // 拦截器已提示
  }
}

function isMine(c: CommentVO) {
  return userStore.isLoggedIn && c.userId === userStore.userId
}

const inputEl = ref<HTMLInputElement>()

/** 切歌：重置评论区全部状态并重查（半屏形态下抽屉上方可切歌） */
watch(songId, () => {
  list.value = []
  total.value = 0
  page.value = 1
  noMore.value = false
  replyStore.value = {}
  replyTarget.value = null
  draft.value = ''
  pendingCount.value = 0
  myLikeIds.value = new Set()
  if (songId.value > 0) loadFirstPage(true)
})

onMounted(() => {
  if (songId.value > 0) loadFirstPage(true)
  pollTimer = setInterval(poll, 10000)
})

onUnmounted(() => {
  if (pollTimer) clearInterval(pollTimer)
})
</script>

<template>
  <div class="cmt-panel" :class="{ 'is-expanded': expanded }">
    <!-- 展开指示条：半屏时点击展开全屏；全屏时收回半屏 -->
    <button
      class="cmt-expand-bar"
      :title="expanded ? '收回半屏' : '展开全屏'"
      @click="expanded = !expanded"
    >
      <span class="expand-handle" />
      <el-icon :size="16" class="expand-arrow">
        <CaretBottom v-if="expanded" />
        <CaretTop v-else />
      </el-icon>
    </button>

    <!-- 头部：返回 + 标题 + 排序 -->
    <header class="cmt-head">
      <button class="cmt-back" title="返回播放页" @click="emit('close')">
        <el-icon :size="20"><CaretLeft /></el-icon>
      </button>
      <h1 class="cmt-title">
        评论 <span class="cmt-total tabular-nums">{{ total }}</span>
      </h1>
      <nav class="cmt-tabs">
        <button
          v-for="t in SORT_TABS"
          :key="t.key"
          class="cmt-tab"
          :class="{ 'is-active': sort === t.key }"
          @click="switchSort(t.key)"
        >
          {{ t.label }}
        </button>
      </nav>
    </header>

    <!-- 新评论提示条 -->
    <div v-if="pendingCount > 0" class="cmt-pending" @click="showPending">
      有 {{ pendingCount }} 条新评论 · 点击查看
    </div>

    <!-- 列表 -->
    <main ref="listBody" class="cmt-body" @scroll="onScroll">
      <p v-if="!loading && list.length === 0" class="cmt-empty">
        还没有评论，来抢沙发～
      </p>
      <div v-for="c in list" :key="c.id" class="cmt-item">
        <div class="cmt-avatar" :style="avatarStyle(c.userId)">
          <img v-if="c.avatar" :src="c.avatar" :alt="c.nickname" />
          <span v-else>{{ firstChar(c.nickname) }}</span>
        </div>
        <div class="cmt-main">
          <div class="cmt-meta">
            <span class="cmt-nick">{{ c.nickname }}</span>
            <span class="cmt-time">{{ c.time }}</span>
            <button v-if="isMine(c)" class="cmt-del" title="删除这条评论" @click="removeComment(c)">删除</button>
          </div>
          <p class="cmt-content">{{ c.content }}</p>

          <!-- 回复预览（未展开楼中楼时显示最新 2 条） -->
          <div
            v-if="c.replyCount > 0 && !replyStore[c.id] && c.replyPreview?.length"
            class="reply-preview"
          >
            <p
              v-for="r in c.replyPreview"
              :key="r.id"
              class="reply-line"
              @click="toggleReplies(c)"
            >
              <span class="reply-nick">{{ r.nickname }}</span>
              <template v-if="r.replyNickname"> 回复 <span class="reply-nick">@{{ r.replyNickname }}</span></template>
              ：{{ r.content }}
            </p>
            <button class="reply-expand" @click="toggleReplies(c)">
              共 {{ c.replyCount }} 条回复
              <el-icon :size="12"><CaretBottom /></el-icon>
            </button>
          </div>

          <!-- 展开的楼中楼 -->
          <div v-if="replyStore[c.id]" class="reply-zone">
            <div v-for="r in replyStore[c.id]!.items" :key="r.id" class="reply-item">
              <div class="reply-avatar" :style="avatarStyle(r.userId)">
                <img v-if="r.avatar" :src="r.avatar" :alt="r.nickname" />
                <span v-else>{{ firstChar(r.nickname) }}</span>
              </div>
              <div class="reply-main">
                <div class="cmt-meta">
                  <span class="reply-nick">{{ r.nickname }}</span>
                  <span class="cmt-time">{{ r.time }}</span>
                  <button v-if="isMine(r)" class="cmt-del" @click="removeComment(r)">删除</button>
                </div>
                <p class="reply-content">
                  <span v-if="r.replyNickname && r.replyUserId !== c.userId" class="reply-at">回复 @{{ r.replyNickname }}：</span>{{ r.content }}
                </p>
                <div class="reply-foot">
                  <button class="reply-btn" @click="startReply(c, r)">回复</button>
                </div>
              </div>
              <!-- 回复点赞 -->
              <div
                class="like-col"
                :class="{ 'is-liked': r.liked }"
                :title="r.liked ? '取消点赞' : '点赞'"
                @click="toggleLike(r)"
              >
                <svg v-if="r.liked" class="like-heart" viewBox="0 0 24 24" aria-hidden="true">
                  <path d="M12 21.35l-1.45-1.32C5.4 15.36 2 12.28 2 8.5 2 5.42 4.42 3 7.5 3c1.74 0 3.41.81 4.5 2.09C13.09 3.81 14.76 3 16.5 3 19.58 3 22 5.42 22 8.5c0 3.78-3.4 6.86-8.55 11.54L12 21.35z" fill="currentColor" />
                </svg>
                <svg v-else class="like-heart" viewBox="0 0 24 24" fill="none" aria-hidden="true">
                  <path d="M12 21.35l-1.45-1.32C5.4 15.36 2 12.28 2 8.5 2 5.42 4.42 3 7.5 3c1.74 0 3.41.81 4.5 2.09C13.09 3.81 14.76 3 16.5 3 19.58 3 22 5.42 22 8.5c0 3.78-3.4 6.86-8.55 11.54L12 21.35z" stroke="currentColor" stroke-width="1.8" stroke-linejoin="round" />
                </svg>
                <span v-if="r.likeCount > 0" class="like-num tabular-nums">{{ r.likeCount }}</span>
              </div>
            </div>
            <button
              v-if="!replyStore[c.id]!.noMore"
              class="reply-expand"
              :disabled="replyStore[c.id]!.loading"
              @click="loadReplies(c.id)"
            >
              {{ replyStore[c.id]!.loading ? '加载中…' : `展开更多回复（${replyStore[c.id]!.total - replyStore[c.id]!.items.length}）` }}
            </button>
            <button v-else class="reply-expand" @click="toggleReplies(c)">
              收起回复 <el-icon :size="12"><CaretTop /></el-icon>
            </button>
          </div>

          <!-- 根评论操作条 -->
          <div class="cmt-foot-actions">
            <button class="reply-btn" @click="startReply(c)">回复</button>
          </div>
        </div>
        <!-- 根评论点赞（右侧） -->
        <div
          class="like-col"
          :class="{ 'is-liked': c.liked }"
          :title="c.liked ? '取消点赞' : '点赞'"
          @click="toggleLike(c)"
        >
          <svg v-if="c.liked" class="like-heart" viewBox="0 0 24 24" aria-hidden="true">
            <path d="M12 21.35l-1.45-1.32C5.4 15.36 2 12.28 2 8.5 2 5.42 4.42 3 7.5 3c1.74 0 3.41.81 4.5 2.09C13.09 3.81 14.76 3 16.5 3 19.58 3 22 5.42 22 8.5c0 3.78-3.4 6.86-8.55 11.54L12 21.35z" fill="currentColor" />
          </svg>
          <svg v-else class="like-heart" viewBox="0 0 24 24" fill="none" aria-hidden="true">
            <path d="M12 21.35l-1.45-1.32C5.4 15.36 2 12.28 2 8.5 2 5.42 4.42 3 7.5 3c1.74 0 3.41.81 4.5 2.09C13.09 3.81 14.76 3 16.5 3 19.58 3 22 5.42 22 8.5c0 3.78-3.4 6.86-8.55 11.54L12 21.35z" stroke="currentColor" stroke-width="1.8" stroke-linejoin="round" />
          </svg>
          <span v-if="c.likeCount > 0" class="like-num tabular-nums">{{ c.likeCount }}</span>
        </div>
      </div>
      <p v-if="loading" class="cmt-loading">加载中…</p>
      <p v-else-if="list.length > 0 && noMore" class="cmt-end">到底啦</p>
    </main>

    <!-- 底部输入条 -->
    <footer class="cmt-foot">
      <div v-if="replyTarget" class="reply-hint">
        <span class="reply-hint-text">回复 @{{ replyTarget.to.nickname }}</span>
        <button class="reply-hint-cancel" @click="cancelReply">取消</button>
      </div>
      <div class="reply-input-row">
        <input
          ref="inputEl"
          v-model="draft"
          class="cmt-input"
          type="text"
          maxlength="500"
          :placeholder="inputPlaceholder"
          @keydown.enter="onInputEnter"
        />
        <button class="cmt-send" :disabled="posting || !draft.trim()" @click="submit">
          {{ posting ? '发布中…' : '发布' }}
        </button>
      </div>
    </footer>
  </div>
</template>

<style scoped>
.cmt-panel {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  height: 55vh;
  z-index: 400;
  display: flex;
  flex-direction: column;
  border-radius: 18px 18px 0 0;
  /* 与 FullPlayer 同款深色渐变，视觉连续 */
  background: linear-gradient(160deg, #1e2433, #171c28);
  color: #fff;
  box-shadow: 0 -12px 40px rgba(0, 0, 0, 0.45);
  transition: height 0.32s cubic-bezier(0.22, 0.8, 0.3, 1);
  animation: cmt-in 0.25s ease;
}

.cmt-panel.is-expanded {
  height: 100vh;
  border-radius: 0;
}

@keyframes cmt-in {
  from { opacity: 0; transform: translateY(16px); }
  to { opacity: 1; transform: translateY(0); }
}

/* ===== 展开指示条 ===== */
.cmt-expand-bar {
  position: relative;
  flex: none;
  height: 26px;
  border: none;
  background: transparent;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  color: rgba(255, 255, 255, 0.45);
  transition: color 0.15s ease;
}

.cmt-expand-bar:hover {
  color: rgba(255, 255, 255, 0.8);
}

.expand-handle {
  position: absolute;
  left: 50%;
  top: 9px;
  transform: translateX(-50%);
  width: 36px;
  height: 4px;
  border-radius: 2px;
  background: rgba(255, 255, 255, 0.22);
}

.expand-arrow {
  position: absolute;
  right: 18px;
}

/* ===== 头部 ===== */
.cmt-head {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 6px 28px 12px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
}

.cmt-back {
  width: 40px;
  height: 40px;
  border: none;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.1);
  color: #fff;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: background 0.15s ease, transform 0.15s ease;
}

.cmt-back:hover {
  background: rgba(255, 255, 255, 0.2);
  transform: scale(1.05);
}

.cmt-title {
  font-size: 19px;
  font-weight: 700;
}

.cmt-total {
  font-size: 14px;
  font-weight: 400;
  color: rgba(255, 255, 255, 0.55);
  margin-left: 4px;
}

/* ===== 排序 tabs ===== */
.cmt-tabs {
  margin-left: auto;
  display: flex;
  gap: 4px;
  background: rgba(255, 255, 255, 0.07);
  border-radius: 999px;
  padding: 3px;
}

.cmt-tab {
  height: 28px;
  padding: 0 14px;
  border: none;
  border-radius: 999px;
  background: transparent;
  color: rgba(255, 255, 255, 0.6);
  font-size: 13px;
  cursor: pointer;
  transition: all 0.15s ease;
}

.cmt-tab:hover {
  color: #fff;
}

.cmt-tab.is-active {
  background: var(--yx-accent-on-dark, #3987e5);
  color: #fff;
  font-weight: 600;
}

/* ===== 新评论提示条 ===== */
.cmt-pending {
  position: absolute;
  top: 66px;
  left: 50%;
  transform: translateX(-50%);
  z-index: 3;
  padding: 6px 18px;
  border-radius: 999px;
  font-size: 13px;
  color: #fff;
  background: rgba(57, 135, 229, 0.5);
  backdrop-filter: blur(6px);
  cursor: pointer;
  transition: background 0.15s ease;
}

.cmt-pending:hover {
  background: rgba(57, 135, 229, 0.7);
}

/* ===== 列表 ===== */
.cmt-body {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 14px max(28px, calc((100% - 760px) / 2)) 24px;
}

.cmt-empty,
.cmt-loading,
.cmt-end {
  text-align: center;
  padding: 28px 0;
  font-size: 14px;
  color: rgba(255, 255, 255, 0.45);
}

.cmt-item {
  display: flex;
  gap: 14px;
  padding: 14px 4px;
}

.cmt-item + .cmt-item {
  border-top: 1px solid rgba(255, 255, 255, 0.06);
}

.cmt-avatar {
  flex: none;
  width: 40px;
  height: 40px;
  border-radius: 50%;
  overflow: hidden;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 16px;
  font-weight: 600;
  color: #fff;
}

.cmt-avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.cmt-main {
  flex: 1;
  min-width: 0;
}

.cmt-meta {
  display: flex;
  align-items: baseline;
  gap: 10px;
}

.cmt-nick,
.reply-nick {
  font-size: 14px;
  font-weight: 600;
  color: rgba(255, 255, 255, 0.85);
}

.cmt-time {
  font-size: 12px;
  color: rgba(255, 255, 255, 0.4);
}

.cmt-del {
  margin-left: auto;
  border: none;
  background: none;
  font-size: 12px;
  color: rgba(255, 255, 255, 0.4);
  cursor: pointer;
  opacity: 0;
  transition: color 0.15s ease, opacity 0.15s ease;
}

.cmt-item:hover .cmt-del,
.reply-item:hover .cmt-del {
  opacity: 1;
}

.cmt-del:hover {
  color: #ff5c7a;
}

.cmt-content {
  margin-top: 6px;
  font-size: 15px;
  line-height: 1.65;
  white-space: pre-wrap;
  word-break: break-word;
  color: rgba(255, 255, 255, 0.92);
}

/* 根评论操作条 */
.cmt-foot-actions {
  margin-top: 8px;
  display: flex;
  gap: 14px;
}

.reply-btn {
  border: none;
  background: none;
  padding: 0;
  font-size: 12px;
  color: rgba(255, 255, 255, 0.5);
  cursor: pointer;
  transition: color 0.15s ease;
}

.reply-btn:hover {
  color: var(--yx-accent-on-dark, #3987e5);
}

/* ===== 点赞列（评论右侧） ===== */
.like-col {
  flex: none;
  width: 34px;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 3px;
  padding-top: 2px;
  color: rgba(255, 255, 255, 0.4);
  cursor: pointer;
  user-select: none;
  transition: color 0.15s ease, transform 0.15s ease;
}

.like-col:hover {
  color: rgba(255, 255, 255, 0.75);
}

.like-col.is-liked {
  color: #ff5c7a;
}

.like-heart {
  width: 17px;
  height: 17px;
  display: block;
}

.like-col:active .like-heart {
  transform: scale(1.3);
}

.like-num {
  font-size: 11px;
}

/* ===== 回复预览 ===== */
.reply-preview {
  margin-top: 10px;
  padding: 8px 12px;
  background: rgba(255, 255, 255, 0.05);
  border-radius: 8px;
}

.reply-line {
  font-size: 13px;
  line-height: 1.7;
  color: rgba(255, 255, 255, 0.65);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  cursor: pointer;
}

.reply-line .reply-nick {
  font-size: 13px;
}

.reply-at {
  color: rgba(255, 255, 255, 0.5);
}

.reply-expand {
  margin-top: 6px;
  display: inline-flex;
  align-items: center;
  gap: 3px;
  border: none;
  background: none;
  padding: 0;
  font-size: 12px;
  color: var(--yx-accent-on-dark, #3987e5);
  cursor: pointer;
}

.reply-expand:disabled {
  opacity: 0.5;
  cursor: wait;
}

/* ===== 展开的楼中楼 ===== */
.reply-zone {
  margin-top: 10px;
  padding: 4px 12px;
  background: rgba(255, 255, 255, 0.04);
  border-radius: 8px;
}

.reply-item {
  display: flex;
  gap: 10px;
  padding: 10px 0;
}

.reply-item + .reply-item {
  border-top: 1px solid rgba(255, 255, 255, 0.05);
}

.reply-avatar {
  flex: none;
  width: 28px;
  height: 28px;
  border-radius: 50%;
  overflow: hidden;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  font-weight: 600;
  color: #fff;
}

.reply-avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.reply-main {
  flex: 1;
  min-width: 0;
}

.reply-content {
  margin-top: 4px;
  font-size: 13.5px;
  line-height: 1.6;
  color: rgba(255, 255, 255, 0.85);
  white-space: pre-wrap;
  word-break: break-word;
}

.reply-foot {
  margin-top: 4px;
}

.reply-zone .reply-expand {
  margin: 4px 0 8px;
}

/* ===== 底部输入条 ===== */
.cmt-foot {
  flex: none;
  padding: 10px max(28px, calc((100% - 760px) / 2)) calc(12px + env(safe-area-inset-bottom));
  border-top: 1px solid rgba(255, 255, 255, 0.08);
  background: rgba(23, 28, 40, 0.92);
}

.reply-hint {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 4px 6px;
}

.reply-hint-text {
  font-size: 12px;
  color: var(--yx-accent-on-dark, #3987e5);
}

.reply-hint-cancel {
  border: none;
  background: none;
  font-size: 12px;
  color: rgba(255, 255, 255, 0.5);
  cursor: pointer;
}

.reply-hint-cancel:hover {
  color: #fff;
}

.reply-input-row {
  display: flex;
  gap: 12px;
}

.cmt-input {
  flex: 1;
  height: 40px;
  border: 1px solid rgba(255, 255, 255, 0.14);
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.08);
  padding: 0 18px;
  color: #fff;
  font-size: 14px;
  outline: none;
  transition: border-color 0.15s ease, background 0.15s ease;
}

.cmt-input::placeholder {
  color: rgba(255, 255, 255, 0.35);
}

.cmt-input:focus {
  border-color: var(--yx-accent-on-dark, #3987e5);
  background: rgba(255, 255, 255, 0.12);
}

.cmt-send {
  height: 40px;
  padding: 0 22px;
  border: none;
  border-radius: 999px;
  background: var(--yx-accent-on-dark, #3987e5);
  color: #fff;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  transition: filter 0.15s ease, opacity 0.15s ease;
}

.cmt-send:hover {
  filter: brightness(1.12);
}

.cmt-send:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}
</style>
