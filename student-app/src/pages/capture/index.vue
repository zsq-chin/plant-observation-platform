<template>
  <view class="page">
    <view class="sec">① 照片（{{ photos.length }}/10）</view>
    <view class="grid">
      <view v-for="(p, i) in photos" :key="i" class="cell">
        <image :src="p" mode="aspectFill" class="photo" />
        <text class="del" @tap="remove(i)">×</text>
      </view>
      <view v-if="photos.length < 10" class="cell add" @tap="pick('camera')">📷 拍摄</view>
      <view v-if="photos.length < 10" class="cell add" @tap="pick('album')">🖼 相册</view>
    </view>
    <view class="sec">器官类型</view>
    <view class="organs">
      <text v-for="o in organs" :key="o.value" class="chip" :class="{ on: organ === o.value }" @tap="organ = o.value">{{ o.label }}</text>
    </view>
    <view class="sec">② 地点（主动选择，不获取定位）</view>
    <RegionPicker :location-text="location" @update:location="location = $event" @region="onRegion" />
    <button type="primary" :loading="saving" @tap="saveDraft">保存草稿</button>
    <view class="hint">没网也能先存本地草稿：保存失败会自动放入“本地待同步”，回家联网后在我的植物里重试。</view>
  </view>
</template>

<script setup lang="ts">
import { onShow } from "@dcloudio/uni-app"
import { reactive, ref } from "vue"
import { useAuthStore } from "@/stores/auth"
import { chooseAndCompressImages } from "@/api/request"
import { createObservation } from "@/api/plant"
import RegionPicker from "@/components/RegionPicker.vue"
import { readLocalDrafts, removeLocalDraft, saveLocalDraft } from "@/utils/storage"

const auth = useAuthStore()
const photos = ref<string[]>([])
const organ = ref("WHOLE")
const location = ref("")
const region = reactive<{ province?: string; city?: string; district?: string }>({})
const saving = ref(false)
const organs = [
  { value: "WHOLE", label: "全株" },
  { value: "LEAF", label: "叶" },
  { value: "FLOWER", label: "花" },
  { value: "FRUIT", label: "果实" },
  { value: "BARK", label: "树皮" },
  { value: "SEED", label: "种子" },
  { value: "OTHER", label: "其他" },
]

onShow(() => {
  if (!auth.isLoggedIn) uni.reLaunch({ url: "/pages/login/index" })
})

async function pick(source: "camera" | "album") {
  const paths = await chooseAndCompressImages(10 - photos.value.length, source).catch(() => [])
  photos.value.push(...paths.slice(0, 10 - photos.value.length))
}

function onRegion(value: { province?: string; city?: string; district?: string }) {
  Object.assign(region, value)
}

function remove(index: number) {
  photos.value.splice(index, 1)
}

async function saveDraft() {
  if (!photos.value.length) {
    uni.showToast({ title: "至少先拍一张照片", icon: "none" })
    return
  }
  saving.value = true
  const body: Record<string, unknown> = {
    provinceCode: region.province || null,
    cityCode: region.city || null,
    districtCode: region.district || null,
    locationText: location.value || null,
  }
  try {
    const created = await createObservation(body)
    let ok = true
    for (const file of photos.value) {
      try {
        await uploadPhotoToServer(String(created.id), file)
      } catch {
        ok = false
        break
      }
    }
    if (ok) {
      uni.showToast({ title: "草稿已保存", icon: "success" })
      setTimeout(() => uni.switchTab({ url: "/pages/my-observations/index" }), 500)
      return
    }
    uni.showToast({ title: "图片上传中断，草稿已保存，可稍后重传", icon: "none" })
    uni.navigateTo({ url: "/pages/observation-edit/index?id=" + created.id })
    return
  } catch {
    // 离线/失败：保存本地草稿
    const localId = "local-" + Date.now()
    saveLocalDraft({
      localId,
      savedAt: Date.now(),
      payload: body,
      localPhotoPaths: photos.value,
    })
    uni.showToast({ title: "网络不可用，已存为本地草稿", icon: "none" })
    uni.switchTab({ url: "/pages/my-observations/index" })
  } finally {
    saving.value = false
  }
}

function uploadPhotoToServer(id: string, filePath: string) {
  return import("@/api/plant").then((mod) => mod.uploadPhoto(id, filePath, organ.value))
}
</script>

<style scoped>
.page { padding: 24rpx; display: flex; flex-direction: column; gap: 14rpx; }
.sec { font-weight: 600; }
.grid { display: flex; flex-wrap: wrap; gap: 12rpx; }
.cell { width: 160rpx; height: 160rpx; position: relative; }
.photo { width: 100%; height: 100%; border-radius: 12rpx; }
.del { position: absolute; top: 4rpx; right: 8rpx; color: #fff; background: rgba(0,0,0,.5); border-radius: 50%; width: 36rpx; height: 36rpx; text-align: center; line-height: 36rpx; }
.add { border: 2rpx dashed #aaa; border-radius: 12rpx; display: flex; align-items: center; justify-content: center; color: #666; }
.organs { display: flex; flex-wrap: wrap; gap: 12rpx; }
.chip { padding: 10rpx 24rpx; background: #fff; border-radius: 999rpx; font-size: 26rpx; }
.chip.on { background: #3f9b3f; color: #fff; }
.hint { color: #999; font-size: 24rpx; }
</style>