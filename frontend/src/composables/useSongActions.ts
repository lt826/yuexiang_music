import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { addFavorite } from '@/api/favorite'
import { getMyPlaylists, addSongToPlaylist, type PlaylistVO } from '@/api/playlist'
import { useUserStore } from '@/stores/user'
import { useAuthPromptStore } from '@/stores/authPrompt'

/** 歌曲操作所需的最小字段（SongVO / AiSongVO / FavoriteItemVO 都满足） */
export interface ActionableSong {
  id: number
  name: string
  singerName?: string | null
}

/**
 * 「收藏 + 加入歌单」跨页面共享逻辑：
 * - toggleFavorite：收藏当前歌（未收藏才收，已收藏提示去我的音乐管理）
 * - 加号弹层：拉我的歌单 → 点歌单加入
 * 各页面自持 favoredIds 集合做星标/红心显示，收藏成功后调 markFavored 同步
 */
export function useSongActions() {
  const userStore = useUserStore()
  const authPrompt = useAuthPromptStore()

  /** 已收藏歌曲ID集合（页面进入时 loadFavored 初始化） */
  const favoredIds = ref<Set<number>>(new Set())
  /** 加载中标记（弹层按钮用） */
  const adding = ref(false)

  /** 加号弹层状态 */
  const addPanel = ref<{ visible: boolean; song: ActionableSong | null }>({
    visible: false,
    song: null,
  })
  const myPlaylists = ref<PlaylistVO[]>([])
  const playlistLoading = ref(false)

  async function loadFavored() {
    if (!userStore.isLoggedIn) return
    try {
      const { getFavoriteSongIds } = await import('@/api/favorite')
      favoredIds.value = new Set(await getFavoriteSongIds())
    } catch {
      // 静默：标注失败不影响浏览
    }
  }

  /** 收藏成功后就地补记（避免整页刷新收藏态） */
  function markFavored(songId: number) {
    if (!favoredIds.value.has(songId)) {
      favoredIds.value = new Set([...favoredIds.value, songId])
    }
  }

  /** 收藏入口：未登录弹引导；已收藏提示去我的音乐管理 */
  async function toggleFavorite(song: ActionableSong) {
    if (!userStore.isLoggedIn) {
      authPrompt.open(() => undefined)
      return
    }
    try {
      if (favoredIds.value.has(song.id)) {
        ElMessage.info('这首歌已在收藏中，可在「我的音乐」管理')
        return
      }
      await addFavorite(song.id)
      markFavored(song.id)
      ElMessage.success(`已收藏《${song.name}》`)
    } catch {
      // 拦截器已提示
    }
  }

  /** 打开"加入歌单"弹层（每次打开都刷新歌单列表，防止新建歌单不同步） */
  async function openAddPanel(song: ActionableSong) {
    if (!userStore.isLoggedIn) {
      authPrompt.open(() => undefined)
      return
    }
    addPanel.value = { visible: true, song }
    playlistLoading.value = true
    try {
      myPlaylists.value = await getMyPlaylists()
    } catch {
      myPlaylists.value = []
    } finally {
      playlistLoading.value = false
    }
  }

  /** 加入指定歌单；成功后关弹层，重复添加由拦截器提示 */
  async function addToPlaylist(playlist: PlaylistVO) {
    const song = addPanel.value.song
    if (!song || adding.value) return
    adding.value = true
    try {
      await addSongToPlaylist(playlist.id, song.id)
      ElMessage.success(`已加入「${playlist.title}」`)
      addPanel.value = { visible: false, song: null }
    } catch {
      // 拦截器已提示（含已在歌单）
    } finally {
      adding.value = false
    }
  }

  return {
    favoredIds,
    adding,
    addPanel,
    myPlaylists,
    playlistLoading,
    loadFavored,
    markFavored,
    toggleFavorite,
    openAddPanel,
    addToPlaylist,
  }
}
