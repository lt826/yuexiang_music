<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { CaretRight } from '@element-plus/icons-vue'
import TopNav from '@/components/TopNav.vue'
import SongCover from '@/components/SongCover.vue'
import { useUserStore } from '@/stores/user'
import { usePlayerStore } from '@/stores/player'
import {
  applySinger,
  getMyApplication,
  publishSong,
  getMySongs,
  offlineSong,
  updateSong,
  resubmitSong,
  uploadFile,
  updateSingerProfile,
  type SingerVO,
} from '@/api/singer'
import type { SongVO } from '@/api/home'

const userStore = useUserStore()
const playerStore = usePlayerStore()

// ===== 认证状态 =====
const application = ref<SingerVO | null>(null)
const appLoading = ref(true)
const isSinger = ref(false)

// 认证表单（歌手名直接取昵称，无需另起艺名）
const applyForm = ref({ introduction: '' })
const applySubmitting = ref(false)

// ===== 作品管理 =====
const mySongs = ref<SongVO[]>([])
const songsLoading = ref(false)

// 发布表单
const publishVisible = ref(false)
const publishForm = ref({
  name: '',
  style: '流行',
  lyric: '',
  audioUrl: '',
  coverUrl: '',
})
const audioUploading = ref(false)
const coverUploading = ref(false)
const publishSubmitting = ref(false)
const styleOptions = ['流行', '摇滚', '民谣', '电子', '后摇', '爵士', '说唱', '古典']

const statusMap: Record<number, { label: string; type: 'info' | 'success' | 'danger' }> = {
  0: { label: '待审核', type: 'info' },
  1: { label: '已发布', type: 'success' },
  2: { label: '已下架', type: 'danger' },
}

async function loadAll() {
  appLoading.value = true
  try {
    application.value = await getMyApplication()
  } catch {
    application.value = null
  } finally {
    appLoading.value = false
  }
  // 先更新歌手标记，再决定是否拉作品（此前顺序颠倒导致首次进入不加载歌曲）
  isSinger.value = application.value?.status === 1
  if (isSinger.value) {
    await loadSongs()
  }
}

async function loadSongs() {
  songsLoading.value = true
  try {
    mySongs.value = await getMySongs()
  } catch {
    // 拦截器已提示
  } finally {
    songsLoading.value = false
  }
}

async function handleApply() {
  applySubmitting.value = true
  try {
    await applySinger({
      // 歌手名直接使用昵称（昵称登录后必有值，注册未填时后端回落用户名）
      name: userStore.nickname,
      introduction: applyForm.value.introduction.trim() || undefined,
    })
    ElMessage.success('申请已提交，请等待管理员审核')
    await loadAll()
  } catch {
    // 拦截器已提示
  } finally {
    applySubmitting.value = false
  }
}

// 音频上传（走 file 服务）
async function handleAudioUpload(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return
  audioUploading.value = true
  try {
    const vo = await uploadFile(file, 'SONG')
    publishForm.value.audioUrl = vo.url
    if (!publishForm.value.name) {
      publishForm.value.name = vo.originalName.replace(/\.[^.]+$/, '')
    }
    ElMessage.success('音频上传成功')
  } catch {
    // 拦截器已提示
  } finally {
    audioUploading.value = false
    input.value = ''
  }
}

async function handleCoverUpload(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return
  coverUploading.value = true
  try {
    const vo = await uploadFile(file, 'COVER')
    publishForm.value.coverUrl = vo.url
    ElMessage.success('封面上传成功')
  } catch {
    // 拦截器已提示
  } finally {
    coverUploading.value = false
    input.value = ''
  }
}

// 歌词导入（读取 .lrc/.txt 文本文件填充表单）
function importLyricFile(event: Event, target: 'publish' | 'edit') {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return
  const reader = new FileReader()
  reader.onload = () => {
    const text = String(reader.result || '').trim()
    if (!text) {
      ElMessage.warning('歌词文件为空')
      return
    }
    if (target === 'publish') {
      publishForm.value.lyric = text
    } else {
      editForm.value.lyric = text
    }
    ElMessage.success('歌词导入成功')
  }
  reader.onerror = () => ElMessage.error('歌词文件读取失败')
  reader.readAsText(file, 'utf-8')
  input.value = ''
}

