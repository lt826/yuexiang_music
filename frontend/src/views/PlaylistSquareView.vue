<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { CaretRight, Headset } from '@element-plus/icons-vue'
import TopNav from '@/components/TopNav.vue'
import { getHotPlaylists, getUserCreatedPlaylists, type PlaylistVO } from '@/api/playlist'

const router = useRouter()
const playlists = ref<PlaylistVO[]>([])
const userPlaylists = ref<PlaylistVO[]>([])
const loading = ref(false)

async function load() {
  loading.value = true
  try {
    const [hot, user] = await Promise.allSettled([
      getHotPlaylists(30),
      getUserCreatedPlaylists(20),
    ])
    // 热门歌单只展示播放量最高的 3 个；乐友歌单展示全部开放（投稿）歌单
    if (hot.status === 'fulfilled') playlists.value = hot.value.slice(0, 3)
    if (user.status === 'fulfilled') userPlaylists.value = user.value
  } finally {
    loading.value = false
  }
}

function openDetail(pl: PlaylistVO) {
  router.push(`/playlist/${pl.id}`)
}

function formatCount(n: number): string {
  if (n >= 100000000) return (n / 100000000).toFixed(1) + '亿'
  if (n >= 10000) return (n / 10000).toFixed(1) + '万'
  return String(n)
}

/** 封面兜底色：无封面歌单的占位底色（六色板经 CVD 六项检查验证） */
const coverPalette = ['#2a78d6', '#eb6834', '#1baf7a', '#eda100', '#e87ba4', '#4a3aa7']
function coverColor(seed: number): string {
  return coverPalette[seed % coverPalette.length] ?? '#2a78d6'
}

onMounted(load)
</script>

<template>
  <div class="page">
    <TopNav />

    <main class="main">
      <header class="head">
        <span class="yx-eyebrow">PLAYLISTS</span>
        <h1 class="title yx-display">歌单广场</h1>
        <p class="sub">发现全站热门歌单与乐友们的精选</p>
      </header>

      <!-- 乐友歌单：用户创建的最新歌单，优先展示（发布即被看见） -->
      <section v-if="userPlaylists.length > 0" class="zone">
        <span class="yx-eyebrow">FRESH DROPS</span>
        <h2 class="zone-title yx-display">乐友歌单</h2>
        <div class="grid">
          <div v-for="pl in userPlaylists" :key="pl.id" class="card" @click="openDetail(pl)">
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
      </section>

      <!-- 官方/热门歌单 -->
      <section class="zone">
        <span class="yx-eyebrow">POPULAR</span>
        <h2 class="zone-title yx-display">热门歌单</h2>
        <div v-loading="loading" class="grid">
          <div v-for="pl in playlists" :key="pl.id" class="card" @click="openDetail(pl)">
            <div class="cover" :style="pl.cover ? undefined : { background: coverColor(pl.id) }">
              <img v-if="pl.cover" class="cover-img" :src="pl.cover" :alt="pl.title" loading="lazy" />
              <span v-else class="cover-tag">{{ pl.tag || '精选' }}</span>
              <span class="stat tabular-nums">
                <el-icon :size="12"><Headset /></el-icon>
                {{ formatCount(pl.playCount) }}
              </span>
              <span class="play">
                <el-icon :size="18"><CaretRight /></el-icon>
              </span>
            </div>
            <p class="card-title">{{ pl.title }}</p>
            <p class="card-meta">
              <el-tag v-if="pl.isOfficial" size="small" effect="plain" type="warning">官方</el-tag>
              {{ pl.tag || '精选' }} · {{ pl.songCount ?? 0 }}首
            </p>
          </div>
        </div>
        <el-empty v-if="!loading && playlists.length === 0" description="暂无歌单" />
      </section>
    </main>
  </div>
</template>

<style scoped>
.main {
  max-width: 1280px;
  margin: 0 auto;
  padding: 28px 20px 60px;
}

.head {
  padding: 6px 0 20px;
}

.title {
  font-size: 26px;
  font-weight: 700;
  color: var(--yx-text-main);
}

.sub {
  margin: 8px 0 4px;
  font-size: 13px;
  color: var(--yx-text-secondary);
}

.zone {
  margin-bottom: 34px;
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
  min-height: 200px;
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

/* 无封面时：中央大字标签 */
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
  display: flex;
  align-items: center;
  gap: 4px;
}
</style>
