import { defineConfig, type Plugin } from 'vite'
import uni from '@dcloudio/vite-plugin-uni'
import os from 'node:os'

/**
 * 编译时探测本机局域网 IP，注入 BASE_URL。
 * 换网络/换路由后直接 npm run build:mp-weixin，无需改任何源码。
 *
 * 规则：
 * - 优先取 192.168./10. 开头的 IPv4（家用/办公路由网段）
 * - 排除虚拟网卡常见网段（172.16~31 Docker/WSL、169.254 链路本地、127 回环）
 * - 探测失败回落 127.0.0.1（开发者工具本机调试仍可用）
 * - 多网卡选不对时可用环境变量 MP_BASE_IP 强制指定，如 MP_BASE_IP=192.168.31.40
 * - MP_BASE_IP 支持完整地址（自动识别）：
 *     MP_BASE_IP=8.138.98.1            → http://8.138.98.1（云服务器 80 端口）
 *     MP_BASE_IP=8.138.98.1:8080       → http://8.138.98.1:8080
 *     MP_BASE_IP=http://192.168.1.5:8080 → 原样使用
 *   不带端口且是公网 IP（非 192.168./10./127. 开头）时默认 80 端口（省略），
 *   局域网 IP 保持默认 8080（本地开发网关端口）。
 */
function resolveLanIp(): string {
  if (process.env.MP_BASE_IP) {
    const raw = process.env.MP_BASE_IP
    if (/^https?:\/\//.test(raw)) return raw
    if (raw.includes(':')) return `http://${raw}`
    // 纯 IP：公网地址默认走 80（云服务器），局域网保持 8080（本地开发）
    const isLan = /^(192\.168\.|10\.|127\.)/.test(raw)
    return isLan ? `http://${raw}:8080` : `http://${raw}`
  }
  const nets = os.networkInterfaces()
  const candidates: string[] = []
  for (const addrs of Object.values(nets)) {
    for (const net of addrs ?? []) {
      if (net.family !== 'IPv4' || net.internal) continue
      const ip = net.address
      if (ip.startsWith('169.254.')) continue // 链路本地
      if (/^172\.(1[6-9]|2\d|3[01])\./.test(ip)) continue // Docker/WSL 虚拟网卡
      if (ip.endsWith('.137.1') || ip.endsWith('.137.2')) continue // Windows 移动热点承载网卡(ICS)
      if (ip.startsWith('192.168.') || ip.startsWith('10.')) candidates.push(ip)
    }
  }
  // 多候选时优先 192.168.（家用路由最常见），其次 10.
  const ip = candidates.find((c) => c.startsWith('192.168.')) ?? candidates[0] ?? '127.0.0.1'
  return `http://${ip}:8080`
}

const BASE_URL = resolveLanIp()
console.log(`[mini] BASE_URL = ${BASE_URL}（编译时自动探测）`)

/** 编译期常量注入：src/config.ts 里的 __MP_BASE_URL__ 占位符替换为探测结果 */
function injectBaseUrl(): Plugin {
  return {
    name: 'inject-base-url',
    config() {
      return {
        define: {
          __MP_BASE_URL__: JSON.stringify(BASE_URL),
        },
      }
    },
  }
}

export default defineConfig({
  plugins: [uni(), injectBaseUrl()],
})
