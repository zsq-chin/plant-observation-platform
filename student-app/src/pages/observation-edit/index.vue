<template>
  <view class="page">
    <view class="card">
      <view class="row">
        <text class="h3">照片</text>
        <text class="chip chip--brand">{{ photos.length }} / 10</text>
      </view>
      <text class="muted">点按照片可设封面 / 排序 / 改器官 / 删除</text>
      <view class="grid">
        <view v-for="(p, i) in photos" :key="String(p.photoId || p.fileUrl)" class="cell" @tap="openPhotoMenu(i)">
          <image :src="resolveMediaUrl(p.fileUrl)" mode="aspectFill" class="photo" />
          <text v-if="p.isCover" class="cover-badge">封面</text>
          <text v-if="organLabel(p.organType)" class="organ-badge">{{ organLabel(p.organType) }}</text>
          <text class="del" @tap.stop="removePhoto(p)">×</text>
        </view>
        <view v-if="photos.length < 10" class="cell add" @tap="pickMore"><text class="add__icon">＋</text></view>
      </view>
    </view>

    <view class="card">
      <text class="h3">植物</text>
      <input class="ipt" v-model="speciesKeyword" placeholder="搜索 银杏 / 桂花…" @input="doSearchSpecies" />
      <view v-if="speciesOptions.length" class="species-list">
        <view v-for="s in speciesOptions" :key="String(s.id)" class="species-item" @tap="chooseSpecies(s)">
          <text>{{ s.commonName }}</text>
          <text v-if="s.scientificName" class="sci">{{ s.scientificName }}</text>
        </view>
      </view>
      <label class="check">
        <switch :checked="unknownPlant" @change="onUnknownChange" style="transform: scale(0.8)" />
        <text class="check__text">未知植物（待鉴定）</text>
      </label>
    </view>

    <view class="card">
      <text class="h3">地点与时间</text>
      <RegionPicker :location-text="String(form.locationText || '')" @update:location="onLocationText" @region="onRegion" />
      <picker mode="date" @change="onDateChange">
        <view class="ipt picker-row">
          <view class="picker-row__left">
            <image class="picker-row__icon" src="/static/icons/calendar.svg" mode="aspectFit" />
            <text>{{ form.observedAt || '选择观察日期' }}</text>
          </view>
          <text class="item__arrow">›</text>
        </view>
      </picker>
    </view>

    <view class="card">
      <text class="h3">基础描述</text>
      <textarea class="ipt area" :value="String(form.description || '')" @input="onDescriptionInput" placeholder="形态 / 生境 / 发现经过…" />
    </view>

    <view class="card">
      <text class="h3">动态描述项</text>
      <DynamicPlantForm :fields="fields" :model="fieldValues" />
    </view>

    <view class="actions">
      <button size="mini" class="btn-ghost" :loading="saving" @tap="saveOnly">保存草稿</button>
      <button size="mini" class="btn-primary" :loading="submitting" @tap="submitNow">提交审核</button>
    </view>
  </view>
</template>

<script setup lang="ts">
import { onLoad } from "@dcloudio/uni-app"
import { reactive, ref } from "vue"
import { useAuthStore } from "@/stores/auth"
import { chooseAndCompressImages } from "@/api/request"
import {
  createObservation, deletePhoto, fetchPlantFields, myObservationDetail,
  myObservationPhotos, reorderPhotos, searchSpecies, setCoverPhoto, submitObservation,
  updateObservation, updatePhotoOrgan, uploadPhoto,
} from "@/api/plant"
import RegionPicker from "@/components/RegionPicker.vue"
import DynamicPlantForm from "@/components/DynamicPlantForm.vue"
import { resolveMediaUrl } from "@/utils/media"
import type { FieldDef, PhotoItem, SpeciesItem } from "@/types/models"

const auth = useAuthStore()
const id = ref<string | null>(null)
const photos = ref<PhotoItem[]>([])
const fields = ref<FieldDef[]>([])
const fieldValues = reactive<Record<string, unknown>>({})
const speciesOptions = ref<SpeciesItem[]>([])
const speciesKeyword = ref("")
const unknownPlant = ref(false)
const submitting = ref(false)
const saving = ref(false)
const form = reactive<Record<string, unknown>>({
  speciesId: null,
  reportedCommonName: "",
  provinceCode: null,
  cityCode: null,
  districtCode: null,
  locationText: "",
  observedAt: "",
  description: "",
})

