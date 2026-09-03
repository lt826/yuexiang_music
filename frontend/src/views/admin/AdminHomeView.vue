<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { CaretRight, Headset, Star, User, Mic, Bell } from '@element-plus/icons-vue'
import {
  adminUserList,
  adminPendingSingers,
  adminPendingSongs,
  type AdminUserVO,
} from '@/api/admin'
import { getHotChart, type SongVO } from '@/api/home'
import { pendingStore } from '@/stores/pending'

const router = useRouter()

/** KPI 统计 */
const users = ref<AdminUserVO[]>([])
const hotSongs = ref<SongVO[]>([])
const loading = ref(false)

async function loadStats() {
  loading.value = true
  try {
    const [userRes, singerRes, songRes, chartRes] = await Promise.allSettled([
      adminUserList(undefined, 1, 100),
      adminPendingSingers(),
      adminPendingSongs(),
      getHotChart(8),
    ])
    if (userRes.status === 'fulfilled') {
      users.value = userRes.value.items
      userTotalDisplay.value = userRes.value.total
    }
    if (singerRes.status === 'fulfilled') {
      pendingStore.pendingSingerCount.value = singerRes.value.length
    }
    if (songRes.status === 'fulfilled') {
      pendingStore.pendingSongCount.value = songRes.value.length
    }
    if (chartRes.status === 'fulfilled') hotSongs.value = chartRes.value
  } finally {
    loading.value = false
  }
}

/** KPI 行：单一现值 + 说明（stat tile 契约，不做成图表） */
const stats = computed(() => {
  const totalUsers = users.value.length
  const singerCount = users.value.filter((u) => u.role === 'SINGER').length
  const activeCount = users.value.filter((u) => u.status === 1).length
  return [
    { label: '注册用户', value: userTotalDisplay.value, sub: `歌手 ${singerCount} 人（前100）`, icon: User, to: '/admin/manage?tab=users' },
    { label: '待审歌手', value: pendingStore.pendingSingerCount.value, sub: '需要尽快处理', icon: Mic, to: '/admin/manage?tab=singers' },
    { label: '待审歌曲', value: pendingStore.pendingSongCount.value, sub: '需要尽快处理', icon: Star, to: '/admin/manage?tab=songs' },
    { label: '活跃账号', value: activeCount, sub: `前100中 ${activeCount} 人`, icon: Headset, to: '/admin/manage?tab=users' },
  ]
})

/** 用户总数（分页接口取 total 字段，KPI 显示全量而非前100） */
const userTotalDisplay = ref(0)

/** 热门榜水平条形图数据（单一系列 → 单一色相，不做值渐变着色） */
const chartMax = computed(() => Math.max(...hotSongs.value.map((s) => s.playCount), 1))

function formatCount(n: number): string {
  if (n >= 100000000) return (n / 100000000).toFixed(1) + '亿'
  if (n >= 10000) return (n / 10000).toFixed(1) + '万'
  return String(n)
}

/** 悬停提示（条形自身即命中目标） */
const hoveredBar = ref<number | null>(null)

function goAudit() {
  router.push('/admin/manage?tab=singers')
}

onMounted(loadStats)
</script>

