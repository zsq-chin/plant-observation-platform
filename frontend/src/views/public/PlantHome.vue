<template>
  <div class="plant-home">
    <!-- Hero -->
    <section class="plant-home__hero">
      <p class="page-kicker">National Plant Observation</p>
      <h1 class="plant-home__title">全国植物观察与交流平台</h1>
      <p class="plant-home__subtitle">记录身边植物 · 分享自然发现 · 共建植物观察数据库</p>
      <div v-if="stats" class="plant-home__stats">
        <div class="stat-card"><b>{{ numberValue(stats.speciesCount) }}</b><span>植物物种</span></div>
        <div class="stat-card"><b>{{ numberValue(stats.observationCount) }}</b><span>观察记录</span></div>
        <div class="stat-card"><b>{{ numberValue(stats.studentCount) }}</b><span>参与学生</span></div>
        <div class="stat-card"><b>{{ numberValue(stats.provinceCount) }}</b><span>覆盖省份</span></div>
      </div>
      <div class="plant-home__cta">
        <router-link class="el-button el-button--primary el-button--large" to="/plant/map">🗺 进入植物地图</router-link>
        <router-link class="el-button el-button--large" to="/plant/gallery">浏览全部植物观察</router-link>
        <router-link class="el-button el-button--large" to="/plant/species">植物物种库</router-link>
      </div>
    </section>

    <!-- 教师点评 -->
    <section v-if="home && home.teacherComments.length" class="plant-home__section">
      <h2 class="plant-home__heading">最新教师点评</h2>
      <div class="plant-home__quotes">
        <router-link v-for="t in home.teacherComments" :key="String(t.commentId)"
          :to="`/plant/observations/${t.observationId}`" class="quote-card">
          <p class="quote-card__text">“{{ t.content }}”</p>
          <p class="quote-card__meta">{{ t.teacherName || "教师" }} · {{ t.plantName || "植物观察" }} · {{ fmtDate(t.createTime) }}</p>
        </router-link>
      </div>
    </section>

    <!-- 优秀观察 -->
    <section v-if="home && home.featuredObservations.length" class="plant-home__section">
      <div class="plant-home__section-head">
        <h2 class="plant-home__heading">优秀观察（教师精选）</h2>
        <router-link to="/plant/gallery?featured=true" class="plant-home__more">查看更多 →</router-link>
      </div>
      <div class="plant-home__grid">
        <ObservationCard v-for="item in home.featuredObservations" :key="String(item.observationId)" :item="item" />
      </div>
    </section>

    <!-- 最新观察 -->
    <section class="plant-home__section">
      <div class="plant-home__section-head">
        <h2 class="plant-home__heading">植物观察展廊 · 最新</h2>
        <router-link to="/plant/gallery" class="plant-home__more">查看更多 →</router-link>
      </div>
      <el-skeleton v-if="!home" :rows="6" animated />
      <div v-else class="plant-home__grid">
        <ObservationCard v-for="item in home.latestObservations" :key="String(item.observationId)" :item="item" />
      </div>
      <el-empty v-if="home && !home.latestObservations.length" description="暂无公开的植物观察，等待第一份记录" />
    </section>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from "vue"
import ObservationCard from "@/components/ObservationCard.vue"
import { fetchHome, fmtDate, numberValue, type HomeData } from "@/api/plant"

const home = ref<HomeData | null>(null)
const error = ref("")
const stats = ref<HomeData["statistics"] | null>(null)

const load = async () => {
  try {
    const data = await fetchHome()
    home.value = data
    stats.value = data.statistics
  } catch {
    error.value = "首页数据加载失败，请稍后重试"
  }
}

onMounted(load)
</script>

<style scoped>
.plant-home {
  display: flex;
  flex-direction: column;
  gap: 44px;
  padding: 36px 28px 64px;
  max-width: 1200px;
  margin: 0 auto;
}
.plant-home__hero {
  text-align: center;
  padding: 52px 24px 30px;
  border-radius: 32px;
  background:
    linear-gradient(160deg, color-mix(in srgb, var(--brand-soft) 80%, transparent), transparent 55%),
    linear-gradient(180deg, color-mix(in srgb, #e8f4e4 70%, transparent), transparent);
}
.plant-home__title {
  margin: 0;
  font-family: var(--font-display);
  font-size: clamp(30px, 4vw, 46px);
}
.plant-home__subtitle {
  color: var(--text-secondary);
  margin: 10px 0 26px;
  font-size: 15px;
}
.plant-home__stats {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 14px;
  margin-bottom: 24px;
}
.stat-card {
  min-width: 120px;
  padding: 14px 18px;
  border-radius: 16px;
  background: color-mix(in srgb, var(--card-bg) 82%, transparent);
  border: 1px solid var(--border-subtle);
}
.stat-card b {
  display: block;
  font-size: 26px;
  color: var(--el-color-primary);
}
.stat-card span {
  font-size: 12px;
  color: var(--text-muted);
}
.plant-home__cta {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 12px;
}
.plant-home__section {
  display: flex;
  flex-direction: column;
  gap: 18px;
}
.plant-home__section-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.plant-home__heading {
  margin: 0;
  font-family: var(--font-display);
  font-size: 24px;
}
.plant-home__more {
  color: var(--el-color-primary);
  text-decoration: none;
  font-size: 14px;
}
.plant-home__grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(230px, 1fr));
  gap: 20px;
}
.plant-home__quotes {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
  gap: 14px;
}
.quote-card {
  padding: 16px 18px;
  border-radius: 16px;
  border: 1px solid var(--border-subtle);
  background: var(--surface-elevated);
  text-decoration: none;
  color: inherit;
}
.quote-card__text {
  margin: 0 0 10px;
  line-height: 1.7;
  color: var(--text-primary);
}
.quote-card__meta {
  margin: 0;
  font-size: 12px;
  color: var(--text-muted);
}
@media (max-width: 640px) {
  .plant-home { padding: 16px 14px 40px; }
}
</style>
