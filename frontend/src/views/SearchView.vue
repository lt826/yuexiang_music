<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { CaretRight, Plus } from '@element-plus/icons-vue'
import TopNav from '@/components/TopNav.vue'
import SongCover from '@/components/SongCover.vue'
import AddToPlaylistDialog from '@/components/AddToPlaylistDialog.vue'
import { usePlayerStore } from '@/stores/player'
import { useSongActions } from '@/composables/useSongActions'
import { searchSongs, getHotKeywords, getSearchHistory, clearSearchHistory, getRealtimeChart, type SearchResultVO } from '@/api/song'
import { searchSingers, type SingerVO } from '@/api/singer'
import { getHotChart, type SongVO } from '@/api/home'

const route = useRoute()
const router = useRouter()
const playerStore = usePlayerStore()
const songActions = useSongActions()
const { favoredIds, addPanel, loadFavored, toggleFavorite, openAddPanel } = songActions

const keyword = ref(String(route.query.keyword || ''))
const result = ref<SearchResultVO | null>(null)
const loading = ref(false)
const page = ref(1)
const size = 20

/** 相关歌手（搜"周杰伦"能直接跳歌手主页） */
const singers = ref<SingerVO[]>([])

// 未搜索时的默认内容：实时热歌榜 + 热搜词 + 我的搜索历史
const hotSongs = ref<SongVO[]>([])
const hotLoading = ref(false)
const hasSearched = computed(() => result.value !== null)

const hotKeywords = ref<{ keyword: string; count: number }[]>([])
const searchHistory = ref<string[]>([])

async function loadHot() {
  hotLoading.value = true
  try {
    // 实时热榜优先（Redis 热度），榜空回退经典热门榜
    const realtime = await getRealtimeChart(10)
    hotSongs.value = realtime.length > 0 ? realtime : await getHotChart(10)
  } catch {
    hotSongs.value = []
  } finally {
    hotLoading.value = false
  }
  loadHotKeywords()
  loadHistory()
}

async function loadHotKeywords() {
  try {
    hotKeywords.value = (await getHotKeywords(8)) || []
  } catch {
    hotKeywords.value = []
  }
}

async function loadHistory() {
  try {
    searchHistory.value = (await getSearchHistory()) || []
  } catch {
    searchHistory.value = []
  }
}

async function doClearHistory() {
  searchHistory.value = []
  try {
    await clearSearchHistory()
  } catch {
    /* 静默失败即可 */
  }
}

async function doSearch(p = 1) {
  const kw = keyword.value.trim()
  if (!kw) return
  loading.value = true
  page.value = p
  try {
    result.value = await searchSongs(kw, p, size)
  } catch {
    result.value = null
  } finally {
    loading.value = false
  }
  // 相关歌手随搜索并行刷新（失败静默，不阻塞歌曲结果）
  if (p === 1) {
    searchSingers(kw, 6)
      .then((list) => (singers.value = list || []))
      .catch(() => (singers.value = []))
  }
}

/** 清空关键字回到默认热门视图 */
function resetSearch() {
  keyword.value = ''
  result.value = null
  singers.value = []
  loadHot()
}

/** 跳歌手主页 */
function goSingerHome(id: number) {
  router.push(`/singer/${id}`)
}

function playAll() {
  if (!result.value || result.value.songs.length === 0) return
  playerStore.playQueue(result.value.songs, 0)
}

onMounted(() => {
  loadFavored()
  if (keyword.value) {
    doSearch()
  } else {
    loadHot()
  }
})

function formatCount(n: number): string {
  if (n >= 100000000) return (n / 100000000).toFixed(1) + '亿'
  if (n >= 10000) return (n / 10000).toFixed(1) + '万'
  return String(n)
}

/** HTML 转义（歌名是用户发布的自由文本，v-html 前必须转义防 XSS） */
function escapeHtml(s: string): string {
  return s
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;')
}

