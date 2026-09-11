<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowLeft, CaretRight, Headset } from '@element-plus/icons-vue'
import TopNav from '@/components/TopNav.vue'
import FollowListDialog from '@/components/FollowListDialog.vue'
import { useUserStore } from '@/stores/user'
import { useAuthPromptStore } from '@/stores/authPrompt'
import {
  getUserPublicInfo,
  getFollowSummary,
  followUser,
  unfollowUser,
  type UserPublicInfo,
  type FollowSummaryVO,
} from '@/api/user'
import { getPlaylistsByUser, type PlaylistVO } from '@/api/playlist'
import { getVirtualFollows } from '@/api/singer'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const authPrompt = useAuthPromptStore()

const userId = Number(route.params.id)
const user = ref<UserPublicInfo | null>(null)
const playlists = ref<PlaylistVO[]>([])
const loading = ref(false)
const loadError = ref(false)

/** 关注/粉丝计数（匿名可看；关注数额外合并虚拟歌手，与"我的音乐→我的关注"同口径） */
const summary = ref<FollowSummaryVO | null>(null)
/** 关注的虚拟歌手数（程一诺等平台导入歌手，落 singer 服务本地表） */
const virtualFollowCount = ref(0)
/** 关注总数 = 用户互关数 + 虚拟歌手数 */
const followingTotal = computed(
  () => (summary.value?.followingCount ?? 0) + virtualFollowCount.value,
)
/** 是否本人主页（本人不显示关注按钮） */
const isSelf = computed(() => userStore.isLoggedIn && userStore.userId === userId)
/** 关注操作进行中 */
const followBusy = ref(false)
/** 列表弹窗 */
const listVisible = ref(false)
const listMode = ref<'following' | 'followers'>('following')

function openList(mode: 'following' | 'followers') {
  listMode.value = mode
  listVisible.value = true
}

/** 关注/取关：未登录弹登录拦截（登录成功自动续做） */
function handleFollow() {
  if (!userStore.isLoggedIn) {
    authPrompt.open(() => void doToggleFollow())
    return
  }
  void doToggleFollow()
}

async function doToggleFollow() {
  followBusy.value = true
  const wasFollowing = !!summary.value?.following
  try {
    if (wasFollowing) {
      await unfollowUser(userId)
    } else {
      await followUser(userId)
    }
    if (summary.value) {
      summary.value.following = !wasFollowing
      summary.value.followerCount = Math.max(
        0,
        (summary.value.followerCount || 0) + (wasFollowing ? -1 : 1),
      )
    }
  } catch {
    // 拦截器已提示
  } finally {
    followBusy.value = false
  }
}

async function load() {
  loading.value = true
  try {
    user.value = await getUserPublicInfo(userId)
    try {
      playlists.value = await getPlaylistsByUser(userId)
    } catch {
      playlists.value = []
    }
  } catch {
    loadError.value = true
  } finally {
    loading.value = false
  }
  try {
    const [sum, vf] = await Promise.all([
      getFollowSummary(userId),
      // 虚拟歌手聚合计数失败仅影响数字口径，不阻塞主页
      getVirtualFollows(userId).catch(() => []),
    ])
    summary.value = sum
    virtualFollowCount.value = vf.length
  } catch {
    summary.value = null
    virtualFollowCount.value = 0
  }
}

function roleLabel(role: string): string {
  if (role === 'ADMIN') return '管理员'
  if (role === 'SINGER') return '认证歌手'
  return '乐友'
}

function openDetail(pl: PlaylistVO) {
  router.push(`/playlist/${pl.id}`)
}

function formatCount(n: number): string {
  if (n >= 100000000) return (n / 100000000).toFixed(1) + '亿'
  if (n >= 10000) return (n / 10000).toFixed(1) + '万'
  return String(n)
}

function formatJoinTime(t: string | null): string {
  if (!t) return ''
  return t.slice(0, 10) + ' 加入'
}

/** 封面兜底色：无封面歌单的占位底色（与歌单广场同一色板） */
const coverPalette = ['#2a78d6', '#eb6834', '#1baf7a', '#eda100', '#e87ba4', '#4a3aa7']
function coverColor(seed: number): string {
  return coverPalette[seed % coverPalette.length] ?? '#2a78d6'
}

onMounted(load)
</script>

