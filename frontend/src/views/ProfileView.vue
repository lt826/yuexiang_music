<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import TopNav from '@/components/TopNav.vue'
import { useUserStore } from '@/stores/user'
import { usePlayerStore } from '@/stores/player'
import { getUserInfo, updateProfile, updateAvatar, changePassword, type UserInfo } from '@/api/user'
import { uploadFile } from '@/api/singer'
import { Camera } from '@element-plus/icons-vue'

const router = useRouter()
const userStore = useUserStore()
const playerStore = usePlayerStore()

const info = ref<UserInfo | null>(null)
const loading = ref(false)

// 头像上传
const avatarUploading = ref(false)

async function handleAvatarUpload(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return
  avatarUploading.value = true
  try {
    const vo = await uploadFile(file, 'AVATAR')
    const updated = await updateAvatar(vo.url)
    // 同步导航栏头像
    userStore.avatar = updated.avatar || ''
    localStorage.setItem('avatar', userStore.avatar)
    info.value = updated
    ElMessage.success('头像已更新')
  } catch {
    // 拦截器已提示
  } finally {
    avatarUploading.value = false
    input.value = ''
  }
}

// 资料表单
const profileForm = ref({ nickname: '', signature: '' })
const profileSubmitting = ref(false)

// 密码表单
const pwdForm = ref({ oldPassword: '', newPassword: '', confirm: '' })
const pwdSubmitting = ref(false)

async function load() {
  loading.value = true
  try {
    info.value = await getUserInfo()
    profileForm.value.nickname = info.value.nickname || ''
    profileForm.value.signature = info.value.signature || ''
  } catch {
    // 拦截器已提示
  } finally {
    loading.value = false
  }
}

async function handleProfile() {
  if (!profileForm.value.nickname.trim()) {
    ElMessage.warning('昵称不能为空')
    return
  }
  profileSubmitting.value = true
  try {
    const updated = await updateProfile({
      nickname: profileForm.value.nickname.trim(),
      signature: profileForm.value.signature.trim(),
    })
    // 同步导航栏昵称
    userStore.nickname = updated.nickname || updated.username
    localStorage.setItem('nickname', userStore.nickname)
    ElMessage.success('资料已更新')
  } catch {
    // 拦截器已提示
  } finally {
    profileSubmitting.value = false
  }
}

async function handlePassword() {
  const form = pwdForm.value
  if (!form.oldPassword || !form.newPassword) {
    ElMessage.warning('请填写完整')
    return
  }
  if (form.newPassword.length < 6 || form.newPassword.length > 20) {
    ElMessage.warning('新密码长度需在 6-20 位之间')
    return
  }
  if (form.newPassword !== form.confirm) {
    ElMessage.warning('两次输入的新密码不一致')
    return
  }
  pwdSubmitting.value = true
  try {
    await changePassword({ oldPassword: form.oldPassword, newPassword: form.newPassword })
    ElMessage.success('密码已修改，请重新登录')
    userStore.logout()
    playerStore.stop()
    router.push('/login')
  } catch {
    // 拦截器已提示
  } finally {
    pwdSubmitting.value = false
  }
}

function roleLabel(role: string): string {
  if (role === 'ADMIN') return '管理员'
  if (role === 'SINGER') return '认证歌手'
  return '普通用户'
}

onMounted(load)
</script>

