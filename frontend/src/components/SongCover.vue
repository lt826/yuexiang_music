<script setup lang="ts">
/** 歌曲封面：有 cover 时显示真实图片，否则按 seed 显示渐变占位 */
const props = withDefaults(
  defineProps<{
    cover?: string | null
    name?: string | null
    seed?: number
    radius?: string
  }>(),
  { cover: null, name: '', seed: 0, radius: '6px' },
)

const palette = [
  'linear-gradient(135deg, #86b6ef, #2a78d6)',
  'linear-gradient(135deg, #f3b08a, #eb6834)',
  'linear-gradient(135deg, #8ad0bc, #199e70)',
  'linear-gradient(135deg, #f2cd86, #c98500)',
  'linear-gradient(135deg, #c3b0ec, #7a68cf)',
  'linear-gradient(135deg, #eda0c0, #c8588b)',
]

function coverStyle(): string {
  return palette[(props.seed || 0) % palette.length] ?? palette[0]!
}
</script>

<template>
  <img v-if="cover" class="song-cover" :src="cover" :alt="name ?? undefined" loading="lazy" :style="{ borderRadius: radius }" />
  <div v-else class="song-cover song-cover-fallback" :style="{ background: coverStyle(), borderRadius: radius }">
    {{ (name || '♪').slice(0, 1) }}
  </div>
</template>

<style scoped>
.song-cover {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.song-cover-fallback {
  color: rgba(255, 255, 255, 0.92);
  font-size: 16px;
  font-weight: 600;
}
</style>
