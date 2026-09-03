<script setup lang="ts">
import PlayerBar from '@/components/PlayerBar.vue'
import FullPlayer from '@/components/FullPlayer.vue'
import LoginPromptDialog from '@/components/LoginPromptDialog.vue'
import { usePlayerStore } from '@/stores/player'

const playerStore = usePlayerStore()
</script>

<template>
  <router-view />
  <!-- 全局底部播放器（对齐详细设计 13.1 PlayerBar） -->
  <PlayerBar />
  <!-- 全屏播放页（浮层：沉浸式唱片 + 歌词同步） -->
  <FullPlayer v-if="playerStore.fullScreen && playerStore.hasCurrent" />
  <!-- 全局登录拦截弹窗（未登录点播放时弹出） -->
  <LoginPromptDialog />
</template>

<style scoped></style>