<template>
  <div class="page">
    <TopNav />
    <main class="main" v-loading="loading">
      <button class="back" @click="router.back()">
        <el-icon :size="14"><ArrowLeft /></el-icon>
        返回
      </button>

      <el-empty v-if="loadError" description="用户不存在或已注销" />

      <template v-else-if="user">
        <!-- 用户信息卡：昵称为展示主体（用户名不上公开主页） -->
        <section class="hero">
          <el-avatar :size="88" :src="user.avatar || undefined" class="hero-avatar">
            {{ (user.nickname || '友').slice(0, 1) }}
          </el-avatar>
          <div class="hero-info">
            <div class="hero-top">
              <h1 class="hero-name yx-display">{{ user.nickname || `用户${user.id}` }}</h1>
              <el-tag :type="user.role === 'SINGER' ? 'warning' : 'info'" size="small" effect="plain">
                {{ roleLabel(user.role) }}
              </el-tag>
            </div>
            <p v-if="user.signature" class="hero-signature">“{{ user.signature }}”</p>
            <div class="hero-stats">
              <button type="button" class="stat-btn" @click="openList('following')">
                <b class="tabular-nums">{{ followingTotal }}</b> 关注
              </button>
              <button type="button" class="stat-btn" @click="openList('followers')">
                <b class="tabular-nums">{{ summary?.followerCount ?? 0 }}</b> 粉丝
              </button>
              <span v-if="user.createTime" class="hero-meta">{{ formatJoinTime(user.createTime) }}</span>
            </div>
            <div v-if="!isSelf" class="hero-actions">
              <el-button
                :type="summary?.following ? 'default' : 'primary'"
                size="small"
                round
                :loading="followBusy"
                @click="handleFollow"
              >
                {{ summary?.following ? '已关注' : '关注' }}
              </el-button>
            </div>
          </div>
        </section>

        <!-- 关注/粉丝列表弹窗 -->
        <FollowListDialog v-model:visible="listVisible" :user-id="userId" :mode="listMode" />

        <!-- TA 的公开歌单 -->
        <section class="zone">
          <span class="yx-eyebrow">PUBLIC PLAYLISTS</span>
          <h2 class="zone-title yx-display">TA 的公开歌单</h2>
          <div v-if="playlists.length" class="grid">
            <div v-for="pl in playlists" :key="pl.id" class="card" @click="openDetail(pl)">
              <div class="cover" :style="pl.cover ? undefined : { background: coverColor(pl.id) }">
                <img v-if="pl.cover" class="cover-img" :src="pl.cover" :alt="pl.title" loading="lazy" />
                <span v-else class="cover-tag">{{ pl.tag || pl.title.slice(0, 2) }}</span>
                <span class="stat tabular-nums">
                  <el-icon :size="12"><Headset /></el-icon>
                  {{ formatCount(pl.playCount) }}
                </span>
                <span class="play">
                  <el-icon :size="18"><CaretRight /></el-icon>
                </span>
              </div>
              <p class="card-title">{{ pl.title }}</p>
              <p class="card-meta">{{ pl.tag || '精选' }} · {{ pl.songCount ?? 0 }}首</p>
            </div>
          </div>
          <el-empty v-else-if="!loading" description="TA 还没有公开歌单" :image-size="80" />
        </section>
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

.hero {
  display: flex;
  align-items: center;
  gap: 22px;
  background: #fff;
  border: 1px solid var(--yx-hairline);
  border-radius: 12px;
  padding: 24px;
  margin-bottom: 28px;
}

.hero-avatar {
  flex-shrink: 0;
  font-size: 34px;
}

.hero-top {
  display: flex;
  align-items: center;
  gap: 10px;
}

.hero-name {
  font-size: 24px;
  font-weight: 700;
  color: var(--yx-text-main);
}

.hero-signature {
  margin-top: 8px;
  font-size: 13px;
  color: var(--yx-text-secondary);
}

/* 关注/粉丝计数（可点击查看列表） */
.hero-stats {
  display: flex;
  align-items: center;
  gap: 14px;
  margin-top: 10px;
}

.stat-btn {
  display: inline-flex;
  align-items: baseline;
  gap: 5px;
  border: none;
  background: none;
  padding: 0;
  font-size: 13px;
  color: var(--yx-text-secondary);
  cursor: pointer;
}

.stat-btn b {
  font-size: 16px;
  color: var(--yx-text-main);
}

.stat-btn:hover {
  color: var(--yx-accent);
}

.hero-actions {
  margin-top: 12px;
}

.hero-meta {
  font-size: 12px;
  color: var(--yx-text-secondary);
}

.zone-title {
  font-size: 19px;
  font-weight: 600;
  margin: 2px 0 14px;
}

.grid {
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  gap: 18px;
  min-height: 120px;
}

@media (max-width: 1000px) {
  .grid {
    grid-template-columns: repeat(3, 1fr);
  }
}

@media (max-width: 640px) {
  .grid {
    grid-template-columns: repeat(2, 1fr);
  }
}

.card {
  cursor: pointer;
  border-radius: 10px;
  transition: transform 0.18s ease;
}

.card:hover {
  transform: translateY(-3px);
}

.cover {
  position: relative;
  aspect-ratio: 1;
  border-radius: 10px;
  overflow: hidden;
  display: flex;
  align-items: flex-end;
  box-shadow: 0 4px 14px rgba(20, 40, 80, 0.14);
}

.cover-img {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.card:hover .cover {
  box-shadow: 0 8px 24px rgba(20, 40, 80, 0.22);
}

.cover-tag {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 30px;
  font-weight: 800;
  letter-spacing: 4px;
  color: rgba(255, 255, 255, 0.55);
  writing-mode: vertical-lr;
  text-orientation: upright;
}

.cover::after {
  content: '';
  position: absolute;
  inset: 0;
  background: linear-gradient(180deg, transparent 62%, rgba(0, 0, 0, 0.16));
  pointer-events: none;
}

.stat {
  position: absolute;
  top: 8px;
  right: 10px;
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  color: rgba(255, 255, 255, 0.95);
  text-shadow: 0 1px 4px rgba(0, 0, 0, 0.5);
  z-index: 1;
}

.play {
  position: absolute;
  right: 10px;
  bottom: 10px;
  width: 34px;
  height: 34px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  color: #fff;
  background: var(--yx-gradient);
  opacity: 0;
  transform: translateY(4px);
  transition: opacity 0.18s ease, transform 0.18s ease;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.35);
  z-index: 1;
}

.card:hover .play {
  opacity: 1;
  transform: translateY(0);
}

.card-title {
  margin-top: 9px;
  font-size: 14px;
  font-weight: 600;
  line-height: 1.45;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  min-height: 2.9em;
}

.card-meta {
  margin-top: 3px;
  font-size: 12px;
  color: var(--yx-text-secondary);
}
</style>
