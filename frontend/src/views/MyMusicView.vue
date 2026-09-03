<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { CaretRight, Plus } from '@element-plus/icons-vue'
import TopNav from '@/components/TopNav.vue'
import SongCover from '@/components/SongCover.vue'
import { usePlayerStore } from '@/stores/player'
import { useUserStore } from '@/stores/user'
import {
  getFavoriteList,
  removeFavorite,
  getHistoryList,
  type FavoriteItemVO,
} from '@/api/favorite'
import {
  getMyPlaylists,
  createPlaylist,
  updatePlaylist,
  deletePlaylist,
  type PlaylistVO,
} from '@/api/playlist'
import { uploadFile } from '@/api/singer'

const router = useRouter()
const userStore = useUserStore()
const playerStore = usePlayerStore()

const activeTab = ref<'favorite' | 'history' | 'playlists'>('favorite')
const favorites = ref<FavoriteItemVO[]>([])
const history = ref<FavoriteItemVO[]>([])
const myPlaylists = ref<PlaylistVO[]>([])
const loading = ref(false)

// 创建歌单
const createVisible = ref(false)
const createForm = ref({ title: '', description: '', tag: '', coverUrl: '' })
const createSubmitting = ref(false)
const coverUploading = ref(false)

// 编辑歌单（改名/封面/标签/描述 + 投稿到广场开关）
const editVisible = ref(false)
const editSubmitting = ref(false)
const editCoverUploading = ref(false)
const editingPlaylist = ref<PlaylistVO | null>(null)
const editForm = ref({ title: '', description: '', tag: '', coverUrl: '', published: 0 })

