<script setup lang="ts">
import { nextTick, onMounted, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, View } from '@element-plus/icons-vue'
import {
  adminUserList,
  adminUserCreate,
  adminUserUpdate,
  adminUserDelete,
  adminUserStatus,
  adminPendingSingers,
  adminAuditSinger,
  adminPendingSongs,
  adminAuditSong,
  adminSongList,
  adminSongOffline,
  adminBroadcast,
  adminBannerList,
  adminBannerSave,
  adminBannerDelete,
  type AdminUserVO,
  type BannerVO,
} from '@/api/admin'
import { getHotChart, type SongVO } from '@/api/home'
import type { SingerVO } from '@/api/singer'
import { uploadFile } from '@/api/singer'
import { pendingStore } from '@/stores/pending'

const route = useRoute()

const activeTab = ref('users')

// URL ?tab= 直接定位（首页待办跳转入口）
function syncFromQuery() {
  const t = route.query.tab
  if (typeof t === 'string' && ['users', 'singers', 'songs', 'all-songs', 'banners', 'notice'].includes(t)) {
    activeTab.value = t
  }
}
watch(() => route.query.tab, syncFromQuery, { immediate: true })

// ===== 用户管理（增删改查 + 分页 + 关键字） =====
const users = ref<AdminUserVO[]>([])
const userKeyword = ref('')
const usersLoading = ref(false)
const userPage = ref(1)
const userTotal = ref(0)
const userPageSize = 10

async function loadUsers(page = userPage.value) {
  usersLoading.value = true
  userPage.value = page
  try {
    const data = await adminUserList(userKeyword.value || undefined, page, userPageSize)
    users.value = data.items
    userTotal.value = data.total
  } catch {
    // 拦截器已提示
  } finally {
    usersLoading.value = false
  }
}

function searchUsers() {
  loadUsers(1)
}

/** 新增/编辑弹窗 */
const userDialogVisible = ref(false)
const userDialogMode = ref<'create' | 'edit'>('create')
const userSubmitting = ref(false)
const userForm = reactive({
  id: 0,
  username: '',
  password: '',
  nickname: '',
  email: '',
  phone: '',
  role: 'USER',
  status: 1,
})

function openUserCreate() {
  userDialogMode.value = 'create'
  Object.assign(userForm, {
    id: 0, username: '', password: '', nickname: '', email: '', phone: '', role: 'USER', status: 1,
  })
  userDialogVisible.value = true
}

function openUserEdit(user: AdminUserVO) {
  userDialogMode.value = 'edit'
  Object.assign(userForm, {
    id: user.id,
    username: user.username,
    password: '',
    nickname: user.nickname || '',
    email: user.email || '',
    phone: user.phone || '',
    role: user.role,
    status: user.status,
  })
  userDialogVisible.value = true
}

async function handleUserSave() {
  if (userDialogMode.value === 'create') {
    if (!userForm.username.trim() || !userForm.password || !userForm.email.trim()) {
      ElMessage.warning('用户名、密码与邮箱为必填')
      return
    }
    userSubmitting.value = true
    try {
      await adminUserCreate({
        username: userForm.username.trim(),
        password: userForm.password,
        email: userForm.email.trim(),
        nickname: userForm.nickname.trim() || undefined,
        phone: userForm.phone.trim() || undefined,
        role: userForm.role,
      })
      ElMessage.success('用户已创建')
      userDialogVisible.value = false
      await loadUsers(1)
    } catch {
      // 拦截器已提示
    } finally {
      userSubmitting.value = false
    }
  } else {
    userSubmitting.value = true
    try {
      await adminUserUpdate(userForm.id, {
        nickname: userForm.nickname.trim(),
        email: userForm.email.trim(),
        phone: userForm.phone.trim(),
        role: userForm.role,
        status: userForm.status,
        // 留空不重置密码
        password: userForm.password || undefined,
      })
      ElMessage.success('用户已更新')
      userDialogVisible.value = false
      await loadUsers()
    } catch {
      // 拦截器已提示
    } finally {
      userSubmitting.value = false
    }
  }
}

async function handleUserDelete(user: AdminUserVO) {
  try {
    await ElMessageBox.confirm(
      `确认删除用户「${user.username}」？该操作为逻辑删除，数据不会物理清除。`,
      '删除确认',
      { type: 'warning' },
    )
  } catch {
    return
  }
  try {
    await adminUserDelete(user.id)
    ElMessage.success('已删除')
    // 当前页删空后回退一页
    if (users.value.length === 1 && userPage.value > 1) {
      await loadUsers(userPage.value - 1)
    } else {
      await loadUsers()
    }
  } catch {
    // 拦截器已提示
  }
}