<template>
  <div class="page">
    <TopNav />

    <main v-loading="loading" class="main">
      <!-- 账号信息卡 -->
      <section class="card info-card">
        <div class="avatar-wrap" :class="{ 'is-loading': avatarUploading }">
          <el-avatar :size="80" :src="info?.avatar || userStore.avatar || undefined" class="avatar">
            {{ (userStore.nickname || '?').slice(0, 1) }}
          </el-avatar>
          <label class="avatar-edit" title="更换头像">
            <el-icon :size="16"><Camera /></el-icon>
            <input type="file" accept=".jpg,.jpeg,.png,.webp" hidden @change="handleAvatarUpload" />
          </label>
          <span v-if="avatarUploading" class="avatar-loading">上传中…</span>
        </div>
        <div class="info">
          <h1 class="name">{{ info?.nickname || userStore.nickname }}</h1>
          <p class="meta">
            <el-tag :type="info?.role === 'SINGER' ? 'warning' : 'info'" size="small">
              {{ roleLabel(info?.role || '') }}
            </el-tag>
            <span class="username">@{{ info?.username }}</span>
            <span v-if="info?.email" class="email">{{ info.email }}</span>
          </p>
          <p v-if="info?.signature" class="signature">“{{ info.signature }}”</p>
        </div>
      </section>

      <div class="grid">
        <!-- 修改资料 -->
        <section class="card">
          <h2 class="card-title">个人资料</h2>
          <el-form label-position="top">
            <el-form-item label="昵称">
              <el-input v-model="profileForm.nickname" maxlength="50" show-word-limit placeholder="你的昵称" />
            </el-form-item>
            <el-form-item label="个性签名">
              <el-input
                v-model="profileForm.signature"
                type="textarea"
                :rows="3"
                maxlength="100"
                show-word-limit
                placeholder="一句话介绍自己（选填）"
              />
            </el-form-item>
            <el-button type="primary" :loading="profileSubmitting" @click="handleProfile">保存资料</el-button>
          </el-form>
        </section>

        <!-- 修改密码 -->
        <section class="card">
          <h2 class="card-title">修改密码</h2>
          <el-form label-position="top">
            <el-form-item label="原密码">
              <el-input v-model="pwdForm.oldPassword" type="password" show-password placeholder="当前密码" />
            </el-form-item>
            <el-form-item label="新密码">
              <el-input v-model="pwdForm.newPassword" type="password" show-password placeholder="6-20 位" />
            </el-form-item>
            <el-form-item label="确认新密码">
              <el-input v-model="pwdForm.confirm" type="password" show-word-limit placeholder="再次输入新密码" />
            </el-form-item>
            <el-button type="primary" :loading="pwdSubmitting" @click="handlePassword">修改密码</el-button>
            <p class="pwd-tip">修改密码后需重新登录</p>
          </el-form>
        </section>
      </div>
    </main>
  </div>
</template>

<style scoped>
.main {
  max-width: 1160px;
  margin: 0 auto;
  padding: 28px 20px 60px;
}

.card {
  background: #fff;
  border: 1px solid var(--yx-hairline);
  border-radius: var(--yx-radius);
  box-shadow: var(--yx-shadow-card);
  padding: 24px 28px;
}

.info-card {
  display: flex;
  align-items: center;
  gap: 18px;
  margin-bottom: 20px;
}

/* 头像 + 悬浮更换入口 */
.avatar-wrap {
  position: relative;
  flex-shrink: 0;
}

.avatar {
  background: var(--yx-red);
  color: #fff;
  font-size: 30px;
}

.avatar-edit {
  position: absolute;
  right: -4px;
  bottom: -4px;
  width: 28px;
  height: 28px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  background: #fff;
  border: 1px solid var(--yx-hairline);
  color: var(--yx-text-secondary);
  cursor: pointer;
  transition: color 0.15s ease, border-color 0.15s ease;
}

.avatar-edit:hover {
  color: var(--yx-accent);
  border-color: var(--yx-accent);
}

.avatar-wrap.is-loading .avatar {
  opacity: 0.55;
}

.avatar-loading {
  position: absolute;
  left: 50%;
  top: 50%;
  transform: translate(-50%, -50%);
  font-size: 12px;
  color: #fff;
  text-shadow: 0 1px 4px rgba(0, 0, 0, 0.6);
}

.name {
  font-size: 20px;
  font-weight: 700;
}

.meta {
  margin-top: 8px;
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 13px;
  color: var(--yx-text-secondary);
}

.signature {
  margin-top: 8px;
  font-size: 13px;
  color: var(--yx-text-secondary);
  font-style: italic;
}

.grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 20px;
  align-items: start;
}

@media (max-width: 760px) {
  .grid {
    grid-template-columns: 1fr;
  }
}

.card-title {
  font-size: 16px;
  font-weight: 600;
  margin-bottom: 18px;
  padding-left: 10px;
  border-left: 3px solid var(--yx-accent);
}

.pwd-tip {
  margin-top: 10px;
  font-size: 12px;
  color: var(--yx-text-disabled);
}
</style>
