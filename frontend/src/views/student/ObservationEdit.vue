<template>
  <div class="obs-edit">
    <div class="obs-edit__head">
      <router-link to="/student/observations">← 我的植物观察</router-link>
      <h1>{{ isEdit ? "补充植物观察信息" : "新增植物观察" }}</h1>
      <p class="obs-edit__tip">现场先拍照片、选地点保存草稿，回家后可继续补充植物信息再提交审核。</p>
    </div>
    <el-skeleton v-if="isEdit && !loaded" :rows="10" animated />
    <el-form v-else label-position="top" class="obs-edit__form">
      <!-- 照片 -->
      <el-form-item label="植物照片（1~10 张，第一张为封面；仅支持 JPG/PNG/WebP/GIF）">
        <div class="obs-edit__photos">
          <div v-for="p in photos" :key="String(p.photoId)" class="obs-edit__photo">
            <el-image :src="resolveMediaUrl(p.fileUrl)" fit="cover" @error="onMediaError" />
            <span v-if="p.isCover" class="obs-edit__cover">封面</span>
            <el-button class="obs-edit__photo-del" size="small" type="danger" circle @click="removePhoto(p)">×</el-button>
          </div>
          <label v-if="photos.length < 10" class="obs-edit__add-photo">
            ＋ 上传照片
            <input type="file" accept="image/jpeg,image/png,image/webp,image/gif" multiple hidden @change="uploadPhotos" />
          </label>
        </div>
      </el-form-item>

      <!-- 地点（主动选择，不获取设备定位） -->
      <el-form-item label="观察地点（主动选择省/市/区县，系统不会读取设备位置）" required>
        <div class="obs-edit__row">
          <el-select v-model="form.provinceCode" placeholder="省份" style="width: 160px" @change="onProvinceChange">
            <el-option v-for="p in provinces" :key="p.regionCode" :label="p.regionName" :value="p.regionCode" />
          </el-select>
          <el-select v-model="form.cityCode" placeholder="城市" style="width: 160px" :disabled="!cities.length" @change="onCityChange">
            <el-option v-for="c in cities" :key="c.regionCode" :label="c.regionName" :value="c.regionCode" />
          </el-select>
          <el-select v-model="form.districtCode" placeholder="区县" clearable style="width: 160px" :disabled="!districts.length">
            <el-option v-for="d in districts" :key="d.regionCode" :label="d.regionName" :value="d.regionCode" />
          </el-select>
          <el-input v-model="form.locationText" placeholder="详细地点（可选，如：校园植物园东侧）" style="flex: 1" />
        </div>
      </el-form-item>

      <!-- 植物信息 -->
      <el-form-item label="标准植物（可搜索中文名/学名；也可稍后由教师绑定）">
        <el-select v-model="form.speciesId" filterable remote clearable placeholder="搜索银杏、桂花…"
          :remote-method="searchSpeciesOptions" :loading="speciesLoading" style="width: 100%">
          <el-option v-for="s in speciesOptions" :key="String(s.id)" :label="speciesLabel(s)" :value="String(s.id)" />
        </el-select>
      </el-form-item>
      <el-form-item label="上报名（未匹配到标准植物时可手填）">
        <el-input v-model="form.reportedCommonName" placeholder="如：路边的黄花灌木" maxlength="100" />
      </el-form-item>
      <el-form-item label="观察时间">
        <el-date-picker v-model="form.observedAt" type="datetime" placeholder="选择观察/采集时间" value-format="YYYY-MM-DD HH:mm:ss" style="width: 240px" />
      </el-form-item>
      <el-form-item v-if="fields.length" label="动态描述项（教师配置）">
        <div class="obs-edit__fields">
          <div v-for="f in fields" :key="String(f.id)" class="obs-edit__field">
            <span class="obs-edit__field-label">{{ f.fieldLabel }}<i v-if="f.required === 1">*</i></span>
            <el-select v-if="f.fieldType === 'SELECT'" v-model="fieldMap[f.id]" clearable style="width: 240px">
              <el-option v-for="opt in parseOptions(f.optionsJson)" :key="opt" :label="opt" :value="opt" />
            </el-select>
            <el-select v-else-if="f.fieldType === 'MULTI_SELECT'" v-model="fieldMap[f.id]" multiple style="width: 100%">
              <el-option v-for="opt in parseOptions(f.optionsJson)" :key="opt" :label="opt" :value="opt" />
            </el-select>
            <el-switch v-else-if="f.fieldType === 'BOOLEAN'" v-model="fieldMap[f.id]" />
            <el-date-picker v-else-if="f.fieldType === 'DATE'" v-model="fieldMap[f.id]" type="date" value-format="YYYY-MM-DD" />
            <el-input-number v-else-if="f.fieldType === 'NUMBER'" v-model="fieldMap[f.id]" style="width: 200px" />
            <el-input v-else-if="f.fieldType === 'TEXTAREA'" v-model="fieldMap[f.id]" type="textarea" :rows="2" style="width: 100%" />
            <el-input v-else v-model="fieldMap[f.id]" style="width: 320px" />
          </div>
        </div>
      </el-form-item>
      <el-form-item label="基础描述">
        <el-input v-model="form.description" type="textarea" :rows="4" placeholder="描述这株植物的形态、生境、发现经过……" maxlength="5000" show-word-limit />
      </el-form-item>
      <div class="obs-edit__actions">
        <el-button :loading="saving" @click="saveDraft">保存草稿</el-button>
        <el-button type="primary" :loading="submitting" @click="submitForReview">提交审核</el-button>
      </div>
    </el-form>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from "vue"