async function toggleUser(user: AdminUserVO) {
  const action = user.status === 1 ? '禁用' : '启用'
  try {
    await ElMessageBox.confirm(`确认${action}用户「${user.username}」？`, `${action}确认`, { type: 'warning' })
  } catch {
    return
  }
  try {
    await adminUserStatus(user.id, user.status !== 1)
    ElMessage.success(`已${action}`)
    await loadUsers()
  } catch {
    // 拦截器已提示
  }
}

// ===== 歌手审核 =====
const pendingSingers = ref<SingerVO[]>([])
const singersLoading = ref(false)

async function loadSingers() {
  singersLoading.value = true
  try {
    pendingSingers.value = await adminPendingSingers()
    pendingStore.pendingSingerCount.value = pendingSingers.value.length
  } catch {
    // 拦截器已提示
  } finally {
    singersLoading.value = false
  }
}

async function auditSinger(singer: SingerVO, approve: boolean) {
  let reason: string | undefined
  if (!approve) {
    try {
      const { value } = await ElMessageBox.prompt('请填写驳回原因', '驳回歌手认证', {
        inputPlaceholder: '将通知申请人',
        inputValidator: (v: string) => !!v?.trim() || '请填写原因',
      })
      reason = value
    } catch {
      return
    }
  }
  try {
    await adminAuditSinger(singer.id, approve, reason)
    ElMessage.success(approve ? '已通过认证' : '已驳回')
    await loadSingers()
  } catch {
    // 拦截器已提示
  }
}

// ===== 歌曲审核 =====
const pendingSongs = ref<SongVO[]>([])
const songsLoading = ref(false)

/** 试听抽屉当前歌曲 */
const detailSong = ref<SongVO | null>(null)
const detailVisible = ref(false)
const previewAudio = ref<HTMLAudioElement | null>(null)

function openDetail(song: SongVO) {
  detailSong.value = song
  detailVisible.value = true
  nextTick(() => {
    if (previewAudio.value) {
      previewAudio.value.load()
    }
  })
}

/** LRC → 纯文本行（去掉时间标签，审核阅读用） */
function lyricText(raw: string | null | undefined): string {
  if (!raw) return '（未上传歌词）'
  const tag = /\[\d{1,2}:\d{1,2}(?:\.\d{1,3})?\]/g
  return raw
    .split(/\r?\n/)
    .map((l) => l.replace(tag, '').trim())
    .filter(Boolean)
    .join('\n') || '（歌词为空）'
}

async function loadSongs() {
  songsLoading.value = true
  try {
    pendingSongs.value = await adminPendingSongs()
    pendingStore.pendingSongCount.value = pendingSongs.value.length
  } catch {
    // 拦截器已提示
  } finally {
    songsLoading.value = false
  }
}

async function auditSong(song: SongVO, approve: boolean) {
  let reason: string | undefined
  if (!approve) {
    try {
      const { value } = await ElMessageBox.prompt('请填写驳回原因', '驳回歌曲', {
        inputPlaceholder: '将通知歌手',
        inputValidator: (v: string) => !!v?.trim() || '请填写原因',
      })
      reason = value
    } catch {
      return
    }
  }
  try {
    await adminAuditSong(song.id, approve, reason)
    ElMessage.success(approve ? '已通过，歌曲上架' : '已驳回')
    await loadSongs()
  } catch {
    // 拦截器已提示
  }
}

// ===== 全量歌曲管理（全部歌曲：搜索/分页/强制下架） =====
const allSongs = ref<SongVO[]>([])
const allSongsLoading = ref(false)
const songKeyword = ref('')
const songPage = ref(1)
const songTotal = ref(0)
const songPageSize = 10

async function loadAllSongs(page = songPage.value) {
  allSongsLoading.value = true
  songPage.value = page
  try {
    const data = await adminSongList(songKeyword.value || undefined, page, songPageSize)
    allSongs.value = data.items
    songTotal.value = data.total
  } catch {
    // 拦截器已提示
  } finally {
    allSongsLoading.value = false
  }
}

function searchSongs() {
  loadAllSongs(1)
}

/** 状态 → 展示标签配置 */
const songStatusMeta: Record<number, { label: string; type: 'warning' | 'success' | 'info' }> = {
  0: { label: '待审核', type: 'warning' },
  1: { label: '已发布', type: 'success' },
  2: { label: '已下架', type: 'info' },
}

