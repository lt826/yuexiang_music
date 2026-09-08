import { createRouter, createWebHistory } from 'vue-router'
import { useUserStore } from '@/stores/user'
import AdminLayout from '@/components/AdminLayout.vue'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    // ===== 系统门户：/ 即平台首页（平台介绍 + 实时统计，进入系统默认页） =====
    {
      path: '/',
      name: 'portal',
      component: () => import('@/views/PortalView.vue'),
    },

    // ===== 用户端首页：/home 音乐首页（热门歌曲、推荐歌单、歌手） =====
    {
      path: '/home',
      name: 'home',
      component: () => import('@/views/HomeView.vue'),
    },

    // 兼容旧地址：/portal 进系统门户
    {
      path: '/portal',
      redirect: '/',
    },

    // ===== 管理端：/admin 前缀 =====
    {
      path: '/admin/login',
      name: 'admin-login',
      component: () => import('@/views/AdminLoginView.vue'),
    },
    {
      path: '/admin',
      component: AdminLayout,
      redirect: '/admin/home',
      children: [
        {
          path: 'home',
          name: 'admin-home',
          component: () => import('@/views/admin/AdminHomeView.vue'),
        },
        {
          path: 'manage',
          name: 'admin-manage',
          component: () => import('@/views/admin/AdminManageView.vue'),
        },
        {
          path: 'setting',
          name: 'admin-setting',
          component: () => import('@/views/admin/AdminSettingView.vue'),
        },
      ],
    },

    // ===== 用户端 =====
    {
      path: '/login',
      name: 'login',
      component: () => import('@/views/LoginView.vue'),
    },
    {
      path: '/search',
      name: 'search',
      component: () => import('@/views/SearchView.vue'),
    },
    // 歌手主页（匿名可看：资料/歌曲/公开歌单/关注数）
    {
      path: '/singer/:id',
      name: 'singer-home',
      component: () => import('@/views/SingerHomeView.vue'),
    },
    // AI 智能搜歌（对话式，登录后可用；未登录由播放拦截统一引导）
    {
      path: '/ai-search',
      name: 'ai-search',
      component: () => import('@/views/AiSearchView.vue'),
    },
    // 心情电台（选频道 AI 串歌自动连播；未登录由播放拦截统一引导）
    {
      path: '/radio',
      name: 'radio',
      component: () => import('@/views/RadioView.vue'),
    },
    // ===== 乐坊（圈子社区）：广场/详情匿名可看；一起听房间需登录 =====
    {
      path: '/circle',
      name: 'circle',
      component: () => import('@/views/CircleView.vue'),
    },
    {
      path: '/circle/:id',
      name: 'circle-detail',
      component: () => import('@/views/CircleDetailView.vue'),
    },
    {
      path: '/circle/listen/:roomId',
      name: 'listen-room',
      component: () => import('@/views/TogetherListenView.vue'),
    },
    {
      path: '/explore',
      name: 'explore',
      component: () => import('@/views/ExploreView.vue'),
    },
    {
      path: '/playlists',
      name: 'playlists',
      component: () => import('@/views/PlaylistSquareView.vue'),
    },
    {
      path: '/playlist/:id',
      name: 'playlist-detail',
      component: () => import('@/views/PlaylistDetailView.vue'),
    },
    {
      path: '/my-music',
      name: 'my-music',
      component: () => import('@/views/MyMusicView.vue'),
    },
    // 听歌周报（登录后可用）
    {
      path: '/weekly-report',
      name: 'weekly-report',
      component: () => import('@/views/WeeklyReportView.vue'),
    },
    {
      path: '/singer-center',
      name: 'singer-center',
      component: () => import('@/views/SingerCenterView.vue'),
    },
    {
      path: '/profile',
      name: 'profile',
      component: () => import('@/views/ProfileView.vue'),
    },
    {
      path: '/register',
      name: 'register',
      component: () => import('@/views/RegisterView.vue'),
    },

    // 兼容旧地址：/user/home 进音乐首页
    {
      path: '/user/home',
      redirect: '/home',
    },
    {
      path: '/user',
      redirect: '/home',
    },
    // 未知路径进音乐首页
    {
      path: '/:pathMatch(.*)*',
      redirect: '/home',
    },
  ],
})

// 路由守卫（对齐详细设计 13.4）：未登录仅可访问登录注册与公开浏览页；管理端要求 ADMIN
const adminPageNames = ['admin-home', 'admin-manage', 'admin-setting']

router.beforeEach((to) => {
  const userStore = useUserStore()
  const publicPages = ['portal', 'home', 'login', 'register', 'admin-login', 'search', 'singer-home', 'ai-search', 'radio', 'circle', 'circle-detail', 'explore', 'playlists', 'playlist-detail']
  if (publicPages.includes(String(to.name))) {
    return
  }
  const isAdminPage = to.path.startsWith('/admin') || adminPageNames.includes(String(to.name))
  if (!userStore.isLoggedIn) {
    // 管理端未登录跳管理员登录，其余跳用户登录
    if (isAdminPage) {
      return { name: 'admin-login' }
    }
    return { name: 'login', query: { redirect: to.fullPath } }
  }
  if (isAdminPage && !userStore.isAdmin) {
    return { name: 'home' }
  }
})

export default router