import { useRoute, useRouter } from "vue-router"
import { ElMessage } from "element-plus"
import request from "@/api/request"
import { onMediaError, resolveMediaUrl } from "@/utils/media"
import {
  fetchProvinces,
  fetchRegionChildren,
  searchSpecies,
  type FieldDef,
  type PhotoItem,
  type RegionItem,
  type SpeciesItem,
} from "@/api/plant"

const route = useRoute()
const router = useRouter()
const obsIdRef = ref<string | null>(route.params.id ? String(route.params.id) : null)
const isEdit = Boolean(route.params.id)

const loaded = ref(false)
const saving = ref(false)
const submitting = ref(false)
const provinces = ref<RegionItem[]>([])
const cities = ref<RegionItem[]>([])
const districts = ref<RegionItem[]>([])
const photos = ref<PhotoItem[]>([])
const fields = ref<FieldDef[]>([])
const speciesOptions = ref<SpeciesItem[]>([])
const speciesLoading = ref(false)

const form = reactive<{
  speciesId?: string
  reportedCommonName: string
  provinceCode?: string
  cityCode?: string
  districtCode?: string
  locationText: string
  observedAt?: string
  description: string
}>({ reportedCommonName: "", locationText: "", description: "" })

const fieldMap = reactive<Record<string, unknown>>({})

const speciesLabel = (s: SpeciesItem) =>
  s.scientificName ? `${s.commonName}（${s.scientificName}）` : s.commonName

const parseOptions = (json?: string | null): string[] => {
  if (!json) return []
  try {
    const parsed = JSON.parse(json)
    return Array.isArray(parsed) ? parsed.map(String) : []
  } catch {
    return []
  }
}

const searchSpeciesOptions = async (keyword: string) => {
  speciesLoading.value = true
  try {
    const result = await searchSpecies(keyword || "")
    speciesOptions.value = result.records
  } finally {
    speciesLoading.value = false
  }
}

const onProvinceChange = async () => {
  form.cityCode = undefined
  form.districtCode = undefined
  cities.value = form.provinceCode ? await fetchRegionChildren(form.provinceCode) : []
  districts.value = []
}
const onCityChange = async () => {
  form.districtCode = undefined
  districts.value = form.cityCode ? await fetchRegionChildren(form.cityCode) : []
}

const loadPhotos = async () => {
  if (!obsIdRef.value) return
  const payload = (await request.get(`/api/student/plant/observations/${obsIdRef.value}/photos`)) as { data: PhotoItem[] }
  photos.value = payload.data
}

const uploadPhotos = async (event: Event) => {
  const input = event.target as HTMLInputElement
  const files = Array.from(input.files || [])
  input.value = ""
  if (!obsIdRef.value) {
    ElMessage.warning("请先保存草稿，再上传照片")
    return
  }
  for (const file of files) {
    const fd = new FormData()
    fd.append("file", file)
    fd.append("organType", "WHOLE")
    await request.post(`/api/student/plant/observations/${obsIdRef.value}/photos`, fd)
  }
  ElMessage.success("照片上传完成")
  loadPhotos()
}

const removePhoto = async (p: PhotoItem) => {
  if (!obsIdRef.value) return
  await request.delete(`/api/student/plant/observations/${obsIdRef.value}/photos/${p.photoId}`)
  loadPhotos()
}

const buildFieldValues = () => {
  return fields.value
    .filter((f) => fieldMap[f.id] !== undefined && fieldMap[f.id] !== null && fieldMap[f.id] !== "")
    .map((f) => ({
      fieldId: String(f.id),
      valueText: Array.isArray(fieldMap[f.id]) ? (fieldMap[f.id] as string[]).join(",") : String(fieldMap[f.id]),
    }))
}

