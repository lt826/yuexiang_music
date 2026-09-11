/**
 * 一起听 WebSocket 链路测试（Node 22 内置 WebSocket，addEventListener 风格）：
 * u1（圈主）与 u2（成员）加入房间 → 同步状态 → 聊天 → 控播广播
 */
const GATEWAY_WS = 'ws://127.0.0.1:8080/api/circle/ws/listen'
const ROOM_ID = 2

async function login(username, password) {
  const res = await fetch('http://127.0.0.1:8080/api/user/login', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username, password }),
  })
  const r = await res.json()
  return r.data.token
}

function connect(token, label, received) {
  return new Promise((resolve, reject) => {
    const ws = new WebSocket(`${GATEWAY_WS}/${ROOM_ID}?token=${encodeURIComponent(token)}`)
    const log = (...a) => console.log(`[${label}]`, ...a)
    ws.addEventListener('open', () => {
      log('已连接，发送 JOIN_ROOM')
      ws.send(JSON.stringify({ type: 'JOIN_ROOM', roomId: ROOM_ID }))
      resolve(ws)
    })
    ws.addEventListener('message', (ev) => {
      const msg = JSON.parse(ev.data)
      received.push(msg)
      if (msg.type === 'ROOM_STATE') {
        log('ROOM_STATE: status=%s song=%s members=%d', msg.data.status, msg.data.song?.name || '无', msg.data.memberCount)
      } else if (msg.type === 'PLAYBACK_CTRL') {
        log('>> 广播 PLAYBACK_CTRL: action=%s position=%s songId=%s', msg.data.action, msg.data.position, msg.data.songId ?? '-')
      } else if (msg.type === 'CHAT_MESSAGE') {
        log('CHAT: %s: %s', msg.data.username, msg.data.content)
      } else if (msg.type === 'USER_JOIN' || msg.type === 'USER_LEAVE') {
        log('%s: %s (在线%d)', msg.type, msg.data.username, msg.data.online)
      } else if (msg.type === 'SYSTEM') {
        log('SYSTEM:', msg.data.content)
      } else if (msg.type === 'ERROR') {
        log('ERROR:', msg.data.message)
      } else {
        log(msg.type)
      }
    })
    ws.addEventListener('error', (e) => reject(new Error('WS 连接失败')))
    ws.addEventListener('close', (e) => log('closed', e.code))
  })
}

const sleep = (ms) => new Promise((r) => setTimeout(r, ms))

async function main() {
  console.log('--- 登录两个用户 ---')
  const [tok1, tok2] = await Promise.all([
    login('musiclover01', '123456'),
    login('quietnight', '123456'),
  ])

  const inbox1 = [], inbox2 = []

  console.log('--- u1 圈主进房 ---')
  const ws1 = await connect(tok1, 'u1圈主', inbox1)
  await sleep(800)

  console.log('--- u2 成员进房 ---')
  const ws2 = await connect(tok2, 'u2成员', inbox2)
  await sleep(800)

  console.log('--- u2 发聊天 ---')
  ws2.send(JSON.stringify({ type: 'SEND_MESSAGE', content: '大家好，一起听吧！' }))
  await sleep(500)

  console.log('--- u1 圈主播放 ---')
  ws1.send(JSON.stringify({ type: 'PLAYBACK_CTRL', action: 'play', payload: { time: 0 } }))
  await sleep(500)

  console.log('--- u1 拖进度到 30s ---')
  ws1.send(JSON.stringify({ type: 'PLAYBACK_CTRL', action: 'seek', payload: { time: 30 } }))
  await sleep(500)

  console.log('--- u2 尝试控播（应被拒） ---')
  ws2.send(JSON.stringify({ type: 'PLAYBACK_CTRL', action: 'pause' }))
  await sleep(500)

  console.log('--- u1 切歌到 44 ---')
  ws1.send(JSON.stringify({ type: 'PLAYBACK_CTRL', action: 'playSong', payload: { songId: 44 } }))
  await sleep(800)

  console.log('--- 结果断言 ---')
  const u2GotPlay = inbox2.some((m) => m.type === 'PLAYBACK_CTRL' && m.data.action === 'play')
  const u2GotSeek = inbox2.some((m) => m.type === 'PLAYBACK_CTRL' && m.data.action === 'seek')
  const u2GotSong = inbox2.some((m) => m.type === 'PLAYBACK_CTRL' && m.data.action === 'playSong' && m.data.songId === 44)
  const u2GotSongSnapshot = inbox2.some((m) => m.type === 'PLAYBACK_CTRL' && m.data.action === 'playSong' && m.data.song?.name)
  const u2GotChat = inbox2.some((m) => m.type === 'CHAT_MESSAGE')
  const u2GotDeny = inbox2.some((m) => m.type === 'ERROR' && (m.data.message || '').includes('圈主'))
  console.log('u2 收到播放广播:', u2GotPlay ? 'PASS' : 'FAIL')
  console.log('u2 收到拖动广播:', u2GotSeek ? 'PASS' : 'FAIL')
  console.log('u2 收到切歌广播:', u2GotSong ? 'PASS' : 'FAIL')
  console.log('u2 收到切歌歌曲快照:', u2GotSongSnapshot ? 'PASS' : 'FAIL')
  console.log('u2 收到聊天:', u2GotChat ? 'PASS' : 'FAIL')
  console.log('u2 控播被权限拦截:', u2GotDeny ? 'PASS' : 'FAIL')

  ws1.close()
  ws2.close()
  await sleep(300)
  process.exit(0)
}

main().catch((e) => { console.error('测试失败:', e.message); process.exit(1) })
