<script setup lang="ts">
import { ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElAvatar } from 'element-plus'
import {
  getFollowingList,
  getFollowersList,
  type FollowUserVO,
} from '@/api/user'
import { getVirtualFollows, type SingerFollowItem } from '@/api/singer'

/**
 * 关注/粉丝列表弹窗（个人中心与用户公开主页共用）：
 * 条目展示昵称/头像/签名，点击进入对方公开主页。
 * 关注列表 = 关注的用户（user_follow）∪ 关注的虚拟歌手（singer 服务本地表），
 * 与"我的音乐→我的关注"同口径。
 */

/** 列表条目：普通用户或虚拟歌手（虚拟歌手点击跳歌手主页） */
interface FollowRow {
  kind: 'user' | 'singer'
  /** kind=user 时为用户ID，kind=singer 时为歌手ID */
  id: number
  nickname: string | null
  avatar: string | null
  signature: string | null
  followed: boolean | null
  /** 认证歌手（role=SINGER）与虚拟歌手统一显示"歌手"标签 */
  isSinger: boolean
}

const props = defineProps<{
  visible: boolean
  userId: number
  mode: 'following' | 'followers'
}>()

const emit = defineEmits<{ 'update:visible': [v: boolean] }>()

const router = useRouter()
const list = ref<FollowRow[]>([])
const loading = ref(false)

const title = () => (props.mode === 'following' ? '关注列表' : '粉丝列表')

function toRow(u: FollowUserVO): FollowRow {
  return {
    kind: 'user',
    id: u.id,
    nickname: u.nickname,
    avatar: u.avatar,
    signature: u.signature,
    followed: u.followed,
    isSinger: u.role === 'SINGER',
  }
}

function singerRow(s: SingerFollowItem): FollowRow {
  return {
    kind: 'singer',
    id: s.singerId,
    nickname: s.name,
    avatar: s.avatar,
    signature: s.introduction,
    followed: null,
    isSinger: true,
  }
}

async function load() {
  if (!props.userId) return
  loading.value = true
  try {
    if (props.mode === 'following') {
      const [users, singers] = await Promise.all([
        getFollowingList(props.userId),
        // 虚拟歌手聚合失败仅少展示，不阻塞整表
        getVirtualFollows(props.userId).catch(() => [] as SingerFollowItem[]),
      ])
      list.value = [...users.map(toRow), ...singers.map(singerRow)]
    } else {
      const users = await getFollowersList(props.userId)
      list.value = users.map(toRow)
    }
  } catch {
    list.value = []
  } finally {
    loading.value = false
  }
}

watch(
  () => props.visible,
  (v) => {
    if (v) void load()
  },
)

function goHome(u: FollowRow) {
  emit('update:visible', false)
  if (u.kind === 'singer') {
    router.push({ name: 'singer-home', params: { id: u.id } })
  } else {
    router.push({ name: 'user-home', params: { id: u.id } })
  }
}
</script>

<template>
  <el-dialog
    :model-value="visible"
    :title="title()"
    width="420px"
    @update:model-value="emit('update:visible', $event)"
  >
    <div v-loading="loading" class="follow-list">
      <div v-for="u in list" :key="`${u.kind}-${u.id}`" class="follow-row" @click="goHome(u)">
        <el-avatar :size="38" :src="u.avatar || undefined">
          {{ (u.nickname || '友').slice(0, 1) }}
        </el-avatar>
        <div class="follow-info">
          <p class="follow-name">{{ u.nickname || `用户${u.id}` }}</p>
          <p v-if="u.signature" class="follow-signature">{{ u.signature }}</p>
        </div>
        <el-tag v-if="u.isSinger" size="small" type="warning" effect="plain" class="follow-flag">
          歌手
        </el-tag>
        <el-tag
          v-else-if="u.followed"
          size="small"
          type="info"
          effect="plain"
          class="follow-flag"
        >
          已关注
        </el-tag>
      </div>
      <el-empty
        v-if="!loading && list.length === 0"
        :description="mode === 'following' ? '还没有关注的人' : '还没有粉丝'"
        :image-size="60"
      />
    </div>
  </el-dialog>
</template>

<style scoped>
.follow-list {
  max-height: 420px;
  overflow-y: auto;
  min-height: 80px;
}

.follow-row {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 9px 4px;
  cursor: pointer;
  border-radius: 8px;
}

.follow-row:hover {
  background: var(--yx-bg-soft, #f5f7fb);
}

.follow-info {
  flex: 1;
  min-width: 0;
}

.follow-name {
  margin: 0;
  font-size: 14px;
  font-weight: 600;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.follow-signature {
  margin: 2px 0 0;
  font-size: 12px;
  color: var(--yx-text-secondary, #8a93a6);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.follow-flag {
  flex-shrink: 0;
}
</style>
