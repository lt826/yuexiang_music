import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { getSongPlay, reportPlay } from '@/api/song'
import { recordHistory } from '@/api/favorite'
import { radioTune } from '@/api/ai'
import { useUserStore } from '@/stores/user'
import { fullUrl } from '@/config'
import type { SongVO } from '@/api/types'

/** 循环模式（对齐 Web 端：顺序、列表循环、单曲循环、随机） */
export type PlayMode = 'order' | 'loop' | 'one' | 'random'

/** 睡眠定时器模式：off 关闭；song 播完当前这首停止；time 定时暂停 */
export type SleepMode = 'off' | 'song' | 'time'

/** 倍速档位（微信 BackgroundAudioManager.playbackRate 支持范围） */
export const PLAY_RATES = [0.5, 0.75, 1.0, 1.25, 1.5, 2.0] as const

/** LRC 歌词行 */
export interface LyricLine {
  time: number
  text: string
}

/**
 * 播放器全局状态：微信小程序用 BackgroundAudioManager
 * （全局单例，支持后台播放/锁屏控制；title 必须在设 src 前赋值）
 */
export const usePlayerStore = defineStore('player', () => {
  const userStore = useUserStore()

  // ===== 队列 =====
  const queue = ref<SongVO[]>([])
  const currentIndex = ref(-1)

  // ===== 播放状态 =====
  const playing = ref(false)
  const currentTime = ref(0)
  const duration = ref(0)
  const loading = ref(false)
  const loadError = ref('')
  const playMode = ref<PlayMode>((uni.getStorageSync('yx_playMode') as PlayMode) || 'order')

  // ===== 倍速 =====
  const playRate = ref<number>(Number(uni.getStorageSync('yx_playRate')) || 1.0)

  // ===== 睡眠定时器 =====
  const sleepMode = ref<SleepMode>('off')
  /** time 模式的绝对截止时间戳（ms）；后台 JS 定时器会被节流，靠 onTimeUpdate 轮询兜底 */
  const sleepEndTime = ref(0)
  const sleepTimer = ref<number | null>(null)

  const currentSong = ref<SongVO | null>(null)
  const lyricLines = ref<LyricLine[]>([])

  const hasCurrent = computed(() => currentIndex.value >= 0 && currentSong.value !== null)

  /** 电台模式（共享时间轴）：禁止拖进度/上下曲，播完自动对时；暂停/倍速不受限 */
  const radioMode = ref(false)

  /**
   * 电台会话（跨页面存活）：记录频道 key/频率/节目单，radio 页重进时恢复"正在收听"画面。
   * 离开电台页去逛别处时电台仍在收听——歌播完由 store 自动对时接上。
   * 注意：普通播放入口（playQueue 等）会退出电台模式，只有电台页的 restorePlaying
   * 依赖本会话恢复收听画面。
   */
  const radioChannel = ref('')
  const radioFreq = ref('')
  const radioPlaylistId = ref(0)
  const radioPlaylistTitle = ref('')

  /**
   * 电台对时（store 内闭环，页面卸载后依然生效）：
   * 一首播完 → 调 tune 拿电台最新位置静默续播。
   */
  async function radioResync() {
    if (!radioMode.value || !radioChannel.value) return
    try {
      const vo = await radioTune(radioChannel.value)
      // 对时请求期间用户可能已退出电台并点了别的歌：此时不能覆盖新队列
      if (!radioMode.value) return
      if (!vo.songs?.length) return
      radioPlaylistId.value = vo.playlistId || 0
      radioPlaylistTitle.value = vo.playlistTitle || radioPlaylistTitle.value
      queue.value = vo.songs.map(toSongVOForRadio)
      currentIndex.value = vo.currentIndex
      pendingSeek = vo.offsetSeconds
      await loadCurrent()
    } catch {
      // 对时失败：静默停在该首结尾；用户任意切台操作可再触发
    }
  }

  /** AiSongVO → 播放器 SongVO（电台队列转换，独立于 api/ai 的 toSongVO 避免循环依赖） */
  function toSongVOForRadio(s: { id: number; name: string; singerName: string | null; singerId: number | null; style: string | null; playCount: number }): SongVO {
    return {
      id: s.id,
      name: s.name,
      cover: null,
      url: null,
      lyric: null,
      style: s.style,
      playCount: s.playCount,
      singerId: s.singerId ?? 0,
      singerName: s.singerName,
    }
  }

  /**
   * 电台监听开关（radio 页调频时调用）：置 radioMode 并记录频道元数据；
   * 传 null 退出电台模式。对时逻辑在 store 内闭环，无需页面回调。
   */
  function setRadioChannel(meta: { channel: string; freq: string; playlistId: number; title: string } | null) {
    if (meta) {
      radioChannel.value = meta.channel
      radioFreq.value = meta.freq
      radioPlaylistId.value = meta.playlistId
      radioPlaylistTitle.value = meta.title
      radioMode.value = true
    } else {
      radioChannel.value = ''
      radioFreq.value = ''
      radioPlaylistId.value = 0
      radioPlaylistTitle.value = ''
      radioMode.value = false
    }
  }

  let audio: UniApp.BackgroundAudioManager | null = null
  /** 倍速切换触发的重载中（用于 onCanplay 恢复进度） */
  let reloadingForRate = false
  /** 重载完成后待恢复的播放进度（秒） */
  let pendingSeek: number | null = null

  /** 挂载全局音频单例事件（App onLaunch 调一次） */
  function setupAudio() {
    if (audio) return
    audio = uni.getBackgroundAudioManager()
    audio.onPlay(() => {
      playing.value = true
      // 起播后补设倍速：src 赋值瞬间设置的速率可能被微信初始化流程覆盖
      if (audio && playRate.value !== 1.0) {
        audio.playbackRate = playRate.value
      }
    })
    audio.onPause(() => {
      playing.value = false
    })
    audio.onStop(() => {
      playing.value = false
    })
    audio.onCanplay(() => {
      loading.value = false
      duration.value = audio?.duration || 0
      // 倍速重载完成：跳回切换时的进度继续播（只处理一次）
      if (pendingSeek != null && audio) {
        const at = pendingSeek
        pendingSeek = null
        audio.seek(at)
      }
    })
    audio.onTimeUpdate(() => {
      currentTime.value = audio?.currentTime || 0
      if (audio && audio.duration && audio.duration !== duration.value) {
        duration.value = audio.duration
      }
      checkSleep()
    })
    audio.onEnded(() => {
      // 电台监听模式：不本地续播，store 内直接重新对时（离开电台页也照常接上电台）
      if (radioMode.value) {
        void radioResync()
        return
      }
      // 睡眠定时器「播完当前这首」：到点停止，不再续播下一首
      if (sleepMode.value === 'song') {
        clearSleep()
        playing.value = false
        return
      }
      if (playMode.value === 'one') {
        // 单曲循环：BackgroundAudioManager 结束后 seek(0) 不一定重新播放，重新加载最稳
        playAt(currentIndex.value)
        return
      }
      next(false)
    })
    audio.onError(() => {
      loading.value = false
      playing.value = false
      loadError.value = '音频加载失败，请稍后重试或换一首'
    })
  }

  // ===== 播放控制 =====

  /** 以新队列播放（各列表页点歌入口）；未登录引导去登录页。
   *  用户主动点歌 = 离开电台收听：先退出电台模式，否则 seek/切歌仍被电台锁死 */
  async function playQueue(songs: SongVO[], index: number) {
    if (!userStore.isLoggedIn) {
      uni.showToast({ title: '请先登录', icon: 'none' })
      setTimeout(() => {
        uni.navigateTo({ url: '/pages/login/login' })
      }, 500)
      return
    }
    if (radioMode.value) setRadioChannel(null)
    queue.value = [...songs]
    currentIndex.value = index
    await loadCurrent()
  }

  /** 以新队列从指定秒数起播（心情电台调频专用：playQueueAt 置于 setRadioChannel 之后调用，
   *  此处不再清电台模式，否则调频自身就被解锁了） */
  async function playQueueAt(songs: SongVO[], index: number, offsetSeconds: number) {
    if (!userStore.isLoggedIn) {
      uni.showToast({ title: '请先登录', icon: 'none' })
      setTimeout(() => {
        uni.navigateTo({ url: '/pages/login/login' })
      }, 500)
      return
    }
    queue.value = [...songs]
    currentIndex.value = index
    pendingSeek = offsetSeconds
    await loadCurrent()
  }

  async function playAt(index: number) {
    if (index < 0 || index >= queue.value.length) return
    // 电台模式下点选节目单其他歌：从头播该首（其电台时刻已过）；点当前这首则追电台进度
    const at = radioMode.value ? (index === currentIndex.value ? Math.max(0, currentTime.value) : 0) : null
    currentIndex.value = index
    if (at != null) pendingSeek = at
    await loadCurrent()
  }

  function toggle() {
    if (!audio || !hasCurrent.value) return
    if (playing.value) {
      audio.pause()
    } else {
      audio.play()
    }
  }

  function next(userTriggered = true) {
    if (queue.value.length === 0) return
    // 电台模式：禁手动切歌（自然播完走 radioEndedListener，见 onEnded）
    if (radioMode.value && userTriggered) return
    if (playMode.value === 'random') {
      // 随机：避免连续两首重复
      let rand = Math.floor(Math.random() * queue.value.length)
      if (queue.value.length > 1 && rand === currentIndex.value) {
        rand = (rand + 1) % queue.value.length
      }
      playAt(rand)
      return
    }
    const nextIndex = currentIndex.value + 1
    if (nextIndex >= queue.value.length) {
      if (playMode.value === 'order' && userTriggered) return
      playAt(0)
      return
    }
    playAt(nextIndex)
  }

  function prev() {
    if (queue.value.length === 0) return
    // 电台模式：不能倒回（电台时间轴只进不退）
    if (radioMode.value) return
    const prevIndex = currentIndex.value - 1
    playAt(prevIndex >= 0 ? prevIndex : queue.value.length - 1)
  }

  function seek(time: number) {
    // 电台模式：拖进度会偏离共享时间轴，禁掉（UI 层滑块已禁用，这里兜底）
    if (radioMode.value) return
    if (audio) audio.seek(time)
    currentTime.value = time
  }

  function cyclePlayMode() {
    const modes: PlayMode[] = ['order', 'loop', 'one', 'random']
    const nextMode = modes[(modes.indexOf(playMode.value) + 1) % modes.length] ?? 'order'
    playMode.value = nextMode
    uni.setStorageSync('yx_playMode', nextMode)
  }

  // ===== 倍速 =====

  /** 设置倍速并即时生效（持久化对齐 yx_playMode） */
  function setPlayRate(rate: number) {
    playRate.value = rate
    uni.setStorageSync('yx_playRate', rate)
    if (!audio || !playing.value) return
    // 微信 bgAudio 的 playbackRate 在播放中直改不生效；仅换 src 在新基础库上
    // 又会因 src 去重不触发重载。最可靠组合：stop 强制释放播放器 → 换带时间戳
    // 的 src 完整重建 → onPlay 里重设倍速 + onCanplay 里 seek 回原进度。
    const resumeAt = audio.currentTime
    const song = queue.value[currentIndex.value]
    if (!song) return
    reloadingForRate = true
    pendingSeek = resumeAt
    loading.value = true
    audio.stop()
    const base = fullUrl(song.url) || ''
    const sep = base.includes('?') ? '&' : '?'
    audio.src = `${base}${sep}ratets=${Date.now()}`
  }

  // ===== 睡眠定时器 =====

  /** 到点暂停（保留进度与队列，醒来可续播）；双保险：前台 timer + onTimeUpdate 轮询 */
  function checkSleep() {
    if (sleepMode.value !== 'time' || !sleepEndTime.value) return
    if (Date.now() >= sleepEndTime.value) {
      clearSleep()
      if (audio) audio.pause()
      uni.showToast({ title: '睡眠定时到，已暂停播放', icon: 'none' })
    }
  }

  function clearSleep() {
    if (sleepTimer.value) {
      clearTimeout(sleepTimer.value)
      sleepTimer.value = null
    }
    sleepMode.value = 'off'
    sleepEndTime.value = 0
  }

  /**
   * 设置睡眠定时器。
   * @param mode 'song' 播完当前这首停止；'time' 定时暂停
   * @param minutes time 模式的分钟数
   */
  function setSleep(mode: SleepMode, minutes?: number) {
    clearSleep()
    if (mode === 'off') return
    sleepMode.value = mode
    if (mode === 'time' && minutes && minutes > 0) {
      sleepEndTime.value = Date.now() + minutes * 60_000
      // 前台路径：到点立即暂停；后台被系统节流时由 onTimeUpdate 兜底
      sleepTimer.value = setTimeout(() => checkSleep(), minutes * 60_000) as unknown as number
    }
  }

  /** 剩余秒数（time 模式倒计时显示用），非 time 模式恒为 0 */
  const sleepRemaining = computed(() => {
    if (sleepMode.value !== 'time' || !sleepEndTime.value) return 0
    return Math.max(0, Math.ceil((sleepEndTime.value - Date.now()) / 1000))
  })

  /** 停止播放并清空队列（退出登录时调用） */
  function stop() {
    if (audio) audio.stop()
    clearSleep()
    // 停播即退出电台监听，避免残留 radioMode 锁住下一次播放
    if (radioMode.value) setRadioChannel(null)
    queue.value = []
    currentIndex.value = -1
    currentSong.value = null
    lyricLines.value = []
    playing.value = false
    currentTime.value = 0
    duration.value = 0
  }

  // ===== 内部 =====

  /** 加载当前歌曲详情（最新 url 与歌词）并起播 */
  async function loadCurrent() {
    setupAudio()
    const song = queue.value[currentIndex.value]
    if (!song || !audio) return
    loading.value = true
    loadError.value = ''
    try {
      const detail = await getSongPlay(song.id)
      currentSong.value = detail
      parseLyric(detail.lyric || '')
      if (!detail.url) {
        loading.value = false
        playing.value = false
        loadError.value = '该歌曲没有可播放的音频文件'
        return
      }
      // title 必须先于 src 设置，否则 iOS 不起播
      audio.title = detail.name
      audio.singer = detail.singerName || '未知歌手'
      audio.epname = ''
      audio.coverImgUrl = fullUrl(detail.cover) || undefined
      audio.src = fullUrl(detail.url)
      // 应用持久化倍速（换曲后微信会重置为 1.0，需要每次重设）
      if (playRate.value !== 1.0) {
        audio.playbackRate = playRate.value
      }
      // 起播即计播放量 + 记历史；未登录直接跳过，避免匿名 401 触发"登录已失效"提示
      if (userStore.isLoggedIn) {
        reportPlay(song.id).catch(() => undefined)
        recordHistory(song.id).catch(() => undefined)
      }
    } catch {
      // 接口报错已由 request 层 toast，播放器停住即可
      loading.value = false
      playing.value = false
    }
  }

  /** 解析 LRC 歌词：[mm:ss.xx] 行 → { time(秒), text }（对齐 Web 端） */
  function parseLyric(raw: string) {
    lyricLines.value = []
    if (!raw) return
    const tag = /\[(\d{1,2}):(\d{1,2})(?:\.(\d{1,3}))?\]/g
    for (const line of raw.split(/\r?\n/)) {
      const times: number[] = []
      let match: RegExpExecArray | null
      tag.lastIndex = 0
      while ((match = tag.exec(line)) !== null) {
        const min = Number(match[1])
        const sec = Number(match[2])
        const ms = match[3] ? Number(match[3].padEnd(3, '0')) : 0
        times.push(min * 60 + sec + ms / 1000)
      }
      const text = line.replace(tag, '').trim()
      if (times.length > 0 && text) {
        for (const time of times) {
          lyricLines.value.push({ time, text })
        }
      }
    }
    lyricLines.value.sort((a, b) => a.time - b.time)
  }

  /** 当前歌词行下标（时间轴定位） */
  const currentLyricIndex = computed(() => {
    if (lyricLines.value.length === 0) return -1
    let idx = -1
    for (let i = 0; i < lyricLines.value.length; i++) {
      const line = lyricLines.value[i]
      if (line && line.time <= currentTime.value) {
        idx = i
      } else {
        break
      }
    }
    return idx
  })

  return {
    queue,
    currentIndex,
    playing,
    currentTime,
    duration,
    loading,
    loadError,
    playMode,
    radioMode,
    radioChannel,
    radioFreq,
    radioPlaylistId,
    radioPlaylistTitle,
    playRate,
    sleepMode,
    sleepEndTime,
    sleepRemaining,
    currentSong,
    lyricLines,
    currentLyricIndex,
    hasCurrent,
    setupAudio,
    playQueue,
    playQueueAt,
    playAt,
    toggle,
    next,
    prev,
    seek,
    cyclePlayMode,
    setPlayRate,
    setSleep,
    clearSleep,
    stop,
    setRadioChannel,
  }
})