function forceOffline(song: SongVO) {
  ElMessageBox.prompt(
    `下架后《${song.name}》将立即不可播放，并通知歌手。`,
    '强制下架歌曲',
    {
      inputPlaceholder: '下架原因（将通知歌手，可留空）',
      confirmButtonText: '确认下架',
      cancelButtonText: '取消',
    },
  )
    .then(async ({ value }) => {
      try {
        await adminSongOffline(song.id, value?.trim() || undefined)
        ElMessage.success('已下架并通知歌手')
        await loadAllSongs(songPage.value)
      } catch {
        // 拦截器已提示
      }
    })
    .catch(() => undefined)
}

// ===== 轮播图管理 =====
const banners = ref<BannerVO[]>([])
const bannersLoading = ref(false)

/** 歌曲选择数据源（筛选用户歌曲用：取热门榜+待审，输入关键字过滤本地） */
const songOptions = ref<SongVO[]>([])
const songOptionsLoading = ref(false)

async function loadSongOptions() {
  songOptionsLoading.value = true
  try {
    songOptions.value = await getHotChart(50)
  } catch {
    songOptions.value = []
  } finally {
    songOptionsLoading.value = false
  }
}

/** 按关键字过滤歌曲（远程无全量接口，本地过滤热门榜） */
function filterSongs(query: string) {
  if (!query) return
  // el-select 远程搜索：拉热门榜后本地过滤即可满足课程场景
  if (!songOptionsLoading.value && songOptions.value.length === 0) {
    loadSongOptions()
  }
}

async function loadBanners() {
  bannersLoading.value = true
  try {
    banners.value = await adminBannerList()
  } catch {
    // 拦截器已提示
  } finally {
    bannersLoading.value = false
  }
}

const bannerDialogVisible = ref(false)
const bannerSubmitting = ref(false)
const bannerUploading = ref(false)
const bannerForm = reactive({
  id: 0,
  title: '',
  subtitle: '',
  imageUrl: '',
  songId: null as number | null,
  sortOrder: 0,
  status: 1,
})

function openBannerCreate() {
  Object.assign(bannerForm, {
    id: 0, title: '', subtitle: '', imageUrl: '', songId: null, sortOrder: 0, status: 1,
  })
  if (songOptions.value.length === 0) loadSongOptions()
  bannerDialogVisible.value = true
}

function openBannerEdit(b: BannerVO) {
  Object.assign(bannerForm, {
    id: b.id,
    title: b.title,
    subtitle: b.subtitle || '',
    imageUrl: b.imageUrl,
    songId: b.songId,
    sortOrder: b.sortOrder,
    status: b.status,
  })
  if (songOptions.value.length === 0) loadSongOptions()
  bannerDialogVisible.value = true
}

/** 上传海报图片（file 服务，type=BANNER 复用 COVER 目录规则） */
async function handleBannerUpload(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return
  bannerUploading.value = true
  try {
    const vo = await uploadFile(file, 'COVER')
    bannerForm.imageUrl = vo.url
    ElMessage.success('海报上传成功')
  } catch {
    // 拦截器已提示
  } finally {
    bannerUploading.value = false
    input.value = ''
  }
}

/** 选中歌曲：海报缺省用该歌曲封面，标题缺省用歌名 */
function onSongSelected(songId: number | null) {
  if (songId == null) return
  const song = songOptions.value.find((s) => s.id === songId)
  if (song) {
    if (!bannerForm.imageUrl && song.cover) bannerForm.imageUrl = song.cover
    if (!bannerForm.title) bannerForm.title = song.name
  }
}

async function handleBannerSave() {
  if (!bannerForm.title.trim() || !bannerForm.imageUrl) {
    ElMessage.warning('请填写标题并提供海报图片（上传或选择歌曲自动取封面）')
    return
  }
  bannerSubmitting.value = true
  try {
    await adminBannerSave({
      id: bannerForm.id || undefined,
      title: bannerForm.title.trim(),
      subtitle: bannerForm.subtitle.trim() || null,
      imageUrl: bannerForm.imageUrl,
      songId: bannerForm.songId,
      sortOrder: bannerForm.sortOrder,
      status: bannerForm.status,
    })
    ElMessage.success(bannerForm.id ? '轮播图已更新' : '轮播图已创建')
    bannerDialogVisible.value = false
    await loadBanners()
  } catch {
    // 拦截器已提示
  } finally {
    bannerSubmitting.value = false
  }
}

async function handleBannerDelete(b: BannerVO) {
  try {
    await ElMessageBox.confirm(`确认删除轮播图「${b.title}」？`, '删除确认', { type: 'warning' })
  } catch {
    return
  }
  try {
    await adminBannerDelete(b.id)
    ElMessage.success('已删除')
    await loadBanners()
  } catch {
    // 拦截器已提示
  }
}

