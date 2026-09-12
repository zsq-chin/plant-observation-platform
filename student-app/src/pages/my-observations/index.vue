<template>
  <view class="page page--hero">
    <AppHero title="我的植物" :subtitle="'共 ' + rows.length + ' 条记录 · 记录成长轨迹'">
      <template #right>
        <view class="avatar" @tap="goCapture">＋</view>
      </template>
    </AppHero>

    <view class="tabs-wrap">
      <scroll-view scroll-x class="tabs" :show-scrollbar="false">
        <view class="tabs__inner">
          <text
            v-for="t in tabs"
            :key="t.value"
            class="pill"
            :class="{ 'pill--on': current === t.value }"
            @tap="switchTab(t.value)"
            >{{ t.label }}</text
          >
        </view>
      </scroll-view>
    </view>

    <view class="lift">
      <view v-if="current === 'LOCAL'" class="list">
        <view v-for="d in localDrafts" :key="d.localId" class="card">
          <view class="row">
            <view class="item__icon">
              <image class="item__icon-img" src="/static/icons/cloud.svg" mode="aspectFit" />
            </view>
            <text class="h3 grow">本地草稿</text>
            <text class="chip chip--warn">{{ photosText(d) }}</text>
          </view>
          <text class="muted">{{ formatTime(d.savedAt) }}</text>
          <button size="mini" class="btn-primary" :loading="syncing === d.localId" @tap="sync(d.localId)">联网同步</button>
        </view>
        <EmptyState v-if="!localDrafts.length" icon="cloud" title="没有本地草稿" hint="弱网下采集会自动存到本机，联网后在这里同步" />
      </view>

      <template v-else>
        <view v-for="r in rows" :key="String(r.id)" class="card ocard" hover-class="item--press" @tap="continueEdit(r)">
          <view class="ocard__head">
            <image class="ocard__cover" :src="resolveMediaUrl(r.coverUrl)" mode="aspectFill" />
            <view class="grow">
              <view class="row">
                <text class="h3 grow ellipsis">{{ r.reportedCommonName || '待鉴定 / 未命名' }}</text>
                <text class="chip" :class="statusChip(r.status)">{{ STATUS_LABELS[r.status] || r.status }}</text>
              </view>
              <view class="ocard__meta">
                <image class="ocard__icon" src="/static/icons/location.svg" mode="aspectFit" />
                <text class="muted ellipsis">{{ place(r) }}</text>
              </view>
              <view class="ocard__meta">
                <image class="ocard__icon" src="/static/icons/calendar.svg" mode="aspectFit" />
                <text class="muted">{{ fmtDate(r.observedAt) }}</text>
                <text v-if="r.photoCount" class="chip chip--plain">{{ r.photoCount }} 张照片</text>
                <text v-else class="chip chip--warn">无照片</text>
              </view>
            </view>
          </view>
          <view class="actions" @tap.stop>
            <button v-if="r.status === 'DRAFT' || r.status === 'REJECTED'" size="mini" class="btn-primary" @tap="submitIt(r)">提交审核</button>
            <button v-if="r.status === 'SUBMITTED'" size="mini" class="btn-ghost" @tap="withdrawIt(r)">撤回</button>
            <button v-if="r.status === 'DRAFT' || r.status === 'REJECTED'" size="mini" class="btn-warn" @tap="removeIt(r)">删除</button>
            <text class="actions__edit">继续编辑 ›</text>
          </view>
        </view>
        <EmptyState
          v-if="!rows.length"
          icon="plant"
          title="这里还空着"
          hint="点右上角「＋」或底部「采集」记录第一株植物"
        />
      </template>
    </view>
  </view>
</template>

<script setup lang="ts">
import { onLoad, onShow, onUnload } from "@dcloudio/uni-app"
import { ref } from "vue"
import { useAuthStore } from "@/stores/auth"
import { createObservation, deleteObservation, myObservations, submitObservation, uploadPhoto, withdrawObservation } from "@/api/plant"
import AppHero from "@/components/AppHero.vue"
import EmptyState from "@/components/EmptyState.vue"
import { readLocalDrafts, removeLocalDraft } from "@/utils/storage"
import { fmtDate, resolveMediaUrl, STATUS_LABELS } from "@/utils/media"
import type { LocalDraft } from "@/utils/storage"
import type { MyObservation } from "@/types/models"

const auth = useAuthStore()
const current = ref("ALL")
const rows = ref<MyObservation[]>([])
const localDrafts = ref<LocalDraft[]>([])
const syncing = ref<string | null>(null)
const tabs = [
  { value: "ALL", label: "全部" },
  { value: "LOCAL", label: "本地草稿" },
  { value: "DRAFT", label: "待完善" },
  { value: "SUBMITTED", label: "待审核" },
  { value: "APPROVED", label: "已通过" },
  { value: "REJECTED", label: "被驳回" },
]