onLoad(async (query) => {
  if (!auth.isLoggedIn) return uni.reLaunch({ url: "/pages/login/index" })
  if (query && query.id) {
    id.value = String(query.id)
    await loadExisting(id.value)
  }
  fields.value = await fetchPlantFields().catch(() => [])
  for (const f of fields.value) {
    if (fieldValues[f.id] === undefined) {
      fieldValues[f.id] = f.fieldType === "MULTI_SELECT" ? [] : f.fieldType === "BOOLEAN" ? false : ""
    }
  }
})

async function loadExisting(obsId: string) {
  const detail = await myObservationDetail(obsId).catch(() => null)
  if (detail) {
    Object.assign(form, {
      speciesId: detail.speciesId || null,
      reportedCommonName: detail.reportedCommonName || "",
      provinceCode: detail.provinceCode || null,
      cityCode: detail.cityCode || null,
      districtCode: detail.districtCode || null,
      locationText: detail.locationText || "",
      observedAt: detail.observedAt ? String(detail.observedAt).slice(0, 10) : "",
      description: detail.description || "",
    })
  }
  photos.value = await myObservationPhotos(obsId).catch(() => [])
}

function onRegion(value: Record<string, unknown>) {
  Object.assign(form, value)
}

function onUnknownChange(event: { detail?: { value?: boolean } } | Event) {
  const detail = (event as { detail?: { value?: boolean } }).detail
  unknownPlant.value = Boolean(detail?.value)
}

function onLocationText(value: string) {
  form.locationText = value || ""
}

function onDescriptionInput(event: Event) {
  const value = (event as { detail?: { value?: string } }).detail?.value
  form.description = value ?? ""
}

function onDateChange(event: { detail?: { value?: string } } | Event) {
  const detail = (event as { detail?: { value?: string } }).detail
  form.observedAt = detail?.value || ""
}

/** 注意：不能与 api 的 searchSpecies 同名，否则会遮蔽导入并触发类型错误 */
async function doSearchSpecies() {
  const keyword = speciesKeyword.value.trim()
  if (!keyword) {
    speciesOptions.value = []
    return
  }
  try {
    const result = await searchSpecies(keyword)
    speciesOptions.value = result.records.slice(0, 6)
  } catch (error) {
    console.error("搜索物种失败", error)
    speciesOptions.value = []
  }
}

function chooseSpecies(s: SpeciesItem) {
  form.speciesId = s.id
  form.reportedCommonName = s.commonName
  speciesKeyword.value = s.commonName
  speciesOptions.value = []
}

async function pickMore() {
  const paths = await chooseAndCompressImages(10 - photos.value.length, "album").catch(() => [])
  if (!paths.length) {
    return // 用户取消选择
  }
  if (!id.value) {
    uni.showToast({ title: "请先保存一次草稿再补图", icon: "none" })
    return
  }
  await uploadPhotos(paths)
}

/** 上传多张照片：逐张收集失败项，失败必须让用户知道（禁止静默吞掉） */
async function uploadPhotos(paths: string[]) {
  if (!id.value) {
    throw new Error("观察记录尚未创建")
  }
  const failed: string[] = []
  uni.showLoading({ title: "上传中…" })
  try {
    for (const file of paths) {
      try {
        await uploadPhoto(String(id.value), file, "WHOLE")
      } catch (error) {
        console.error("上传图片失败:", file, error)
        failed.push(file)
      }
    }
  } finally {
    uni.hideLoading()
  }
  await refreshPhotos()
  if (failed.length) {
    uni.showToast({ title: "有 " + failed.length + " 张图片上传失败，请重试", icon: "none" })
    return
  }
  uni.showToast({ title: "图片已上传", icon: "success" })
}

