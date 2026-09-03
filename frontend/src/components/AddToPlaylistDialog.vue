<script setup lang="ts">
import { Check } from '@element-plus/icons-vue'
import { useSongActions } from '@/composables/useSongActions'

/**
 * 「加入歌单」弹窗：与 useSongActions 配对使用。
 * 用法：const actions = useSongActions(); 打开用 actions.openAddPanel(song)，
 * 本组件读同一份 actions 状态渲染歌单列表。
 */
const props = defineProps<{
  actions: ReturnType<typeof useSongActions>
}>()

const { addPanel, myPlaylists, playlistLoading, adding, addToPlaylist } = props.actions
</script>

<template>
  <el-dialog
    :model-value="addPanel.visible"
    title="加入我的歌单"
    width="420px"
    :append-to-body="true"
    @update:model-value="addPanel.visible = $event"
  >
    <p v-if="addPanel.song" class="add-target">
      歌曲：<b>{{ addPanel.song.name }}</b> - {{ addPanel.song.singerName || '未知歌手' }}
    </p>
    <div v-loading="playlistLoading" class="playlist-options">
      <template v-if="myPlaylists.length > 0">
        <button
          v-for="pl in myPlaylists"
          :key="pl.id"
          class="playlist-option"
          type="button"
          :disabled="adding"
          @click="addToPlaylist(pl)"
        >
          <span class="pl-title">{{ pl.title }}</span>
          <span class="pl-count tabular-nums">{{ pl.songCount }}首</span>
          <el-icon v-if="adding" class="pl-adding"><Check /></el-icon>
        </button>
      </template>
      <el-empty
        v-else-if="!playlistLoading"
        description="还没有自己创建的歌单，去「我的音乐」创建一个吧"
        :image-size="60"
      />
    </div>
    <template #footer>
      <el-button @click="addPanel.visible = false">关闭</el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.add-target {
  margin: 0 0 12px;
  font-size: 13px;
  color: var(--yx-text-secondary);
}

.playlist-options {
  min-height: 80px;
  max-height: 320px;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.playlist-option {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  border: 1px solid var(--yx-hairline);
  border-radius: 8px;
  background: #fff;
  cursor: pointer;
  font-size: 13px;
  text-align: left;
  transition: all 0.15s ease;
}

.playlist-option:hover {
  border-color: var(--yx-accent);
  background: var(--yx-accent-weak);
}

.playlist-option:disabled {
  opacity: 0.6;
  cursor: wait;
}

.pl-title {
  flex: 1;
  min-width: 0;
  font-weight: 600;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.pl-count {
  font-size: 12px;
  color: var(--yx-text-secondary);
}

.pl-adding {
  color: var(--yx-accent);
}
</style>
