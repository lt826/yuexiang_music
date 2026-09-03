<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { User, Lock } from '@element-plus/icons-vue'
import type { FormInstance, FormRules } from 'element-plus'
import { adminLogin } from '@/api/admin'
import { useUserStore } from '@/stores/user'
import AuthShell from '@/components/AuthShell.vue'

const router = useRouter()
const userStore = useUserStore()

const formRef = ref<FormInstance>()
const loading = ref(false)
const form = reactive({
  username: '',
  password: '',
})

const rules: FormRules = {
  username: [{ required: true, message: '请输入管理员账号', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
}

async function handleLogin() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  loading.value = true
  try {
    const vo = await adminLogin({ username: form.username, password: form.password })
    userStore.setAdmin(vo)
    ElMessage.success('登录成功')
    router.push('/admin')
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <AuthShell variant="admin" title="后台管理" subtitle="仅限管理员账号登录">
    <el-form
      ref="formRef"
      :model="form"
      :rules="rules"
      label-position="top"
      size="large"
      @keyup.enter="handleLogin"
    >
      <el-form-item label="管理员账号" prop="username">
        <el-input v-model="form.username" placeholder="管理员账号" :prefix-icon="User" clearable />
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
      <nav class="foot-nav">
        <router-link to="/home" class="foot-minor">返回首页</router-link>
        <span class="foot-dot">·</span>
        <router-link to="/login" class="foot-minor">用户登录</router-link>
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
}

.foot-nav {
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
