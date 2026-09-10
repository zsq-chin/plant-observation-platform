<template>
  <view class="page">
    <view class="tabs">
      <text v-for="t in tabs" :key="t.value" class="tab" :class="{ on: current === t.value }" @tap="switchTab(t.value)">{{ t.label }}</text>
    </view>
    <view v-if="current === 'LOCAL'" class="local-list">
      <view v-for="d in localDrafts" :key="d.localId" class="card">
        <view>本地草稿 · {{ photosText(d) }} · {{ new Date(d.savedAt).toLocaleString() }}</view>
        <button size="mini" class="btn-primary" :loading="syncing === d.localId" @tap="sync(d.localId)">联网同步</button>
      </view>
      <view v-if="!localDrafts.length" class="empty">没有本地草稿</view>
    </view>
    <template v-else>
      <view v-for="r in rows" :key="String(r.id)" class="card" @tap="continueEdit(r)">
        <view class="row">
          <text class="name">{{ r.reportedCommonName || '待鉴定/未命名' }}</text>
          <text class="tag" :class="r.status.toLowerCase()">{{ STATUS_LABELS[r.status] || r.status }}</text>
        </view>
        <view class="muted">{{ r.provinceName || '' }} {{ r.cityName || '' }} {{ r.districtName || '' }} · {{ fmtDate(r.observedAt) }}</view>
        <view class="actions" @tap.stop>
          <button v-if="r.status === 'DRAFT' || r.status === 'REJECTED'" size="mini" class="btn-primary" @tap="submitIt(r)">提交审核</button>
          <button v-if="r.status === 'SUBMITTED'" size="mini" @tap="withdrawIt(r)">撤回</button>
          <button v-if="r.status === 'DRAFT' || r.status === 'REJECTED'" size="mini" class="btn-warn" @tap="removeIt(r)">删除</button>
        </view>
      </view>
      <view v-if="!rows.length" class="empty">暂无记录，去拍一株植物吧</view>
    </template>
  </view>
</template>

<script setup lang="ts">
import { onLoad, onShow, onUnload } from "@dcloudio/uni-app"
import { ref } from "vue"
import { useAuthStore } from "@/stores/auth"
import { createObservation, deleteObservation, myObservations, submitObservation, uploadPhoto, withdrawObservation } from "@/api/plant"
import { readLocalDrafts, removeLocalDraft, saveLocalDraft } from "@/utils/storage"
import { fmtDate, STATUS_LABELS } from "@/utils/media"
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
      // 保留本地草稿，避免照片丢失
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

/** 提交审核：只有后端成功才提示成功，失败保持原状态并提示 */
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
.page { padding: 20rpx; }
.tabs { display: flex; flex-wrap: wrap; gap: 10rpx; margin-bottom: 16rpx; }
.tab { padding: 8rpx 22rpx; background: #fff; border-radius: 999rpx; font-size: 26rpx; }
.tab.on { background: #3f9b3f; color: #fff; }
.card { background: #fff; border-radius: 14rpx; padding: 18rpx; margin-bottom: 14rpx; display: flex; flex-direction: column; gap: 8rpx; }
.row { display: flex; align-items: center; justify-content: space-between; }
.name { font-weight: 600; font-size: 30rpx; }
.tag { font-size: 22rpx; padding: 4rpx 14rpx; border-radius: 999rpx; }
.tag.draft { background: #f2f2f2; color: #666; }
.tag.submitted { background: #e8f3ff; color: #2a7de1; }
.tag.approved { background: #e9f9e9; color: #2a9d3f; }
.tag.rejected { background: #fff0f0; color: #e14d4d; }
.muted { color: #999; font-size: 24rpx; }
.actions { display: flex; gap: 12rpx; }
.empty { color: #999; text-align: center; padding: 80rpx 0; }
.local-list { display: flex; flex-direction: column; gap: 14rpx; }
</style>