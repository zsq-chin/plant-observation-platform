<template>
  <div class="species-page">
    <div class="species-page__head">
      <div>
        <p class="page-kicker">Species Library</p>
        <h1>植物物种库</h1>
        <p class="species-page__sub">标准植物物种条目（学生观察记录按物种归档聚合，点击查看该物种的观察记录）</p>
      </div>
      <router-link class="el-button" to="/plant">← 返回首页</router-link>
    </div>
    <el-form inline class="species-page__filters">
      <el-form-item>
        <el-input v-model="keyword" placeholder="中文名 / 学名 / 别名" clearable style="width: 240px" @keyup.enter="search(1)" />
      </el-form-item>
      <el-form-item label="类别">
        <el-select v-model="categoryId" placeholder="全部类别" clearable style="width: 130px">
          <el-option v-for="c in categories" :key="String(c.id)" :label="c.name" :value="String(c.id)" />
        </el-select>
      </el-form-item>
      <el-form-item><el-button type="primary" @click="search(1)">搜索</el-button></el-form-item>
    </el-form>
    <el-skeleton v-if="loading && !items.length" :rows="6" animated />
    <el-empty v-else-if="!items.length" description="未找到相关物种" />
    <div v-else class="species-page__grid">
      <div v-for="s in items" :key="String(s.id)" class="species-card">
        <div class="species-card__cover">
          <img v-if="s.coverUrl" :src="resolveMediaUrl(s.coverUrl)" :alt="s.commonName + ' 物种照片'" loading="lazy" @error="onMediaError" />
          <span v-else>🌿</span>
        </div>
        <div class="species-card__body">
          <h3>{{ s.commonName }}</h3>
          <p v-if="s.scientificName" class="species-card__sci">{{ s.scientificName }}</p>
          <p class="species-card__tax" v-if="s.familyName || s.genusName">{{ s.familyName || "-" }} · {{ s.genusName || "-" }}</p>
          <div class="species-card__links">
            <router-link class="species-card__link" :to="'/plant/gallery?keyword=' + encodeURIComponent(s.commonName)">查看观察记录 →</router-link>
            <router-link class="species-card__link" :to="'/plant/map?sp=' + String(s.id)">🗺 在地图中查看</router-link>
          </div>
        </div>
      </div>
    </div>
    <div v-if="total > 0" class="species-page__pager">
      <el-pagination v-model:current-page="page" :page-size="pageSize" :total="total" layout="prev, pager, next, total" @current-change="search(page)" />
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from "vue"
import { fetchCategories, searchSpecies, type CategoryItem, type SpeciesItem } from "@/api/plant"
import { onMediaError, resolveMediaUrl } from "@/utils/media"

const items = ref<SpeciesItem[]>([])
const categories = ref<CategoryItem[]>([])
const keyword = ref("")
const categoryId = ref<string | undefined>(undefined)
const total = ref(0)
const page = ref(1)
const pageSize = 20
const loading = ref(false)

const search = async (target = 1) => {
  loading.value = true
  try {
    const result = await searchSpecies(keyword.value || "", categoryId.value ? String(categoryId.value) : undefined)
    items.value = result.records
    total.value = Number(result.total)
    page.value = target
  } finally {
    loading.value = false
  }
}

onMounted(async () => {
  try { categories.value = await fetchCategories() } catch { /* ignore */ }
  search(1)
})
</script>

<style scoped>
.species-page { max-width: 1200px; margin: 0 auto; padding: 30px 28px 60px; display: flex; flex-direction: column; gap: 20px; }
.species-page__head { display: flex; justify-content: space-between; align-items: center; }
.species-page__sub { color: var(--text-secondary); }
.species-page__grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(230px, 1fr)); gap: 18px; }
.species-card { border: 1px solid var(--border-subtle); border-radius: 16px; overflow: hidden; background: var(--surface-elevated); }
.species-card__cover { height: 120px; display: flex; align-items: center; justify-content: center; font-size: 52px; background: linear-gradient(135deg, var(--brand-soft), #dfe8c8); }
.species-card__cover img { width: 100%; height: 100%; object-fit: cover; }
.species-card__body { padding: 14px 16px 16px; }
.species-card__body h3 { margin: 0 0 4px; }
.species-card__sci { margin: 0 0 6px; color: var(--text-muted); font-size: 12px; font-style: italic; }
.species-card__tax { margin: 0 0 10px; color: var(--text-secondary); font-size: 13px; }
.species-card__links { display: flex; gap: 12px; flex-wrap: wrap; }
.species-card__link { font-size: 13px; color: var(--el-color-primary); text-decoration: none; }
.species-card__link:hover { text-decoration: underline; }
.species-page__pager { display: flex; justify-content: center; }
</style>
