/** 兜底修复：从播放页等返回后原生 tabBar 偶发不恢复，onShow 时强制拉起 */
export function ensureTabBar() {
  uni.showTabBar({ fail: () => undefined })
}
