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
          <view class="grow">
            <text class="h2">{{ auth.displayName }}</text>
            <text class="muted">{{ auth.className || '未绑定班级' }}</text>
          </view>
          <text class="chip chip--brand">学生端</text>
        </view>
      </view>

      <view class="card">
        <view class="item" @tap="go('/pages/suggestion/index')">
          <view class="item__icon">💡</view>
          <text class="grow">新物种建议</text>
          <text class="item__arrow">›</text>
        </view>
        <view class="item" @tap="go('/pages/notification/index')">
          <view class="item__icon">🔔</view>
          <text class="grow">消息通知</text>
          <text class="item__arrow">›</text>
        </view>
        <view class="item" @tap="go('/pages/map/index')">
          <view class="item__icon">🗺️</view>
          <text class="grow">全国植物地图</text>
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
import { computed } from "vue"
import { useAuthStore } from "@/stores/auth"
import AppHero from "@/components/AppHero.vue"

const auth = useAuthStore()
const avatarText = computed(() => (auth.displayName || "同").slice(0, 1))

onShow(() => {
  if (!auth.isLoggedIn) uni.reLaunch({ url: "/pages/login/index" })
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
.card .item { background: transparent; box-shadow: none; padding: 22rpx 0; border-bottom: 1rpx solid #f1f5ef; }
.card .item:last-child { border-bottom: none; }
.version { text-align: center; color: #a8b3aa; font-size: 22rpx; }
.version--sub { margin-top: -12rpx; font-size: 21rpx; }
</style>
