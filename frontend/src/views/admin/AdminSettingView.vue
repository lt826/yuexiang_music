<script setup lang="ts">
import { reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/stores/user'

const userStore = useUserStore()

/** 展示性账号信息（后端暂无管理员资料修改接口，只读展示） */
const profile = reactive({
  account: '',
  nickname: '',
})

profile.account = userStore.nickname
profile.nickname = userStore.nickname

/** 偏好设置：仅本地生效（localStorage），课程演示用 */
const prefs = reactive({
  compactTable: localStorage.getItem('adminCompactTable') === '1',
  autoRefresh: localStorage.getItem('adminAutoRefresh') === '1',
})

const saving = ref(false)

function handleSave() {
  saving.value = true
  localStorage.setItem('adminCompactTable', prefs.compactTable ? '1' : '0')
  localStorage.setItem('adminAutoRefresh', prefs.autoRefresh ? '1' : '0')
  setTimeout(() => {
    saving.value = false
    ElMessage.success('设置已保存')
  }, 300)
}
</script>

<template>
  <div class="setting-page">
    <!-- 账号信息 -->
    <section class="card">
      <header class="card-head">
        <h2 class="card-title">账号信息</h2>
        <span class="card-note">只读 · 由系统分配</span>
      </header>
      <dl class="info-grid">
        <div class="info-item">
          <dt class="info-label">管理员账号</dt>
          <dd class="info-value">{{ profile.account }}</dd>
        </div>
        <div class="info-item">
          <dt class="info-label">角色</dt>
          <dd class="info-value"><el-tag type="danger" effect="plain">管理员</el-tag></dd>
        </div>
        <div class="info-item">
          <dt class="info-label">登录方式</dt>
          <dd class="info-value">独立后台账号（/admin/login）</dd>
        </div>
        <div class="info-item">
          <dt class="info-label">令牌有效期</dt>
          <dd class="info-value">由网关统一管理</dd>
        </div>
      </dl>
    </section>

    <!-- 界面偏好 -->
    <section class="card">
      <header class="card-head">
        <h2 class="card-title">界面偏好</h2>
        <span class="card-note">本地生效</span>
      </header>
      <div class="pref-list">
        <div class="pref-item">
          <div class="pref-text">
            <span class="pref-name">紧凑表格</span>
            <span class="pref-desc">管理中心表格使用更小的行高，单屏显示更多数据</span>
          </div>
          <el-switch v-model="prefs.compactTable" />
        </div>
        <div class="pref-item">
          <div class="pref-text">
            <span class="pref-name">进入页面自动刷新待办</span>
            <span class="pref-desc">每次进入管理端时立即重新拉取待审核数量</span>
          </div>
          <el-switch v-model="prefs.autoRefresh" />
        </div>
      </div>
      <div class="pref-actions">
        <el-button type="primary" size="large" :loading="saving" @click="handleSave">保存设置</el-button>
      </div>
    </section>

    <!-- 关于 -->
    <section class="card">
      <header class="card-head">
        <h2 class="card-title">关于系统</h2>
      </header>
      <div class="about">
        <p class="about-line">悦享音乐 · 后台管理系统</p>
        <p class="about-sub">微服务架构课程演示项目 · 前端 Vue 3 + Element Plus · 后端 Spring Cloud Alibaba</p>
      </div>
    </section>
  </div>
</template>

<style scoped>
.setting-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
  max-width: 860px;
}

.card {
  background: #fff;
  border: 1px solid var(--yx-hairline);
  border-radius: 10px;
  padding: 22px 24px;
}

.card-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  margin-bottom: 18px;
}

.card-title {
  font-size: 16px;
  font-weight: 600;
}

.card-note {
  font-size: 12px;
  color: var(--yx-text-disabled);
}

.info-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
}

.info-item {
  padding: 14px 16px;
  background: #fafbfd;
  border: 1px solid var(--yx-hairline);
  border-radius: 8px;
}

.info-label {
  font-size: 12.5px;
  color: var(--yx-text-secondary);
}

.info-value {
  margin-top: 6px;
  font-size: 15px;
  font-weight: 500;
  color: var(--yx-text-main);
}

.pref-list {
  display: flex;
  flex-direction: column;
}

.pref-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  padding: 16px 4px;
  border-bottom: 1px solid var(--yx-hairline);
}

.pref-text {
  display: flex;
  flex-direction: column;
  gap: 3px;
}

.pref-name {
  font-size: 14.5px;
  font-weight: 600;
}

.pref-desc {
  font-size: 12.5px;
  color: var(--yx-text-secondary);
}

.pref-actions {
  margin-top: 20px;
}

.about {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.about-line {
  font-size: 14.5px;
  font-weight: 600;
}

.about-sub {
  font-size: 12.5px;
  color: var(--yx-text-secondary);
}
</style>