async function handlePublish() {
  const form = publishForm.value
  if (!form.name.trim() || !form.audioUrl) {
    ElMessage.warning('请填写歌名并上传音频')
    return
  }
  publishSubmitting.value = true
  try {
    await publishSong({
      name: form.name.trim(),
      url: form.audioUrl,
      cover: form.coverUrl || null,
      lyric: form.lyric.trim() || null,
      style: form.style,
    })
    ElMessage.success('已提交审核，通过后自动上架')
    publishVisible.value = false
    publishForm.value = { name: '', style: '流行', lyric: '', audioUrl: '', coverUrl: '' }
    await loadSongs()
  } catch {
    // 拦截器已提示
  } finally {
    publishSubmitting.value = false
  }
}

async function handleOffline(song: SongVO) {
  try {
    await ElMessageBox.confirm(
      `确认下架《${song.name}》？下架后听众将无法播放；之后可在操作列编辑内容，保存后重新送审上架。`,
      '下架确认',
      { type: 'warning' },
    )
  } catch {
    return
  }
  try {
    await offlineSong(song.id)
    ElMessage.success('已下架')
    await loadSongs()
  } catch {
    // 拦截器已提示
  }
}

// ===== 编辑歌曲（已发布/已下架均可编辑资料） =====
const editVisible = ref(false)
const editSubmitting = ref(false)
const editAudioUploading = ref(false)
const editCoverUploading = ref(false)
const editingId = ref(0)
const editForm = ref({
  name: '',
  style: '流行',
  lyric: '',
  audioUrl: '',
  coverUrl: '',
})

function openEdit(song: SongVO) {
  editingId.value = song.id
  editForm.value = {
    name: song.name,
    style: song.style || '流行',
    lyric: song.lyric || '',
    audioUrl: song.url || '',
    coverUrl: song.cover || '',
  }
  editVisible.value = true
}

async function handleEditAudioUpload(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return
  editAudioUploading.value = true
  try {
    const vo = await uploadFile(file, 'SONG')
    editForm.value.audioUrl = vo.url
    ElMessage.success('音频上传成功')
  } catch {
    // 拦截器已提示
  } finally {
    editAudioUploading.value = false
    input.value = ''
  }
}

async function handleEditCoverUpload(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return
  editCoverUploading.value = true
  try {
    const vo = await uploadFile(file, 'COVER')
    editForm.value.coverUrl = vo.url
    ElMessage.success('封面上传成功')
  } catch {
    // 拦截器已提示
  } finally {
    editCoverUploading.value = false
    input.value = ''
  }
}

/** 保存编辑：仅已下架歌曲可编辑，保存后自动重新送审（后端强制复审） */
async function handleEditSave() {
  const form = editForm.value
  if (!form.name.trim() || !form.audioUrl) {
    ElMessage.warning('请填写歌名并保留音频')
    return
  }
  editSubmitting.value = true
  try {
    await updateSong(editingId.value, {
      name: form.name.trim(),
      url: form.audioUrl,
      cover: form.coverUrl || null,
      lyric: form.lyric.trim() || null,
      style: form.style,
    })
    ElMessage.success('修改已保存，已重新提交审核，通过后自动上架')
    editVisible.value = false
    await loadSongs()
  } catch {
    // 拦截器已提示
  } finally {
    editSubmitting.value = false
  }
}

/** 直接重新送审（不改内容） */
async function handleResubmit(song: SongVO) {
  try {
    await ElMessageBox.confirm(
      `将《${song.name}》重新提交上架审核？`,
      '送审确认',
      { type: 'info' },
    )
  } catch {
    return
  }
  try {
    await resubmitSong(song.id)
    ElMessage.success('已重新提交审核')
    await loadSongs()
  } catch {
    // 拦截器已提示
  }
}

