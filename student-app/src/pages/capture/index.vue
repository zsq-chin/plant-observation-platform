<template>
  <view class="page">
    <view class="card">
      <view class="row">
        <text class="h3">① 拍摄照片</text>
        <text class="chip chip--brand">{{ photos.length }} / 10</text>
      </view>
      <text class="muted">建议拍摄全株 + 叶片 + 花果特写，审核通过率更高</text>
      <view class="grid">
        <view v-for="(p, i) in photos" :key="i" class="cell">
          <image :src="p" mode="aspectFill" class="photo" />
          <text class="del" @tap="remove(i)">×</text>
        </view>
        <view v-if="photos.length < 10" class="cell add" @tap="pick('camera')">
          <text class="add__icon">📷</text><text class="add__text">拍摄</text>
        </view>
        <view v-if="photos.length < 10" class="cell add" @tap="pick('album')">
          <text class="add__icon">🖼️</text><text class="add__text">相册</text>
        </view>
      </view>
    </view>

    <view class="card">
      <text class="h3">② 器官类型</text>
      <view class="organs">
        <text v-for="o in organs" :key="o.value" class="pill" :class="{ 'pill--on': organ === o.value }" @tap="organ = o.value">{{ o.label }}</text>
      </view>
    </view>

    <view class="card">
      <view class="row">
        <text class="h3">③ 观察地点</text>
        <text class="chip">主动选择 · 不获取定位</text>
      </view>
      <RegionPicker :location-text="location" @update:location="location = $event" @region="onRegion" />
    </view>

    <button class="btn-primary btn-block" :loading="saving" @tap="saveDraft">保存草稿</button>
    <view class="hint">
      <text class="hint__icon">💡</text>
      <text class="hint__text">没网也能先存本地草稿：保存失败会自动放入「本地待同步」，回家联网后在“我的植物”里重试。</text>
    </view>
  </view>
</template>

<script setup lang="ts">
import { onShow } from "@dcloudio/uni-app"
import { reactive, ref } from "vue"
import { useAuthStore } from "@/stores/auth"
import { chooseAndCompressImages } from "@/api/request"
import { createObservation } from "@/api/plant"
import RegionPicker from "@/components/RegionPicker.vue"
import { saveLocalDraft } from "@/utils/storage"

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
.grid { display: flex; flex-wrap: wrap; gap: 14rpx; margin-top: 8rpx; }
.cell { width: calc((100% - 28rpx) / 3); height: 190rpx; position: relative; }
.photo { width: 100%; height: 100%; border-radius: 18rpx; }
.del {
  position: absolute; top: 6rpx; right: 10rpx; color: #fff; background: rgba(0, 0, 0, 0.55);
  border-radius: 50%; width: 40rpx; height: 40rpx; text-align: center; line-height: 40rpx; font-size: 30rpx;
}
.add {
  border: 2rpx dashed #c6d6c2; border-radius: 18rpx; display: flex; flex-direction: column;
  align-items: center; justify-content: center; gap: 6rpx; background: #fbfdfa;
}
.add__icon { font-size: 42rpx; }
.add__text { font-size: 24rpx; color: #7b8a80; }
.organs { display: flex; flex-wrap: wrap; gap: 14rpx; margin-top: 8rpx; }
.pill {
  padding: 12rpx 30rpx; background: #f4f7f2; border-radius: 999rpx; font-size: 26rpx; color: #55645a;
}
.pill--on { background: linear-gradient(135deg, #4aa64a 0%, #2f7a34 100%); color: #fff; font-weight: 600; }
.hint { display: flex; gap: 12rpx; padding: 0 8rpx; }
.hint__icon { font-size: 26rpx; }
.hint__text { flex: 1; color: #8a968c; font-size: 23rpx; line-height: 1.6; }
</style>