const organOptions = [
  { value: "WHOLE", label: "全株" },
  { value: "LEAF", label: "叶" },
  { value: "FLOWER", label: "花" },
  { value: "FRUIT", label: "果实" },
  { value: "BARK", label: "树皮" },
  { value: "SEED", label: "种子" },
  { value: "OTHER", label: "其他" },
]

function organLabel(type?: string | null) {
  const hit = organOptions.find((o) => o.value === type)
  return hit ? hit.label : ""
}

async function refreshPhotos() {
  if (!id.value) return
  photos.value = await myObservationPhotos(id.value).catch(() => photos.value)
}

function openPhotoMenu(index: number) {
  const p = photos.value[index]
  if (!p || !p.photoId) {
    uni.showToast({ title: "该照片尚未同步，请稍后操作", icon: "none" })
    return
  }
  uni.showActionSheet({
    itemList: p.isCover ? ["设为封面（已是封面）", "修改器官标签", "上移一位", "下移一位", "删除"] : ["设为封面", "修改器官标签", "上移一位", "下移一位", "删除"],
    success: (res) => {
      if (res.tapIndex === 0 && !p.isCover) {
        void setCover(String(p.photoId))
      } else if (res.tapIndex === 1) {
        pickOrganFor(p)
      } else if (res.tapIndex === 2 || res.tapIndex === 3) {
        movePhoto(index, res.tapIndex === 2 ? -1 : 1)
      } else if (res.tapIndex === 4) {
        removePhoto(p)
      }
    },
    fail: () => undefined,
  })
}

function pickOrganFor(p: PhotoItem) {
  const current = organOptions.findIndex((o) => o.value === p.organType)
  uni.showActionSheet({
    itemList: organOptions.map((o) => o.label),
    success: (res) => {
      const organ = organOptions[res.tapIndex]
      if (organ) {
        const idStr = String(id.value)
        const pid = String(p.photoId)
        updatePhotoOrgan(idStr, pid, organ.value)
          .then(() => {
            uni.showToast({ title: "器官已更新为" + organ.label, icon: "none" })
            p.organType = organ.value
          })
          .catch((error) => {
            console.error("修改器官标签失败", error)
            uni.showToast({ title: "器官标签修改失败", icon: "none" })
          })
      }
    },
    fail: () => undefined,
  })
}

async function setCover(photoId: string) {
  if (!id.value) return
  try {
    await setCoverPhoto(String(id.value), photoId)
    await refreshPhotos()
    uni.showToast({ title: "已设为封面", icon: "success" })
  } catch (error) {
    console.error("设置封面失败", error)
    uni.showToast({ title: "设置封面失败，请重试", icon: "none" })
  }
}

async function movePhoto(index: number, delta: number) {
  const target = index + delta
  if (target < 0 || target >= photos.value.length || !id.value) return
  const previous = [...photos.value]
  const arr = [...photos.value]
  const [item] = arr.splice(index, 1)
  arr.splice(target, 0, item)
  photos.value = arr
  const ids = arr.map((x) => String(x.photoId)).filter(Boolean)
  try {
    await reorderPhotos(String(id.value), ids)
  } catch (error) {
    console.error("调整照片顺序失败", error)
    photos.value = previous // 回滚本地顺序，避免与服务端不一致
    uni.showToast({ title: "排序失败，请重试", icon: "none" })
  }
}

async function removePhoto(p: PhotoItem) {
  if (!id.value || !p.photoId) {
    return
  }
  try {
    await deletePhoto(id.value, String(p.photoId))
    await refreshPhotos()
    uni.showToast({ title: "照片已删除", icon: "success" })
  } catch (error) {
    console.error("删除照片失败", error)
    uni.showToast({ title: "删除照片失败，请重试", icon: "none" })
  }
}

function buildFieldValues() {
  return fields.value
    .filter((f) => fieldValues[f.id] !== undefined && fieldValues[f.id] !== "" && fieldValues[f.id] !== false)
    .map((f) => ({
      fieldId: f.id,
      valueText: Array.isArray(fieldValues[f.id]) ? (fieldValues[f.id] as string[]).join(",") : String(fieldValues[f.id]),
    }))
}