const STATUS_CHIP: Record<string, string> = {
  DRAFT: "chip--plain",
  SUBMITTED: "chip--info",
  APPROVED: "chip--brand",
  REJECTED: "chip--danger",
  OFFLINE: "chip--plain",
}
function statusChip(status: string) {
  return STATUS_CHIP[status] || "chip--plain"
}
function place(r: MyObservation) {
  const parts = [r.provinceName, r.cityName, r.districtName].filter(Boolean)
  return parts.length ? parts.join(" ") : "未填写地点"
}
function formatTime(ts: number) {
  try {
    return new Date(ts).toLocaleString()
  } catch {
    return ""
  }
}
function goCapture() {
  uni.navigateTo({ url: "/pages/capture/index" })
}

function onPlantStatus(status: string) {
  current.value = status === "LOCAL" ? "LOCAL" : tabs.some((t) => t.value === status) ? status : "ALL"
  load()
}
onLoad(() => {
  uni.$on("plant-status", onPlantStatus)
})
onUnload(() => {
  uni.$off("plant-status", onPlantStatus)
})
onShow(() => {
  if (!auth.isLoggedIn) return uni.reLaunch({ url: "/pages/login/index" })
  load()
})
function switchTab(value: string) {
  current.value = value
  load()
}

async function load() {
  localDrafts.value = readLocalDrafts()
  if (current.value === "LOCAL") return
  const status = current.value === "ALL" ? undefined : current.value
  const result = await myObservations(status, 1, 20).catch(() => null)
  rows.value = result ? result.records : []
}

async function sync(localId: string) {
  syncing.value = localId
  const draft = localDrafts.value.find((d) => d.localId === localId)
  if (!draft) return
  try {
    const created = await createObservation(draft.payload as Record<string, unknown>)
    const failed: string[] = []
    for (const file of draft.localPhotoPaths) {
      try {
        await uploadPhoto(String(created.id), file, "WHOLE")
      } catch (error) {
        console.error("上传本地草稿图片失败:", file, error)
        failed.push(file)
      }
    }
    if (failed.length) {
      uni.showToast({ title: "有 " + failed.length + " 张图片未同步，请重试", icon: "none" })
      load()
      return
    }
    removeLocalDraft(localId)
    uni.showToast({ title: "本地草稿已同步", icon: "success" })
    load()
  } catch (error) {
    console.error("同步本地草稿失败", error)
    uni.showToast({ title: "同步失败，请检查网络后重试", icon: "none" })
  } finally {
    syncing.value = null
  }
}

function continueEdit(r: MyObservation) {
  uni.navigateTo({ url: "/pages/observation-edit/index?id=" + r.id })
}

async function submitIt(r: MyObservation) {
  try {
    await submitObservation(String(r.id))
    uni.showToast({ title: "已提交审核", icon: "success" })
    await load()
  } catch (error) {
    console.error("提交审核失败", error)
    uni.showToast({ title: "提交失败，请检查照片与观察信息后重试", icon: "none" })
  }
}

async function withdrawIt(r: MyObservation) {
  try {
    await withdrawObservation(String(r.id))
    uni.showToast({ title: "已撤回为草稿", icon: "success" })
    await load()
  } catch (error) {
    console.error("撤回失败", error)
    uni.showToast({ title: "撤回失败，请重试", icon: "none" })
  }
}

async function removeIt(r: MyObservation) {
  uni.showModal({
    title: "删除",
    content: "删除后照片与草稿将一并清除，确认？",
    success: async (res) => {
      if (!res.confirm) return
      try {
        await deleteObservation(String(r.id))
        uni.showToast({ title: "已删除", icon: "success" })
        await load()
      } catch (error) {
        console.error("删除观察失败", error)
        uni.showToast({ title: "删除失败，请重试", icon: "none" })
      }
    },
  })
}

function photosText(d: LocalDraft) {
  return d.localPhotoPaths.length + " 张照片"
}
</script>

<style scoped>
.tabs-wrap { margin-top: 0; padding-top: 20rpx; }
.tabs { width: 100%; white-space: nowrap; }
.tabs__inner { display: inline-flex; gap: 14rpx; padding: 0 24rpx; }
.pill {
  padding: 12rpx 28rpx;
  border-radius: 999rpx;
  font-size: 26rpx;
  color: #55645a;
  background: #ffffff;
  box-shadow: 0 6rpx 18rpx rgba(31, 45, 36, 0.06);
}
.pill--on {
  background: linear-gradient(135deg, #4aa64a 0%, #2f7a34 100%);
  color: #ffffff;
  font-weight: 600;
}
.list { display: flex; flex-direction: column; }
.ocard { margin-bottom: 18rpx; gap: 16rpx; }
.ocard__head { display: flex; gap: 20rpx; align-items: flex-start; }
.ocard__cover {
  width: 168rpx;
  height: 168rpx;
  border-radius: 20rpx;
  background: #eef4ea;
  flex-shrink: 0;
}
.ocard__meta { display: flex; align-items: center; gap: 8rpx; margin-top: 8rpx; }
.ocard__icon { width: 24rpx; height: 24rpx; opacity: 0.55; flex-shrink: 0; }
.actions { display: flex; align-items: center; gap: 12rpx; }
.actions__edit { margin-left: auto; font-size: 22rpx; color: #a8b3aa; }
.ellipsis { overflow: hidden; white-space: nowrap; text-overflow: ellipsis; }
</style>
