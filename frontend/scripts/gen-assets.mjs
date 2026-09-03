/**
 * 生成封面/头像/轮播海报 PNG（Node 无依赖，手写 PNG 编码器 + 简单渐变/文字渲染）
 * 用法：node scripts/gen-assets.mjs
 * 输出：public/covers/{id}.png、public/avatars/{id}.png、public/banners/banner-{n}.png
 */
import { deflateSync } from 'node:zlib'
import { mkdirSync, writeFileSync, readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { dirname } from 'node:path'

const ROOT = dirname(fileURLToPath(import.meta.url)) // scripts/
const DATA = JSON.parse(readFileSync(new URL('./assets-data.json', import.meta.url), 'utf8'))

mkdirSync(new URL('../public/covers/', import.meta.url), { recursive: true })
mkdirSync(new URL('../public/avatars/', import.meta.url), { recursive: true })
mkdirSync(new URL('../public/banners/', import.meta.url), { recursive: true })

// ===== 最小 PNG 编码器（RGBA → PNG，无依赖） =====
const CRC_TABLE = (() => {
  const t = new Uint32Array(256)
  for (let n = 0; n < 256; n++) {
    let c = n
    for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1
    t[n] = c >>> 0
  }
  return t
})()
function crc32(buf) {
  let c = 0xffffffff
  for (let i = 0; i < buf.length; i++) c = CRC_TABLE[(c ^ buf[i]) & 0xff] ^ (c >>> 8)
  return (c ^ 0xffffffff) >>> 0
}
function chunk(type, data) {
  const len = Buffer.alloc(4)
  len.writeUInt32BE(data.length)
  const body = Buffer.concat([Buffer.from(type, 'ascii'), data])
  const crc = Buffer.alloc(4)
  crc.writeUInt32BE(crc32(body))
  return Buffer.concat([len, body, crc])
}
function encodePNG(width, height, rgba) {
  // 滤波：每行 filter 0（None）——渐变图足够小
  const stride = width * 4
  const raw = Buffer.alloc((stride + 1) * height)
  for (let y = 0; y < height; y++) {
    raw[y * (stride + 1)] = 0
    rgba.copy(raw, y * (stride + 1) + 1, y * stride, (y + 1) * stride)
  }
  const ihdr = Buffer.alloc(13)
  ihdr.writeUInt32BE(width, 0)
  ihdr.writeUInt32BE(height, 4)
  ihdr[8] = 8  // bit depth
  ihdr[9] = 6  // color type RGBA
  return Buffer.concat([
    Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]),
    chunk('IHDR', ihdr),
    chunk('IDAT', deflateSync(raw, { level: 9 })),
    chunk('IEND', Buffer.alloc(0)),
  ])
}

// ===== 颜色工具 =====
function hex(c) {
  return [parseInt(c.slice(1, 3), 16), parseInt(c.slice(3, 5), 16), parseInt(c.slice(5, 7), 16)]
}
function mix(a, b, t) {
  return [a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t, a[2] + (b[2] - a[2]) * t]
}

// ===== 5x7 像素字体（ASCII 可打印子集 + 常用中文用图形替代不了，中文改用"声波纹+渐变"设计） =====

/** 画布：线性渐变背景 + 简单装饰 */
function makeCanvas(w, h, palPair) {
  const [c1, c2] = [hex(palPair[0]), hex(palPair[1])]
  const buf = Buffer.alloc(w * h * 4)
  for (let y = 0; y < h; y++) {
    for (let x = 0; x < w; x++) {
      // 对角渐变
      const t = (x / w + y / h) / 2
      let [r, g, b] = mix(c1, c2, t)
      // 大同心圆装饰（唱片感）
      const dx = x - w / 2, dy = y - h / 2
      const d = Math.sqrt(dx * dx + dy * dy)
      const ring = Math.sin(d / (Math.min(w, h) / 14)) * 0.5 + 0.5
      const ringA = Math.max(0, 1 - d / (Math.min(w, h) * 0.62))
      const rr = r + (255 - r) * ring * ringA * 0.16
      const gg = g + (255 - g) * ring * ringA * 0.16
      const bb = b + (255 - b) * ring * ringA * 0.16
      const i = (y * w + x) * 4
      buf[i] = Math.min(255, rr)
      buf[i + 1] = Math.min(255, gg)
      buf[i + 2] = Math.min(255, bb)
      buf[i + 3] = 255
    }
  }
  return buf
}

/** 声波条装饰（底部） */
function drawWave(buf, w, h, phase) {
  for (let x = 0; x < w; x++) {
    const amp = (Math.sin(x / 18 + phase) * 0.5 + 0.5) * (h * 0.06)
    const y0 = h * 0.86 - amp
    for (let y = Math.floor(y0); y < h * 0.86; y++) {
      const i = (y * w + x) * 4
      buf[i] = Math.min(255, buf[i] + 70)
      buf[i + 1] = Math.min(255, buf[i + 1] + 70)
      buf[i + 2] = Math.min(255, buf[i + 2] + 70)
    }
  }
}

// ===== 生成：歌曲封面 300x300 =====
DATA.covers.forEach((c, idx) => {
  const pal = DATA.palettes[idx % DATA.palettes.length]
  const w = 300, h = 300
  const buf = makeCanvas(w, h, pal)
  drawWave(buf, w, h, idx)
  writeFileSync(new URL(`../public/covers/${c.id}.png`, import.meta.url), encodePNG(w, h, buf))
})

// ===== 生成：歌手头像 200x200 =====
DATA.avatars.forEach((a, idx) => {
  const pal = DATA.palettes[(idx + 4) % DATA.palettes.length]
  const w = 200, h = 200
  const buf = makeCanvas(w, h, pal)
  writeFileSync(new URL(`../public/avatars/${a.id}.png`, import.meta.url), encodePNG(w, h, buf))
})

// ===== 生成：轮播海报 1200x380 =====
DATA.banners.forEach((b) => {
  const pal = DATA.palettes[b.pal]
  const w = 1200, h = 380
  const buf = makeCanvas(w, h, pal)
  // 右侧唱片装饰
  const cx = w * 0.78, cy = h / 2, R = h * 0.36
  for (let y = 0; y < h; y++) {
    for (let x = 0; x < w; x++) {
      const dx = x - cx, dy = y - cy
      const d = Math.sqrt(dx * dx + dy * dy)
      if (d < R) {
        const i = (y * w + x) * 4
        const ring = Math.sin(d / 10) * 0.5 + 0.5
        const dark = mix([20, 24, 34], [38, 44, 58], ring)
        const edge = Math.max(0, 1 - Math.abs(d - R * 0.35) / 8)
        buf[i] = Math.min(255, dark[0] * (1 - edge) + 80 * edge)
        buf[i + 1] = Math.min(255, dark[1] * (1 - edge) + 100 * edge)
        buf[i + 2] = Math.min(255, dark[2] * (1 - edge) + 140 * edge)
      }
    }
  }
  writeFileSync(new URL(`../public/banners/${b.file}.png`, import.meta.url), encodePNG(w, h, buf))
})

console.log('assets generated:', DATA.covers.length, 'covers,', DATA.avatars.length, 'avatars,', DATA.banners.length, 'banners')