<template>
  <div v-loading="loading" class="admin-home">
    <!-- KPI 行 -->
    <section class="kpi-row">
      <div
        v-for="s in stats"
        :key="s.label"
        class="kpi-tile"
        role="button"
        tabindex="0"
        @click="router.push(s.to)"
        @keyup.enter="router.push(s.to)"
      >
        <div class="kpi-icon"><el-icon :size="20"><component :is="s.icon" /></el-icon></div>
        <div class="kpi-body">
          <span class="kpi-label">{{ s.label }}</span>
          <span class="kpi-value">{{ s.value }}</span>
          <span class="kpi-sub">{{ s.sub }}</span>
        </div>
      </div>
    </section>

    <div class="panel-grid">
      <!-- 热门歌曲播放量：水平条形图（单系列单色相） -->
      <section class="panel chart-panel">
        <header class="panel-head">
          <h2 class="panel-title">热门歌曲播放量</h2>
          <span class="panel-note">全站 TOP {{ hotSongs.length }} · 单系列</span>
        </header>
        <ul class="bar-chart">
          <li
            v-for="(song, i) in hotSongs"
            :key="song.id"
            class="bar-row"
            :class="{ 'is-hover': hoveredBar === i }"
            @mouseenter="hoveredBar = i"
            @mouseleave="hoveredBar = null"
          >
            <span class="bar-name" :title="song.name">{{ song.name }}</span>
            <div class="bar-track">
              <div
                class="bar-fill"
                :style="{ width: Math.max(2, (song.playCount / chartMax) * 100) + '%' }"
              />
            </div>
            <span class="bar-value tabular-nums">{{ formatCount(song.playCount) }}</span>
          </li>
        </ul>
        <p v-if="hotSongs.length === 0" class="chart-empty">暂无上架歌曲数据</p>
      </section>

      <!-- 待办与快捷入口 -->
      <div class="side-col">
        <section class="panel todo-panel">
          <header class="panel-head">
            <h2 class="panel-title">待办事项</h2>
          </header>
          <div class="todo-list">
            <button class="todo-item" @click="goAudit">
              <span class="todo-icon todo-icon--audit"><el-icon :size="16"><Mic /></el-icon></span>
              <span class="todo-text">
                <span class="todo-name">歌手认证审核</span>
                <span class="todo-sub">申请人正在等待通知</span>
              </span>
              <span class="todo-count tabular-nums" :class="{ 'is-zero': pendingStore.pendingSingerCount.value === 0 }">
                {{ pendingStore.pendingSingerCount.value }}
              </span>
              <el-icon class="todo-arrow"><CaretRight /></el-icon>
            </button>
            <button class="todo-item" @click="router.push('/admin/manage?tab=songs')">
              <span class="todo-icon todo-icon--song"><el-icon :size="16"><Star /></el-icon></span>
              <span class="todo-text">
                <span class="todo-name">歌曲上架审核</span>
                <span class="todo-sub">通过后立即对全站可见</span>
              </span>
              <span class="todo-count tabular-nums" :class="{ 'is-zero': pendingStore.pendingSongCount.value === 0 }">
                {{ pendingStore.pendingSongCount.value }}
              </span>
              <el-icon class="todo-arrow"><CaretRight /></el-icon>
            </button>
            <button class="todo-item" @click="router.push('/admin/manage?tab=notice')">
              <span class="todo-icon todo-icon--notice"><el-icon :size="16"><Bell /></el-icon></span>
              <span class="todo-text">
                <span class="todo-name">发布公告</span>
                <span class="todo-sub">向全部用户推送系统通知</span>
              </span>
              <el-icon class="todo-arrow"><CaretRight /></el-icon>
            </button>
          </div>
        </section>

        <section class="panel">
          <header class="panel-head">
            <h2 class="panel-title">最新注册用户</h2>
            <span class="panel-note">前 5 位</span>
          </header>
          <ul class="new-user-list">
            <li v-for="u in users.slice(0, 5)" :key="u.id" class="new-user">
              <el-avatar :size="32" class="new-user-avatar">{{ (u.nickname || u.username).slice(0, 1) }}</el-avatar>
              <span class="new-user-info">
                <span class="new-user-name">{{ u.nickname || u.username }}</span>
                <span class="new-user-mail">{{ u.email || '未填写邮箱' }}</span>
              </span>
              <el-tag
                size="small"
                effect="plain"
                :type="u.role === 'ADMIN' ? 'danger' : u.role === 'SINGER' ? 'warning' : 'info'"
              >
                {{ u.role === 'ADMIN' ? '管理员' : u.role === 'SINGER' ? '歌手' : '用户' }}
              </el-tag>
            </li>
          </ul>
          <p v-if="users.length === 0" class="chart-empty">暂无用户数据</p>
        </section>
      </div>
    </div>
  </div>
</template>