/** 关键词高亮：先转义再包 <mark>，将歌名中命中 keyword 的片段高亮（网易云式搜索反馈） */
function highlight(name: string): string {
  const safe = escapeHtml(name)
  const kw = keyword.value.trim()
  if (!kw) return safe
  const escaped = escapeHtml(kw).replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
  return safe.replace(new RegExp(`(${escaped})`, 'gi'), '<mark class="hl">$1</mark>')
}

</script>

<template>
  <div class="page">
    <TopNav />

    <main class="main">
      <div class="search-head">
        <el-input
          v-model="keyword"
          class="search-input"
          placeholder="搜索歌曲、歌词"
          clearable
          size="large"
          @keyup.enter="doSearch()"
          @clear="resetSearch"
        />
        <el-button type="primary" size="large" @click="doSearch()">搜索</el-button>
      </div>

      <div v-loading="loading" class="result-area">
        <template v-if="result">
          <!-- 相关歌手：点击直达歌手主页 -->
          <div v-if="singers.length > 0 && page === 1" class="singer-block">
            <header class="singer-block-head">🎤 相关歌手</header>
            <div class="singer-chips">
              <button
                v-for="s in singers"
                :key="s.id"
                class="singer-chip"
                @click="goSingerHome(s.id)"
              >
                <img v-if="s.avatar" :src="s.avatar" :alt="s.name" class="singer-avatar" />
                <span v-else class="singer-avatar singer-avatar-fallback">{{ s.name.slice(0, 1) }}</span>
                <span class="singer-chip-name">{{ s.name }}</span>
                <span class="singer-chip-go">主页 ›</span>
              </button>
            </div>
          </div>

          <header class="result-head">
            <span class="result-count">
              「{{ keyword }}」共找到
              <b class="tabular-nums">{{ result.total }}</b>
              首
            </span>
            <el-button v-if="result.songs.length > 0" text type="primary" @click="playAll">
              播放全部
            </el-button>
          </header>

          <ol v-if="result.songs.length > 0" class="song-list">
            <li
              v-for="(song, index) in result.songs"
              :key="song.id"
              class="song-item"
              :class="{ 'is-playing': playerStore.currentSong?.id === song.id }"
              @click="playerStore.playQueue(result!.songs, index)"
              @dblclick="playerStore.playQueue(result!.songs, index)"
            >
              <span class="rank tabular-nums">{{ String(index + 1).padStart(2, '0') }}</span>
              <span class="cover">
                <SongCover :cover="song.cover" :name="song.name" :seed="song.id" radius="4px" />
                <span class="cover-play" title="播放" @click.stop="playerStore.playQueue(result!.songs, index)">
                  <el-icon :size="16"><CaretRight /></el-icon>
                </span>
              </span>
              <span class="info">
                <span class="name" v-html="highlight(song.name)"></span>
                <span class="meta">
                  <a
                    v-if="song.singerId"
                    class="meta-singer"
                    title="查看歌手主页"
                    @click.stop="goSingerHome(song.singerId)"
                  >{{ song.singerName || '未知歌手' }}</a>
                  <template v-else>{{ song.singerName || '未知歌手' }}</template>
                  · {{ song.style || '流行' }}
                </span>
              </span>
              <span class="count tabular-nums">{{ formatCount(song.playCount) }}次播放</span>
              <span class="song-actions">
                <button
                  class="act-btn"
                  :class="{ 'is-favored': favoredIds.has(song.id) }"
                  :title="favoredIds.has(song.id) ? '已收藏' : '收藏'"
                  @click.stop="toggleFavorite(song)"
                >
                  {{ favoredIds.has(song.id) ? '♥' : '♡' }}
                </button>
                <button class="act-btn" title="加入歌单" @click.stop="openAddPanel(song)">
                  <el-icon :size="14"><Plus /></el-icon>
                </button>
              </span>
            </li>
          </ol>

          <el-empty v-else description="未找到相关歌曲">
            <div class="ai-hint">
              <p>普通搜索没找到？让 AI 帮你换思路找找</p>
              <el-button type="warning" round @click="$router.push({ path: '/ai-search', query: { q: keyword } })">
                🤖 用 AI 找歌
              </el-button>
            </div>
          </el-empty>

          <div v-if="result.total > size" class="pager">
            <el-pagination
              layout="prev, pager, next"
              :total="result.total"
              :page-size="size"
              :current-page="page"
              @current-change="doSearch($event)"
            />
          </div>
        </template>

        <!-- 未搜索：默认展示实时热歌榜 + 热搜词 + 搜索历史 -->
        <template v-else-if="!loading">
          <div v-if="hotKeywords.length > 0 || searchHistory.length > 0" class="discover-row">
            <div v-if="searchHistory.length > 0" class="discover-block">
              <header class="discover-head">
                <span class="discover-title">🕐 搜索历史</span>
                <el-button text size="small" @click="doClearHistory">清空</el-button>
              </header>
              <div class="chip-row">
                <button
                  v-for="kw in searchHistory"
                  :key="kw"
                  class="chip"
                  @click="keyword = kw; doSearch()"
                >
                  {{ kw }}
                </button>
              </div>
            </div>
            <div v-if="hotKeywords.length > 0" class="discover-block">
              <header class="discover-head">
                <span class="discover-title">🔥 大家都在搜</span>
              </header>
              <div class="chip-row">
                <button
                  v-for="(item, i) in hotKeywords"
                  :key="item.keyword"
                  class="chip"
                  @click="keyword = item.keyword; doSearch()"
                >
                  <i v-if="i < 3" class="chip-rank" :class="{ 'chip-rank-top': i < 3 }">{{ i + 1 }}</i>
                  {{ item.keyword }}
                </button>
              </div>
            </div>
          </div>

          <header class="result-head">
            <span class="result-count">🎧 实时热歌榜</span>
            <el-button v-if="hotSongs.length > 0" text type="primary" @click="playerStore.playQueue(hotSongs, 0)">
              播放全部
            </el-button>
          </header>
          <div v-loading="hotLoading">
            <ol v-if="hotSongs.length > 0" class="song-list">
              <li
                v-for="(song, index) in hotSongs"
                :key="song.id"
                class="song-item"
                :class="{ 'is-playing': playerStore.currentSong?.id === song.id }"
                @click="playerStore.playQueue(hotSongs, index)"
                @dblclick="playerStore.playQueue(hotSongs, index)"
              >
                <span class="rank tabular-nums" :class="{ 'rank-top': index < 3 }">
                  {{ String(index + 1).padStart(2, '0') }}
                </span>
                <span class="cover">
                  <SongCover :cover="song.cover" :name="song.name" :seed="song.id" radius="4px" />
                  <span class="cover-play" title="播放" @click.stop="playerStore.playQueue(hotSongs, index)">
                    <el-icon :size="16"><CaretRight /></el-icon>
                  </span>
                </span>
                <span class="info">
                  <span class="name">{{ song.name }}</span>
                  <span class="meta">{{ song.singerName || '未知歌手' }} · {{ song.style || '流行' }}</span>
                </span>
                <span class="count tabular-nums">{{ formatCount(song.playCount) }}次播放</span>
                <span class="song-actions">
                  <button
                    class="act-btn"
                    :class="{ 'is-favored': favoredIds.has(song.id) }"
                    :title="favoredIds.has(song.id) ? '已收藏' : '收藏'"
                    @click.stop="toggleFavorite(song)"
                  >
                    {{ favoredIds.has(song.id) ? '♥' : '♡' }}
                  </button>
                  <button class="act-btn" title="加入歌单" @click.stop="openAddPanel(song)">
                    <el-icon :size="14"><Plus /></el-icon>
                  </button>
                </span>
              </li>
            </ol>
            <el-empty v-else-if="!hotLoading" description="输入关键字搜索，或听听热门歌曲" />
          </div>
        </template>
      </div>

      <!-- 加入歌单弹窗 -->
      <AddToPlaylistDialog :actions="songActions" />
    </main>
  </div>
