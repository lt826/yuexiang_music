<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { CaretRight, Plus, Star, StarFilled } from '@element-plus/icons-vue'
import TopNav from '@/components/TopNav.vue'
import SongCover from '@/components/SongCover.vue'
import { useUserStore } from '@/stores/user'
import { usePlayerStore } from '@/stores/player'
import { ElMessage } from 'element-plus'
import {
  followSinger,
  getSingerHome,
  getSongsBySinger,
  unfollowSinger,
  type SingerHomeVO,
} from '@/api/singer'
import { getPlaylistsByUser, type PlaylistVO } from '@/api/playlist'
import type { SongVO } from '@/api/home'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const playerStore = usePlayerStore()

/** 路由参数响应式：主页间跳转（搜索页点另一个歌手）复用组件实例，参数变化须重载 */
const singerId = computed(() => Number(route.params.id))
const singer = ref<SingerHomeVO | null>(null)
const loading = ref(false)

/** 歌手的作品与公开歌单 */
const songs = ref<SongVO[]>([])
const songsLoading = ref(false)
const playlists = ref<PlaylistVO[]>([])
const playlistsLoading = ref(false)

/** 关注操作进行中（防连点） */
const followBusy = ref(false)

/** 是否歌手本人浏览（本人不显示关注按钮，给"进入歌手中心"入口） */
const isSelf = computed(() => !!singer.value?.userId && userStore.userId === singer.value.userId)

async function load() {
  loading.value = true
  try {
    singer.value = await getSingerHome(singerId.value)
  } catch {
    // 拦截器已提示（歌手不存在等）
  } finally {
    loading.value = false
  }
  loadSongs()
  loadPlaylists()
}

async function loadSongs() {
  songsLoading.value = true
  try {
    songs.value = await getSongsBySinger(singerId.value)
  } catch {
    songs.value = []
  } finally {
    songsLoading.value = false
  }
}

async function loadPlaylists() {
  // 未关联用户的歌手（导入的歌手数据）没有歌单，静默为空
  if (!singer.value?.userId) return
  playlistsLoading.value = true
  try {
    playlists.value = await getPlaylistsByUser(singer.value.userId)
  } catch {
    playlists.value = []
  } finally {
    playlistsLoading.value = false
  }
}

/** 关注/取消关注：未登录提示去登录 */
async function toggleFollow() {
  if (!singer.value) return
  if (!userStore.isLoggedIn) {
    ElMessage.info('登录后即可关注歌手')
    return
  }
  if (followBusy.value) return
  followBusy.value = true
  try {
    if (singer.value.followed) {
      await unfollowSinger(singerId.value)
      singer.value.followed = false
      singer.value.followerCount = Math.max(0, singer.value.followerCount - 1)
      ElMessage.success(`已取消关注 ${singer.value.name}`)
    } else {
      await followSinger(singerId.value)
      singer.value.followed = true
      singer.value.followerCount += 1
      ElMessage.success(`已关注 ${singer.value.name}，新歌上架会通知你`)
    }
  } catch {
    // 拦截器已提示（重复关注等）
  } finally {
    followBusy.value = false
  }
}

function playAll() {
  if (songs.value.length === 0) return
  playerStore.playQueue(songs.value, 0)
}

function playOne(index: number) {
  playerStore.playQueue(songs.value, index)
}

function formatCount(n: number | null | undefined): string {
  const v = n ?? 0
  if (v >= 100000000) return `${(v / 100000000).toFixed(1)}亿`
  if (v >= 10000) return `${(v / 10000).toFixed(1)}万`
  return String(v)
}

/** 头像兜底渐变（无 avatar 时按 id 稳定取色，与 SingerRow 同款色板） */
const palette = [
  'linear-gradient(135deg, #86b6ef, #2a78d6)',
  'linear-gradient(135deg, #f3b08a, #eb6834)',
  'linear-gradient(135deg, #8ad0bc, #199e70)',
  'linear-gradient(135deg, #f2cd86, #c98500)',
  'linear-gradient(135deg, #c3b0ec, #7a68cf)',
  'linear-gradient(135deg, #eda0c0, #c8588b)',
]
const avatarBg = computed(() => palette[singerId.value % palette.length] ?? palette[0]!)

onMounted(load)

/** 同页路由参数变化（如主页间互跳）时重载全部数据 */
watch(singerId, () => {
  playlists.value = []
  load()
})
</script>

