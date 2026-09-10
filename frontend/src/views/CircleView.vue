<script setup lang="ts">
import { onMounted, ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Search, Headset } from '@element-plus/icons-vue'
import TopNav from '@/components/TopNav.vue'
import { useUserStore } from '@/stores/user'
import {
  getSquare,
  getMyCircles,
  createCircle,
  joinCircle,
  getLiveRooms,
  type CircleVO,
  type ListenRoomVO,
} from '@/api/circle'

const router = useRouter()
const userStore = useUserStore()

const circles = ref<CircleVO[]>([])
const myCircles = ref<CircleVO[]>([])
const liveRooms = ref<ListenRoomVO[]>([])
const loading = ref(false)
const keyword = ref('')

/** 广场展示：审核通过的圈子 */
const publicCircles = computed(() => circles.value)

async function load() {
  loading.value = true
  try {
    const loadSquare = getSquare(keyword.value || undefined).then((v) => {
      circles.value = v
    })
    const loadMine = userStore.isLoggedIn
      ? getMyCircles().then((v) => {
          myCircles.value = v
        })
      : Promise.resolve()
    const loadLive = getLiveRooms(6).then((v) => {
      liveRooms.value = v
    })
    await Promise.all([loadSquare, loadMine, loadLive])
  } catch {
    // 拦截器已提示
  } finally {
    loading.value = false
  }
}

function search() {
  load()
}

// ===== 创建圈子（名称审核闭环提示） =====
const createVisible = ref(false)
const submitting = ref(false)
const form = ref({ name: '', description: '' })

function openCreate() {
  if (!userStore.isLoggedIn) {
    router.push({ name: 'login', query: { redirect: '/circle' } })
    return
  }
  form.value = { name: '', description: '' }
  createVisible.value = true
}

async function submitCreate() {
  if (!form.value.name.trim()) {
    ElMessage.warning('请填写圈子名称')
    return
  }
  submitting.value = true
  try {
    await createCircle({
      name: form.value.name.trim(),
      description: form.value.description.trim() || undefined,
    })
    createVisible.value = false
    ElMessage.success('已提交创建申请，名称审核通过后圈子将出现在广场')
    await load()
  } catch {
    // 拦截器已提示
  } finally {
    submitting.value = false
  }
}

// ===== 加入圈子 =====
async function join(c: CircleVO) {
  if (!userStore.isLoggedIn) {
    router.push({ name: 'login', query: { redirect: '/circle' } })
    return
  }
  try {
    await joinCircle(c.id)
    ElMessage.success(`已加入「${c.name}」`)
    await load()
  } catch {
    // 拦截器已提示
  }
}

function enter(c: CircleVO) {
  router.push({ name: 'circle-detail', params: { id: c.id } })
}

function enterListen(room: ListenRoomVO) {
  router.push({ name: 'listen-room', params: { roomId: room.roomId } })
}

/** 从广场卡片的"一起听中"徽标直接进房间 */
function enterListenId(roomId: number) {
  router.push({ name: 'listen-room', params: { roomId: String(roomId) } })
}

/** 状态徽标：我的圈子里待审核/驳回的展示 */
function statusLabel(c: CircleVO): string {
  if (c.status === 0) return '名称审核中'
  if (c.status === 2) return `未通过：${c.rejectReason || '不符合规范'}`
  return ''
}

function formatTime(t: string): string {
  if (!t) return ''
  return t.slice(0, 10)
}

/** 封面兜底渐变（对齐全站六色板） */
function coverGradient(seed: number): string {
  const palette = [
    'linear-gradient(135deg, #86b6ef, #2a78d6)',
    'linear-gradient(135deg, #f3b08a, #eb6834)',
    'linear-gradient(135deg, #8ad0bc, #199e70)',
    'linear-gradient(135deg, #f2cd86, #c98500)',
    'linear-gradient(135deg, #c3b0ec, #7a68cf)',
    'linear-gradient(135deg, #eda0c0, #c8588b)',
  ]
  return palette[seed % palette.length] ?? palette[0]!
}

onMounted(load)
</script>