<style scoped>
.admin-home {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

/* ===== KPI 行（stat tile 契约：标签/数值/说明） ===== */
.kpi-row {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
}

@media (max-width: 1100px) {
  .kpi-row {
    grid-template-columns: repeat(2, 1fr);
  }
}

.kpi-tile {
  display: flex;
  align-items: center;
  gap: 14px;
  background: #fff;
  border: 1px solid var(--yx-hairline);
  border-radius: 10px;
  padding: 18px 20px;
  cursor: pointer;
  transition: box-shadow 0.15s ease, transform 0.15s ease;
}

.kpi-tile:hover {
  box-shadow: var(--yx-shadow-card);
  transform: translateY(-1px);
}

.kpi-icon {
  width: 44px;
  height: 44px;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 10px;
  color: #fff;
  background: var(--yx-gradient);
}

.kpi-body {
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.kpi-label {
  font-size: 13px;
  color: var(--yx-text-secondary);
}

/* 大数值用比例数字（非等宽），字号充分放大 */
.kpi-value {
  font-size: 30px;
  font-weight: 700;
  line-height: 1.15;
  color: var(--yx-text-main);
  margin-top: 2px;
}

.kpi-sub {
  font-size: 12px;
  color: var(--yx-text-disabled);
  margin-top: 2px;
}

/* ===== 面板 ===== */
.panel-grid {
  display: grid;
  grid-template-columns: 1.6fr 1fr;
  gap: 16px;
  align-items: start;
}

@media (max-width: 1100px) {
  .panel-grid {
    grid-template-columns: 1fr;
  }
}

.side-col {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.panel {
  background: #fff;
  border: 1px solid var(--yx-hairline);
  border-radius: 10px;
  padding: 18px 20px;
}

.panel-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  margin-bottom: 16px;
}

.panel-title {
  font-size: 16px;
  font-weight: 600;
}

.panel-note {
  font-size: 12px;
  color: var(--yx-text-disabled);
}

/* ===== 条形图（细条、4px 圆角数据端、单色相） ===== */
.bar-chart {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.bar-row {
  display: grid;
  grid-template-columns: 110px 1fr 52px;
  align-items: center;
  gap: 12px;
  padding: 3px 4px;
  border-radius: 6px;
}

.bar-row.is-hover {
  background: var(--yx-accent-weak);
}

.bar-name {
  font-size: 13px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: var(--yx-text-main);
}

.bar-track {
  height: 16px;
  border-radius: 4px 4px 4px 4px;
  background: #eef1f6;
  overflow: hidden;
}

.bar-fill {
  height: 100%;
  border-radius: 0 4px 4px 0;
  background: #2a78d6;
  transition: width 0.4s ease;
}

.bar-row.is-hover .bar-fill {
  background: #1c5cab;
}

.bar-value {
  font-size: 12.5px;
  color: var(--yx-text-secondary);
  text-align: right;
}

.chart-empty {
  padding: 26px 0;
  text-align: center;
  font-size: 13px;
  color: var(--yx-text-disabled);
}

/* ===== 待办 ===== */
.todo-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.todo-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 14px;
  border: 1px solid var(--yx-hairline);
  border-radius: 8px;
  background: #fafbfd;
  cursor: pointer;
  text-align: left;
  width: 100%;
  font-family: inherit;
  transition: border-color 0.15s ease, background 0.15s ease;
}

.todo-item:hover {
  border-color: var(--yx-accent);
  background: var(--yx-accent-weak);
}

.todo-icon {
  width: 34px;
  height: 34px;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 8px;
  color: #fff;
}

.todo-icon--audit { background: #3987e5; }
.todo-icon--song { background: #eb6834; }
.todo-icon--notice { background: #1baf7a; }

.todo-text {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.todo-name {
  font-size: 14px;
  font-weight: 600;
  color: var(--yx-text-main);
}

.todo-sub {
  font-size: 12px;
  color: var(--yx-text-disabled);
}

.todo-count {
  font-size: 20px;
  font-weight: 700;
  color: var(--yx-red);
}

.todo-count.is-zero {
  color: var(--yx-text-disabled);
}

.todo-arrow {
  color: var(--yx-text-disabled);
  font-size: 12px;
}

/* ===== 最新用户 ===== */
.new-user-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.new-user {
  display: flex;
  align-items: center;
  gap: 10px;
}

.new-user-avatar {
  flex-shrink: 0;
  background: var(--yx-accent-weak);
  color: var(--yx-red-deep);
  font-weight: 600;
}

.new-user-info {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 1px;
}

.new-user-name {
  font-size: 13.5px;
  font-weight: 500;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.new-user-mail {
  font-size: 12px;
  color: var(--yx-text-disabled);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