<template>
  <div class="page">
    <TopNav />

    <main v-loading="loading" class="main">
      <template v-if="singer">
        <!-- ===== 头部横幅：头像 + 名字 + 数据 + 关注 ===== -->
        <section class="hero">
          <div class="hero-avatar">
            <img v-if="singer.avatar" :src="singer.avatar" :alt="singer.name" class="avatar-img" />
            <div v-else class="avatar-fallback" :style="{ background: avatarBg }">
              {{ singer.name.slice(0, 1) }}
            </div>
          </div>

          <div class="hero-info">
            <h1 class="hero-name">{{ singer.name }}</h1>
            <p class="hero-intro">{{ singer.introduction || '这位歌手还没有留下简介' }}</p>
            <div class="hero-stats">
              <span class="stat"><b class="tabular-nums">{{ formatCount(singer.songCount) }}</b> 歌曲</span>
              <span class="stat-divider" />
              <span class="stat"><b class="tabular-nums">{{ formatCount(singer.followerCount) }}</b> 粉丝</span>
              <span class="stat-divider" />
              <span class="stat"><b class="tabular-nums">{{ formatCount(singer.totalPlayCount) }}</b> 播放</span>
            </div>
            <div class="hero-actions">
              <!-- 本人浏览：给歌手中心入口 -->
              <el-button v-if="isSelf" type="primary" round @click="router.push('/singer-center')">
                进入歌手中心
              </el-button>
              <template v-else>
                <el-button
                  :type="singer.followed ? 'default' : 'primary'"
                  round
                  :loading="followBusy"
                  @click="toggleFollow"
                >
                  <el-icon v-if="!singer.followed" class="btn-icon"><Plus /></el-icon>
                  <el-icon v-else class="btn-icon"><StarFilled /></el-icon>
                  {{ singer.followed ? '已关注' : '关注' }}
                </el-button>
                <el-button v-if="songs.length > 0" round @click="playAll">
                  <el-icon class="btn-icon"><CaretRight /></el-icon>
                  播放全部
                </el-button>
              </template>
            </div>
          </div>
        </section>

        <!-- ===== 歌曲 ===== -->
        <section class="block">
          <header class="block-head">
            <h2 class="block-title">歌曲 <span class="tabular-nums">{{ songs.length }}</span></h2>
          </header>
          <div v-loading="songsLoading" class="block-body">
            <ol v-if="songs.length > 0" class="song-list">
              <li
                v-for="(song, index) in songs"
                :key="song.id"
                class="song-item"
                :class="{ 'is-playing': playerStore.currentSong?.id === song.id }"
                @dblclick="playOne(index)"
              >
                <span class="rank tabular-nums">{{ String(index + 1).padStart(2, '0') }}</span>
                <span class="cover">
                  <SongCover :cover="song.cover" :name="song.name" :seed="song.id" radius="4px" />
                  <span class="cover-play" title="播放" @click.stop="playOne(index)">
                    <el-icon :size="16"><CaretRight /></el-icon>
                  </span>
                </span>
                <span class="info" @click="playOne(index)">
                  <span class="name">{{ song.name }}</span>
                  <span class="meta">{{ song.style || '流行' }} · {{ formatCount(song.playCount) }}次播放</span>
                </span>
                <span class="count tabular-nums">
                  <el-icon :size="12"><Star /></el-icon>
                  {{ formatCount(song.playCount) }}
                </span>
              </li>
            </ol>
            <el-empty v-else-if="!songsLoading" description="暂无已发布的歌曲" />
          </div>
        </section>

        <!-- ===== TA 的公开歌单 ===== -->
        <section v-if="singer.userId" class="block">
          <header class="block-head">
            <h2 class="block-title">歌单 <span class="tabular-nums">{{ playlists.length }}</span></h2>
          </header>
          <div v-loading="playlistsLoading" class="block-body">
            <div v-if="playlists.length > 0" class="pl-grid">
              <div
                v-for="pl in playlists"
                :key="pl.id"
                class="pl-card"
                @click="router.push(`/playlist/${pl.id}`)"
              >
                <div class="pl-cover">
                  <SongCover :cover="pl.cover" :name="pl.title" :seed="pl.id" radius="8px" />
                </div>
                <p class="pl-title">{{ pl.title }}</p>
                <p class="pl-meta tabular-nums">{{ pl.songCount }}首</p>
              </div>
            </div>
            <el-empty v-else-if="!playlistsLoading" description="暂无公开歌单" />
          </div>
        </section>
      </template>

      <el-empty v-else-if="!loading" description="歌手不存在或已退出平台">
        <el-button type="primary" @click="router.push('/home')">回首页</el-button>
      </el-empty>
    </main>
  </div>