function openEditPlaylist(pl: PlaylistVO) {
  editingPlaylist.value = pl
  editForm.value = {
    title: pl.title,
    description: pl.description || '',
    tag: pl.tag || '',
    coverUrl: pl.cover || '',
    published: pl.published ?? 0,
  }
  editVisible.value = true
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

async function handleEditSave() {
  if (!editForm.value.title.trim()) {
    ElMessage.warning('请输入歌单名称')
    return
  }
  if (!editingPlaylist.value) return
  editSubmitting.value = true
  try {
    await updatePlaylist(editingPlaylist.value.id, {
      title: editForm.value.title.trim(),
      cover: editForm.value.coverUrl || null,
      description: editForm.value.description.trim() || null,
      tag: editForm.value.tag.trim() || null,
      published: editForm.value.published,
    })
    ElMessage.success(editForm.value.published ? '已保存并投稿到广场' : '已保存')
    editVisible.value = false
    myPlaylists.value = await getMyPlaylists()
  } catch {
    // 拦截器已提示
  } finally {
    editSubmitting.value = false
  }
}

/** 上传歌单封面（走 file 服务，type=COVER） */
async function handleCoverUpload(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return
  coverUploading.value = true
  try {
    const vo = await uploadFile(file, 'COVER')
    createForm.value.coverUrl = vo.url
    ElMessage.success('封面上传成功')
  } catch {
    // 拦截器已提示
  } finally {
    coverUploading.value = false
    input.value = ''
  }
}

async function load() {
  loading.value = true
  try {
    const [fav, his, pls] = await Promise.allSettled([
      getFavoriteList(),
      getHistoryList(1, 50),
      getMyPlaylists(),
    ])
    if (fav.status === 'fulfilled') favorites.value = fav.value
    if (his.status === 'fulfilled') history.value = his.value.items
    if (pls.status === 'fulfilled') myPlaylists.value = pls.value
  } finally {
    loading.value = false
  }
}

async function handleCreate() {
  if (!createForm.value.title.trim()) {
    ElMessage.warning('请输入歌单名称')
    return
  }
  createSubmitting.value = true
  try {
    await createPlaylist({
      title: createForm.value.title.trim(),
      cover: createForm.value.coverUrl || null,
      description: createForm.value.description.trim() || null,
      tag: createForm.value.tag.trim() || null,
    })
    ElMessage.success('歌单已创建')
    createVisible.value = false
    createForm.value = { title: '', description: '', tag: '', coverUrl: '' }
    myPlaylists.value = await getMyPlaylists()
  } catch {
    // 拦截器已提示
  } finally {
    createSubmitting.value = false
  }
}

async function handleDeletePlaylist(pl: PlaylistVO) {
  try {
    await ElMessageBox.confirm(`删除歌单「${pl.title}」？其中 ${pl.songCount} 首歌曲的收藏不受影响。`, '删除确认', {
      type: 'warning',
    })
  } catch {
    return
  }
  try {
    await deletePlaylist(pl.id)
    ElMessage.success('已删除')
    myPlaylists.value = await getMyPlaylists()
  } catch {
    // 拦截器已提示
  }
}

function toSongVO(item: FavoriteItemVO) {
  return {
    id: item.songId,
    name: item.name || `歌曲#${item.songId}`,
    cover: item.cover,
    url: item.url,
    style: item.style,
    playCount: item.playCount || 0,
    singerId: 0,
    singerName: item.singerName,
  }
}

function playFavoriteList() {
  if (favorites.value.length === 0) return
  playerStore.playQueue(favorites.value.map(toSongVO), 0)
}

/** 单击/双击行或封面播放钮：以所在列表为队列播放该首（点击效果明确） */
function playOne(item: FavoriteItemVO, list: FavoriteItemVO[]) {
  const queue = list.map(toSongVO)
  const idx = queue.findIndex((s) => s.id === item.songId)
  playerStore.playQueue(queue, idx < 0 ? 0 : idx)
}

async function handleRemove(songId: number, name: string | null) {
  try {
    await removeFavorite(songId)
    favorites.value = favorites.value.filter((f) => f.songId !== songId)
    ElMessage.success(`已取消收藏《${name || songId}》`)
  } catch {
    // 拦截器已提示
  }
}

function formatTime(time: string): string {
  const d = new Date(time)
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

onMounted(load)

</script>

<template>
  <div class="page">
    <TopNav />

    <main class="main">
      <header class="head">
        <el-avatar :size="56" :src="userStore.avatar || undefined" class="avatar">
          {{ userStore.nickname.slice(0, 1) }}
        </el-avatar>
        <div class="head-info">
          <h1 class="nickname">{{ userStore.nickname }}</h1>
          <p class="sub">收藏 {{ favorites.length }} 首 · 最近收听 {{ history.length }} 条</p>
        </div>
      </header>

      <!-- 听歌周报入口 -->
      <div class="report-entry" @click="router.push('/weekly-report')">
        <span class="re-mark">📊</span>
        <span class="re-info">
          <span class="re-title">我的听歌周报</span>
          <span class="re-sub">最近 7 天你与音乐的故事</span>
        </span>
        <span class="re-arrow">›</span>
      </div>

      <el-tabs v-model="activeTab" class="tabs">
        <el-tab-pane label="我的收藏" name="favorite">
          <div v-loading="loading" class="list-area">
            <div v-if="favorites.length > 0" class="list-head">
              <el-button text type="primary" @click="playFavoriteList">播放全部</el-button>
            </div>
            <ol v-if="favorites.length > 0" class="item-list">
              <li
                v-for="item in favorites"
                :key="`${item.songId}`"
                class="item"
                :class="{ 'is-playing': playerStore.currentSong?.id === item.songId }"
                @dblclick="playOne(item, favorites)"
              >
                <span class="cover">
                  <SongCover :cover="item.cover" :name="item.name || ''" :seed="item.songId" radius="4px" />
                  <!-- 悬浮播放按钮：酷狗式，单击/双击行都可播 -->
                  <span class="cover-play" title="播放" @click.stop="playOne(item, favorites)">
                    <el-icon :size="18"><CaretRight /></el-icon>
                  </span>
                </span>
                <span class="info" @click="playOne(item, favorites)">
                  <span class="name">{{ item.name || `歌曲#${item.songId}` }}</span>
                  <span class="meta">
                    {{ item.singerName || '未知歌手' }} · 收藏于 {{ formatTime(item.time) }}
                  </span>
                </span>
                <el-button text size="small" type="danger" @click="handleRemove(item.songId, item.name)">
                  取消收藏
                </el-button>
              </li>
            </ol>
            <el-empty v-else-if="!loading" description="还没有收藏歌曲，去首页听听看吧">
              <router-link to="/home" class="empty-link">回首页发现音乐</router-link>
            </el-empty>
          </div>
        </el-tab-pane>

        <el-tab-pane label="收听历史" name="history">
          <div v-loading="loading" class="list-area">
            <ol v-if="history.length > 0" class="item-list">
              <li
                v-for="item in history"
                :key="`${item.songId}-${item.time}`"
                class="item"
                :class="{ 'is-playing': playerStore.currentSong?.id === item.songId }"
                @dblclick="playOne(item, history)"
              >
                <span class="cover">
                  <SongCover :cover="item.cover" :name="item.name || ''" :seed="item.songId" radius="4px" />
                  <span class="cover-play" title="播放" @click.stop="playOne(item, history)">
                    <el-icon :size="18"><CaretRight /></el-icon>
                  </span>
                </span>
                <span class="info" @click="playOne(item, history)">
                  <span class="name">{{ item.name || `歌曲#${item.songId}` }}</span>
                  <span class="meta">{{ item.singerName || '未知歌手' }} · {{ formatTime(item.time) }}</span>
                </span>
              </li>
            </ol>
            <el-empty v-else-if="!loading" description="暂无收听记录，登录后播放歌曲会自动记录" />
          </div>
        </el-tab-pane>

        <el-tab-pane label="我的歌单" name="playlists">
          <div v-loading="loading" class="list-area">
            <div class="list-head">
              <el-button text type="primary" @click="createVisible = true">
                <el-icon class="btn-icon"><Plus /></el-icon>创建歌单
              </el-button>
            </div>
            <ol v-if="myPlaylists.length > 0" class="pl-list">
              <li v-for="pl in myPlaylists" :key="pl.id" class="pl-item">
                <span class="cover">
                  <SongCover :cover="pl.cover" :name="pl.title" :seed="pl.id" radius="4px" />
                </span>
                <span class="info" @click="router.push(`/playlist/${pl.id}`)">
                  <span class="name">
                    {{ pl.title }}
                    <el-tag v-if="pl.published" size="small" type="success" effect="plain" class="pub-tag">已投稿</el-tag>
                  </span>
                  <span class="meta">{{ pl.songCount ?? 0 }}首 · {{ formatTime(pl.createTime) }}创建</span>
                </span>
                <el-button text size="small" type="primary" @click="openEditPlaylist(pl)">编辑</el-button>
                <el-button text size="small" type="danger" @click="handleDeletePlaylist(pl)">删除</el-button>
              </li>
            </ol>
            <el-empty v-else-if="!loading" description="还没有自己的歌单，创建一个整理喜欢的音乐吧">
              <el-button type="primary" @click="createVisible = true">创建歌单</el-button>
            </el-empty>
          </div>
        </el-tab-pane>
      </el-tabs>
    </main>

    <!-- 创建歌单弹窗 -->
    <el-dialog v-model="createVisible" title="创建歌单" width="480px">      <el-form label-position="top">
        <el-form-item label="歌单封面">
          <div class="cover-upload">
            <div class="cover-preview" :style="createForm.coverUrl ? undefined : { background: 'var(--yx-gradient)' }">
              <img v-if="createForm.coverUrl" :src="createForm.coverUrl" alt="封面预览" />
              <el-icon v-else :size="28"><Plus /></el-icon>
            </div>
            <div class="cover-actions">
              <label class="cover-btn" :class="{ 'is-loading': coverUploading }">
                {{ coverUploading ? '上传中...' : '选择图片（jpg/png，≤5MB）' }}
                <input type="file" accept=".jpg,.jpeg,.png,.webp" hidden @change="handleCoverUpload" />
              </label>
              <el-button v-if="createForm.coverUrl" text size="small" type="danger" @click="createForm.coverUrl = ''">
                移除
              </el-button>
            </div>
          </div>
        </el-form-item>
        <el-form-item label="歌单名称" required>
          <el-input v-model="createForm.title" maxlength="100" placeholder="如：深夜循环" show-word-limit />
        </el-form-item>
        <el-form-item label="标签">
          <el-input v-model="createForm.tag" maxlength="20" placeholder="如：民谣（选填）" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="createForm.description" type="textarea" :rows="3" maxlength="200" placeholder="选填" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" :loading="createSubmitting" @click="handleCreate">创建</el-button>
      </template>
    </el-dialog>

    <!-- 编辑歌单弹窗：改名/封面/标签/描述 + 投稿到广场 -->
    <el-dialog v-model="editVisible" title="编辑歌单" width="480px">
      <el-form label-position="top">
        <el-form-item label="歌单封面">
          <div class="cover-upload">
            <div class="cover-preview" :style="editForm.coverUrl ? undefined : { background: 'var(--yx-gradient)' }">
              <img v-if="editForm.coverUrl" :src="editForm.coverUrl" alt="封面预览" />
              <el-icon v-else :size="28"><Plus /></el-icon>
            </div>
            <div class="cover-actions">
              <label class="cover-btn" :class="{ 'is-loading': editCoverUploading }">
                {{ editCoverUploading ? '上传中...' : '更换图片（jpg/png，≤5MB）' }}
                <input type="file" accept=".jpg,.jpeg,.png,.webp" hidden @change="handleEditCoverUpload" />
              </label>
              <el-button v-if="editForm.coverUrl" text size="small" type="danger" @click="editForm.coverUrl = ''">
                移除
              </el-button>
            </div>
          </div>
        </el-form-item>
        <el-form-item label="歌单名称" required>
          <el-input v-model="editForm.title" maxlength="100" show-word-limit />
        </el-form-item>
        <el-form-item label="标签">
          <el-input v-model="editForm.tag" maxlength="20" placeholder="如：民谣（选填）" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="editForm.description" type="textarea" :rows="3" maxlength="200" placeholder="选填" />
        </el-form-item>
        <el-form-item label="广场可见性">
          <div class="pub-switch">
            <el-switch v-model="editForm.published" :active-value="1" :inactive-value="0" />
            <span class="pub-hint">
              {{ editForm.published ? '已投稿：所有人可在歌单广场看到' : '私藏：仅自己可见，开启后投稿到广场' }}
            </span>
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editVisible = false">取消</el-button>
        <el-button type="primary" :loading="editSubmitting" @click="handleEditSave">保存</el-button>
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
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 8px 0 20px;
}

.avatar {
  background: var(--yx-accent);
  font-size: 22px;
}

.nickname {
  font-size: 20px;
  font-weight: 700;
}

.sub {
  margin-top: 6px;
  font-size: 13px;
  color: var(--yx-text-secondary);
}

/* 听歌周报入口 */
.report-entry {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 16px 20px;
  margin-bottom: 8px;
  background: linear-gradient(135deg, #eef4fd, #f7f0fd);
  border-radius: 12px;
  cursor: pointer;
  transition: transform 0.15s;
}

.report-entry:hover {
  transform: translateY(-1px);
}

.re-mark {
  font-size: 26px;
}

.re-info {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.re-title {
  font-size: 15px;
  font-weight: 700;
}

.re-sub {
  font-size: 12px;
  color: var(--yx-text-secondary);
}

.re-arrow {
  color: #b4bac6;
  font-size: 22px;
}

.tabs {
  background: #fff;
  border: 1px solid var(--yx-hairline);
  border-radius: var(--yx-radius);
  box-shadow: var(--yx-shadow-card);
  padding: 6px 18px 18px;
}

.list-head {
  display: flex;
  justify-content: flex-end;
  padding: 4px 0;
}

.btn-icon {
  margin-right: 4px;
}

.pl-list {
  border-top: 1px solid var(--yx-hairline);
}

.pub-tag {
  margin-left: 6px;
}

.pub-switch {
  display: flex;
  align-items: center;
  gap: 10px;
}

.pub-hint {
  font-size: 12px;
  color: var(--yx-text-secondary);
}

.pl-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 2px;
  border-bottom: 1px solid var(--yx-hairline);
}

.item-list {
  border-top: 1px solid var(--yx-hairline);
}

.item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 2px;
  border-bottom: 1px solid var(--yx-hairline);
  border-radius: 8px;
  transition: background 0.2s ease;
}

.item:hover {
  background: var(--yx-accent-weak);
}

/* 正在播放的行：歌名高亮 */
.item.is-playing .name {
  color: var(--yx-accent);
  font-weight: 600;
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

/* 封面上的悬浮播放钮：酷狗式，默认隐藏，hover 出现 */
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
.item:hover .cover-play {
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

.empty-link {
  font-size: 13px;
  color: var(--yx-accent);
}

/* 封面上传 */
.cover-upload {
  display: flex;
  align-items: center;
  gap: 16px;
  width: 100%;
}

.cover-preview {
  width: 96px;
  height: 96px;
  flex-shrink: 0;
  border-radius: 8px;
  overflow: hidden;
  display: flex;
  align-items: center;
  justify-content: center;
  color: rgba(255, 255, 255, 0.8);
  border: 1px solid var(--yx-hairline);
}

.cover-preview img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.cover-actions {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 8px;
}

.cover-btn {
  padding: 8px 16px;
  font-size: 13px;
  border: 1px dashed var(--yx-text-disabled);
  border-radius: 4px;
  cursor: pointer;
  color: var(--yx-text-secondary);
}

.cover-btn:hover {
  border-color: var(--yx-accent);
  color: var(--yx-accent);
}

.cover-btn.is-loading {
  opacity: 0.6;
  pointer-events: none;
}
</style>
