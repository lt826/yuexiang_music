<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { User, Lock, Message } from '@element-plus/icons-vue'
import type { FormInstance, FormRules } from 'element-plus'
import { register } from '@/api/user'
import AuthShell from '@/components/AuthShell.vue'

const router = useRouter()

const formRef = ref<FormInstance>()
const loading = ref(false)
const form = reactive({
  username: '',
  email: '',
  password: '',
  confirmPassword: '',
})

/** 确认密码与密码一致 */
const validateConfirm = (_rule: unknown, value: string, callback: (error?: Error) => void) => {
  if (value !== form.password) {
    callback(new Error('两次输入的密码不一致'))
  } else {
    callback()
  }
}

const rules: FormRules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    {
      pattern: /^[a-zA-Z0-9_]{3,20}$/,
      message: '3-20 位字母、数字或下划线',
      trigger: 'blur',
    },
  ],
  email: [
    { required: true, message: '请输入邮箱', trigger: 'blur' },
    { type: 'email', message: '邮箱格式不正确', trigger: 'blur' },
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, max: 20, message: '密码长度需在 6-20 位之间', trigger: 'blur' },
  ],
  confirmPassword: [
    { required: true, message: '请再次输入密码', trigger: 'blur' },
    { validator: validateConfirm, trigger: 'blur' },
  ],
}

async function handleRegister() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  loading.value = true
  try {
    await register({
      username: form.username,
      email: form.email,
      password: form.password,
    })
    ElMessage.success('账号已创建，用新身份登录吧')
    router.push('/login')
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <AuthShell title="创建账号" subtitle="两分钟后，你就拥有自己的音乐主页">
    <el-form
      ref="formRef"
      :model="form"
      :rules="rules"
      label-position="top"
      size="large"
      @keyup.enter="handleRegister"
    >
      <el-form-item label="用户名" prop="username">
        <el-input v-model="form.username" placeholder="3-20 位字母、数字或下划线" :prefix-icon="User" clearable />
      </el-form-item>
      <el-form-item label="邮箱" prop="email">
        <el-input v-model="form.email" placeholder="you@example.com" :prefix-icon="Message" clearable />
      </el-form-item>
      <el-form-item label="密码" prop="password">
        <el-input
          v-model="form.password"
          type="password"
          placeholder="6-20 位"
          :prefix-icon="Lock"
          show-password
        />
      </el-form-item>
      <el-form-item label="确认密码" prop="confirmPassword">
        <el-input
          v-model="form.confirmPassword"
          type="password"
          placeholder="再输入一次"
          :prefix-icon="Lock"
          show-password
        />
      </el-form-item>
      <el-form-item>
        <el-button class="submit-btn" type="primary" :loading="loading" @click="handleRegister">
          注 册
        </el-button>
      </el-form-item>
    </el-form>

    <footer class="panel-foot">
      <p>
        已经有账号了？
        <router-link to="/login" class="foot-link">直接登录</router-link>
      </p>
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
  margin-top: 22px;
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
</style>
