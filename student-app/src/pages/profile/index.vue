<template>
  <view class="page">
    <view class="me">
      <view class="name">{{ auth.displayName }}</view>
      <view class="muted">{{ auth.className || '未绑定班级' }}</view>
    </view>
    <view class="menu">
      <view class="item" @tap="go('/pages/suggestion/index')">新物种建议</view>
      <view class="item" @tap="go('/pages/notification/index')">消息通知</view>
      <view class="item" @tap="logout">退出登录</view>
    </view>
    <view class="muted version">菁选 · 学生端 v1.0.0-rc1（数据仅保存在登录账号下；不采集位置权限）</view>
  </view>
</template>

<script setup lang="ts">
import { onShow } from "@dcloudio/uni-app"
import { useAuthStore } from "@/stores/auth"

const auth = useAuthStore()
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
.page { padding: 30rpx; display: flex; flex-direction: column; gap: 20rpx; }
.me { display: flex; flex-direction: column; gap: 6rpx; }
.name { font-size: 42rpx; font-weight: 700; }
.muted { color: #999; }
.menu { background: #fff; border-radius: 16rpx; }
.item { padding: 24rpx; border-bottom: 1rpx solid #f2f2f2; }
.version { font-size: 22rpx; text-align: center; }
</style>