/** 上/下架快捷切换 */
async function handleBannerToggleStatus(b: BannerVO) {
  try {
    await adminBannerSave({
      id: b.id,
      title: b.title,
      subtitle: b.subtitle,
      imageUrl: b.imageUrl,
      songId: b.songId,
      sortOrder: b.sortOrder,
      status: b.status === 1 ? 0 : 1,
    })
    ElMessage.success(b.status === 1 ? '已下架' : '已上架')
    await loadBanners()
  } catch {
    // 拦截器已提示
  }
}

// ===== 公告 =====
const noticeForm = ref({ title: '', content: '' })
const noticeSending = ref(false)

async function handleBroadcast() {
  if (!noticeForm.value.title.trim() || !noticeForm.value.content.trim()) {
    ElMessage.warning('请填写公告标题与内容')
    return
  }
  try {
    await ElMessageBox.confirm('公告将发送给全部用户，确认发布？', '发布确认', { type: 'warning' })
  } catch {
    return
  }
  noticeSending.value = true
  try {
    await adminBroadcast(noticeForm.value.title.trim(), noticeForm.value.content.trim())
    ElMessage.success('公告已群发')
    noticeForm.value = { title: '', content: '' }
  } catch {
    // 拦截器已提示
  } finally {
    noticeSending.value = false
  }
}

function onTabChange(tab: string) {
  if (tab === 'users') loadUsers()
  else if (tab === 'singers') loadSingers()
  else if (tab === 'songs') loadSongs()
  else if (tab === 'all-songs') loadAllSongs()
  else if (tab === 'banners') loadBanners()
}

onMounted(() => {
  loadUsers()
  loadSingers()
  loadSongs()
  // URL 带 ?tab= 直达时对应数据也要拉（如从首页待办跳转）
  if (activeTab.value === 'all-songs') loadAllSongs()
})
</script>

