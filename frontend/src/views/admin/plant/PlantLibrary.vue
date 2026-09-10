<template>
  <div class="plant-admin">
    <div class="plant-admin__head">
      <div>
        <p class="page-kicker">Plant Platform · Admin</p>
        <h1>植物平台管理</h1>
      </div>
    </div>
    <el-tabs v-model="tab">
      <!-- 植物类别 -->
      <el-tab-pane label="植物类别" name="category">
        <div class="plant-admin__toolbar">
          <el-button type="primary" @click="openCategory()">＋ 新增类别</el-button>
        </div>
        <el-table :data="categories" v-loading="categoryLoading">
          <el-table-column prop="name" label="名称" width="140" />
          <el-table-column prop="code" label="编码" width="140" />
          <el-table-column prop="description" label="说明" min-width="220" />
          <el-table-column prop="sortOrder" label="排序" width="80" />
          <el-table-column label="启用" width="90">
            <template #default="{ row }">{{ row.enabled === 1 ? "是" : "否" }}</template>
          </el-table-column>
          <el-table-column label="操作" width="150" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" @click="openCategory(row)">编辑</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>
      <!-- 植物物种 -->
      <el-tab-pane label="植物物种库" name="species">
        <div class="plant-admin__toolbar">
          <el-input v-model="speciesKeyword" placeholder="中文名/学名" clearable style="width: 220px" @keyup.enter="loadSpecies(1)" />
          <el-select v-model="speciesCategory" placeholder="类别" clearable style="width: 140px">
            <el-option v-for="c in categories" :key="String(c.id)" :label="c.name" :value="String(c.id)" />
          </el-select>
          <el-button type="primary" @click="loadSpecies(1)">搜索</el-button>
          <el-button type="primary" plain @click="openSpecies()">＋ 新增物种</el-button>
        </div>
        <el-table :data="speciesRows" v-loading="speciesLoading">
          <el-table-column prop="commonName" label="中文名" width="140" />
          <el-table-column prop="scientificName" label="学名" min-width="180" />
          <el-table-column label="科/属" min-width="160">
            <template #default="{ row }">{{ row.familyName || "-" }} / {{ row.genusName || "-" }}</template>
          </el-table-column>
          <el-table-column label="类别" width="110">
            <template #default="{ row }">{{ categoryName(row.categoryId) }}</template>
          </el-table-column>
          <el-table-column label="启用" width="90">
            <template #default="{ row }">{{ row.enabled === 1 ? "是" : "否" }}</template>
          </el-table-column>
          <el-table-column label="操作" width="120" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" @click="openSpecies(row)">编辑</el-button>
            </template>
          </el-table-column>
        </el-table>
        <el-pagination v-if="speciesTotal > 0" v-model:current-page="speciesPage" :page-size="20" :total="speciesTotal" layout="prev, pager, next, total" @current-change="loadSpecies" />
      </el-tab-pane>
    </el-tabs>

    <el-dialog v-model="categoryDialog" :title="categoryForm.id ? '编辑类别' : '新增类别'" width="480px">
      <el-form label-width="80px">
        <el-form-item label="名称" required><el-input v-model="categoryForm.name" /></el-form-item>
        <el-form-item label="编码" required><el-input v-model="categoryForm.code" /></el-form-item>
        <el-form-item label="说明"><el-input v-model="categoryForm.description" type="textarea" /></el-form-item>
        <el-form-item label="排序"><el-input-number v-model="categoryForm.sortOrder" :min="0" /></el-form-item>
        <el-form-item label="启用"><el-switch v-model="categoryForm.enabled" :active-value="1" :inactive-value="0" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="categoryDialog = false">取消</el-button>
        <el-button type="primary" @click="saveCategory">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="speciesDialog" :title="speciesForm.id ? '编辑物种' : '新增物种'" width="620px">
      <el-form label-width="90px">
        <el-form-item label="中文名" required><el-input v-model="speciesForm.commonName" /></el-form-item>
        <el-form-item label="学名"><el-input v-model="speciesForm.scientificName" placeholder="如 Ginkgo biloba" /></el-form-item>
        <el-form-item label="科"><el-input v-model="speciesForm.familyName" /></el-form-item>
        <el-form-item label="属"><el-input v-model="speciesForm.genusName" /></el-form-item>
        <el-form-item label="类别">
          <el-select v-model="speciesForm.categoryId" clearable style="width: 100%">
            <el-option v-for="c in categories" :key="String(c.id)" :label="c.name" :value="String(c.id)" />
          </el-select>
        </el-form-item>
        <el-form-item label="别名"><el-input v-model="speciesForm.aliasNames" placeholder="逗号分隔" /></el-form-item>
        <el-form-item label="介绍"><el-input v-model="speciesForm.description" type="textarea" :rows="3" /></el-form-item>
        <el-form-item label="启用"><el-switch v-model="speciesForm.enabled" :active-value="1" :inactive-value="0" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="speciesDialog = false">取消</el-button>
        <el-button type="primary" @click="saveSpecies">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from "vue"
