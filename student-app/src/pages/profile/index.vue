<template>
  <view class="page page--hero">
    <AppHero title="我的" subtitle="账号、消息与偏好设置">
      <template #right>
        <view class="avatar">{{ avatarText }}</view>
      </template>
    </AppHero>

    <view class="lift">
      <view class="card me">
        <view class="row">
          <view class="avatar avatar--plain me__avatar">{{ avatarText }}</view>
          <view class="grow">
            <text class="h2">{{ auth.displayName }}</text>
            <text class="muted">{{ auth.className || '未绑定班级' }}</text>
          </view>
          <text class="chip chip--brand">学生端</text>
        </view>
      </view>

      <view class="card">
        <view v-for="m in menus" :key="m.key" class="item" hover-class="item--press" @tap="go(m.url)">
          <view class="item__icon">
            <image class="item__icon-img" :src="'/static/icons/' + m.icon + '.svg'" mode="aspectFit" />
          </view>
          <text class="grow">{{ m.label }}</text>
          <text v-if="m.badge" class="chip chip--danger">{{ m.badge }}</text>
          <text class="item__arrow">›</text>
        </view>
      </view>

      <button class="btn-ghost btn-block" @tap="logout">退出登录</button>
      <text class="version">菁选 · 学生端 v1.0.0-rc1</text>
      <text class="version version--sub">数据仅保存在登录账号下；不采集位置权限</text>
    </view>
  </view>
</template>

<script setup lang="ts">
import { onShow } from "@dcloudio/uni-app"
import { computed, ref } from "vue"
import { useAuthStore } from "@/stores/auth"
import AppHero from "@/components/AppHero.vue"
import { fetchUnreadCount } from "@/api/plant"

const auth = useAuthStore()
const unread = ref(0)
const avatarText = computed(() => (auth.displayName || "同").slice(0, 1))

const menus = computed(() => [
  { key: "suggest", icon: "bulb", label: "新物种建议", url: "/pages/suggestion/index", badge: "" },
  { key: "notify", icon: "bell", label: "消息通知", url: "/pages/notification/index", badge: unread.value > 0 ? String(unread.value) : "" },
  { key: "map", icon: "map", label: "全国植物地图", url: "/pages/map/index", badge: "" },
])

onShow(() => {
  if (!auth.isLoggedIn) return uni.reLaunch({ url: "/pages/login/index" })
  fetchUnreadCount().then((c) => (unread.value = c)).catch(() => undefined)
})

function go(url: string) {
  uni.navigateTo({ url })
}
function logout() {
  uni.showModal({
    title: "退出登录",
    content: "确认退出当前账号？",
    success: (res) => {
      if (res.confirm) {
        auth.logout()
        uni.reLaunch({ url: "/pages/login/index" })
      }
    },
  })
}
</script>

<style scoped>
.me { gap: 0; }
.me__avatar { width: 92rpx; height: 92rpx; font-size: 34rpx; }
.card .item { background: transparent; box-shadow: none; padding: 22rpx 0; border-radius: 16rpx; border-bottom: 1rpx solid #f1f5ef; }
.card .item:last-child { border-bottom: none; }
.version { text-align: center; color: #a8b3aa; font-size: 22rpx; }
.version--sub { margin-top: -12rpx; font-size: 21rpx; }
</style>
