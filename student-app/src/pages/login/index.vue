<template>
  <view class="page">
    <view class="title">全国植物观察与交流平台</view>
    <view class="subtitle">学生端 · 记录身边植物</view>
    <input v-model="username" class="ipt" placeholder="学号 / 用户名" />
    <input v-model="password" class="ipt" password placeholder="密码" />
    <button type="primary" :loading="loading" @tap="submit">登 录</button>
    <view class="tip">账号密码登录；登录态安全保存在本机（不保存明文密码）。</view>
  </view>
</template>

<script setup lang="ts">
import { ref } from "vue"
import { useAuthStore } from "@/stores/auth"

const auth = useAuthStore()
const username = ref("")
const password = ref("")
const loading = ref(false)

async function submit() {
  if (!username.value || !password.value) {
    uni.showToast({ title: "请输入账号和密码", icon: "none" })
    return
  }
  loading.value = true
  try {
    await auth.login(username.value.trim(), password.value)
    uni.showToast({ title: "登录成功", icon: "success" })
    setTimeout(() => uni.switchTab({ url: "/pages/home/index" }), 400)
  } catch {
    // toast 已由 request 层提示
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.page { padding: 80rpx 48rpx; display: flex; flex-direction: column; gap: 24rpx; }
.title { font-size: 44rpx; font-weight: 700; }
.subtitle { color: #777; margin-bottom: 24rpx; }
.ipt { border: 1rpx solid #ddd; border-radius: 12rpx; padding: 20rpx; background: #fff; }
.tip { color: #999; font-size: 24rpx; margin-top: 12rpx; }
</style>