<template>
  <div class="page">
    <TopNav />
    <main class="main">
      <header class="head">
        <span class="yx-eyebrow">CIRCLE</span>
        <h1 class="title yx-display">乐坊</h1>
        <p class="sub">同好聚一堂：找圈子、发帖子、一起听歌聊天</p>
      </header>

      <!-- 一起听进行中（全站） -->
      <section v-if="liveRooms.length" class="live-strip">
        <div class="live-strip-head">
          <el-icon :size="16" class="live-icon"><Headset /></el-icon>
          <span class="live-title">正在一起听</span>
        </div>
        <div class="live-list">
          <button v-for="room in liveRooms" :key="room.roomId" class="live-chip" @click="enterListen(room)">
            <span class="live-dot" />
            <span class="live-theme">{{ room.theme || room.circleName || `房间 #${room.roomId}` }}</span>
            <span class="live-count tabular-nums">{{ room.memberCount }} 人在线</span>
          </button>
        </div>
      </section>

      <!-- 我的圈子 -->
      <section v-if="myCircles.length" class="block">
        <h2 class="block-title">我的圈子</h2>
        <div class="my-grid">
          <div
            v-for="c in myCircles"
            :key="c.id"
            class="my-card"
            :class="{ 'my-card--review': c.status !== 1 }"
            @click="enter(c)"
          >
            <div class="my-card-top">
              <span class="my-name">{{ c.name }}</span>
              <el-tag v-if="c.myRole === 'OWNER'" size="small" type="warning" effect="plain">圈主</el-tag>
            </div>
            <p class="my-desc">{{ c.description || '暂无简介' }}</p>
            <p v-if="c.status !== 1" class="my-status">
              <el-tag size="small" :type="c.status === 0 ? 'warning' : 'danger'" effect="light">
                {{ statusLabel(c) }}
              </el-tag>
            </p>
          </div>
        </div>
      </section>

      <!-- 广场：搜索 + 创建 -->
      <section class="block">
        <div class="block-head">
          <h2 class="block-title">发现圈子</h2>
          <div class="head-actions">
            <el-input
              v-model="keyword"
              placeholder="搜索圈子名称"
              clearable
              class="search-input"
              :prefix-icon="Search"
              @keyup.enter="search"
              @clear="search"
            />
            <el-button type="primary" :icon="Plus" @click="openCreate">创建圈子</el-button>
          </div>
        </div>

        <div v-loading="loading" class="grid">
          <div v-for="c in publicCircles" :key="c.id" class="card" @click="enter(c)">
            <div class="card-cover" :style="{ background: coverGradient(c.id) }">
              <img v-if="c.cover" :src="c.cover" :alt="c.name" class="card-img" />
              <span v-else class="card-glyph">{{ c.name.slice(0, 1) }}</span>
              <span v-if="c.liveRoom" class="live-badge" @click.stop="enterListenId(c.liveRoomId!)">
                <span class="live-dot" />
                一起听中
              </span>
            </div>
            <div class="card-body">
              <p class="card-name">{{ c.name }}</p>
              <p class="card-desc">{{ c.description || '暂无简介' }}</p>
              <div class="card-foot">
                <span class="card-meta tabular-nums">{{ c.memberCount }} 成员</span>
                <span class="card-dot">·</span>
                <span class="card-meta tabular-nums">{{ c.postCount }} 帖子</span>
                <span class="flex-spacer" />
                <el-button
                  v-if="!c.joined"
                  type="primary"
                  size="small"
                  round
                  @click.stop="join(c)"
                >
                  加入
                </el-button>
                <el-tag v-else size="small" type="success" effect="plain">已加入</el-tag>
              </div>
            </div>
          </div>
        </div>
        <el-empty
          v-if="!loading && publicCircles.length === 0"
          :description="keyword ? '没有找到相关圈子' : '还没有圈子，创建第一个吧'"
        />
      </section>
    </main>

    <!-- 创建圈子弹窗 -->
    <el-dialog v-model="createVisible" title="创建圈子" width="480px">
      <el-alert
        type="info"
        :closable="false"
        show-icon
        title="创建后名称需管理员审核，审核结果将通过站内信通知您"
        class="create-tip"
      />
      <el-form label-position="top">
        <el-form-item label="圈子名称" required>
          <el-input
            v-model="form.name"
            maxlength="50"
            show-word-limit
            placeholder="如：周杰伦粉丝、摇滚乐、深夜助眠"
          />
        </el-form-item>
        <el-form-item label="圈子简介">
          <el-input
            v-model="form.description"
            type="textarea"
            :rows="3"
            maxlength="500"
            show-word-limit
            placeholder="一句话介绍这个圈子"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitCreate">提交审核</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.main {
  max-width: 1160px;
  margin: 0 auto;
  padding: 28px 20px 60px;
}

