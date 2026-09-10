<template>
  <div class="plant-gallery">
    <div class="plant-gallery__head">
      <div>
        <p class="page-kicker">Plant Gallery</p>
        <h1 class="plant-gallery__title">植物观察展廊</h1>
      </div>
      <router-link class="el-button el-button--primary" to="/plant">← 返回首页</router-link>
    </div>

    <el-form class="plant-gallery__filters" inline>
      <el-form-item>
        <el-input v-model="query.keyword" placeholder="搜索植物 / 学名 / 学生姓名" clearable style="width: 220px" @keyup.enter="search(1)" />
      </el-form-item>
      <el-form-item label="省份">
        <el-select v-model="query.provinceCode" placeholder="全部省份" clearable style="width: 150px">
          <el-option v-for="p in provinces" :key="p.regionCode" :label="p.regionName" :value="p.regionCode" />
        </el-select>
      </el-form-item>
      <el-form-item label="类别">
        <el-select v-model="query.categoryId" placeholder="全部类别" clearable style="width: 130px">
          <el-option v-for="c in categories" :key="String(c.id)" :label="c.name" :value="String(c.id)" />
        </el-select>
      </el-form-item>
      <el-form-item label="班级">
        <el-select v-model="query.classId" placeholder="全部班级" clearable style="width: 140px">
          <el-option v-for="c in classes" :key="String(c.id)" :label="c.dictLabel" :value="String(c.id)" />
        </el-select>
      </el-form-item>
      <el-form-item label="年份">
        <el-select v-model="query.year" placeholder="全部年份" clearable style="width: 110px">
          <el-option v-for="y in years" :key="y" :label="String(y)" :value="y" />
        </el-select>
      </el-form-item>
      <el-form-item label="排序">
        <el-select v-model="query.sort" style="width: 120px">
          <el-option label="精选优先" value="featured" />
          <el-option label="最新发布" value="latest" />
          <el-option label="浏览最多" value="view" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" @click="search(1)">搜索</el-button>
        <el-button @click="reset">重置</el-button>
      </el-form-item>
    </el-form>

    <el-skeleton v-if="loading && !items.length" :rows="8" animated />
    <el-empty v-else-if="!items.length" description="没有找到符合条件的植物观察，去记录第一份吧" />

    <div v-else class="plant-gallery__grid">
      <ObservationCard v-for="item in items" :key="String(item.observationId)" :item="item" />
    </div>

    <div v-if="total > 0" class="plant-gallery__pager">
      <el-pagination
        v-model:current-page="page"
        :page-size="pageSize"
        :total="total"
        layout="prev, pager, next, total"
        @current-change="search(page)"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from "vue"
import { useRoute, useRouter } from "vue-router"
import ObservationCard from "@/components/ObservationCard.vue"
import {
  fetchCategories,
  fetchGallery,
  fetchProvinces,
  fetchPublicClasses,
  type CategoryItem,
  type ClassItem,
  type GalleryItem,
  type RegionItem,
} from "@/api/plant"

const route = useRoute()
const router = useRouter()

const items = ref<GalleryItem[]>([])
const provinces = ref<RegionItem[]>([])
const categories = ref<CategoryItem[]>([])
const classes = ref<ClassItem[]>([])
const total = ref(0)
const page = ref(1)
const pageSize = 12
const loading = ref(false)
const years = [2024, 2025, 2026]

const query = reactive<{
  keyword?: string
  provinceCode?: string
  categoryId?: number | string
  classId?: number | string
  year?: number
  featured?: boolean
  sort: "featured" | "latest" | "view"
}>({
  keyword: route.query.keyword ? String(route.query.keyword) : "",
  featured: route.query.featured === "true" ? true : undefined,
  sort: "featured",
})

const loadOptions = async () => {
  try {
    const [p, c, cl] = await Promise.all([fetchProvinces(), fetchCategories(), fetchPublicClasses()])
    provinces.value = p
    categories.value = c
    classes.value = cl
  } catch {
    /* options 失败不阻塞列表 */
  }
}

const search = async (targetPage = page.value) => {
  loading.value = true
  try {
    const result = await fetchGallery({
      page: targetPage,
      size: pageSize,
      keyword: query.keyword || undefined,
      provinceCode: query.provinceCode || undefined,
      categoryId: query.categoryId ? String(query.categoryId) : undefined,
      classId: query.classId ? String(query.classId) : undefined,
      year: query.year || undefined,
      featured: query.featured,
      sort: query.sort,
    })
    items.value = result.records
    total.value = Number(result.total)
    page.value = targetPage
    router.replace({ query: { ...route.query, keyword: query.keyword || undefined } })
  } finally {
    loading.value = false
  }
}

const reset = () => {
  query.keyword = undefined
  query.provinceCode = undefined
  query.categoryId = undefined
  query.classId = undefined
  query.year = undefined
  query.featured = undefined
  query.sort = "featured"
  search(1)
}

onMounted(() => {
  loadOptions()
  search(1)
})
</script>

<style scoped>
.plant-gallery {
  max-width: 1200px;
  margin: 0 auto;
  padding: 30px 28px 60px;
  display: flex;
  flex-direction: column;
  gap: 22px;
}
.plant-gallery__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.plant-gallery__title {
  margin: 0;
  font-family: var(--font-display);
  font-size: 30px;
}
.plant-gallery__grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(230px, 1fr));
  gap: 20px;
}
.plant-gallery__pager {
  display: flex;
  justify-content: center;
}
</style>
