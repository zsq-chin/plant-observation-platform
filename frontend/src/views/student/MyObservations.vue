<template>
  <div class="my-observations">
    <div class="my-observations__head">
      <div>
        <p class="page-kicker">My Observations</p>
        <h1>我的植物观察</h1>
      </div>
      <router-link class="el-button el-button--primary" to="/student/observations/create">＋ 新增植物记录</router-link>
    </div>
    <el-tabs v-model="activeTab" @tab-change="load(1)">
      <el-tab-pane label="全部" name="ALL" />
      <el-tab-pane label="草稿" name="DRAFT" />
      <el-tab-pane label="待审核" name="SUBMITTED" />
      <el-tab-pane label="已通过" name="APPROVED" />
      <el-tab-pane label="被驳回" name="REJECTED" />
    </el-tabs>
    <el-skeleton v-if="loading && !rows.length" :rows="5" animated />
    <el-empty v-else-if="!rows.length" description="暂无记录，点击右上角开始你的第一次植物观察" />
    <div v-else class="my-observations__list">
      <article v-for="r in rows" :key="String(r.id)" class="obs-row">
        <div class="obs-row__main">
          <h3>{{ r.reportedCommonName || "未命名植物" }} <span class="obs-row__status" :class="`is-${r.status}`">{{ statusLabel(r.status) }}</span></h3>
          <p v-if="r.provinceName" class="obs-row__meta">📍 {{ r.provinceName }} {{ r.cityName || "" }} {{ r.districtName || "" }}<template v-if="r.observedAt"> · 📅 {{ fmtDate(r.observedAt) }}</template></p>
          <p v-if="r.description" class="obs-row__desc">{{ r.description }}</p>
        </div>
        <div class="obs-row__actions">
          <el-button size="small" type="primary" plain @click="continueEdit(r)">继续编辑</el-button>
          <el-button v-if="r.status === 'DRAFT' || r.status === 'REJECTED'" size="small" type="primary" @click="submitObs(r)">提交审核</el-button>
          <el-button v-if="r.status === 'SUBMITTED'" size="small" @click="withdraw(r)">撤回</el-button>
          <el-button v-if="r.status === 'DRAFT' || r.status === 'REJECTED'" size="small" type="danger" plain @click="remove(r)">删除</el-button>
        </div>
      </article>
    </div>
    <el-pagination v-if="total > 0" v-model:current-page="page" :page-size="pageSize" :total="total" layout="prev, pager, next, total" @current-change="load(page)" />
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from "vue"
import { useRouter } from "vue-router"
import { ElMessage, ElMessageBox } from "element-plus"
import request from "@/api/request"
import { fmtDate, type MyObsRow, type PageResult } from "@/api/plant"

const router = useRouter()
const rows = ref<MyObsRow[]>([])
const activeTab = ref("ALL")
const total = ref(0)
const page = ref(1)
const pageSize = 10
const loading = ref(false)

const statusLabel = (s: string) =>
  ({ DRAFT: "草稿", SUBMITTED: "待审核", APPROVED: "已通过", REJECTED: "被驳回", OFFLINE: "已下线" } as Record<string, string>)[s] || s

const load = async (target = 1) => {
  loading.value = true
  try {
    const payload = (await request.get("/api/student/plant/observations", {
      params: { status: activeTab.value === "ALL" ? undefined : activeTab.value, page: target, size: pageSize },
    })) as { data: PageResult<MyObsRow> }
    rows.value = payload.data.records
    total.value = Number(payload.data.total)
    page.value = target
  } finally {
    loading.value = false
  }
}

const continueEdit = (r: MyObsRow) => router.push(`/student/observations/edit/${r.id}`)

const submitObs = async (r: MyObsRow) => {
  await request.post(`/api/student/plant/observations/${r.id}/submit`)
  ElMessage.success("已提交审核")
  load()
}

const withdraw = async (r: MyObsRow) => {
  await request.post(`/api/student/plant/observations/${r.id}/withdraw`)
  ElMessage.success("已撤回为草稿")
  load()
}

const remove = async (r: MyObsRow) => {
  await ElMessageBox.confirm("确认删除这条记录？", "提示", { type: "warning" })
  await request.delete(`/api/student/plant/observations/${r.id}`)
  ElMessage.success("已删除")
  load()
}

onMounted(() => load(1))
</script>

<style scoped>
.my-observations { max-width: 980px; margin: 0 auto; padding: 26px 24px 50px; display: flex; flex-direction: column; gap: 18px; }
.my-observations__head { display: flex; justify-content: space-between; align-items: center; }
.my-observations__list { display: flex; flex-direction: column; gap: 12px; }
.obs-row { display: flex; justify-content: space-between; gap: 16px; padding: 16px 18px; border: 1px solid var(--border-subtle); border-radius: 16px; background: var(--surface-elevated); }
.obs-row__main h3 { margin: 0 0 6px; }
.obs-row__status { font-size: 12px; font-weight: 500; padding: 2px 10px; border-radius: 999px; margin-left: 8px; }
.is-DRAFT { background: #f4f4f5; color: #909399; } .is-SUBMITTED { background: #ecf5ff; color: #409eff; }
.is-APPROVED { background: #f0f9eb; color: #67c23a; } .is-REJECTED { background: #fef0f0; color: #f56c6c; }
.obs-row__meta { margin: 0 0 4px; color: var(--text-secondary); font-size: 13px; }
.obs-row__desc { margin: 0; color: var(--text-muted); font-size: 13px; line-height: 1.6; display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden; }
.obs-row__actions { display: flex; flex-direction: column; gap: 8px; justify-content: center; flex-shrink: 0; }
</style>
