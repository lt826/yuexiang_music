<script setup lang="ts">
import { onMounted, onUnmounted, ref } from 'vue'

/**
 * 首页轮播图：全宽 380px，3 秒自动切换，淡入淡出过渡，左右箭头 + 底部小圆点指示器均可切换
 * 海报来源：后台轮播图管理（管理员上传/关联歌曲）或内置兜底
 */
interface BannerItem {
  id: number
  image: string
  star: string
  slogan: string
  songId?: number | null
}

const props = withDefaults(
  defineProps<{ items: BannerItem[]; interval?: number }>(),
  { interval: 3000 },
)

const emit = defineEmits<{ (e: 'click', item: BannerItem): void }>()

const current = ref(0)
let timer: ReturnType<typeof setInterval> | null = null

function go(index: number) {
  if (index < 0 || index >= props.items.length) return
  current.value = index
  restart()
}

function nextSlide() {
  if (props.items.length === 0) return
  current.value = (current.value + 1) % props.items.length
}

/** 左箭头：上一张（边界回绕到最后一张） */
function prevSlide() {
  if (props.items.length === 0) return
  current.value = (current.value - 1 + props.items.length) % props.items.length
}

/** 手动切换（箭头）：切换后重置自动轮播计时 */
function manualGo(slide: () => void) {
  slide()
  restart()
}

function restart() {
  if (timer) clearInterval(timer)
  timer = setInterval(nextSlide, props.interval)
}

/** 鼠标悬停暂停轮播（酷狗行为），移出恢复 */
function pause() {
  if (timer) clearInterval(timer)
  timer = null
}

onMounted(restart)
onUnmounted(() => {
  if (timer) clearInterval(timer)
})
</script>

<template>
  <div
    class="carousel"
    @mouseenter="pause"
    @mouseleave="restart"
  >
    <!-- 幻灯片：绝对定位叠放，透明度切换实现淡入淡出 -->
    <div
      v-for="(item, i) in items"
      :key="item.id"
      class="slide"
      :class="{ 'is-active': i === current }"
      @click="emit('click', item)"
    >
      <img class="slide-img" :src="item.image" :alt="item.star" loading="lazy" />
      <!-- 左下文案（海报上叠加，深底保证可读） -->
      <div class="slide-text">
        <p class="slide-star">{{ item.star }}</p>
        <p class="slide-slogan">{{ item.slogan }}</p>
      </div>
    </div>

    <!-- 左右切换箭头：半透明圆形，悬停显示；手动切换后重置自动轮播计时 -->
    <button class="arrow arrow-left" aria-label="上一张" @click="manualGo(prevSlide)">‹</button>
    <button class="arrow arrow-right" aria-label="下一张" @click="manualGo(nextSlide)">›</button>

    <!-- 底部小圆点指示器：半透明，激活加长加亮，可点击切换 -->
    <div class="dots">
      <button
        v-for="(item, i) in items"
        :key="item.id"
        class="dot"
        :class="{ 'is-active': i === current }"
        :aria-label="`切换到第 ${i + 1} 张`"
        @click="go(i)"
      />
    </div>
  </div>
</template>

<style scoped>
.carousel {
  position: relative;
  width: 100%;
  height: 380px;
  overflow: hidden;
  background: #171c28;
}

.slide {
  position: absolute;
  inset: 0;
  opacity: 0;
  transition: opacity 0.8s ease;
  pointer-events: none;
}

.slide.is-active {
  opacity: 1;
  pointer-events: auto;
  z-index: 1;
}

.slide-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

/* 左下文案条：渐变底衬保证任何海报上均可读 */
.slide-text {
  position: absolute;
  left: 0;
  right: 0;
  bottom: 0;
  padding: 46px 48px 40px;
  background: linear-gradient(180deg, transparent, rgba(10, 14, 24, 0.72));
  color: #fff;
}

.slide-star {
  font-size: 30px;
  font-weight: 800;
  letter-spacing: 2px;
  text-shadow: 0 2px 12px rgba(0, 0, 0, 0.45);
}

.slide-slogan {
  margin-top: 8px;
  font-size: 15px;
  color: rgba(255, 255, 255, 0.85);
  text-shadow: 0 1px 8px rgba(0, 0, 0, 0.5);
}

/* 左右切换箭头：两侧垂直居中，半透明圆形，悬停加亮 */
.arrow {
  position: absolute;
  top: 50%;
  transform: translateY(-50%);
  z-index: 2;
  width: 42px;
  height: 42px;
  padding: 0;
  border: none;
  border-radius: 999px;
  background: rgba(10, 14, 24, 0.35);
  color: #fff;
  font-size: 28px;
  line-height: 1;
  cursor: pointer;
  opacity: 0;
  transition: opacity 0.3s ease, background 0.3s ease;
}

.carousel:hover .arrow {
  opacity: 1;
}

.arrow:hover {
  background: rgba(10, 14, 24, 0.6);
}

.arrow-left {
  left: 18px;
}

.arrow-right {
  right: 18px;
}

/* 小圆点指示器 */
.dots {
  position: absolute;
  left: 0;
  right: 0;
  bottom: 14px;
  z-index: 2;
  display: flex;
  justify-content: center;
  gap: 10px;
}

.dot {
  width: 10px;
  height: 10px;
  padding: 0;
  border: none;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.45);
  cursor: pointer;
  transition: width 0.3s ease, background 0.3s ease;
}

.dot:hover {
  background: rgba(255, 255, 255, 0.75);
}

/* 激活：加长胶囊 + 亮白 */
.dot.is-active {
  width: 26px;
  background: #fff;
}

@media (max-width: 760px) {
  .carousel {
    height: 240px;
  }
  .slide-text {
    padding: 30px 22px 26px;
  }
  .slide-star {
    font-size: 22px;
  }
  .arrow {
    width: 32px;
    height: 32px;
    font-size: 20px;
  }
  .arrow-left {
    left: 10px;
  }
  .arrow-right {
    right: 10px;
  }
}
</style>
