<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { User, Lock } from '@element-plus/icons-vue'
import type { FormInstance, FormRules } from 'element-plus'
import { useUserStore } from '@/stores/user'
import AuthShell from '@/components/AuthShell.vue'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

const formRef = ref<FormInstance>()
const loading = ref(false)
const form = reactive({
  username: '',
  password: '',
})

const rules: FormRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
}

async function handleLogin() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  loading.value = true
  try {
    await userStore.login(form.username, form.password)
    ElMessage.success('欢迎回来')
    // 管理员账号走独立后台（yuexiang_admin 体系），普通用户支持重定向回原页
    if (userStore.isAdmin) {
      router.push('/admin')
    } else {
      router.push((route.query.redirect as string) || '/home')
    }
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <AuthShell title="登录" subtitle="继续你的音乐旅程">
    <el-form
      ref="formRef"
      :model="form"
      :rules="rules"
      label-position="top"
      size="large"
      @keyup.enter="handleLogin"
    >
      <el-form-item label="用户名" prop="username">
        <el-input v-model="form.username" placeholder="你的用户名" :prefix-icon="User" clearable />
      </el-form-item>
      <el-form-item label="密码" prop="password">
        <el-input
          v-model="form.password"
          type="password"
          placeholder="密码"
          :prefix-icon="Lock"
          show-password
        />
      </el-form-item>
      <el-form-item>
        <el-button class="submit-btn" type="primary" :loading="loading" @click="handleLogin">
          登 录
        </el-button>
      </el-form-item>
    </el-form>

    <footer class="panel-foot">
      <p>
        还没有账号？
        <router-link to="/register" class="foot-link">创建一个</router-link>
      </p>
      <nav class="foot-nav">
        <router-link to="/home" class="foot-minor">先去逛逛</router-link>
        <span class="foot-dot">·</span>
        <router-link to="/admin/login" class="foot-minor">管理员入口</router-link>
      </nav>
    </footer>
  </AuthShell>
</template>

<style scoped>
.submit-btn {
  width: 100%;
  margin-top: 6px;
  height: 46px;
  font-size: 15px;
  letter-spacing: 6px;
}

.submit-btn:focus-visible {
  outline: 2px solid var(--yx-accent);
  outline-offset: 2px;
}

.panel-foot {
  margin-top: 24px;
  font-size: 14px;
  color: var(--yx-text-secondary);
  text-align: center;
}

.foot-link {
  color: var(--yx-accent);
  font-weight: 600;
}

.foot-link:hover {
  color: var(--yx-accent-hover);
}

.foot-nav {
  margin-top: 14px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  font-size: 13px;
}

.foot-minor {
  color: var(--yx-text-disabled);
}

.foot-minor:hover {
  color: var(--yx-text-secondary);
}

.foot-dot {
  color: var(--yx-hairline);
}
</style>
