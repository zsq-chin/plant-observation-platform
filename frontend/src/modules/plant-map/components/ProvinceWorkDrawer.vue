<template>
  <aside class="province-drawer" aria-label="省份作品面板">
    <header class="province-drawer__header">
      <div>
        <p class="province-drawer__kicker">省份作品</p>
        <h2 class="province-drawer__title">{{ provinceName || "省份" }}</h2>
        <p class="province-drawer__stat">
          {{ observationCount }} 条观察 · {{ speciesCount }} 种植物
          <span v-if="studentCount"> · {{ studentCount }} 名学生</span>
        </p>
      </div>
      <button type="button" class="province-drawer__close" aria-label="关闭面板" @click="emit('close')">×</button>
    </header>

    <div v-if="loading && !works.length" class="province-drawer__state">作品加载中…</div>
    <el-alert v-else-if="error" type="error" :closable="false" class="province-drawer__alert">
      <template #title>
        {{ error }}
        <el-button size="small" link type="primary" @click="emit('retry')">重试</el-button>
      </template>
    </el-alert>
    <template v-else-if="works.length">
      <section v-if="featuredWorks.length" class="province-drawer__section">
        <h3 class="province-drawer__subtitle">精选作品</h3>
        <div class="province-drawer__featured">
          <PlantWorkCard v-for="work in featuredWorks" :key="String(work.observationId)" :work="work" />
        </div>
      </section>
      <section class="province-drawer__section">
        <h3 class="province-drawer__subtitle">{{ featuredWorks.length ? "全部观察" : "学生观察" }}</h3>
        <div class="province-drawer__grid">
          <PlantWorkCard v-for="work in normalWorks" :key="String(work.observationId)" :work="work" compact />
        </div>
      </section>
      <div class="province-drawer__more">
        <el-button v-if="hasMore" size="small" :loading="loading" @click="emit('load-more')">
          查看更多（已显示 {{ works.length }} / {{ total }}）
        </el-button>
        <span v-else class="province-drawer__end">已显示全部 {{ total }} 条</span>
      </div>
    </template>
    <div v-else class="province-drawer__empty">
      <p>该省暂无公开作品</p>
      <router-link class="province-drawer__link" :to="'/plant/gallery?provinceCode=' + provinceCode">去展廊浏览全国作品 →</router-link>
    </div>
  </aside>
</template>

<script setup lang="ts">
import { computed } from "vue"
import PlantWorkCard from "./PlantWorkCard.vue"
import type { ProvinceWorkRow } from "@/modules/plant-map/types"

const props = defineProps<{
  provinceCode: string
  provinceName?: string
  observationCount?: number | string
  speciesCount?: number | string
  studentCount?: number | string
  works: ProvinceWorkRow[]
  loading?: boolean
  error?: string
  total: number
}>()

const emit = defineEmits<{
  (event: "close"): void
  (event: "load-more"): void
  (event: "retry"): void
}>()

const featuredWorks = computed(() => props.works.filter((work) => work.featured))
const normalWorks = computed(() => props.works.filter((work) => !work.featured))
const hasMore = computed(() => props.works.length < Number(props.total || 0))
</script>

<style scoped>
.province-drawer { display: flex; flex-direction: column; gap: 12px; }
.province-drawer__header { display: flex; justify-content: space-between; align-items: flex-start; gap: 8px; }
.province-drawer__kicker { margin: 0; color: var(--text-muted); font-size: 12px; }
.province-drawer__title { margin: 2px 0; font-size: 20px; }
.province-drawer__stat { margin: 0; color: var(--text-secondary); font-size: 12px; }
.province-drawer__close { border: none; background: none; font-size: 22px; line-height: 1; cursor: pointer; color: var(--text-muted); }
.province-drawer__section { display: flex; flex-direction: column; gap: 8px; }
.province-drawer__subtitle { margin: 0; font-size: 13px; color: var(--text-secondary); }
.province-drawer__featured { display: grid; grid-template-columns: 1fr; gap: 10px; }
.province-drawer__grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(132px, 1fr)); gap: 8px; }
.province-drawer__state { padding: 18px 4px; color: var(--text-muted); font-size: 13px; }
.province-drawer__empty { display: flex; flex-direction: column; gap: 8px; padding: 24px 4px; color: var(--text-muted); font-size: 13px; }
.province-drawer__link { color: var(--el-color-primary); font-size: 13px; }
.province-drawer__more { display: flex; justify-content: center; padding-top: 4px; }
.province-drawer__end { color: var(--text-muted); font-size: 12px; }
</style>
