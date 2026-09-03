<script setup lang="ts">
import { computed } from 'vue'
import { CaretRight } from '@element-plus/icons-vue'
import type { SingerVO } from '@/api/home'

/**
 * 歌手横向列表行：左侧圆形头像（榜位序号在头像左侧），右侧上歌手名下简介。
 * hover 行底变色、头像浮现播放钮；点击打开歌手搜索结果
 */
const props = defineProps<{ singer: SingerVO; rank?: number }>()

const emit = defineEmits<{ (e: 'open', singer: SingerVO): void }>()

const palette = [
  'linear-gradient(135deg, #86b6ef, #2a78d6)',
  'linear-gradient(135deg, #f3b08a, #eb6834)',
  'linear-gradient(135deg, #8ad0bc, #199e70)',
  'linear-gradient(135deg, #f2cd86, #c98500)',
  'linear-gradient(135deg, #c3b0ec, #7a68cf)',
  'linear-gradient(135deg, #eda0c0, #c8588b)',
]
const avatarBg = computed(() => palette[props.singer.id % palette.length] ?? palette[0]!)
</script>

<template>
  <div class="singer-row" @click="emit('open', singer)">
    <!-- 榜位序号：前三名红色高亮 -->
    <span v-if="(rank ?? 0) > 0" class="row-rank tabular-nums" :class="{ 'is-top': rank! <= 3 }">
      {{ rank }}
    </span>

    <div class="row-avatar">
      <img v-if="singer.avatar" class="avatar-img" :src="singer.avatar" :alt="singer.name" loading="lazy" />
      <div v-else class="avatar-fallback" :style="{ background: avatarBg }">
        {{ singer.name.slice(0, 1) }}
      </div>
      <span class="avatar-play" title="查看歌手">
        <el-icon :size="13"><CaretRight /></el-icon>
      </span>
    </div>

    <div class="row-info">
      <p class="row-name">{{ singer.name }}</p>
      <p class="row-sub">{{ singer.introduction || '热门歌手' }}</p>
    </div>
  </div>
</template>

<style scoped>
/* 行本体：hover 变底色 */
.singer-row {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 7px 10px;
  border-radius: 10px;
  cursor: pointer;
  transition: background 0.2s ease;
}

.singer-row:hover {
  background: var(--yx-accent-weak);
}

/* 榜位序号：与 SongRow 同款，前三名暖色强调 */
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

/* 左侧圆形头像 */
.row-avatar {
  position: relative;
  flex: none;
  width: 52px;
  height: 52px;
  border-radius: 50%;
  overflow: hidden;
  background: #eef1f6;
}

.avatar-img,
.avatar-fallback {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.avatar-fallback {
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-size: 20px;
  font-weight: 700;
}

/* 头像悬浮钮 */
.avatar-play {
  position: absolute;
  left: 50%;
  top: 50%;
  width: 24px;
  height: 24px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  color: #fff;
  background: rgba(0, 0, 0, 0.45);
  transform: translate(-50%, -50%) scale(0.85);
  opacity: 0;
  transition: opacity 0.2s ease, transform 0.2s ease;
}

.singer-row:hover .avatar-play {
  opacity: 1;
  transform: translate(-50%, -50%) scale(1);
}

/* 中间文案区：上名字下简介 */
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
</style>