function formatCount(n: number): string {
  if (n >= 100000000) return (n / 100000000).toFixed(1) + '亿'
  if (n >= 10000) return (n / 10000).toFixed(1) + '万'
  return String(n)
}

/** 试听自己的作品（含待审核/已下架，后端对归属歌手放行） */
function previewSong(song: SongVO) {
  // 列表行直接入队，播放器拉详情时以本人身份经网关注入 X-User-Id 放行
  void playerStore.playQueue([song], 0)
}

// ===== 歌手资料编辑（艺名即歌曲发布者署名） =====
const profileVisible = ref(false)
const profileSubmitting = ref(false)
const profileAvatarUploading = ref(false)
const profileForm = ref({ name: '', introduction: '', avatar: '' })

function openProfileEdit() {
  profileForm.value = {
    name: application.value?.name || userStore.nickname,
    introduction: application.value?.introduction || '',
    avatar: application.value?.avatar || '',
  }
  profileVisible.value = true
}

async function handleProfileAvatarUpload(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return
  profileAvatarUploading.value = true
  try {
    const vo = await uploadFile(file, 'AVATAR')
    profileForm.value.avatar = vo.url
    ElMessage.success('头像上传成功')
  } catch {
    // 拦截器已提示
  } finally {
    profileAvatarUploading.value = false
    input.value = ''
  }
}

async function handleProfileSave() {
  if (!profileForm.value.name.trim()) {
    ElMessage.warning('艺名不能为空')
    return
  }
  profileSubmitting.value = true
  try {
    await updateSingerProfile({
      name: profileForm.value.name.trim(),
      introduction: profileForm.value.introduction.trim() || undefined,
      avatar: profileForm.value.avatar || undefined,
    })
    ElMessage.success('资料已更新，作品署名将同步生效')
    profileVisible.value = false
    await loadAll()
  } catch {
    // 拦截器已提示
  } finally {
    profileSubmitting.value = false
  }
}

onMounted(loadAll)
</script>