<template>
  <div class="manage-center">
    <el-tabs v-model="activeTab" class="big-tabs" @tab-change="onTabChange">
      <!-- 用户管理：增删改查 + 分页 + 关键字 -->
      <el-tab-pane label="用户管理" name="users">
        <div class="toolbar">
          <el-input
            v-model="userKeyword"
            placeholder="搜索用户名 / 昵称 / 邮箱"
            clearable
            style="width: 320px"
            size="large"
            @keyup.enter="searchUsers"
            @clear="searchUsers"
          />
          <el-button type="primary" size="large" @click="searchUsers">查询</el-button>
          <div class="toolbar-spacer" />
          <el-button type="primary" size="large" :icon="Plus" @click="openUserCreate">新增用户</el-button>
        </div>
        <el-table
          v-loading="usersLoading"
          :data="users"
          size="large"
          :header-cell-style="{ fontSize: '14px' }"
          :cell-style="{ fontSize: '14.5px' }"
        >
          <el-table-column prop="id" label="ID" width="70" />
          <el-table-column prop="username" label="用户名" min-width="120" />
          <el-table-column prop="nickname" label="昵称" min-width="110" />
          <el-table-column prop="email" label="邮箱" min-width="180" />
          <el-table-column prop="phone" label="手机号" width="120" />
          <el-table-column label="角色" width="100">
            <template #default="{ row }">
              <el-tag :type="row.role === 'ADMIN' ? 'danger' : row.role === 'SINGER' ? 'warning' : 'info'">
                {{ row.role === 'ADMIN' ? '管理员' : row.role === 'SINGER' ? '歌手' : '用户' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="90">
            <template #default="{ row }">
              <el-tag :type="row.status === 1 ? 'success' : 'danger'">
                {{ row.status === 1 ? '正常' : '禁用' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="createTime" label="注册时间" width="170" />
          <el-table-column label="操作" width="200" fixed="right">
            <template #default="{ row }">
              <div class="op-cell">
                <el-button text size="default" type="primary" @click="openUserEdit(row)">编辑</el-button>
                <el-button
                  text
                  size="default"
                  :type="row.status === 1 ? 'danger' : 'success'"
                  @click="toggleUser(row)"
                >
                  {{ row.status === 1 ? '禁用' : '启用' }}
                </el-button>
                <el-button text size="default" type="danger" @click="handleUserDelete(row)">删除</el-button>
              </div>
            </template>
          </el-table-column>
        </el-table>
        <div class="pager">
          <el-pagination
            layout="total, prev, pager, next, jumper"
            :total="userTotal"
            :page-size="userPageSize"
            :current-page="userPage"
            @current-change="loadUsers($event)"
          />
        </div>
      </el-tab-pane>

      <!-- 歌手审核 -->
      <el-tab-pane name="singers">
        <template #label>
          歌手审核
          <el-badge v-if="pendingSingers.length" :value="pendingSingers.length" class="tab-badge" />
        </template>
        <el-table
          v-loading="singersLoading"
          :data="pendingSingers"
          size="large"
          :header-cell-style="{ fontSize: '14px' }"
          :cell-style="{ fontSize: '14.5px' }"
        >
          <el-table-column prop="id" label="ID" width="80" />
          <el-table-column prop="userId" label="用户ID" width="90" />
          <el-table-column prop="name" label="艺名" min-width="140" show-overflow-tooltip />
          <el-table-column prop="introduction" label="简介" min-width="240" show-overflow-tooltip />
          <el-table-column prop="createTime" label="申请时间" width="170" />
          <el-table-column label="操作" width="150" fixed="right">
            <template #default="{ row }">
              <div class="op-cell">
                <el-button text size="default" type="success" @click="auditSinger(row, true)">通过</el-button>
                <el-button text size="default" type="danger" @click="auditSinger(row, false)">驳回</el-button>
              </div>
            </template>
          </el-table-column>
        </el-table>
        <el-empty v-if="!singersLoading && pendingSingers.length === 0" description="暂无待审核的歌手申请" />
      </el-tab-pane>

      <!-- 歌曲审核 -->
      <el-tab-pane name="songs">
        <template #label>
          歌曲审核
          <el-badge v-if="pendingSongs.length" :value="pendingSongs.length" class="tab-badge" />
        </template>
        <el-table
          v-loading="songsLoading"
          :data="pendingSongs"
          size="large"
          :header-cell-style="{ fontSize: '14px' }"
          :cell-style="{ fontSize: '14.5px' }"
        >
          <el-table-column prop="id" label="ID" width="80" />
          <el-table-column prop="name" label="歌曲" min-width="160" show-overflow-tooltip />
          <el-table-column prop="singerName" label="歌手" min-width="120" show-overflow-tooltip />
          <el-table-column prop="style" label="风格" width="90" />
          <el-table-column prop="createTime" label="提交时间" width="170" />
          <el-table-column label="操作" width="200" fixed="right">
            <template #default="{ row }">
              <div class="op-cell">
                <el-button text size="default" :icon="View" @click="openDetail(row)">详情</el-button>
                <el-button text size="default" type="success" @click="auditSong(row, true)">通过</el-button>
                <el-button text size="default" type="danger" @click="auditSong(row, false)">驳回</el-button>
              </div>
            </template>
          </el-table-column>
        </el-table>
        <el-empty v-if="!songsLoading && pendingSongs.length === 0" description="暂无待审核的歌曲" />
      </el-tab-pane>

      <!-- 全量歌曲管理：搜索/分页/强制下架 -->
      <el-tab-pane label="歌曲管理" name="all-songs">
        <div class="toolbar">
          <el-input
            v-model="songKeyword"
            class="toolbar-search"
            placeholder="按歌名或风格搜索"
            clearable
            size="large"
            @keyup.enter="searchSongs"
            @clear="searchSongs"
          />
          <el-button size="large" @click="searchSongs">搜索</el-button>
          <div class="toolbar-spacer" />
          <span class="song-total tabular-nums">共 {{ songTotal }} 首</span>
        </div>
        <el-table
          v-loading="allSongsLoading"
          :data="allSongs"
          size="large"
          :header-cell-style="{ fontSize: '14px' }"
          :cell-style="{ fontSize: '14.5px' }"
        >
          <el-table-column prop="id" label="ID" width="80" />
          <el-table-column prop="name" label="歌曲" min-width="160" show-overflow-tooltip />
          <el-table-column prop="singerName" label="歌手" min-width="120" show-overflow-tooltip />
          <el-table-column prop="style" label="风格" width="90" />
          <el-table-column label="播放量" width="110">
            <template #default="{ row }">
              <span class="tabular-nums">{{ row.playCount ?? 0 }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="createTime" label="上架时间" width="170" />
          <el-table-column label="状态" width="100">
            <template #default="{ row }">
              <el-tag
                size="small"
                effect="plain"
                :type="songStatusMeta[row.status]?.type ?? 'info'"
              >
                {{ songStatusMeta[row.status]?.label ?? '未知' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="160" fixed="right">
            <template #default="{ row }">
              <div class="op-cell">
                <el-button text size="default" :icon="View" @click="openDetail(row)">详情</el-button>
                <el-button
                  v-if="row.status !== 2"
                  text
                  size="default"
                  type="danger"
                  @click="forceOffline(row)"
                >
                  下架
                </el-button>
              </div>
            </template>
          </el-table-column>
        </el-table>
        <div class="pager">
          <el-pagination
            v-model:current-page="songPage"
            :page-size="songPageSize"
            :total="songTotal"
            layout="prev, pager, next, total"
            :total-render="() => `共 ${songTotal} 首`"
            @current-change="loadAllSongs"
          />
        </div>
      </el-tab-pane>

      <!-- 轮播图管理 -->
      <el-tab-pane label="轮播图管理" name="banners">
        <div class="toolbar">
          <span class="banner-tip">配置首页轮播：上传海报图片，或选择歌曲自动取其封面；无任何上架项时首页显示内置海报。</span>
          <div class="toolbar-spacer" />
          <el-button type="primary" size="large" :icon="Plus" @click="openBannerCreate">新增轮播图</el-button>
        </div>
        <el-table
          v-loading="bannersLoading"
          :data="banners"
          size="large"
          :header-cell-style="{ fontSize: '14px' }"
          :cell-style="{ fontSize: '14.5px' }"
        >
          <el-table-column label="海报" width="120">
            <template #default="{ row }">
              <img class="banner-thumb" :src="row.imageUrl" :alt="row.title" />
            </template>
          </el-table-column>
          <el-table-column prop="title" label="标题" min-width="140" />
          <el-table-column prop="subtitle" label="推广语" min-width="220" show-overflow-tooltip />
          <el-table-column label="关联歌曲" min-width="140">
            <template #default="{ row }">
              <span v-if="row.songId">
                {{ songOptions.find((s) => s.id === row.songId)?.name || `歌曲#${row.songId}` }}
              </span>
              <span v-else class="op-none">—</span>
            </template>
          </el-table-column>
          <el-table-column prop="sortOrder" label="排序" width="80" />
          <el-table-column label="状态" width="90">
            <template #default="{ row }">
              <el-tag :type="row.status === 1 ? 'success' : 'info'">
                {{ row.status === 1 ? '上架' : '下架' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="200" fixed="right">
            <template #default="{ row }">
              <div class="op-cell">
                <el-button text size="default" type="primary" @click="openBannerEdit(row)">编辑</el-button>
                <el-button
                  text
                  size="default"
                  :type="row.status === 1 ? 'warning' : 'success'"
                  @click="handleBannerToggleStatus(row)"
                >
                  {{ row.status === 1 ? '下架' : '上架' }}
                </el-button>
                <el-button text size="default" type="danger" @click="handleBannerDelete(row)">删除</el-button>
              </div>
            </template>
          </el-table-column>
        </el-table>
        <el-empty v-if="!bannersLoading && banners.length === 0" description="还没有轮播图，点右上角新增一张" />
      </el-tab-pane>

      <!-- 公告发布 -->
      <el-tab-pane label="公告发布" name="notice">
        <div class="notice-card">
          <el-form label-position="top" size="large">
            <el-form-item label="公告标题" required>
              <el-input v-model="noticeForm.title" maxlength="100" show-word-limit placeholder="如：平台维护通知" />
            </el-form-item>
            <el-form-item label="公告内容" required>
              <el-input
                v-model="noticeForm.content"
                type="textarea"
                :rows="6"
                maxlength="500"
                show-word-limit
                placeholder="支持全站用户在消息中心看到此公告"
              />
            </el-form-item>
            <el-button type="primary" size="large" :loading="noticeSending" @click="handleBroadcast">
              群发公告
            </el-button>
          </el-form>
        </div>
      </el-tab-pane>
    </el-tabs>

    <!-- ===== 用户新增/编辑弹窗 ===== -->
    <el-dialog
      v-model="userDialogVisible"
      :title="userDialogMode === 'create' ? '新增用户' : `编辑用户 #${userForm.id}`"
      width="520px"
    >
      <el-form label-position="top">
        <el-form-item label="用户名" required>
          <el-input v-model="userForm.username" :disabled="userDialogMode === 'edit'" maxlength="50" />
        </el-form-item>
        <el-form-item :label="userDialogMode === 'create' ? '密码' : '重置密码（留空不重置）'" :required="userDialogMode === 'create'">
          <el-input
            v-model="userForm.password"
            type="password"
            show-password
            :placeholder="userDialogMode === 'create' ? '6-20 位' : '输入新密码则重置'"
          />
        </el-form-item>
        <el-form-item label="昵称">
          <el-input v-model="userForm.nickname" maxlength="50" placeholder="默认同用户名" />
        </el-form-item>
        <el-form-item label="邮箱" required>
          <el-input v-model="userForm.email" placeholder="you@example.com" />
        </el-form-item>
        <el-form-item label="手机号">
          <el-input v-model="userForm.phone" maxlength="20" />
        </el-form-item>
        <el-form-item label="角色">
          <el-select v-model="userForm.role" style="width: 100%">
            <el-option label="普通用户" value="USER" />
            <el-option label="歌手" value="SINGER" />
            <el-option label="管理员" value="ADMIN" disabled />
          </el-select>
        </el-form-item>
        <el-form-item v-if="userDialogMode === 'edit'" label="状态">
          <el-switch v-model="userForm.status" :active-value="1" :inactive-value="0" active-text="正常" inactive-text="禁用" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="userDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="userSubmitting" @click="handleUserSave">保存</el-button>
      </template>
    </el-dialog>

    <!-- ===== 轮播图新增/编辑弹窗 ===== -->
    <el-dialog
      v-model="bannerDialogVisible"
      :title="bannerForm.id ? `编辑轮播图 #${bannerForm.id}` : '新增轮播图'"
      width="560px"
    >
      <el-form label-position="top">
        <el-form-item label="海报图片" required>
          <div class="banner-upload-row">
            <div class="banner-preview" :style="bannerForm.imageUrl ? undefined : { background: 'var(--yx-gradient)' }">
              <img v-if="bannerForm.imageUrl" :src="bannerForm.imageUrl" alt="海报预览" />
              <span v-else class="banner-preview-empty">待上传</span>
            </div>
            <div class="banner-upload-actions">
              <label class="upload-btn" :class="{ 'is-loading': bannerUploading }">
                {{ bannerUploading ? '上传中...' : '上传图片（jpg/png，≤5MB）' }}
                <input type="file" accept=".jpg,.jpeg,.png,.webp" hidden @change="handleBannerUpload" />
              </label>
              <p class="banner-upload-hint">也可以在下方选择歌曲，自动使用该歌曲封面</p>
            </div>
          </div>
        </el-form-item>
        <el-form-item label="主标题（如歌星名）" required>
          <el-input v-model="bannerForm.title" maxlength="100" show-word-limit />
        </el-form-item>
        <el-form-item label="推广语">
          <el-input v-model="bannerForm.subtitle" maxlength="200" show-word-limit placeholder="如：晴天 · 七里香　华语经典永流传" />
        </el-form-item>
        <el-form-item label="关联歌曲（可选：点击轮播图播放该歌曲）">
          <el-select
            v-model="bannerForm.songId"
            clearable
            filterable
            placeholder="搜索并选择歌曲"
            :loading="songOptionsLoading"
            style="width: 100%"
            @visible-change="filterSongs"
            @change="onSongSelected(bannerForm.songId)"
          >
            <el-option
              v-for="s in songOptions"
              :key="s.id"
              :label="`${s.name} - ${s.singerName || '未知歌手'}`"
              :value="s.id"
            />
          </el-select>
        </el-form-item>
        <div class="banner-form-row">
          <el-form-item label="排序（小的在前）">
            <el-input-number v-model="bannerForm.sortOrder" :min="0" :max="999" />
          </el-form-item>
          <el-form-item label="状态">
            <el-switch v-model="bannerForm.status" :active-value="1" :inactive-value="0" active-text="上架" inactive-text="下架" />
          </el-form-item>
        </div>
      </el-form>
      <template #footer>
        <el-button @click="bannerDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="bannerSubmitting" @click="handleBannerSave">保存</el-button>
      </template>
    </el-dialog>

    <!-- 歌曲详情抽屉：审核时可查看封面、试听音频、歌词 -->
    <el-drawer v-model="detailVisible" :title="detailSong ? `《${detailSong.name}》` : '歌曲详情'" size="400px">
      <div v-if="detailSong" class="song-detail">
        <img v-if="detailSong.cover" class="detail-cover" :src="detailSong.cover" :alt="detailSong.name" />
        <div v-else class="detail-cover detail-cover--empty">♪</div>

        <p class="detail-meta">
          <el-tag size="small" effect="plain">{{ detailSong.style || '未分类' }}</el-tag>
          <span>{{ detailSong.singerName || '未知歌手' }}</span>
        </p>

        <h3 class="detail-sub">音频试听</h3>
        <audio
          v-if="detailSong.url"
          ref="previewAudio"
          class="detail-audio"
          :src="detailSong.url"
          controls
          preload="none"
        />
        <p v-else class="detail-empty">未上传音频</p>

        <h3 class="detail-sub">歌词</h3>
        <pre class="detail-lyric">{{ lyricText(detailSong.lyric) }}</pre>
      </div>
    </el-drawer>
  </div>
</template>

<style scoped>
.manage-center {
  background: #fff;
  border: 1px solid var(--yx-hairline);
  border-radius: 10px;
  padding: 8px 24px 28px;
}

/* 页签加大 */
.manage-center :deep(.el-tabs__item) {
  font-size: 15px;
  height: 52px;
  line-height: 52px;
}

.toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin: 18px 0 16px;
}

.toolbar-spacer {
  flex: 1;
}

.toolbar-search {
  width: 260px;
}

.song-total {
  font-size: 13.5px;
  color: var(--yx-text-secondary);
}

.pager {
  margin-top: 18px;
  display: flex;
  justify-content: center;
}

/* ===== 操作列防挤压：flex 不换行 + 收紧按钮间距 =====
   el-button 默认 margin-left:12px 且 .el-table .cell 允许换行，
   前面内容列撑宽时操作按钮会被挤到第二行。统一改为 flex 容器：
   nowrap 保证恒在一行，间距 4px 更紧凑，文字按钮天然窄。 */
.op-cell {
  display: flex;
  align-items: center;
  flex-wrap: nowrap;
  white-space: nowrap;
  overflow: hidden;
}

.op-cell .el-button {
  margin-left: 0;
  margin-right: 0;
  padding: 0 10px;
  flex: none;
}

.op-cell .el-button + .el-button {
  margin-left: 4px;
}

.banner-tip {
  font-size: 13px;
  color: var(--yx-text-secondary);
}

.banner-thumb {
  width: 96px;
  height: 42px;
  object-fit: cover;
  border-radius: 6px;
  border: 1px solid var(--yx-hairline);
}

.op-none {
  color: var(--yx-text-disabled);
  font-size: 12px;
}

.tab-badge {
  margin-left: 6px;
  transform: translateY(-2px);
}

.notice-card {
  padding: 24px 8px;
  max-width: 640px;
}

/* 轮播图表单 */
.banner-upload-row {
  display: flex;
  gap: 16px;
  width: 100%;
  align-items: flex-start;
}

.banner-preview {
  width: 180px;
  height: 76px;
  flex-shrink: 0;
  border-radius: 8px;
  overflow: hidden;
  display: flex;
  align-items: center;
  justify-content: center;
  color: rgba(255, 255, 255, 0.85);
  font-size: 13px;
  border: 1px solid var(--yx-hairline);
}

.banner-preview img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.banner-preview-empty {
  font-size: 12px;
}

.banner-upload-actions {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.banner-upload-hint {
  font-size: 12px;
  color: var(--yx-text-disabled);
  line-height: 1.6;
}

.banner-form-row {
  display: flex;
  gap: 24px;
}

.upload-btn {
  display: inline-flex;
  align-items: center;
  padding: 8px 16px;
  font-size: 13px;
  border: 1px dashed var(--yx-text-disabled);
  border-radius: 4px;
  cursor: pointer;
  color: var(--yx-text-secondary);
}

.upload-btn:hover {
  border-color: var(--yx-accent);
  color: var(--yx-accent);
}

.upload-btn.is-loading {
  opacity: 0.6;
  pointer-events: none;
}

/* 歌曲详情抽屉 */
.song-detail {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.detail-cover {
  width: 100%;
  aspect-ratio: 1;
  object-fit: cover;
  border-radius: 10px;
  border: 1px solid var(--yx-hairline);
}

.detail-cover--empty {
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 56px;
  color: rgba(255, 255, 255, 0.6);
  background: var(--yx-gradient);
}

.detail-meta {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 14px;
  color: var(--yx-text-secondary);
  margin: 6px 0 10px;
}

.detail-sub {
  font-size: 14px;
  font-weight: 600;
  margin: 10px 0 6px;
  padding-left: 8px;
  border-left: 3px solid var(--yx-accent);
}

.detail-audio {
  width: 100%;
}

.detail-empty {
  font-size: 13px;
  color: var(--yx-text-disabled);
}

.detail-lyric {
  max-height: 320px;
  overflow-y: auto;
  padding: 12px 14px;
  background: #fafbfd;
  border: 1px solid var(--yx-hairline);
  border-radius: 8px;
  font-family: inherit;
  font-size: 13.5px;
  line-height: 2;
  white-space: pre-wrap;
  color: var(--yx-text-main);
}
</style>
