<template>
  <view class="page">
    <view v-for="n in rows" :key="String(n.id)" class="card" :class="{ unread: n.isRead === 0 }" @tap="open(n)">
      <view class="title">{{ n.title || '通知' }}</view>
      <view class="muted">{{ n.content }}</view>
      <view class="muted time">{{ fmtDate(n.createTime) }}</view>
    </view>
    <view v-if="!rows.length" class="empty">暂无消息</view>
  </view>
</template>

<script setup lang="ts">
import { onShow } from "@dcloudio/uni-app"
import { ref } from "vue"
import { fetchNotifications } from "@/api/plant"
import { fmtDate } from "@/utils/media"
import type { NotificationItem } from "@/types/models"

const rows = ref<NotificationItem[]>([])

onShow(async () => {
  const result = await fetchNotifications(1, 30).catch(() => null)
  rows.value = result ? result.records : []
})

function open(n: NotificationItem) {
  if (n.isRead === 0) {
    // 标记已读（端点按需接入）
  }
}
</script>

<style scoped>
.page { padding: 20rpx; }
.card { background: #fff; border-radius: 14rpx; padding: 18rpx; margin-bottom: 14rpx; }
.card.unread { border-left: 6rpx solid #3f9b3f; }
.title { font-weight: 600; }
.muted { color: #999; font-size: 24rpx; margin-top: 6rpx; }
.time { font-size: 22rpx; }
.empty { color: #999; text-align: center; padding: 80rpx 0; }
</style>
