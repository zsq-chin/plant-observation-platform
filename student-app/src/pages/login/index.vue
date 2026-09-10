<template>
  <view class="page">
    <view class="title">全国植物观察与交流平台</view>
    <view class="subtitle">学生端 · 记录身边植物</view>
    <input v-model="username" class="ipt" placeholder="学号 / 用户名" />
    <input v-model="password" class="ipt" password placeholder="密码" />
    <button class="btn-primary" :loading="loading" @tap="submit">登 录</button>
    <view class="tip">账号密码登录；登录态安全保存在本机（不保存明文密码）。</view>
    <view class="server">
      <text class="server__label">服务器：{{ apiBaseLabel }}</text>
      <text class="server__link" @tap="openServerSetting">设置</text>
    </view>
    <view class="tip">若打不开页面或提示网络异常，请点「设置」填写后端地址（如 http://192.168.1.5），需与后端处于同一网络。</view>
  </view>
</template>

<script setup lang="ts">
import { computed, ref } from "vue"
import { useAuthStore } from "@/stores/auth"
import { getApiBase, setApiBase } from "@/config"

const auth = useAuthStore()
const username = ref("")
const password = ref("")
const loading = ref(false)
const apiBase = ref(getApiBase())

const apiBaseLabel = computed(() => apiBase.value || "同源（跟随当前页面地址）")

function openServerSetting() {
  uni.showModal({
    title: "后端服务器地址",
    editable: true,
    placeholderText: "如 http://10.120.46.175 或 http://192.168.1.5:8080",
    content: apiBase.value,
    success: (res) => {
      if (!res.confirm) return
      const saved = setApiBase(res.content || "")
      apiBase.value = saved
      uni.showToast({ title: saved ? "已保存：" + saved : "已恢复默认（同源）", icon: "none" })
    },
  })
}

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
.server { display: flex; align-items: center; gap: 12rpx; margin-top: 8rpx; font-size: 24rpx; color: #666; }
.server__link { color: #2f9b3f; text-decoration: underline; }
</style>