</template>

<style scoped>
.main {
  max-width: 1160px;
  margin: 0 auto;
  padding: 32px 20px 60px;
}

.search-head {
  display: flex;
  gap: 12px;
}

.search-input {
  flex: 1;
}

.result-area {
  margin-top: 24px;
  min-height: 300px;
}

/* 搜索历史 + 热搜词区块 */
.discover-row {
  display: flex;
  flex-direction: column;
  gap: 14px;
  margin-bottom: 20px;
}

.discover-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}

.discover-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--yx-text-secondary);
}

.chip-row {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.chip {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  border: 1px solid var(--yx-hairline);
  background: #fff;
  border-radius: 999px;
  padding: 5px 12px;
  font-size: 13px;
  color: var(--yx-text-primary);
  cursor: pointer;
  transition: all 0.15s ease;
}

.chip:hover {
  border-color: var(--yx-accent);
  color: var(--yx-accent);
  background: var(--yx-accent-weak);
}

.chip-rank {
  font-style: normal;
  font-size: 12px;
  color: var(--yx-text-disabled);
  font-weight: 700;
}

.chip-rank-top {
  color: var(--yx-accent);
}

.result-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}

.result-count {
  font-size: 14px;
  color: var(--yx-text-secondary);
}

.song-list {
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

.song-item.is-playing .name {
  color: var(--yx-accent);
  font-weight: 600;
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

/* 歌手名可点进主页 */
.meta-singer {
  color: var(--yx-text-secondary);
  cursor: pointer;
  transition: color 0.15s ease;
}

.meta-singer:hover {
  color: var(--yx-accent);
  text-decoration: underline;
  text-underline-offset: 2px;
}

/* ===== 相关歌手区 ===== */
.singer-block {
  margin-bottom: 18px;
}

.singer-block-head {
  font-size: 13px;
  font-weight: 600;
  color: var(--yx-text-secondary);
  margin-bottom: 10px;
}

.singer-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.singer-chip {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 6px 14px 6px 6px;
  border: 1px solid var(--yx-hairline);
  border-radius: 999px;
  background: #fff;
  cursor: pointer;
  transition: all 0.15s ease;
}

.singer-chip:hover {
  border-color: var(--yx-accent);
  background: var(--yx-accent-weak);
}

.singer-avatar {
  width: 28px;
  height: 28px;
  border-radius: 50%;
  object-fit: cover;
  flex-shrink: 0;
}

.singer-avatar-fallback {
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--yx-gradient);
  color: #fff;
  font-size: 13px;
  font-weight: 600;
}

.singer-chip-name {
  font-size: 13px;
  color: var(--yx-text-main);
  font-weight: 500;
}

.singer-chip:hover .singer-chip-name {
  color: var(--yx-accent);
}

.singer-chip-go {
  font-size: 11px;
  color: var(--yx-text-disabled);
}

.count {
  font-size: 12px;
  color: var(--yx-text-secondary);
}

/* 命中关键词高亮 */
.name :deep(mark.hl) {
  color: var(--yx-accent);
  background: var(--yx-accent-weak);
  font-weight: 700;
  border-radius: 2px;
  padding: 0 1px;
}

.pager {
  margin-top: 20px;
  display: flex;
  justify-content: center;
}

/* 搜索无结果的 AI 引导块 */
.ai-hint {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
}

.ai-hint p {
  margin: 0;
  font-size: 13px;
  color: var(--yx-text-secondary);
}

/* 行尾操作区：收藏 / 加号，hover 行时浮现 */
.song-actions {
  display: flex;
  gap: 2px;
  flex-shrink: 0;
  opacity: 0;
  transition: opacity 0.15s ease;
}

.song-item:hover .song-actions {
  opacity: 1;
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
</style>
