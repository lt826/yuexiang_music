<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import TopNav from '@/components/TopNav.vue'
import SongRow from '@/components/SongRow.vue'
import SingerRow from '@/components/SingerRow.vue'
import { useUserStore } from '@/stores/user'
import { usePlayerStore } from '@/stores/player'
import { useAuthPromptStore } from '@/stores/authPrompt'
import {
  getHotChart,
  getNewSongs,
  getRecommendSongs,
  getHotSingers,
  type SongVO,
  type SingerVO,
} from '@/api/home'
import { addFavorite, removeFavorite, getFavoriteSongIds } from '@/api/favorite'

/**
 * 分类完整列表页（首页各模块"更多"落地）：新歌首发 / 推荐歌曲 / 热门歌曲 / 推荐歌手
 * 沿用首页左图右文榜单行样式，保证全站视觉统一
 */
const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const playerStore = usePlayerStore()
const authPrompt = useAuthPromptStore()

type TabKey = 'new' | 'recommend' | 'hot' | 'singer'

const tabMeta: Record<TabKey, { label: string }> = {
  new: { label: '新歌首发' },
  recommend: { label: '推荐歌曲' },
  hot: { label: '热门歌曲' },
  singer: { label: '推荐歌手' },
}

const tabs: TabKey[] = ['new', 'recommend', 'hot', 'singer']
const activeTab = ref<TabKey>('new')
const loading = ref(false)

const songs = ref<SongVO[]>([])
const singers = ref<SingerVO[]>([])
const favoredIds = ref<Set<number>>(new Set())

function syncFromQuery() {
  const t = route.query.tab as TabKey
  if (tabs.includes(t)) {
    activeTab.value = t
  }
}

async function loadFavorites() {
  if (!userStore.isLoggedIn) {
    favoredIds.value = new Set()
    return
  }
  try {
    favoredIds.value = new Set(await getFavoriteSongIds())
  } catch {
    favoredIds.value = new Set()
  }
}

async function load() {
  loading.value = true
  try {
    if (activeTab.value === 'singer') {
      singers.value = await getHotSingers(24)
      songs.value = []
    } else if (activeTab.value === 'new') {
      songs.value = await getNewSongs(30)
    } else if (activeTab.value === 'recommend') {
      songs.value = await getRecommendSongs(30)
    } else {
      songs.value = await getHotChart(30)
    }
  } catch {
    // 拦截器已提示
  } finally {
    loading.value = false
  }
}

function switchTab(t: TabKey) {
  activeTab.value = t
  router.replace({ query: { tab: t } })
}

function playSong(song: SongVO) {
  const index = songs.value.findIndex((s) => s.id === song.id)
  playerStore.playQueue(songs.value, index < 0 ? 0 : index)
}

async function toggleFavorite(song: SongVO) {
  if (!userStore.isLoggedIn) {
    authPrompt.open(() => undefined)
    return
  }
  const wasFavored = favoredIds.value.has(song.id)
  try {
    if (wasFavored) {
      await removeFavorite(song.id)
      favoredIds.value.delete(song.id)
    } else {
      await addFavorite(song.id)
      favoredIds.value.add(song.id)
    }
    favoredIds.value = new Set(favoredIds.value)
  } catch {
    // 拦截器已提示
  }
}

watch(() => route.query.tab, syncFromQuery, { immediate: true })
watch(activeTab, load)

onMounted(() => {
  syncFromQuery()
  load()
  loadFavorites()
})
</script>

<template>
  <div class="page">
    <TopNav />

    <main class="main">
      <header class="head">
        <span class="yx-eyebrow">EXPLORE</span>
        <h1 class="title yx-display">{{ tabMeta[activeTab].label }}</h1>
      </header>

      <!-- 分类切换：胶囊 tab -->
      <nav class="tabs">
        <button
          v-for="t in tabs"
          :key="t"
          class="tab"
          :class="{ 'is-active': t === activeTab }"
          @click="switchTab(t)"
        >
          {{ tabMeta[t].label }}
        </button>
      </nav>

      <div v-loading="loading" class="list">
        <!-- 歌手榜：圆形头像列表行 -->
        <div v-if="activeTab === 'singer'" class="row-grid">
          <SingerRow
            v-for="(singer, i) in singers"
            :key="singer.id"
            :singer="singer"
            :rank="i + 1"
            @open="router.push(`/singer/${singer.id}`)"
          />
        </div>

        <!-- 歌曲榜：左图右文列表行（与首页同款） -->
        <div v-else class="row-grid">
          <SongRow
            v-for="(song, i) in songs"
            :key="song.id"
            :song="song"
            :rank="i + 1"
            :favored="favoredIds.has(song.id)"
            @play="playSong"
            @toggle-fav="toggleFavorite"
          />
        </div>

        <el-empty
          v-if="!loading && (activeTab === 'singer' ? singers.length === 0 : songs.length === 0)"
          description="暂无数据"
        />
      </div>
    </main>
  </div>
</template>

<style scoped>
.main {
  max-width: 1280px;
  margin: 0 auto;
  padding: 28px 24px 60px;
}

.head {
  margin-bottom: 18px;
}

.title {
  font-size: 24px;
  font-weight: 700;
  color: var(--yx-text-main);
  margin-top: 3px;
}

/* 胶囊 tab */
.tabs {
  display: flex;
  gap: 10px;
  margin-bottom: 20px;
}

.tab {
  padding: 8px 20px;
  border: 1px solid var(--yx-hairline);
  border-radius: 999px;
  background: #fff;
  font-size: 14px;
  color: var(--yx-text-secondary);
  cursor: pointer;
  transition: all 0.3s ease;
}

.tab:hover {
  color: var(--yx-accent);
  border-color: var(--yx-accent);
}

.tab.is-active {
  color: #fff;
  background: var(--yx-gradient);
  border-color: transparent;
  font-weight: 600;
}

.list {
  min-height: 300px;
}

/* 与首页同款双列榜单行 */
.row-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 4px 24px;
}

@media (max-width: 760px) {
  .row-grid {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
