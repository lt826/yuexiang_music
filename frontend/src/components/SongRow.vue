<script setup lang="ts">
import { computed, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { CaretRight, Headset, Plus, Star, StarFilled } from '@element-plus/icons-vue'
import type { SongVO } from '@/api/home'
import { getMyPlaylists, addSongToPlaylist, type PlaylistVO } from '@/api/playlist'

/**
 * 歌曲横向列表行：左侧小封面（榜位序号在封面左侧），右侧上歌名下"歌手 · 流派"，
 * 最右播放量、收藏星标与"加入歌单"加号。hover 行底变色、封面浮现播放钮。
 * 单击 = 播放；双击 = 播放（触发时机不同，都以单击兜底）
 */
const props = withDefaults(
  defineProps<{
    song: SongVO
    /** 榜位序号（新歌首发/热门歌曲等榜单场景），不传不显示 */
    rank?: number
    /** 是否显示收藏星标（默认显示，列表页可关） */
    showFav?: boolean
    /** 是否显示"加入歌单"加号（默认显示） */
    showAdd?: boolean
    favored?: boolean
  }>(),
  { rank: 0, showFav: true, showAdd: true, favored: false },
)

const emit = defineEmits<{
  (e: 'play', song: SongVO): void
  (e: 'toggle-fav', song: SongVO): void
}>()

/** 加号弹层状态：我的歌单懒加载（首次打开拉取） */
const addVisible = ref(false)
const myPlaylists = ref<PlaylistVO[]>([])
const playlistsLoaded = ref(false)
const adding = ref(false)

async function openAddMenu() {
  addVisible.value = true
  if (playlistsLoaded.value) return
  try {
    myPlaylists.value = await getMyPlaylists()
    playlistsLoaded.value = true
  } catch {
    // 拦截器已提示（未登录等）；下次再试
  }
}

async function handleAddToPlaylist(pl: PlaylistVO) {
  if (adding.value) return
  adding.value = true
  try {
    await addSongToPlaylist(pl.id, props.song.id)
    ElMessage.success(`已加入「${pl.title}」`)
    addVisible.value = false
  } catch {
    // 已在歌单等业务错误由拦截器提示
  } finally {
    adding.value = false
  }
}

/** 封面兜底渐变（无 cover 时按 id 稳定取色） */
const palette = [
  'linear-gradient(135deg, #86b6ef, #2a78d6)',
  'linear-gradient(135deg, #f3b08a, #eb6834)',
  'linear-gradient(135deg, #8ad0bc, #199e70)',
  'linear-gradient(135deg, #f2cd86, #c98500)',
  'linear-gradient(135deg, #c3b0ec, #7a68cf)',
  'linear-gradient(135deg, #eda0c0, #c8588b)',
]
const coverBg = computed(() => palette[props.song.id % palette.length] ?? palette[0]!)

function formatCount(n: number): string {
  if (n >= 100000000) return (n / 100000000).toFixed(1) + '亿'
  if (n >= 10000) return (n / 10000).toFixed(1) + '万'
  return String(n)
}
</script>

<template>
  <div
    class="song-row"
    @click="emit('play', song)"
    @dblclick="emit('play', song)"
  >
    <!-- 榜位序号：前三名红色高亮 -->
    <span v-if="rank > 0" class="row-rank tabular-nums" :class="{ 'is-top': rank <= 3 }">
      {{ rank }}
    </span>

    <div class="row-cover">
      <img v-if="song.cover" class="cover-img" :src="song.cover" :alt="song.name" loading="lazy" />
      <div v-else class="cover-fallback" :style="{ background: coverBg }">
        <span>{{ song.name.slice(0, 1) }}</span>
      </div>

      <!-- 悬浮播放按钮：hover 出现的小圆钮 -->
      <span class="row-play-btn" title="播放">
        <el-icon :size="14"><CaretRight /></el-icon>
      </span>
    </div>

    <div class="row-info">
      <p class="row-name" :title="song.name">{{ song.name }}</p>
      <p class="row-sub">
        {{ song.singerName || '未知歌手' }}
        <span v-if="song.style" class="row-style">· {{ song.style }}</span>
      </p>
    </div>

    <!-- 播放量 -->
    <span class="row-playcount tabular-nums">
      <el-icon :size="12"><Headset /></el-icon>
      {{ formatCount(song.playCount) }}
    </span>

    <!-- 加入歌单加号：hover 行时出现，点开歌单选择弹层 -->
    <el-popover
      v-if="showAdd"
      v-model:visible="addVisible"
      width="230"
      trigger="click"
      :show-arrow="false"
      placement="bottom-end"
      @show="openAddMenu"
    >
      <template #reference>
        <span class="row-add" title="加入歌单" @click.stop>
          <el-icon :size="14"><Plus /></el-icon>
        </span>
      </template>
      <div class="pl-menu" @click.stop>
        <p class="pl-menu-title">加入歌单</p>
        <p v-if="myPlaylists.length === 0" class="pl-menu-empty">
          还没有自己创建的歌单
        </p>
        <p
          v-for="pl in myPlaylists"
          :key="pl.id"
          class="pl-menu-item"
          :class="{ 'is-busy': adding }"
          @click="handleAddToPlaylist(pl)"
        >
          {{ pl.title }}
          <span class="pl-menu-count tabular-nums">{{ pl.songCount ?? 0 }}首</span>
        </p>
      </div>
    </el-popover>

    <!-- 收藏星标：默认隐藏，hover 行时出现；已收藏常亮金色 -->
    <el-icon
      v-if="showFav"
      class="row-fav"
      :size="15"
      :class="{ 'is-favored': favored }"
      title="收藏"
      @click.stop="emit('toggle-fav', song)"
    >
      <StarFilled v-if="favored" />
      <Star v-else />
    </el-icon>
  </div>
</template>

<style scoped>
/* 行本体：hover 变底色（不再上浮阴影） */
.song-row {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 7px 10px;
  border-radius: 10px;
  cursor: pointer;
  transition: background 0.2s ease;
}

.song-row:hover {
  background: var(--yx-accent-weak);
}

/* 榜位序号：固定宽度对齐，前三名暖色强调 */
.row-rank {
  flex: none;
  min-width: 20px;
  text-align: center;
  font-size: 15px;
  font-weight: 700;
  font-style: italic;
  color: var(--yx-text-disabled);
}

.row-rank.is-top {
  color: var(--yx-danger);
  font-size: 17px;
}

/* 左侧小封面 */
.row-cover {
  position: relative;
  flex: none;
  width: 52px;
  height: 52px;
  border-radius: 8px;
  overflow: hidden;
  background: #eef1f6;
}

.cover-img,
.cover-fallback {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.cover-fallback {
  display: flex;
  align-items: center;
  justify-content: center;
  color: rgba(255, 255, 255, 0.92);
  font-size: 22px;
  font-weight: 700;
}

/* 封面悬浮播放钮 */
.row-play-btn {
  position: absolute;
  left: 50%;
  top: 50%;
  width: 26px;
  height: 26px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  color: #fff;
  background: rgba(0, 0, 0, 0.45);
  backdrop-filter: blur(2px);
  transform: translate(-50%, -50%) scale(0.85);
  opacity: 0;
  transition: opacity 0.2s ease, transform 0.2s ease, background 0.2s ease;
}

.song-row:hover .row-play-btn {
  opacity: 1;
  transform: translate(-50%, -50%) scale(1);
}

.row-play-btn:hover {
  background: var(--yx-primary);
}

/* 中间文案区：上歌名下歌手，超长省略 */
.row-info {
  flex: 1;
  min-width: 0;
}

.row-name {
  font-size: 14px;
  font-weight: 600;
  color: var(--yx-text-main);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.row-sub {
  margin-top: 2px;
  font-size: 12px;
  color: var(--yx-text-secondary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.row-style {
  color: var(--yx-text-disabled);
}

/* 右侧播放量 */
.row-playcount {
  flex: none;
  display: inline-flex;
  align-items: center;
  gap: 4px;
  margin-left: 8px;
  font-size: 12px;
  color: var(--yx-text-disabled);
}

/* 加号与收藏星标：默认隐藏，hover 行时出现 */
.row-add,
.row-fav {
  flex: none;
  margin-left: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--yx-text-disabled);
  opacity: 0;
  transition: opacity 0.2s ease, transform 0.2s ease, color 0.2s ease;
}

.song-row:hover .row-add,
.song-row:hover .row-fav {
  opacity: 1;
}

/* 加号展开弹层期间保持可见 */
.row-add:hover {
  opacity: 1;
  color: var(--yx-text-secondary);
}

.row-fav:hover {
  transform: scale(1.25);
  color: var(--yx-text-secondary);
}

.row-fav.is-favored {
  opacity: 1;
  color: #f7ba2a;
}

/* 加号弹层菜单（popover 内容） */
.pl-menu {
  max-height: 260px;
  overflow-y: auto;
}

.pl-menu-title {
  margin: 0 0 6px;
  font-size: 12px;
  color: var(--yx-text-secondary);
}

.pl-menu-empty {
  margin: 0;
  padding: 8px 0;
  font-size: 12px;
  color: var(--yx-text-disabled);
}

.pl-menu-item {
  margin: 0;
  padding: 7px 6px;
  font-size: 13px;
  border-radius: 6px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  transition: background 0.15s ease;
}

.pl-menu-item:hover {
  background: var(--yx-accent-weak);
}

.pl-menu-item.is-busy {
  opacity: 0.6;
  pointer-events: none;
}

.pl-menu-count {
  font-size: 11px;
  color: var(--yx-text-disabled);
}
</style>
