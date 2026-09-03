<script setup lang="ts">
/**
 * 登录/注册共享外壳 v2：左右双栏。
 * 左栏 = 品牌叙事（衬线大标 + 旋转黑胶 + 底部频谱律动带，与门户首页签名元素同体系）；
 * 右栏 = 表单卡。窄屏（<880px）退化为单卡片：背景保留频谱，叙事区隐藏。
 * variant="admin" 时左栏切暮紫端，与管理员身份匹配。
 */
import { computed } from 'vue'

withDefaults(
  defineProps<{ title: string; subtitle?: string; variant?: 'user' | 'admin' }>(),
  { subtitle: '', variant: 'user' },
)

const reducedMotion = computed(() => window.matchMedia('(prefers-reduced-motion: reduce)').matches)

/** 频谱律动条：36 根，高度按伪随机波形分布（与 PortalView 同参数，视觉成体系） */
const spectrumBars = Array.from({ length: 36 }, (_, i) => i)
</script>

<template>
  <div class="auth-overlay" :class="{ 'is-admin': variant === 'admin' }">
    <div class="auth-bg" aria-hidden="true">
      <!-- 左栏品牌叙事 -->
      <div class="narrative">
        <div class="brand-row" @click="$router.push('/home')">
          <span class="brand-mark">♪</span>
          <span class="brand-name">悦享音乐</span>
        </div>

        <div class="hero-block">
          <p class="hero-eyebrow">{{ variant === 'admin' ? 'ADMIN CONSOLE' : 'YUEXIANG MUSIC' }}</p>
          <h2 class="hero-title yx-display">
            <template v-if="variant === 'admin'">听见每一处<br />需要被管理的声音</template>
            <template v-else>听见原创<br />的力量。</template>
          </h2>
          <p v-if="variant === 'admin'" class="hero-desc">
            歌曲审核 · 用户管理 · 数据总览 · 公告广播
          </p>
          <p v-else class="hero-desc">上传你的作品，收藏喜欢的歌，生成属于你的听歌周报。</p>

          <!-- 旋转黑胶（用户侧签名装饰） -->
          <div v-if="variant !== 'admin'" class="vinyl-stage" aria-hidden="true">
            <div class="vinyl" :class="{ 'is-static': reducedMotion }">
              <span class="vinyl-groove" />
              <span class="vinyl-groove groove-2" />
              <span class="vinyl-label">悦享</span>
              <span class="vinyl-hole" />
            </div>
            <div class="tone-arm" :class="{ 'is-static': reducedMotion }">
              <span class="arm-stick" />
              <span class="arm-head" />
            </div>
          </div>
        </div>

        <!-- 底部频谱律动带（签名元素，与门户首页同款） -->
        <div class="spectrum" aria-hidden="true">
          <span
            v-for="i in spectrumBars"
            :key="i"
            class="spectrum-bar"
            :class="{ 'is-static': reducedMotion }"
            :style="{
              '--d': (i * 0.13).toFixed(2) + 's',
              '--h': 22 + Math.abs(Math.sin(i * 1.7)) * 60 + '%',
            }"
          />
        </div>
      </div>

      <!-- 右栏表单卡 -->
      <div class="auth-center" @click.self="$router.push('/home')">
        <div class="auth-card">
          <button class="card-close" title="返回首页" @click="$router.push('/home')">✕</button>

          <div class="card-head">
            <span class="card-mark" aria-hidden="true">♪</span>
            <h1 class="card-title">{{ title }}</h1>
            <p v-if="subtitle" class="card-sub">{{ subtitle }}</p>
          </div>

          <slot />
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.auth-overlay {
  position: fixed;
  inset: 0;
  z-index: 300;
}

/* ===== 背景：夜幕深色面（夜/昼体系），admin 走暮紫端 ===== */
.auth-bg {
  position: absolute;
  inset: 0;
  overflow: hidden;
  background: var(--yx-dusk-gradient);
  background-attachment: fixed;
}

