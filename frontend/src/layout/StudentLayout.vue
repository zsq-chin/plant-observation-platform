<template>
  <div class="workspace-layout student-layout">
    <div class="workspace-layout__frame">
      <aside class="workspace-layout__sidebar">
        <div class="workspace-layout__brand">
          <span class="workspace-layout__brand-note">Student Studio</span>
          <span class="workspace-layout__brand-title">学生创作台</span>
          <span class="workspace-layout__brand-desc">从草稿、提交到展示，作品以更完整的编辑节奏被整理与表达。</span>
        </div>

        <el-menu :default-active="route.path" router class="workspace-layout__menu">
          <el-menu-item index="/student/home">
            <el-icon><HomeFilled /></el-icon>
            <span>首页</span>
          </el-menu-item>
          <el-menu-item index="/student/todos">
            <el-icon><List /></el-icon>
            <span>我的待办</span>
          </el-menu-item>
          <el-menu-item index="/student/observations">
            <el-icon><Document /></el-icon>
            <span>我的植物观察</span>
          </el-menu-item>
          <el-menu-item index="/student/ranking">
            <el-icon><Trophy /></el-icon>
            <span>我的评分</span>
          </el-menu-item>
          <el-menu-item index="/student/notify">
            <el-icon><Bell /></el-icon>
            <span>消息通知</span>
          </el-menu-item>
        </el-menu>

        <div class="workspace-layout__section-note">
          学生端围绕「拍摄植物 → 记录观察 → 提交审核」展开，公开展示页展示审核通过的植物观察。
        </div>
      </aside>

      <div class="workspace-layout__main">
        <header class="workspace-layout__topbar">
          <div class="workspace-layout__headline">
            <h1>{{ pageTitle }}</h1>
            <p>{{ pageDescription }}</p>
          </div>

          <div class="workspace-layout__tools">
            <el-badge :value="unreadCount" :hidden="!hasUnread" class="workspace-layout__notify">
              <el-button circle @click="goNotify">
                <el-icon :size="18"><Bell /></el-icon>
              </el-button>
            </el-badge>
            <AppThemeToggle />
            <el-dropdown trigger="click" @command="handleCommand">
              <span class="workspace-layout__user">
                <el-avatar :size="34" :src="authStore.userInfo?.avatar || undefined">
                  {{ avatarFallback }}
                </el-avatar>
                <span>{{ authStore.userInfo?.realName || '学生' }}</span>
                <el-icon><ArrowDown /></el-icon>
              </span>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item command="home">首页</el-dropdown-item>
                  <el-dropdown-item command="profile">个人信息</el-dropdown-item>
                  <el-dropdown-item command="logout" divided>退出登录</el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </div>
        </header>

        <main class="workspace-layout__content">
          <router-view />
        </main>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/app/session/auth'
import { getUnreadCount } from '@/api/notify'
import { useNotificationPolling } from '@/composables/useNotificationPolling'
import { ArrowDown, HomeFilled, List, Document, Trophy, Bell } from '@element-plus/icons-vue'
import AppThemeToggle from '@/components/AppThemeToggle.vue'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const { unreadCount, hasUnread } = useNotificationPolling({
  fetchFn: () => getUnreadCount('student').then((r) => r.data as { count: number }),
  eventName: 'student-notify-changed',
})
const avatarFallback = computed(() => authStore.userInfo?.realName?.charAt(0) || '学')

const pageTitle = computed(() => {
  const map: Record<string, string> = {
    '/student/home': '学生首页',
    '/student/todos': '我的待办',
    '/student/observations': '我的植物观察',
    '/student/ranking': '我的评分',
    '/student/notify': '消息通知',
  }
  return (route.meta.title as string) || map[route.path] || '学生端'
})

const pageDescription = computed(() => {
  const map: Record<string, string> = {
    '/student/home': '查看植物观察的草稿、审核进度与公开展示入口。',
    '/student/todos': '查看各评分批次的待办任务。',
    '/student/observations': '统一管理草稿、待审核、已通过与已下线的植物观察。',
    '/student/ranking': '查看评分反馈与当前表现。',
    '/student/notify': '集中查看审核结果、发布动态与评论回复提醒。',
  }
  return map[route.path] || '围绕植物观察组织信息。'
})

const goNotify = () => router.push('/student/notify')

function handleCommand(cmd: string) {
  if (cmd === 'home') router.push('/student/home')
  else if (cmd === 'profile') router.push('/profile')
  else if (cmd === 'logout') authStore.logout()
}

onMounted(() => {
  if (authStore.token && !authStore.userInfo) {
    authStore.fetchUserInfo().catch(() => undefined)
  }
})
</script>

<style scoped>
.student-layout {
  --layout-accent: #9f7640;
}
</style>