function buildBody() {
  return {
    speciesId: form.speciesId ? String(form.speciesId) : null,
    reportedCommonName: (form.reportedCommonName as string) || null,
    provinceCode: form.provinceCode || null,
    cityCode: form.cityCode || null,
    districtCode: form.districtCode || null,
    locationText: (form.locationText as string) || null,
    observedAt: form.observedAt ? String(form.observedAt) + " 00:00:00" : null,
    description: (form.description as string) || null,
    fieldValues: buildFieldValues(),
    unknownPlant: unknownPlant.value || false,
  }
}

/**
 * 保存草稿/修改：失败直接抛出异常，由调用方统一提示。
 * 不在这里吞异常，避免「保存失败仍继续提交」造成假成功。
 */
async function persistObservation(): Promise<void> {
  if (id.value) {
    await updateObservation(String(id.value), buildBody())
    return
  }
  const created = await createObservation(buildBody())
  id.value = String(created.id)
}

async function saveOnly() {
  if (saving.value) return
  saving.value = true
  try {
    await persistObservation()
    uni.showToast({ title: "草稿已保存", icon: "success" })
  } catch (error) {
    console.error("保存植物观察失败", error)
    uni.showToast({ title: "保存失败，请检查网络后重试", icon: "none" })
  } finally {
    saving.value = false
  }
}

async function submitNow() {
  if (submitting.value) return
  submitting.value = true
  try {
    // 保存失败会抛出异常，直接进入 catch，不会继续提交
    await persistObservation()
    if (!id.value) {
      throw new Error("观察记录尚未创建")
    }
    console.debug("submit observation", { id: id.value, body: buildBody() })
    await submitObservation(String(id.value))
    uni.showToast({ title: "已提交审核", icon: "success" })
    setTimeout(() => uni.switchTab({ url: "/pages/my-observations/index" }), 500)
  } catch (error) {
    console.error("提交审核失败", error)
    uni.showToast({ title: "提交失败，请重试", icon: "none" })
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped>
.grid { display: flex; flex-wrap: wrap; gap: 14rpx; margin-top: 8rpx; }
.cell { width: calc((100% - 42rpx) / 4); height: 150rpx; position: relative; }
.photo { width: 100%; height: 100%; border-radius: 16rpx; }
.cover-badge {
  position: absolute; left: 6rpx; top: 6rpx; background: #e6a23c; color: #fff;
  font-size: 19rpx; padding: 2rpx 10rpx; border-radius: 999rpx;
}
.organ-badge {
  position: absolute; left: 6rpx; bottom: 6rpx; background: rgba(0, 0, 0, 0.55); color: #fff;
  font-size: 19rpx; padding: 2rpx 10rpx; border-radius: 999rpx;
}
.del {
  position: absolute; top: 4rpx; right: 6rpx; color: #fff; background: rgba(0, 0, 0, 0.5);
  border-radius: 50%; width: 38rpx; height: 38rpx; text-align: center; line-height: 38rpx; font-size: 28rpx;
}
.add {
  border: 2rpx dashed #c6d6c2; border-radius: 16rpx; display: flex; align-items: center;
  justify-content: center; background: #fbfdfa;
}
.add__icon { font-size: 40rpx; color: #9db29a; }
.area { height: 170rpx; width: 100%; box-sizing: border-box; }
.sci { font-style: italic; color: #8a968c; font-size: 24rpx; margin-left: 10rpx; }
.check { display: flex; align-items: center; gap: 10rpx; margin-top: 6rpx; }
.check__text { color: #55645a; font-size: 26rpx; }
.species-list { border: 1rpx solid #e8efe4; border-radius: 16rpx; background: #fff; margin-top: 8rpx; overflow: hidden; }
.species-item { padding: 20rpx 22rpx; border-bottom: 1rpx solid #f1f5ef; display: flex; align-items: baseline; }
.species-item:last-child { border-bottom: none; }
.picker-row { display: flex; align-items: center; justify-content: space-between; margin-top: 8rpx; }
.picker-row__left { display: flex; align-items: center; gap: 12rpx; }
.picker-row__icon { width: 30rpx; height: 30rpx; opacity: 0.6; }
.actions { display: flex; gap: 20rpx; }
</style>
