import { ref, computed } from 'vue'

/** 管理端待办计数（侧边栏角标与管理中心共享） */
const pendingSingerCount = ref(0)
const pendingSongCount = ref(0)
const pendingCircleCount = ref(0)

export const pendingStore = {
  pendingSingerCount,
  pendingSongCount,
  pendingCircleCount,
  total: computed(() => pendingSingerCount.value + pendingSongCount.value + pendingCircleCount.value),
}