.head {
  margin-bottom: 22px;
}

.title {
  font-size: 30px;
  margin: 6px 0 4px;
}

.sub {
  color: var(--yx-text-secondary);
  font-size: 14px;
}

/* ===== 正在一起听横条 ===== */
.live-strip {
  border: 1px solid var(--yx-hairline);
  background: #fff;
  border-radius: 10px;
  padding: 12px 16px;
  margin-bottom: 24px;
}

.live-strip-head {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 10px;
}

.live-icon {
  color: #eb6834;
}

.live-title {
  font-size: 13.5px;
  font-weight: 600;
}

.live-list {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.live-chip {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  border: 1px solid var(--yx-hairline);
  background: #fafbfd;
  border-radius: 999px;
  padding: 6px 14px;
  cursor: pointer;
  transition: border-color 0.15s ease, box-shadow 0.15s ease;
}

.live-chip:hover {
  border-color: var(--yx-accent);
  box-shadow: 0 2px 8px rgba(28, 92, 171, 0.12);
}

.live-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: #eb6834;
  box-shadow: 0 0 6px rgba(235, 104, 52, 0.8);
}

.live-theme {
  font-size: 13px;
  max-width: 220px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.live-count {
  font-size: 12px;
  color: var(--yx-text-secondary);
}

/* ===== 区块 ===== */
.block {
  margin-bottom: 28px;
}

.block-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 14px;
  gap: 12px;
  flex-wrap: wrap;
}

.block-title {
  font-size: 18px;
  font-weight: 700;
}

.head-actions {
  display: flex;
  align-items: center;
  gap: 10px;
}

.search-input {
  width: 240px;
}

/* ===== 我的圈子 ===== */
.my-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(250px, 1fr));
  gap: 12px;
}

.my-card {
  background: #fff;
  border: 1px solid var(--yx-hairline);
  border-radius: 10px;
  padding: 14px 16px;
  cursor: pointer;
  transition: border-color 0.15s ease;
}

.my-card:hover {
  border-color: var(--yx-accent);
}

.my-card--review {
  background: #fffdf5;
}

.my-card-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.my-name {
  font-weight: 600;
  font-size: 14.5px;
}

.my-desc {
  font-size: 12.5px;
  color: var(--yx-text-secondary);
  margin-top: 4px;
  display: -webkit-box;
  -webkit-line-clamp: 1;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.my-status {
  margin-top: 8px;
}

/* ===== 广场卡片 ===== */
.grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(250px, 1fr));
  gap: 16px;
  min-height: 120px;
}

.card {
  background: #fff;
  border: 1px solid var(--yx-hairline);
  border-radius: 10px;
  overflow: hidden;
  cursor: pointer;
  transition: transform 0.15s ease, box-shadow 0.15s ease;
}

.card:hover {
  transform: translateY(-2px);
  box-shadow: 0 6px 18px rgba(28, 92, 171, 0.1);
}

.card-cover {
  height: 110px;
  display: flex;
  align-items: center;
  justify-content: center;
  position: relative;
}

.card-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.card-glyph {
  font-size: 40px;
  color: rgba(255, 255, 255, 0.9);
  font-weight: 700;
}

/* 广场卡片"一起听中"徽标 */
.live-badge {
  position: absolute;
  left: 10px;
  bottom: 10px;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 4px 10px;
  border-radius: 999px;
  font-size: 12px;
  color: #fff;
  background: rgba(20, 30, 50, 0.55);
  backdrop-filter: blur(4px);
  cursor: pointer;
}

.live-badge:hover {
  background: rgba(20, 30, 50, 0.75);
}

.card-body {
  padding: 12px 14px 14px;
}

.card-name {
  font-weight: 600;
  font-size: 15px;
}

.card-desc {
  font-size: 12.5px;
  color: var(--yx-text-secondary);
  margin: 4px 0 10px;
  min-height: 34px;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.card-foot {
  display: flex;
  align-items: center;
  gap: 6px;
}

.card-meta {
  font-size: 12px;
  color: var(--yx-text-secondary);
}

.card-dot {
  color: var(--yx-text-disabled);
  font-size: 12px;
}

.flex-spacer {
  flex: 1;
}

.create-tip {
  margin-bottom: 14px;
}
</style>
