<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { CaretRight, Star, StarFilled, Top, Bottom } from '@element-plus/icons-vue'
import TopNav from '@/components/TopNav.vue'
import SongCover from '@/components/SongCover.vue'
import { useUserStore } from '@/stores/user'
import { usePlayerStore } from '@/stores/player'
import {
  getPlaylistDetail,
  favoritePlaylist,
  unfavoritePlaylist,
  removeSongFromPlaylist,
  reorderPlaylistSongs,
  type PlaylistDetailVO,
  type PlaylistSongItem,
} from '@/api/playlist'

const route = useRoute()
const userStore = useUserStore()
const playerStore = usePlayerStore()

const playlistId = Number(route.params.id)
const detail = ref<PlaylistDetailVO | null>(null)
const loading = ref(false)
const favLoading = ref(false)

const playlist = computed(() => detail.value?.playlist)
/** 展示顺序：拥有者在排序模式下用 draftSongs（未保存的本地顺序），否则用服务器顺序 */
const songs = computed(() =>
  isSorting.value ? draftSongs.value : (detail.value?.songs ?? []),
)
const isOwner = computed(
  () => !!playlist.value && userStore.userId === playlist.value.userId && userStore.isLoggedIn,
)

// ===== 歌曲排序（仅拥有者） =====
const isSorting = ref(false)
const draftSongs = ref<PlaylistSongItem[]>([])
const reorderSaving = ref(false)

function startSort() {
  draftSongs.value = [...(detail.value?.songs ?? [])]
  isSorting.value = true
}

function cancelSort() {
  isSorting.value = false
  draftSongs.value = []
}

/** 上移/下移：交换相邻位置 */
function moveSong(index: number, dir: -1 | 1) {
  const target = index + dir
  const list = draftSongs.value
  if (target < 0 || target >= list.length) return
  const a = list[index]
  const b = list[target]
  if (!a || !b) return
  list[index] = b
  list[target] = a
}

async function saveSort() {
  if (!playlist.value) return
  reorderSaving.value = true
  try {
    await reorderPlaylistSongs(
      playlist.value.id,
      draftSongs.value.map((s) => s.id),
    )
    ElMessage.success('顺序已保存')
    isSorting.value = false
    await load()
  } catch {
    // 拦截器已提示
  } finally {
    reorderSaving.value = false
  }
}

async function load() {
  loading.value = true
  try {
    detail.value = await getPlaylistDetail(playlistId)
  } catch {
    // 拦截器已提示
  } finally {
    loading.value = false
  }
}

function playAll() {
  if (songs.value.length === 0) return
  playerStore.playQueue(songs.value as never, 0)
}

function playOne(song: PlaylistSongItem, index: number) {
  playerStore.playQueue(songs.value as never, index)
}

async function toggleFavorite() {
  if (!userStore.isLoggedIn) {
    ElMessage.info('登录后即可收藏歌单')
    return
  }
  if (!playlist.value) return
  favLoading.value = true
  try {
    if (playlist.value.favored) {
      await unfavoritePlaylist(playlist.value.id)
      playlist.value.favored = false
      ElMessage.success('已取消收藏')
    } else {
      await favoritePlaylist(playlist.value.id)
      playlist.value.favored = true
      ElMessage.success('已收藏该歌单')
    }
  } catch {
    // 拦截器已提示
  } finally {
    favLoading.value = false
  }
}

async function handleRemoveSong(song: PlaylistSongItem) {
  try {
    await ElMessageBox.confirm(`从歌单移除《${song.name}》？`, '移除确认', { type: 'warning' })
  } catch {
    return
  }
  try {
    await removeSongFromPlaylist(playlistId, song.id)
    ElMessage.success('已移除')
    await load()
  } catch {
    // 拦截器已提示
  }
}

function formatCount(n: number): string {
  if (n >= 100000000) return (n / 100000000).toFixed(1) + '亿'
  if (n >= 10000) return (n / 10000).toFixed(1) + '万'
  return String(n)
}

onMounted(load)
</script>

