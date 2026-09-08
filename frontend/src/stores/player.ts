import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type { SongVO } from '@/api/home'
import { getSongPlay, reportPlay } from '@/api/song'
import { radioTune } from '@/api/ai'
import { useUserStore } from '@/stores/user'
import { useAuthPromptStore } from '@/stores/authPrompt'

/** 循环模式（对齐详细设计 13.2：顺序、单曲循环、列表循环、随机） */
export type PlayMode = 'order' | 'one' | 'loop' | 'random'

/** 倍速档位（与小程序端 PLAY_RATES 对齐） */
export const PLAY_RATES = [0.5, 0.75, 1.0, 1.25, 1.5, 2.0]

/** LRC 歌词行 */
export interface LyricLine {
  time: number
  text: string
}/** 播放器全局状态（对齐详细设计 13.4 playerStore：队列、循环模式、进度） */
export const usePlayerStore = defineStore('player', () => {
  const userStore = useUserStore()
  const authPrompt = useAuthPromptStore()

  // ===== 队列 =====
  const queue = ref<SongVO[]>([])
  const currentIndex = ref(-1)

  // ===== 播放状态 =====
  const playing = ref(false)
  const currentTime = ref(0)
  const duration = ref(0)
  const volume = ref(Number(localStorage.getItem('playerVolume')) || 0.8)
  /** 静音标记（记住上次音量，取消静音恢复） */
  const muted = ref(false)
  const playMode = ref<PlayMode>((localStorage.getItem('playMode') as PlayMode) || 'order')
  /** 播放倍速档位（0.5/0.75/1/1.25/1.5/2），持久化，换曲自动保持 */
  const playRate = ref<number>(Number(localStorage.getItem('playRate')) || 1.0)

  /** 电台模式（共享时间轴）：禁止拖进度/上下曲，播完由电台页重新对时；暂停/音量/倍速不受限 */
  const radioMode = ref(false)

  /**
   * 电台会话（跨页面存活，存放于 store 而非电台页组件）：
   * 离开电台页去逛别的页面时，电台仍在"收听"——歌播完自动对时接上电台。
   * 频道 key / 频率 / 节目单让 RadioView 重进时能恢复"正在收听 XX 频道"的画面。
   */
  const radioChannel = ref('')
  const radioFreq = ref('')
  const radioPlaylistId = ref(0)
  const radioPlaylistTitle = ref('')

  /**
   * 电台对时（store 内闭环，页面卸载后依然生效）：
   * 当前正在播完一首 → 调 tune 拿电台最新位置继续播。
   * 不弹全屏播放页（后台静默续上，像真收音机）。
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
      const list = vo.songs.map((s) => ({
        id: s.id,
        name: s.name,
        cover: null,
        url: null,
        lyric: null,
        style: s.style,
        playCount: s.playCount,
        singerId: s.singerId ?? 0,
        singerName: s.singerName,
      }))
      // 静默换曲：替换队列并从电台当前位置起播；保持 radioMode 不被重置
      queue.value = list
      currentIndex.value = vo.currentIndex
      fullScreen.value = false
      await loadCurrent(vo.offsetSeconds)
    } catch {
      // 对时失败（网络抖动等）：静默，自然停在该首结尾；用户任意交互可再触发
    }
  }

  /** 音频加载中（拉详情/缓冲，用于 UI 等待反馈） */
  const loading = ref(false)

  /** 播放失败原因（加载出错的可见反馈，UI 层展示） */
  const loadError = ref('')

  // ===== 当前歌曲 =====
  const currentSong = ref<SongVO | null>(null)
  const lyricLines = ref<LyricLine[]>([])

  // ===== 全屏播放页（浮层，仿网易云沉浸播放） =====
  const fullScreen = ref(false)

  function openFullScreen() {
    fullScreen.value = true
  }

  function closeFullScreen() {
    fullScreen.value = false
  }

  /** audio 元素由 PlayerBar 挂载并注入，store 只发指令不碰 DOM */
  let audioEl: HTMLAudioElement | null = null

  function bindAudio(el: HTMLAudioElement) {
    audioEl = el
    el.volume = volume.value
    // 音频加载/解码失败可见反馈（此前静默吞掉，用户以为"没反应"）
    el.addEventListener('error', () => {
      loading.value = false
      playing.value = false
      loadError.value = '音频加载失败，请稍后重试或换一首'
    })
    el.addEventListener('waiting', () => {
      loading.value = true
    })
    el.addEventListener('playing', () => {
      loading.value = false
      loadError.value = ''
    })
    el.addEventListener('canplay', () => {
      loading.value = false
    })
  }

  const hasCurrent = computed(() => currentIndex.value >= 0 && currentSong.value !== null)

  // ===== 播放控制 =====

  /**
   * 以新队列播放（首页/搜索页点歌入口）；点歌即开全屏播放页（酷狗式体验）。
   * 用户主动点歌 = 离开电台收听：先退出电台模式，否则 seek/切歌仍被电台锁死。
   */
  async function playQueue(songs: SongVO[], index: number) {
    // 未登录：弹登录拦截窗，登录成功后自动恢复本次播放
    if (!userStore.isLoggedIn) {
      authPrompt.open(() => {
        void playQueueInternal(songs, index)
      })
      return
    }
    await playQueueInternal(songs, index)
  }

  async function playQueueInternal(songs: SongVO[], index: number) {
    if (radioMode.value) setRadioChannel(null)
    queue.value = [...songs]
    currentIndex.value = index
    fullScreen.value = true
    await loadCurrent()
  }

  /** 以新队列从指定秒数起播（心情电台调频：seek 到电台时间轴位置） */
  async function playQueueAt(songs: SongVO[], index: number, offsetSeconds: number) {
    // 未登录：与 playQueue 同一套登录拦截（登录成功后自动恢复本次播放）
    if (!userStore.isLoggedIn) {
      authPrompt.open(() => {
        void playQueueAtOffset(songs, index, offsetSeconds)
      })
      return
    }
    await playQueueAtOffset(songs, index, offsetSeconds)
  }

  async function playQueueAtOffset(songs: SongVO[], index: number, offsetSeconds: number) {
    // 由 RadioView 调频调用（调用方已置 radioMode），此处只兜底；同段逻辑见 playQueueInternal
    queue.value = [...songs]
    currentIndex.value = index
    fullScreen.value = true
    await loadCurrent(offsetSeconds)
  }

  /** 播放队列中指定歌（电台页点选节目单条目 / 全屏页失败重试共用） */
  async function playAt(index: number) {
    if (index < 0 || index >= queue.value.length) {
      return
    }
    // 电台模式下点选节目单其他歌：重新对时到该首的电台位置（允许，等同电台自己跳节目）
    const at = radioMode.value ? await radioOffsetFor(index) : 0
    currentIndex.value = index
    await loadCurrent(at)
  }

  /**
   * 电台模式下点选第 index 首时的应播进度：
   * 若选的正是当前在播的这首 → 追电台时间轴；选其他首 → 从头播（该首的电台时刻已过）
   */
  async function radioOffsetFor(index: number): Promise<number> {
    return index === currentIndex.value ? Math.max(0, currentTime.value) : 0
  }

  /** 播放/暂停切换 */
  function toggle() {
    if (!audioEl) return
    if (audioEl.paused) {
      audioEl.play().catch(() => undefined)
    } else {
      audioEl.pause()
    }
  }

  function next(userTriggered = true) {
    if (queue.value.length === 0) return
    // 电台模式：禁手动切歌（自然播完走 radioEndedListener，见 onEnded）
    if (radioMode.value && userTriggered) return
    if (playMode.value === 'random') {
      const rand = Math.floor(Math.random() * queue.value.length)
      playAt(rand)
      return
    }
    const nextIndex = currentIndex.value + 1
    if (nextIndex >= queue.value.length) {
      // 列表播完：顺序模式停止，循环模式回到开头
      if (playMode.value === 'order' && userTriggered) {
        return
      }
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

  /** 停止播放并清空队列（退出登录时调用） */
  function stop() {
    if (audioEl) {
      audioEl.pause()
      audioEl.removeAttribute('src')
      audioEl.load()
    }
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

  /** 进度条拖动中 / seek 落地中：timeupdate 不回写，避免拖柄被播放进度拽回。
   *  seek 是异步操作：赋值 audio.currentTime 后浏览器要缓冲定位（几百 ms），
   *  期间 timeupdate 仍报告旧位置——若此时解除标志，进度条立即"弹回来"。 */
  const seeking = ref(false)

  function seek(time: number) {
    // 电台模式：拖进度会偏离共享时间轴，禁掉（UI 层拖动条置灰，这里兜底）
    if (radioMode.value) return
    // 立即回显目标位置，同时压住 timeupdate 回写
    currentTime.value = time
    seeking.value = true
    if (audioEl) {
      audioEl.currentTime = time
    } else {
      // 无音频元素（异常路径）：没人会触发 seeked，直接解除避免永久卡住
      seeking.value = false
    }
  }

  /** 浏览器完成异步 seek 后由 seeked 事件调用：恢复 timeupdate 回写 */
  function onSeeked() {
    seeking.value = false
    if (audioEl) {
      currentTime.value = audioEl.currentTime
    }
  }

  /** 拖动开始 / 结束（PlayerBar / FullPlayer 的 el-slider 事件回调）；
   *  拖动结束的解除由 seek() + seeked 事件接管，此处仅负责"拖动中"置位 */
  function setSeeking(v: boolean) {
    seeking.value = v
  }

  function setVolume(v: number) {
    const clamped = Math.min(1, Math.max(0, v))
    volume.value = clamped
    muted.value = clamped === 0
    if (audioEl) {
      audioEl.volume = clamped
      audioEl.muted = false
    }
    localStorage.setItem('playerVolume', String(clamped))
  }

  /** 静音/取消静音（静音记住当前音量，恢复时还原） */
  let volumeBeforeMute = 0
  function toggleMute() {
    if (!audioEl) return
    if (!muted.value) {
      volumeBeforeMute = volume.value
      muted.value = true
      audioEl.volume = 0
    } else {
      muted.value = false
      audioEl.volume = volumeBeforeMute || volume.value || 0.8
      volume.value = audioEl.volume
    }
  }

  function cyclePlayMode() {
    const modes: PlayMode[] = ['order', 'loop', 'one', 'random']
    const nextMode = modes[(modes.indexOf(playMode.value) + 1) % modes.length]
      ?? 'order'
    playMode.value = nextMode
    localStorage.setItem('playMode', nextMode)
  }

  /** 设置倍速；HTMLAudioElement 的 playbackRate 即设即生效 */
  function setPlayRate(rate: number) {
    playRate.value = rate
    localStorage.setItem('playRate', String(rate))
    if (audioEl) {
      audioEl.playbackRate = rate
    }
  }

  // ===== 内部 =====

  /** 加载当前歌曲详情（拿最新 url 与歌词）并起播；startAt 秒数大于 0 时 seek 到该进度（电台调频） */
  async function loadCurrent(startAt = 0) {
    const song = queue.value[currentIndex.value]
    if (!song) return
    loading.value = true
    loadError.value = ''
    try {
      // 播放接口下发完整详情（含 LRC 歌词）
      const detail = await getSongPlay(song.id)
      currentSong.value = detail
      parseLyric(detail.lyric || '')
      if (audioEl) {
        if (!detail.url) {
          // 无音频地址：明确提示而不是静默
          loading.value = false
          playing.value = false
          loadError.value = '该歌曲没有可播放的音频文件'
          return
        }
        audioEl.src = detail.url
        // 换曲后保持倍速（浏览器换 src 会重置为 1.0）
        audioEl.playbackRate = playRate.value
        // 电台调频：seek 到电台时间轴位置再起播（src 重载后 currentTime 才能落准）
        if (startAt > 0) {
          const seekTo = startAt
          const onReady = () => {
            if (!audioEl) return
            audioEl.currentTime = seekTo
            audioEl.removeEventListener('loadedmetadata', onReady)
          }
          audioEl.addEventListener('loadedmetadata', onReady)
        }
        audioEl.play().catch(() => {
          // 自动播放被拦截或解码失败：error 事件会给出可见反馈
          playing.value = false
        })
      }
      // 起播即计一次播放量；reportPlay 发 MQ 事件异步写计数与收听历史（需登录）
      if (userStore.isLoggedIn) {
        reportPlay(song.id).catch(() => undefined)
      }
    } catch {
      // 接口报错（审核中/下架/无版权）已在拦截器提示，播放器停住即可
      loading.value = false
      playing.value = false
    }
  }

  /** 解析 LRC 歌词：[mm:ss.xx] 行 → { time(秒), text } */
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

  /** audio 事件回调（PlayerBar 绑定后由元素事件触发） */
  function onTimeUpdate() {
    if (audioEl && !seeking.value) {
      currentTime.value = audioEl.currentTime
    }
  }

  function onLoadedMetadata() {
    if (audioEl) {
      duration.value = audioEl.duration || 0
    }
  }

  function onEnded() {
    // 电台监听模式：不本地续播，store 内直接重新对时（离开电台页也照常接上电台）
    if (radioMode.value) {
      void radioResync()
      return
    }
    if (playMode.value === 'one') {
      playAt(currentIndex.value)
      return
    }
    next(false)
  }

  /**
   * 电台监听开关（RadioView 注册）：置 radioMode 并记录频道元数据；
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

  function onPlay() {
    playing.value = true
  }

  function onPause() {
    playing.value = false
  }

  return {
    queue,
    currentIndex,
    playing,
    currentTime,
    duration,
    volume,
    muted,
    playMode,
    radioMode,
    radioChannel,
    radioFreq,
    radioPlaylistId,
    radioPlaylistTitle,
    loading,
    loadError,
    currentSong,
    lyricLines,
    currentLyricIndex,
    hasCurrent,
    seeking,
    fullScreen,
    openFullScreen,
    closeFullScreen,
    bindAudio,
    playQueue,
    playQueueAt,
    playAt,
    toggle,
    next,
    prev,
    stop,
    seek,
    setSeeking,
    setVolume,
    toggleMute,
    cyclePlayMode,
    playRate,
    setPlayRate,
    onTimeUpdate,
    onLoadedMetadata,
    onEnded,
    onPlay,
    onPause,
    onSeeked,
    setRadioChannel,
  }
})
