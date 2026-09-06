<script setup lang="ts">
import { onLaunch } from '@dcloudio/uni-app'
import { usePlayerStore } from '@/stores/player'

onLaunch(() => {
  // BackgroundAudioManager 是全局单例，尽早挂事件，保证后台/锁屏事件不丢
  usePlayerStore().setupAudio()
})
</script>

<style>
/* ===== 全局样式与主题变量（品牌蓝对齐 Web 端） ===== */
page {
  background: #f5f6fa;
  font-size: 28rpx;
  color: #22242a;
  font-family:
    -apple-system, BlinkMacSystemFont, 'Helvetica Neue', 'PingFang SC', 'Hiragino Sans GB',
    'Microsoft YaHei', sans-serif;
}

/* 通用工具类 */
.card {
  background: #fff;
  border-radius: 20rpx;
  padding: 24rpx;
  margin: 0 24rpx 24rpx;
}

.section-title {
  font-size: 32rpx;
  font-weight: 600;
  margin: 28rpx 24rpx 18rpx;
  display: flex;
  align-items: center;
  gap: 12rpx;
}

.section-title::before {
  content: '';
  width: 8rpx;
  height: 30rpx;
  border-radius: 4rpx;
  background: linear-gradient(180deg, #3987e5, #1c5cab);
}

.empty-tip {
  padding: 60rpx 0;
  text-align: center;
  font-size: 26rpx;
  color: #9aa0ab;
}

.btn-primary {
  background: linear-gradient(135deg, #3987e5, #1c5cab);
  color: #fff;
  border-radius: 999rpx;
  font-size: 30rpx;
  height: 84rpx;
  line-height: 84rpx;
}

.btn-primary::after {
  border: none;
}

/* 加载与骨架 */
.loading-tip {
  padding: 40rpx 0;
  text-align: center;
  font-size: 26rpx;
  color: #9aa0ab;
}
</style>
