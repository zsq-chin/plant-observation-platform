<template>
  <view class="page">
    <view class="card toolbar">
      <view class="row">
        <view class="grow">
          <text class="h3">消息通知</text>
          <text class="muted">{{ unread > 0 ? unread + ' 条未读' : '全部已读' }}</text>
        </view>
        <text v-if="unread > 0" class="chip chip--brand" @tap="readAll">全部已读</text>
      </view>
    </view>

    <view
      v-for="n in rows"
      :key="String(n.id)"
      class="item"
      :class="{ 'item--unread': n.isRead === 0 }"
      hover-class="item--press"
      @tap="open(n)"
    >
      <view class="item__icon">
        <image class="item__icon-img" :src="'/static/icons/' + icon(n) + '.svg'" mode="aspectFit" />
      </view>
      <view class="grow">
        <view class="row">
          <text class="h3 grow ellipsis">{{ n.title || '通知' }}</text>
          <text v-if="n.isRead === 0" class="dot"></text>
        </view>
        <text class="muted content">{{ n.content }}</text>
        <text class="muted time">{{ fmtDate(n.createTime) }}</text>
      </view>
    </view>
    <EmptyState v-if="!rows.length" icon="bell" title="暂无消息" hint="审核结果、教师点评会在这里通知你" />
  </view>
</template>

<script setup lang="ts">
import { onShow } from "@dcloudio/uni-app"
import { ref } from "vue"
import { fetchNotifications, fetchUnreadCount, markAllNotificationsRead, markNotificationRead } from "@/api/plant"
import EmptyState from "@/components/EmptyState.vue"
import { fmtDate } from "@/utils/media"
import type { NotificationItem } from "@/types/models"

const rows = ref<NotificationItem[]>([])
const unread = ref(0)

async function load() {
  const result = await fetchNotifications(1, 30).catch(() => null)
  rows.value = result ? result.records : []
  unread.value = rows.value.filter((n) => n.isRead === 0).length
  const serverCount = await fetchUnreadCount().catch(() => unread.value)
  unread.value = serverCount
}

onShow(load)

function icon(n: NotificationItem) {
  const text = String(n.title || "") + String(n.content || "")
  if (text.includes("审核") || text.includes("驳回")) return "edit"
  if (text.includes("评分") || text.includes("点评") || text.includes("优秀")) return "star"
  if (text.includes("建议") || text.includes("物种")) return "bulb"
  return "bell"
}

/** 点击即标记已读（失败不阻断浏览，仅记录日志） */
async function open(n: NotificationItem) {
  if (n.isRead === 0) {
    try {
      await markNotificationRead(String(n.id))
      n.isRead = 1
      unread.value = Math.max(0, unread.value - 1)
    } catch (error) {
      console.error("标记已读失败", error)
    }
  }
}

async function readAll() {
  try {
    await markAllNotificationsRead()
    rows.value = rows.value.map((n) => ({ ...n, isRead: 1 }))
    unread.value = 0
    uni.showToast({ title: "已全部标记为已读", icon: "success" })
  } catch (error) {
    console.error("全部已读失败", error)
    uni.showToast({ title: "操作失败，请重试", icon: "none" })
  }
}
</script>

<style scoped>
.toolbar { gap: 4rpx; }
.item { align-items: flex-start; }
.item--unread { border-left: 8rpx solid #3f9b3f; }
.content { display: block; margin-top: 6rpx; }
.time { display: block; margin-top: 8rpx; font-size: 22rpx; }
.dot { width: 14rpx; height: 14rpx; border-radius: 50%; background: #e14d4d; flex-shrink: 0; }
.ellipsis { overflow: hidden; white-space: nowrap; text-overflow: ellipsis; }
</style>