<template>
  <div class="page">
    <TopNav />

    <main v-loading="loading" class="main">
      <template v-if="playlist">
        <!-- 头部信息 -->
        <header class="head">
          <div class="head-cover">
            <SongCover :cover="playlist.cover" :name="playlist.title" :seed="playlist.id" radius="10px" />
          </div>
          <div class="head-info">
            <div class="head-title-row">
              <h1 class="head-title">{{ playlist.title }}</h1>
              <el-tag v-if="playlist.isOfficial" size="small" type="warning" effect="plain">官方</el-tag>
            </div>
            <p class="head-meta tabular-nums">
              {{ formatCount(playlist.playCount) }}次播放 · {{ songs.length }}首
            </p>
            <p v-if="playlist.description" class="head-desc">{{ playlist.description }}</p>
            <div class="head-actions">
              <template v-if="!isSorting">
                <el-button type="primary" :disabled="songs.length === 0" @click="playAll">
                  <el-icon class="btn-icon"><CaretRight /></el-icon>播放全部
                </el-button>
                <el-button :loading="favLoading" @click="toggleFavorite">
                  <el-icon v-if="playlist.favored" class="btn-icon fav-active"><StarFilled /></el-icon>
                  <el-icon v-else class="btn-icon"><Star /></el-icon>
                  {{ playlist.favored ? '已收藏' : '收藏歌单' }}
                </el-button>
                <el-button v-if="isOwner && songs.length > 1" @click="startSort">
                  <el-icon class="btn-icon"><Top /></el-icon>调整顺序
                </el-button>
              </template>
              <template v-else>
                <el-button type="primary" :loading="reorderSaving" @click="saveSort">保存顺序</el-button>
                <el-button @click="cancelSort">取消</el-button>
                <span class="sort-hint">使用每行右侧的 ↑ ↓ 调整歌曲顺序</span>
              </template>
            </div>
          </div>
        </header>

        <!-- 歌曲列表 -->
        <section class="songs">
          <ol class="song-list">
            <li
              v-for="(song, index) in songs"
              :key="song.id"
              class="song-item"
              :class="{ 'is-playing': playerStore.currentSong?.id === song.id }"
              @click="playOne(song, index)"
              @dblclick="playOne(song, index)"
            >
              <span class="rank tabular-nums">{{ String(index + 1).padStart(2, '0') }}</span>
              <span class="cover">
                <SongCover :cover="song.cover" :name="song.name" :seed="song.id" radius="4px" />
                <span class="cover-play" title="播放" @click.stop="playOne(song, index)">
                  <el-icon :size="16"><CaretRight /></el-icon>
                </span>
              </span>
              <span class="info">
                <span class="name">{{ song.name }}</span>
                <span class="meta">{{ song.singerName || '未知歌手' }} · {{ song.style || '流行' }}</span>
              </span>
              <span class="count tabular-nums">{{ formatCount(song.playCount) }}次播放</span>
              <span v-if="isSorting" class="sort-btns">
                <el-button
                  text size="small"
                  :disabled="index === 0"
                  @click.stop="moveSong(index, -1)"
                >↑</el-button>
                <el-button
                  text size="small"
                  :disabled="index === songs.length - 1"
                  @click.stop="moveSong(index, 1)"
                >↓</el-button>
              </span>
              <el-button
                v-else-if="isOwner"
                text
                size="small"
                type="danger"
                class="remove-btn"
                @click.stop="handleRemoveSong(song)"
              >
                移除
              </el-button>
            </li>
          </ol>
          <el-empty v-if="!loading && songs.length === 0" description="歌单还是空的" />
        </section>
      </template>
      <el-empty v-else-if="!loading" description="歌单不存在" />
    </main>
  </div>
</template>

<style scoped>
.main {
  max-width: 1160px;
  margin: 0 auto;
  padding: 28px 20px 60px;
}

.head {
  display: flex;
  gap: 22px;
  padding: 10px 0 26px;
}

.head-cover {
  width: 150px;
  height: 150px;
  border-radius: 10px;
  overflow: hidden;
  flex-shrink: 0;
  box-shadow: var(--yx-shadow-card);
}

.head-info {
  flex: 1;
  min-width: 0;
}

.head-title-row {
  display: flex;
  align-items: center;
  gap: 10px;
}

.head-title {
  font-size: 24px;
  font-weight: 700;
}

.head-meta {
  margin-top: 8px;
  font-size: 13px;
  color: var(--yx-text-secondary);
}

.head-desc {
  margin-top: 8px;
  font-size: 13px;
  color: var(--yx-text-secondary);
  line-height: 1.7;
}

.head-actions {
  margin-top: 16px;
  display: flex;
  gap: 10px;
  align-items: center;
}

.sort-hint {
  font-size: 12px;
  color: var(--yx-text-secondary);
}

.sort-btns {
  display: flex;
  gap: 2px;
  flex-shrink: 0;
}

.btn-icon {
  margin-right: 4px;
}

.fav-active {
  color: #f7ba2a;
}

.songs {
  background: #fff;
  border: 1px solid var(--yx-hairline);
  border-radius: var(--yx-radius);
  box-shadow: var(--yx-shadow-card);
  padding: 6px 0;
}

.song-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 14px;
  cursor: pointer;
}

.song-item:hover {
  background: var(--yx-accent-weak);
}

.rank {
  width: 24px;
  font-size: 14px;
  color: var(--yx-text-disabled);
}

.cover {
  position: relative;
  width: 40px;
  height: 40px;
  border-radius: 4px;
  color: rgba(255, 255, 255, 0.92);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 16px;
  font-weight: 600;
  flex-shrink: 0;
  overflow: hidden;
}

/* 封面悬浮播放钮：hover 出现，单击即播 */
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

/* 正在播放行高亮 */
.song-item.is-playing .name {
  color: var(--yx-accent);
  font-weight: 600;
}

.info {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 3px;
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
  font-size: 12px;
  color: var(--yx-text-secondary);
}

.remove-btn {
  opacity: 0;
}

.song-item:hover .remove-btn {
  opacity: 1;
}
</style>