<template>
  <div class="page">
    <TopNav />

    <main class="main" v-loading="appLoading">
      <!-- ===== 未申请 / 审核中 / 驳回：认证区 ===== -->
      <section v-if="!isSinger" class="apply-card">
        <template v-if="application === null">
          <h1 class="apply-title">申请成为歌手</h1>
          <p class="apply-sub">
            通过认证后即可上传、发布你的音乐作品，让更多人听到你的声音。
            歌手名将直接使用你的昵称「{{ userStore.nickname }}」。
          </p>
          <el-form label-position="top" class="apply-form">
            <el-form-item label="歌手名">
              <el-input :model-value="userStore.nickname" disabled>
                <template #suffix><span class="name-hint">取自昵称</span></template>
              </el-input>
            </el-form-item>
            <el-form-item label="简介">
              <el-input
                v-model="applyForm.introduction"
                type="textarea"
                :rows="3"
                placeholder="介绍你的音乐风格、代表作品（选填）"
                maxlength="200"
                show-word-limit
              />
            </el-form-item>
            <el-button type="primary" :loading="applySubmitting" @click="handleApply">提交申请</el-button>
          </el-form>
        </template>

        <template v-else-if="application.status === 0">
          <div class="status-block">
            <el-tag type="info" size="large">审核中</el-tag>
            <h1 class="apply-title">「{{ application.name }}」认证申请已提交</h1>
            <p class="apply-sub">管理员会尽快审核，审核结果将通过站内消息通知你。</p>
          </div>
        </template>

        <template v-else-if="application.status === 2">
          <div class="status-block">
            <el-tag type="danger" size="large">已驳回</el-tag>
            <h1 class="apply-title">上次申请未通过</h1>
            <p class="apply-sub reject-reason">驳回原因：{{ application.rejectReason || '未通过审核' }}</p>
            <el-form label-position="top" class="apply-form">
              <el-form-item label="简介">
                <el-input v-model="applyForm.introduction" type="textarea" :rows="3" maxlength="200" />
              </el-form-item>
              <el-button type="primary" :loading="applySubmitting" @click="handleApply">重新申请</el-button>
            </el-form>
          </div>
        </template>
      </section>

      <!-- ===== 已认证：歌手中心 ===== -->
      <template v-else>
        <header class="center-head">
          <div class="center-head-left">
            <el-avatar :size="56" :src="application?.avatar || userStore.avatar || undefined" class="center-avatar">
              {{ (application?.name || userStore.nickname).slice(0, 1) }}
            </el-avatar>
            <div>
              <h1 class="center-title">歌手中心</h1>
              <p class="center-sub">{{ application?.name }} · 管理你的音乐作品</p>
            </div>
          </div>
          <div class="center-head-actions">
            <el-button @click="openProfileEdit">编辑资料</el-button>
            <el-button type="primary" @click="publishVisible = true">+ 发布歌曲</el-button>
          </div>
        </header>

        <section class="songs-card" v-loading="songsLoading">
          <el-table :data="mySongs" style="width: 100%">
            <el-table-column label="歌曲" min-width="200">
              <template #default="{ row }">
                <div class="song-cell" @click="previewSong(row)">
                  <div class="song-cell-cover">
                    <SongCover :cover="row.cover" :name="row.name" :seed="row.id" radius="4px" />
                    <span class="song-cell-play" title="试听">
                      <el-icon :size="12"><CaretRight /></el-icon>
                    </span>
                  </div>
                  <span class="song-cell-name">{{ row.name }}</span>
                </div>
              </template>
            </el-table-column>
            <el-table-column prop="style" label="风格" width="90" />
            <el-table-column label="播放量" width="110">
              <template #default="{ row }">
                <span class="tabular-nums">{{ formatCount(row.playCount) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="状态" width="100">
              <template #default="{ row }">
                <el-tag :type="statusMap[row.status]?.type ?? 'info'" size="small">
                  {{ statusMap[row.status]?.label ?? '未知' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="驳回原因" min-width="140">
              <template #default="{ row }">
                <span v-if="row.status === 2 && row.rejectReason" class="reject-text">{{ row.rejectReason }}</span>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="200" fixed="right">
              <template #default="{ row }">
                <template v-if="row.status === 1">
                  <!-- 已发布：内容变更必须走"下架→编辑→复审"流程，保证上架内容经过审核 -->
                  <el-button text size="small" type="danger" @click="handleOffline(row)">下架</el-button>
                </template>
                <template v-else-if="row.status === 2">
                  <el-button text size="small" type="primary" @click="openEdit(row)">编辑</el-button>
                  <el-button text size="small" type="success" @click="handleResubmit(row)">送审</el-button>
                </template>
                <span v-else class="op-none">—</span>
              </template>
            </el-table-column>
          </el-table>
          <el-empty v-if="!songsLoading && mySongs.length === 0" description="还没有发布作品，点击右上角发布第一首歌吧" />
        </section>
      </template>
    </main>

    <!-- 发布弹窗 -->
    <el-dialog v-model="publishVisible" title="发布歌曲" width="520px">
      <el-form label-position="top">
        <el-form-item label="音频文件" required>
          <div class="upload-row">
            <label class="upload-btn" :class="{ 'is-loading': audioUploading }">
              {{ audioUploading ? '上传中...' : '选择音频（mp3/flac/wav，≤50MB）' }}
              <input type="file" accept=".mp3,.flac,.wav" hidden @change="handleAudioUpload" />
            </label>
            <span v-if="publishForm.audioUrl" class="upload-ok">✓ 已上传</span>
          </div>
        </el-form-item>
        <el-form-item label="歌曲名" required>
          <el-input v-model="publishForm.name" maxlength="100" />
        </el-form-item>
        <el-form-item label="风格">
          <el-select v-model="publishForm.style">
            <el-option v-for="s in styleOptions" :key="s" :label="s" :value="s" />
          </el-select>
        </el-form-item>
        <el-form-item label="封面">
          <div class="upload-row">
            <label class="upload-btn" :class="{ 'is-loading': coverUploading }">
              {{ coverUploading ? '上传中...' : '选择封面（jpg/png，≤5MB）' }}
              <input type="file" accept=".jpg,.jpeg,.png,.webp" hidden @change="handleCoverUpload" />
            </label>
            <span v-if="publishForm.coverUrl" class="upload-ok">✓ 已上传</span>
          </div>
        </el-form-item>
        <el-form-item label="歌词（LRC 格式，选填，可导入 .lrc 文件）">
          <div class="lyric-field">
            <label class="upload-btn">
              导入 .lrc 文件
              <input type="file" accept=".lrc,.txt" hidden @change="importLyricFile($event, 'publish')" />
            </label>
            <el-input v-model="publishForm.lyric" type="textarea" :rows="4" placeholder="[00:00.00]第一句歌词..." />
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="publishVisible = false">取消</el-button>
        <el-button type="primary" :loading="publishSubmitting" @click="handlePublish">提交审核</el-button>
      </template>
    </el-dialog>

    <!-- 编辑歌曲弹窗 -->
    <el-dialog v-model="editVisible" title="编辑歌曲" width="520px">      <el-form label-position="top">
        <el-form-item label="歌曲名" required>
          <el-input v-model="editForm.name" maxlength="100" />
        </el-form-item>
        <el-form-item label="风格">
          <el-select v-model="editForm.style">
            <el-option v-for="s in styleOptions" :key="s" :label="s" :value="s" />
          </el-select>
        </el-form-item>
        <el-form-item label="封面">
          <div class="upload-row">
            <label class="upload-btn" :class="{ 'is-loading': editCoverUploading }">
              {{ editCoverUploading ? '上传中...' : '更换封面（jpg/png，≤5MB）' }}
              <input type="file" accept=".jpg,.jpeg,.png,.webp" hidden @change="handleEditCoverUpload" />
            </label>
            <span v-if="editForm.coverUrl" class="upload-ok">✓ 已有封面</span>
          </div>
        </el-form-item>
        <el-form-item label="音频">
          <div class="upload-row">
            <label class="upload-btn" :class="{ 'is-loading': editAudioUploading }">
              {{ editAudioUploading ? '上传中...' : '更换音频（mp3/flac/wav，≤50MB）' }}
              <input type="file" accept=".mp3,.flac,.wav" hidden @change="handleEditAudioUpload" />
            </label>
            <span v-if="editForm.audioUrl" class="upload-ok">✓ 已有音频</span>
          </div>
        </el-form-item>
        <el-form-item label="歌词（LRC 格式，选填，可导入 .lrc 文件）">
          <div class="lyric-field">
            <label class="upload-btn">
              导入 .lrc 文件
              <input type="file" accept=".lrc,.txt" hidden @change="importLyricFile($event, 'edit')" />
            </label>
            <el-input v-model="editForm.lyric" type="textarea" :rows="4" placeholder="[00:00.00]第一句歌词..." />
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editVisible = false">取消</el-button>
        <el-button type="primary" :loading="editSubmitting" @click="handleEditSave">
          保存并重新送审
        </el-button>
      </template>
    </el-dialog>

    <!-- 歌手资料编辑弹窗 -->
    <el-dialog v-model="profileVisible" title="编辑歌手资料" width="480px">
      <el-form label-position="top">
        <el-form-item label="头像">
          <div class="upload-row">
            <el-avatar
              :size="56"
              :src="profileForm.avatar || undefined"
              class="profile-avatar-preview"
            >
              {{ (profileForm.name || '歌').slice(0, 1) }}
            </el-avatar>
            <label class="upload-btn" :class="{ 'is-loading': profileAvatarUploading }">
              {{ profileAvatarUploading ? '上传中...' : '选择头像（jpg/png，≤5MB）' }}
              <input type="file" accept=".jpg,.jpeg,.png,.webp" hidden @change="handleProfileAvatarUpload" />
            </label>
          </div>
        </el-form-item>
        <el-form-item label="艺名（即歌曲的发布者名字）" required>
          <el-input v-model="profileForm.name" maxlength="50" show-word-limit />
        </el-form-item>
        <el-form-item label="简介">
          <el-input
            v-model="profileForm.introduction"
            type="textarea"
            :rows="3"
            maxlength="200"
            show-word-limit
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="profileVisible = false">取消</el-button>
        <el-button type="primary" :loading="profileSubmitting" @click="handleProfileSave">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.main {
  max-width: 1160px;
  margin: 0 auto;
  padding: 32px 20px 90px;
}

/* 认证卡片 */
.apply-card {
  background: #fff;
  border: 1px solid var(--yx-hairline);
  border-radius: var(--yx-radius);
  box-shadow: var(--yx-shadow-card);
  padding: 36px 40px;
  max-width: 560px;
  margin: 24px auto;
}

.apply-title {
  font-size: 20px;
  font-weight: 700;
  margin-bottom: 8px;
}

.apply-sub {
  font-size: 13px;
  color: var(--yx-text-secondary);
  margin-bottom: 22px;
  line-height: 1.7;
}

.reject-reason {
  color: #f56c6c;
}

.apply-form {
  max-width: 400px;
}

.name-hint {
  font-size: 12px;
  color: var(--yx-text-disabled);
}

.status-block {
  text-align: center;
  padding: 10px 0;
}

/* 歌手中心 */
.center-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 6px 0 20px;
}

.center-head-left {
  display: flex;
  align-items: center;
  gap: 14px;
}

.center-head-actions {
  display: flex;
  gap: 10px;
  flex-shrink: 0;
}

.center-avatar {
  background: var(--yx-red);
  color: #fff;
  font-size: 22px;
  font-weight: 600;
}

.center-title {
  font-size: 22px;
  font-weight: 700;
}

.center-sub {
  margin-top: 6px;
  font-size: 13px;
  color: var(--yx-text-secondary);
}

.songs-card {
  background: #fff;
  border: 1px solid var(--yx-hairline);
  border-radius: var(--yx-radius);
  box-shadow: var(--yx-shadow-card);
  padding: 10px 18px 18px;
}

.song-cell {
  display: flex;
  align-items: center;
  gap: 10px;
  cursor: pointer;
}

.song-cell:hover .song-cell-name {
  color: var(--yx-accent);
}

.song-cell-cover {
  position: relative;
  width: 40px;
  height: 40px;
  border-radius: 4px;
  overflow: hidden;
  flex-shrink: 0;
}

/* 悬浮试听按钮：hover 封面时浮现 */
.song-cell-play {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 4px;
  color: #fff;
  background: rgba(0, 0, 0, 0.45);
  opacity: 0;
  transition: opacity 0.2s ease;
}

.song-cell:hover .song-cell-play {
  opacity: 1;
}

.song-cell-name {
  font-size: 14px;
}

.reject-text {
  font-size: 12px;
  color: #f56c6c;
}

.op-none {
  color: var(--yx-text-disabled);
  font-size: 12px;
}

/* 上传 */
.upload-row {
  display: flex;
  align-items: center;
  gap: 12px;
}

/* 歌词栏：导入按钮在文本域上方 */
.lyric-field {
  width: 100%;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.lyric-field .upload-btn {
  align-self: flex-start;
}

.upload-btn {
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

.upload-ok {
  font-size: 13px;
  color: #67c23a;
}

/* 歌手资料弹窗：头像预览 */
.profile-avatar-preview {
  background: var(--yx-accent);
  font-size: 20px;
  font-weight: 600;
  flex-shrink: 0;
}
</style>
