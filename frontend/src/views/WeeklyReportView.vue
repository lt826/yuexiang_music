<script setup lang="ts">
/** 听歌周报：最近 7 天播放统计（数据口径：播放次数，listen_history 无时长字段） */
import { onMounted, ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import { CaretRight } from '@element-plus/icons-vue'
import TopNav from '@/components/TopNav.vue'
import SongCover from '@/components/SongCover.vue'
import { getWeeklyReport, type WeeklyReportVO } from '@/api/favorite'
import { useUserStore } from '@/stores/user'
import { usePlayerStore } from '@/stores/player'

const router = useRouter()
const userStore = useUserStore()
const playerStore = usePlayerStore()

const report = ref<WeeklyReportVO | null>(null)
const loading = ref(false)

async function load() {
  loading.value = true
  try {
    report.value = await getWeeklyReport()
  } catch {
    // 拦截器已提示；保持空态
  } finally {
    loading.value = false
  }
}

const isEmpty = computed(() => !report.value || report.value.playCount === 0)

/** TOP5 组成播放队列，从点击项起播（url 缺省由播放器详情接口兜底） */
function playTop(index: number) {
  if (!report.value?.topSongs.length) return
  const queue = report.value.topSongs.map((t) => ({
    id: t.songId,
    name: t.name || `歌曲#${t.songId}`,
    cover: t.cover,
    url: null as string | null,
    style: null as string | null,
    playCount: 0,
    singerId: 0,
    singerName: t.singerName,
  }))
  playerStore.playQueue(queue, index)
}

onMounted(load)
</script>

<template>
  <div class="page">
    <TopNav />

    <main class="main">
      <header class="head">
        <span class="yx-eyebrow">WEEKLY REPORT</span>
        <h1 class="title yx-display">我的听歌周报</h1>
        <p class="sub">最近 7 天 · {{ userStore.nickname }}</p>
      </header>

      <div v-loading="loading" class="body">
        <el-empty v-if="isEmpty && !loading" description="这 7 天还没有听歌记录">
          <template #description>
            <p>这 7 天还没有听歌记录</p>
            <p class="empty-sub">去听听歌，下周这里就有你的故事</p>
          </template>
          <el-button type="primary" @click="router.push('/home')">回首页发现音乐</el-button>
        </el-empty>

        <template v-else-if="report">
          <!-- 核心数字 -->
          <div class="stats">
            <div class="stat">
              <div class="num">{{ report.playCount }}</div>
              <div class="label">播放次数</div>
            </div>
            <div class="stat">
              <div class="num">{{ report.songCount }}</div>
              <div class="label">听过歌曲</div>
            </div>
            <div class="stat">
              <div class="num">{{ report.activeDays }}</div>
              <div class="label">活跃天数</div>
            </div>
          </div>

          <div class="panels">
            <!-- 最爱歌手 -->
            <section v-if="report.topSingers.length" class="panel">
              <h2 class="panel-title">最爱的歌手</h2>
              <ol class="singer-list">
                <li v-for="(s, i) in report.topSingers" :key="s.name" class="singer">
                  <span class="rank" :class="`rank-${i + 1}`">{{ i + 1 }}</span>
                  <span class="singer-name">{{ s.name }}</span>
                  <span class="count">{{ s.count }} 次</span>
                </li>
              </ol>
            </section>

            <!-- 单曲 TOP5 -->
            <section v-if="report.topSongs.length" class="panel">
              <h2 class="panel-title">单曲循环 TOP5</h2>
              <ol class="song-list">
                <li
                  v-for="(t, i) in report.topSongs"
                  :key="t.songId"
                  class="song"
                  :class="{ 'is-playing': playerStore.currentSong?.id === t.songId }"
                  @click="playTop(i)"
                >
                  <span class="rank" :class="{ 'is-top3': i < 3 }">{{ i + 1 }}</span>
                  <span class="cover">
                    <SongCover :cover="t.cover" :name="t.name || ''" :seed="t.songId" radius="4px" />
                    <span class="cover-play" title="播放">
                      <el-icon :size="18"><CaretRight /></el-icon>
                    </span>
                  </span>
                  <span class="info">
                    <span class="name">{{ t.name || `歌曲#${t.songId}` }}</span>
                    <span class="singer">{{ t.singerName || '未知歌手' }}</span>
                  </span>
                  <span class="count">{{ t.count }} 次</span>
                </li>
              </ol>
              <p class="tip">点击歌曲可直接播放</p>
            </section>
          </div>
        </template>
      </div>
    </main>
  </div>
</template>

<style scoped>
.page {
  min-height: 100vh;
  background: var(--yx-bg, #f5f6fa);
}

.main {
  max-width: 880px;
  margin: 0 auto;
  padding: 28px 24px 60px;
}

.head {
  margin-bottom: 20px;
}

.title {
  font-size: 26px;
  font-weight: 700;
  margin: 0;
}

.sub {
  margin: 6px 0 0;
  color: #9aa0ab;
  font-size: 13px;
}

.empty-sub {
  color: #b4bac6;
  font-size: 12px;
  margin-top: 4px;
}

/* 核心数字 */
.stats {
  display: flex;
  background: #fff;
  border-radius: 12px;
  padding: 26px 0;
  margin-bottom: 18px;
}

.stat {
  flex: 1;
  text-align: center;
}

.stat + .stat {
  border-left: 1px solid #eef0f4;
}

.num {
  font-size: 34px;
  font-weight: 700;
  color: #2a78d6;
}

.label {
  margin-top: 4px;
  color: #9aa0ab;
  font-size: 13px;
}

/* 区块 */
.panels {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 18px;
}

@media (max-width: 720px) {
  .panels {
    grid-template-columns: 1fr;
  }
}

.panel {
  background: #fff;
  border-radius: 12px;
  padding: 20px;
}

.panel-title {
  margin: 0 0 14px;
  font-size: 16px;
  font-weight: 700;
}

/* 歌手榜 */
.singer-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.singer {
  display: flex;
  align-items: center;
  gap: 12px;
}

.rank {
  width: 26px;
  height: 26px;
  border-radius: 6px;
  background: #eef1f6;
  color: #9aa0ab;
  font-size: 13px;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.rank.rank-1 {
  background: #fdecd2;
  color: #d98b1f;
}

.rank.rank-2 {
  background: #e8edf5;
  color: #7d8aa5;
}

.rank.rank-3 {
  background: #f9e8dd;
  color: #c4713a;
}

.singer-name {
  flex: 1;
  min-width: 0;
  font-size: 15px;
  font-weight: 600;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.count {
  color: #9aa0ab;
  font-size: 13px;
  flex-shrink: 0;
}

/* 单曲 TOP */
.song-list {
  list-style: none;
  margin: 0;
  padding: 0;
}

.song {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 8px 4px;
  border-radius: 8px;
  cursor: pointer;
}

.song:hover {
  background: #f6f7fa;
}

.song.is-playing .name {
  color: #2a78d6;
}

.rank.is-top3 {
  background: transparent;
  color: #2a78d6;
}

.cover {
  position: relative;
  width: 44px;
  height: 44px;
  flex-shrink: 0;
}

.cover-play {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(0, 0, 0, 0.35);
  color: #fff;
  opacity: 0;
  transition: opacity 0.15s;
}

.song:hover .cover-play {
  opacity: 1;
}

.info {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.name {
  font-size: 14px;
  font-weight: 600;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.singer {
  color: #9aa0ab;
  font-size: 12px;
}

.song .count {
  font-size: 13px;
}

.tip {
  margin: 12px 0 0;
  text-align: center;
  color: #b4bac6;
  font-size: 12px;
}
</style>