.auth-overlay.is-admin .auth-bg {
  background:
    radial-gradient(900px 420px at 20% 20%, rgba(122, 104, 207, 0.28), transparent 62%),
    linear-gradient(160deg, #232c45 0%, #2a3b6e 100%);
}

/* ===== 左栏叙事 ===== */
.narrative {
  position: absolute;
  inset: 0;
  right: 46%;
  display: flex;
  flex-direction: column;
  padding: 36px 48px 0;
  color: var(--yx-night-text);
}

.brand-row {
  display: flex;
  align-items: center;
  gap: 10px;
  cursor: pointer;
  width: fit-content;
}

.brand-mark {
  width: 34px;
  height: 34px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 19px;
  border-radius: 9px;
  background: var(--yx-gradient);
  color: #fff;
}

.brand-name {
  font-size: 18px;
  font-weight: 700;
  letter-spacing: 1px;
}

.hero-block {
  flex: 1;
  display: flex;
  flex-direction: column;
  justify-content: center;
  max-width: 460px;
}

.hero-eyebrow {
  font-size: 12px;
  font-weight: 600;
  letter-spacing: 3px;
  color: var(--yx-accent-on-dark);
  text-transform: uppercase;
  margin-bottom: 14px;
}

.is-admin .hero-eyebrow {
  color: var(--yx-dusk);
}

.hero-title {
  font-size: 44px;
  line-height: 1.28;
  font-weight: 700;
  letter-spacing: 1px;
  text-shadow: 0 2px 18px rgba(0, 0, 0, 0.35);
}

.hero-desc {
  margin-top: 16px;
  font-size: 14px;
  line-height: 1.8;
  color: var(--yx-night-text-dim);
}

/* ===== 旋转黑胶 ===== */
.vinyl-stage {
  position: relative;
  margin-top: 28px;
  width: 190px;
  height: 150px;
}

.vinyl {
  position: absolute;
  width: 150px;
  height: 150px;
  border-radius: 50%;
  background:
    radial-gradient(circle at 35% 30%, rgba(255, 255, 255, 0.16), transparent 45%),
    repeating-radial-gradient(circle, #10131c 0 2px, #1a1f2e 2px 4px);
  box-shadow:
    0 12px 34px rgba(0, 0, 0, 0.5),
    inset 0 0 0 1px rgba(255, 255, 255, 0.06);
  display: flex;
  align-items: center;
  justify-content: center;
  animation: vinyl-spin 7s linear infinite;
}

.vinyl.is-static {
  animation: none;
}

@keyframes vinyl-spin {
  to {
    transform: rotate(360deg);
  }
}

.vinyl-groove {
  position: absolute;
  inset: 12%;
  border-radius: 50%;
  border: 1px solid rgba(255, 255, 255, 0.07);
}

.vinyl-groove.groove-2 {
  inset: 26%;
}

.vinyl-label {
  position: absolute;
  width: 46%;
  height: 46%;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 15px;
  font-weight: 700;
  letter-spacing: 3px;
  color: #fff;
  background: radial-gradient(circle at 35% 30%, #4b8fe8, #1c5cab);
}

.vinyl-hole {
  position: absolute;
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: #10131c;
}

/* 唱针 */
.tone-arm {
  position: absolute;
  right: 6px;
  top: 4px;
  width: 66px;
  height: 110px;
  transform-origin: top right;
  transform: rotate(14deg);
  animation: arm-sway 7s ease-in-out infinite alternate;
}

.tone-arm.is-static {
  animation: none;
}

@keyframes arm-sway {
  from {
    transform: rotate(10deg);
  }
  to {
    transform: rotate(19deg);
  }
}

.arm-stick {
  position: absolute;
  right: 12px;
  top: 0;
  width: 5px;
  height: 88px;
  border-radius: 3px;
  background: linear-gradient(180deg, #c9cede, #8f97ad);
}

.arm-head {
  position: absolute;
  right: 4px;
  top: 84px;
  width: 22px;
  height: 30px;
  border-radius: 5px;
  background: linear-gradient(180deg, #dfe3ee, #aab2c6);
  box-shadow: 0 3px 8px rgba(0, 0, 0, 0.4);
}

/* ===== 底部频谱律动带（签名元素，与门户首页同款参数） ===== */
.spectrum {
  position: absolute;
  left: 48px;
  right: 0;
  bottom: 0;
  height: 84px;
  display: flex;
  align-items: flex-end;
  gap: 6px;
  opacity: 0.8;
  mask-image: linear-gradient(90deg, transparent, #000 10%, #000 92%, transparent);
  pointer-events: none;
}

.spectrum-bar {
  flex: 1;
  max-width: 13px;
  height: var(--h);
  border-radius: 3px 3px 0 0;
  background: linear-gradient(180deg, #4b8fe8 0%, rgba(75, 143, 232, 0.2) 100%);
  transform-origin: bottom;
  animation: eq-bounce 1.6s ease-in-out infinite alternate;
  animation-delay: var(--d);
}

.is-admin .spectrum-bar {
  background: linear-gradient(180deg, #9d8de0 0%, rgba(122, 104, 207, 0.2) 100%);
}

.spectrum-bar.is-static {
  animation: none;
}

@keyframes eq-bounce {
  from {
    transform: scaleY(0.3);
  }
  to {
    transform: scaleY(1);
  }
}

/* ===== 右栏表单卡 ===== */
.auth-center {
  position: absolute;
  inset: 0;
  left: 54%;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
}

.auth-card {
  position: relative;
  width: 400px;
  max-width: 100%;
  max-height: calc(100vh - 48px);
  overflow-y: auto;
  background: #fff;
  border-radius: 16px;
  box-shadow: 0 24px 70px rgba(6, 10, 24, 0.45);
  padding: 34px 36px 30px;
  animation: card-in 0.35s ease;
}

@keyframes card-in {
  from {
    transform: translateY(16px);
    opacity: 0;
  }
  to {
    transform: translateY(0);
    opacity: 1;
  }
}

.card-close {
  position: absolute;
  top: 14px;
  right: 16px;
  width: 28px;
  height: 28px;
  border: none;
  background: transparent;
  color: #b8bcc5;
  font-size: 14px;
  cursor: pointer;
  border-radius: 50%;
  transition: background 0.15s ease, color 0.15s ease;
}

.card-close:hover {
  background: #f2f3f6;
  color: #2f3238;
}

.card-head {
  text-align: center;
  margin-bottom: 22px;
}

.card-mark {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 46px;
  height: 46px;
  border-radius: 12px;
  font-size: 23px;
  color: #fff;
  background: var(--yx-gradient);
  margin-bottom: 12px;
}

.is-admin .card-mark {
  background: linear-gradient(135deg, #7a68cf 0%, #4b3f8f 100%);
}

.card-title {
  font-size: 22px;
  font-weight: 800;
  color: var(--yx-text-main);
}

.card-sub {
  margin-top: 6px;
  font-size: 13px;
  color: var(--yx-text-secondary);
}

/* ===== 窄屏：退化为单卡片（背景保留频谱氛围） ===== */
@media (max-width: 880px) {
  .narrative .hero-block,
  .narrative .vinyl-stage {
    display: none;
  }

  .narrative {
    right: 0;
    padding: 24px 24px 0;
  }

  .auth-center {
    left: 0;
    align-items: center;
  }

  .spectrum {
    left: 24px;
    opacity: 0.35;
  }
}

@media (prefers-reduced-motion: reduce) {
  .vinyl,
  .tone-arm,
  .spectrum-bar {
    animation: none;
  }
}
</style>