import { ElMessage } from "element-plus"
import request from "@/api/request"
import { type CategoryItem, type PageResult, type SpeciesItem } from "@/api/plant"

const tab = ref("category")
const categories = ref<CategoryItem[]>([])
const categoryLoading = ref(false)
const speciesRows = ref<SpeciesItem[]>([])
const speciesLoading = ref(false)
const speciesKeyword = ref("")
const speciesCategory = ref<string | undefined>(undefined)
const speciesPage = ref(1)
const speciesTotal = ref(0)

const loadCategories = async () => {
  categoryLoading.value = true
  try {
    const payload = (await request.get("/api/admin/plant/categories")) as { data: CategoryItem[] }
    categories.value = payload.data
  } finally {
    categoryLoading.value = false
  }
}

const loadSpecies = async (page = speciesPage.value) => {
  speciesLoading.value = true
  try {
    const payload = (await request.get("/api/admin/plant/species", {
      params: { keyword: speciesKeyword.value || undefined, categoryId: speciesCategory.value || undefined, page, size: 20 },
    })) as { data: PageResult<SpeciesItem> }
    speciesRows.value = payload.data.records
    speciesTotal.value = Number(payload.data.total)
    speciesPage.value = page
  } finally {
    speciesLoading.value = false
  }
}

const categoryName = (id?: number | string | null) =>
  categories.value.find((c) => String(c.id) === String(id))?.name || "-"

const categoryDialog = ref(false)
const categoryForm = reactive<{ id?: string; name: string; code: string; description: string; sortOrder: number; enabled: number }>({
  name: "", code: "", description: "", sortOrder: 0, enabled: 1,
})
const openCategory = (row?: { id: number | string; name: string; code: string; description?: string | null; sortOrder?: number | null; enabled?: number | null }) => {
  categoryForm.id = row ? String(row.id) : undefined
  categoryForm.name = row?.name || ""
  categoryForm.code = row?.code || ""
  categoryForm.description = row?.description || ""
  categoryForm.sortOrder = row?.sortOrder ?? 0
  categoryForm.enabled = row?.enabled ?? 1
  categoryDialog.value = true
}
const saveCategory = async () => {
  const body = {
    name: categoryForm.name,
    code: categoryForm.code,
    description: categoryForm.description || null,
    sortOrder: categoryForm.sortOrder,
    enabled: categoryForm.enabled,
  }
  if (categoryForm.id) {
    await request.put(`/api/admin/plant/categories/${categoryForm.id}`, body)
  } else {
    await request.post("/api/admin/plant/categories", body)
  }
  ElMessage.success("已保存")
  categoryDialog.value = false
  loadCategories()
}

const speciesDialog = ref(false)
const speciesForm = reactive<{ id?: string; commonName: string; scientificName: string; familyName: string; genusName: string; categoryId?: string; aliasNames: string; description: string; enabled: number }>({
  commonName: "", scientificName: "", familyName: "", genusName: "", aliasNames: "", description: "", enabled: 1,
})
const openSpecies = (row?: { id: number | string; commonName: string; scientificName?: string | null; familyName?: string | null; genusName?: string | null; categoryId?: number | string | null; aliasNames?: string | null; description?: string | null; enabled?: number | null }) => {
  speciesForm.id = row ? String(row.id) : undefined
  speciesForm.commonName = row?.commonName || ""
  speciesForm.scientificName = row?.scientificName || ""
  speciesForm.familyName = row?.familyName || ""
  speciesForm.genusName = row?.genusName || ""
  speciesForm.categoryId = row?.categoryId ? String(row.categoryId) : undefined
  speciesForm.aliasNames = row?.aliasNames || ""
  speciesForm.description = row?.description || ""
  speciesForm.enabled = row?.enabled ?? 1
  speciesDialog.value = true
}
const saveSpecies = async () => {
  if (!speciesForm.commonName.trim()) {
    ElMessage.warning("请填写中文名")
    return
  }
  const body = {
    commonName: speciesForm.commonName,
    scientificName: speciesForm.scientificName || null,
    familyName: speciesForm.familyName || null,
    genusName: speciesForm.genusName || null,
    categoryId: speciesForm.categoryId ? String(speciesForm.categoryId) : null,
    aliasNames: speciesForm.aliasNames || null,
    description: speciesForm.description || null,
    source: "管理员维护",
    enabled: speciesForm.enabled,
  }
  if (speciesForm.id) {
    await request.put(`/api/admin/plant/species/${speciesForm.id}`, body)
  } else {
    await request.post("/api/admin/plant/species", body)
  }
  ElMessage.success("已保存")
  speciesDialog.value = false
  loadSpecies(1)
  loadCategories()
}

onMounted(() => {
  loadCategories()
  loadSpecies(1)
})
</script>

<style scoped>
.plant-admin { max-width: 1200px; margin: 0 auto; padding: 22px; display: flex; flex-direction: column; gap: 16px; }
.plant-admin__head h1 { margin: 0; }
.plant-admin__toolbar { display: flex; gap: 10px; margin-bottom: 14px; flex-wrap: wrap; }
</style>
