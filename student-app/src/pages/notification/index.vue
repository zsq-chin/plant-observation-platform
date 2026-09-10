<template>
  <view class="page">
    <view v-for="n in rows" :key="String(n.id)" class="item" :class="{ 'item--unread': n.isRead === 0 }" @tap="open(n)">
      <view class="item__icon">{{ icon(n) }}</view>
      <view class="grow">
        <view class="row">
          <text class="h3 grow ellipsis">{{ n.title || '通知' }}</text>
          <text v-if="n.isRead === 0" class="dot"></text>
        </view>
        <text class="muted content">{{ n.content }}</text>
        <text class="muted time">{{ fmtDate(n.createTime) }}</text>
      </view>
    </view>
    <EmptyState v-if="!rows.length" icon="🔔" title="暂无消息" hint="审核结果、教师点评会在这里通知你" />
  </view>
</template>

<script setup lang="ts">
import { onShow } from "@dcloudio/uni-app"
import { ref } from "vue"
import { fetchNotifications } from "@/api/plant"
import EmptyState from "@/components/EmptyState.vue"
import { fmtDate } from "@/utils/media"
import type { NotificationItem } from "@/types/models"

const rows = ref<NotificationItem[]>([])

onShow(async () => {
  const result = await fetchNotifications(1, 30).catch(() => null)
  rows.value = result ? result.records : []
})

function icon(n: NotificationItem) {
  const text = String(n.title || "")
  if (text.includes("审核")) return "📝"
  if (text.includes("评分") || text.includes("点评")) return "⭐"
  if (text.includes("建议") || text.includes("物种")) return "💡"
  return "🔔"
}

function open(n: NotificationItem) {
  if (n.isRead === 0) {
    // 标记已读（端点按需接入）
  }
}
</script>

<style scoped>
.item { align-items: flex-start; }
.item--unread { border-left: 8rpx solid #3f9b3f; }
.content { display: block; margin-top: 6rpx; }
.time { display: block; margin-top: 8rpx; font-size: 22rpx; }
.dot { width: 14rpx; height: 14rpx; border-radius: 50%; background: #e14d4d; flex-shrink: 0; }
</style>
