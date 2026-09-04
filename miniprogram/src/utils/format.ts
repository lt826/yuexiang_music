/** 播放量等大数格式化（对齐 Web 端 formatCount） */
export function formatCount(n: number | null | undefined): string {
  const v = n ?? 0
  if (v >= 100000000) return (v / 100000000).toFixed(1) + '亿'
  if (v >= 10000) return (v / 10000).toFixed(1) + '万'
  return String(v)
}

/** 秒 → mm:ss（播放器进度） */
export function formatTime(sec: number): string {
  const s = Math.max(0, Math.floor(sec || 0))
  const m = Math.floor(s / 60)
  const r = s % 60
  return `${String(m).padStart(2, '0')}:${String(r).padStart(2, '0')}`
}