</template>

<style scoped>
.main {
  max-width: 1000px;
  margin: 0 auto;
  padding: 28px 20px 80px;
}

/* ===== 头部横幅 ===== */
.hero {
  display: flex;
  gap: 28px;
  align-items: flex-end;
  padding: 32px 28px;
  border-radius: 14px;
  background: linear-gradient(135deg, #eef4fd 0%, #f7f0fd 100%);
}

.hero-avatar {
  position: relative;
  flex: none;
  width: 140px;
  height: 140px;
  border-radius: 50%;
  overflow: hidden;
  box-shadow: 0 10px 30px rgba(42, 120, 214, 0.25);
}

.avatar-img,
.avatar-fallback {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.avatar-fallback {
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-size: 52px;
  font-weight: 700;
}

.hero-info {
  flex: 1;
  min-width: 0;
}

.hero-name {
  font-size: 28px;
  font-weight: 700;
  color: var(--yx-text-main);
}

.hero-intro {
  margin-top: 8px;
  font-size: 14px;
  color: var(--yx-text-secondary);
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.hero-stats {
  display: flex;
  align-items: center;
  gap: 14px;
  margin-top: 14px;
  font-size: 13px;
  color: var(--yx-text-secondary);
}

.stat b {
  color: var(--yx-text-main);
  font-size: 16px;
  margin-right: 3px;
}

.stat-divider {
  width: 1px;
  height: 14px;
  background: rgba(42, 120, 214, 0.25);
}

.hero-actions {
  display: flex;
  gap: 12px;
  margin-top: 18px;
}

.btn-icon {
  margin-right: 4px;
}

/* ===== 区块 ===== */
.block {
  margin-top: 30px;
}

.block-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}

.block-title {
  font-size: 18px;
  font-weight: 700;
  color: var(--yx-text-main);
}

.block-title span {
  font-size: 13px;
  font-weight: 500;
  color: var(--yx-text-secondary);
  margin-left: 4px;
}

.block-body {
  background: #fff;
  border: 1px solid var(--yx-hairline);
  border-radius: var(--yx-radius);
  box-shadow: var(--yx-shadow-card);
  padding: 6px 16px;
  min-height: 120px;
}

/* ===== 歌曲列表 ===== */
.song-list {
  border-top: 1px solid var(--yx-hairline);
}

.song-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 4px;
  border-bottom: 1px solid var(--yx-hairline);
  border-radius: 8px;
  transition: background 0.2s ease;
}

.song-item:hover {
  background: var(--yx-accent-weak);
}

.song-item.is-playing .name {
  color: var(--yx-accent);
  font-weight: 600;
}

.rank {
  flex: none;
  min-width: 26px;
  text-align: center;
  font-size: 14px;
  font-style: italic;
  font-weight: 700;
  color: var(--yx-text-disabled);
}

.cover {
  position: relative;
  width: 42px;
  height: 42px;
  flex-shrink: 0;
  border-radius: 4px;
  overflow: hidden;
}

.cover-play {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  background: rgba(0, 0, 0, 0.42);
  opacity: 0;
  transition: opacity 0.25s ease;
  cursor: pointer;
}

.cover:hover .cover-play,
.song-item:hover .cover-play {
  opacity: 1;
}

.info {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 3px;
  cursor: pointer;
}

.info:hover .name {
  color: var(--yx-accent);
}

.name {
  font-size: 14px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.meta {
  font-size: 12px;
  color: var(--yx-text-secondary);
}

.count {
  flex: none;
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  color: var(--yx-text-secondary);
}

/* ===== 歌单网格 ===== */
.pl-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(130px, 1fr));
  gap: 16px;
  padding: 12px 4px;
}

.pl-card {
  cursor: pointer;
}

.pl-cover {
  border-radius: 8px;
  overflow: hidden;
  transition: transform 0.2s ease;
}

.pl-card:hover .pl-cover {
  transform: translateY(-3px);
}

.pl-title {
  margin-top: 8px;
  font-size: 13px;
  color: var(--yx-text-main);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.pl-meta {
  margin-top: 2px;
  font-size: 12px;
  color: var(--yx-text-secondary);
}

@media (max-width: 640px) {
  .hero {
    flex-direction: column;
    align-items: center;
    text-align: center;
  }

  .hero-stats,
  .hero-actions {
    justify-content: center;
  }
}
</style>