const body = () => ({
  speciesId: form.speciesId ? String(form.speciesId) : null,
  reportedCommonName: form.reportedCommonName || null,
  provinceCode: form.provinceCode || null,
  cityCode: form.cityCode || null,
  districtCode: form.districtCode || null,
  locationText: form.locationText || null,
  observedAt: form.observedAt || null,
  description: form.description || null,
  fieldValues: buildFieldValues(),
})

const saveDraft = async () => {
  saving.value = true
  try {
    if (obsIdRef.value) {
      await request.put(`/api/student/plant/observations/${obsIdRef.value}`, body())
    } else {
      const payload = (await request.post("/api/student/plant/observations", body())) as { data: { id: number | string } }
      obsIdRef.value = String(payload.data.id)
      await router.replace(`/student/observations/edit/${obsIdRef.value}`)
      loadPhotos()
    }
    ElMessage.success("草稿已保存，可继续上传照片")
  } finally {
    saving.value = false
  }
}

const submitForReview = async () => {
  submitting.value = true
  try {
    const targetId = obsIdRef.value
    if (!targetId) {
      ElMessage.warning("请先保存草稿")
      return
    }
    await request.put(`/api/student/plant/observations/${targetId}`, body())
    await request.post(`/api/student/plant/observations/${targetId}/submit`)
    ElMessage.success("已提交审核，请等待教师审核")
    router.push("/student/observations")
  } finally {
    submitting.value = false
  }
}

const loadEdit = async () => {
  if (!obsIdRef.value) return
  const payload = (await request.get(`/api/student/plant/observations/${obsIdRef.value}`)) as { data: Record<string, unknown> }
  const d = payload.data
  form.speciesId = d.speciesId ? String(d.speciesId) : undefined
  form.reportedCommonName = String(d.reportedCommonName || "")
  form.provinceCode = d.provinceCode ? String(d.provinceCode) : undefined
  form.cityCode = d.cityCode ? String(d.cityCode) : undefined
  form.districtCode = d.districtCode ? String(d.districtCode) : undefined
  form.locationText = String(d.locationText || "")
  form.observedAt = d.observedAt ? String(d.observedAt) : undefined
  form.description = String(d.description || "")
  await onProvinceChange()
  if (form.cityCode) await onCityChange()
  loadPhotos()
}

onMounted(async () => {
  provinces.value = await fetchProvinces()
  const fieldsPayload = (await request.get("/api/student/plant/plant-fields")) as { data: FieldDef[] }
  fields.value = fieldsPayload.data
  for (const f of fields.value) {
    if (f.fieldType === 'MULTI_SELECT') fieldMap[f.id] = []
    else if (f.fieldType === 'BOOLEAN') fieldMap[f.id] = false
    else fieldMap[f.id] = ""
  }
  await loadEdit()
  loaded.value = true
  searchSpeciesOptions("")
})
</script>

<style scoped>
.obs-edit { max-width: 900px; margin: 0 auto; padding: 26px 24px 60px; display: flex; flex-direction: column; gap: 18px; }
.obs-edit__head a { color: var(--el-color-primary); text-decoration: none; font-size: 14px; }
.obs-edit__head h1 { margin: 8px 0 4px; }
.obs-edit__tip { color: var(--text-secondary); font-size: 13px; }
.obs-edit__form { border: 1px solid var(--border-subtle); border-radius: 20px; padding: 22px 26px; background: var(--surface-elevated); }
.obs-edit__photos { display: flex; flex-wrap: wrap; gap: 10px; width: 100%; }
.obs-edit__photo { position: relative; width: 110px; height: 110px; border-radius: 12px; overflow: hidden; border: 1px solid var(--border-color); }
.obs-edit__photo :deep(.el-image) { width: 100%; height: 100%; }
.obs-edit__cover { position: absolute; left: 6px; top: 6px; background: rgba(64, 158, 255, 0.9); color: #fff; font-size: 11px; padding: 1px 8px; border-radius: 999px; }
.obs-edit__photo-del { position: absolute; right: 4px; top: 4px; width: 22px; height: 22px; }
.obs-edit__add-photo { display: flex; align-items: center; justify-content: center; width: 110px; height: 110px; border: 1px dashed var(--border-color); border-radius: 12px; cursor: pointer; color: var(--text-secondary); }
.obs-edit__row { display: flex; gap: 10px; width: 100%; flex-wrap: wrap; }
.obs-edit__fields { display: flex; flex-direction: column; gap: 14px; width: 100%; }
.obs-edit__field { display: flex; align-items: center; gap: 14px; }
.obs-edit__field-label { min-width: 130px; color: var(--text-secondary); }
.obs-edit__field-label i { color: #f56c6c; font-style: normal; margin-left: 3px; }
.obs-edit__actions { display: flex; gap: 12px; justify-content: flex-end; }
</style>