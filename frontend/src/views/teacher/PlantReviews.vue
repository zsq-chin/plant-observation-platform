<template>
  <div class="plant-reviews">
    <div class="plant-reviews__head">
      <div>
        <p class="page-kicker">Plant Review</p>
        <h1>植物观察审核</h1>
      </div>
      <div class="plant-reviews__filters">
        <el-select v-model="status" style="width: 130px" @change="load(1)">
          <el-option label="待审核" value="SUBMITTED" />
          <el-option label="已通过" value="APPROVED" />
          <el-option label="已驳回" value="REJECTED" />
        </el-select>
        <el-input v-model="keyword" placeholder="筛选提交人/植物（本地）" clearable style="width: 180px" />
      </div>
    </div>

    <div v-if="dash" class="plant-reviews__dash">
      <span>待审 <b>{{ dash.pendingCount }}</b></span>
      <span>今日提交 <b>{{ dash.todaySubmittedCount }}</b></span>
      <span>今日通过 <b>{{ dash.todayApprovedCount }}</b></span>
      <span>今日驳回 <b>{{ dash.todayRejectedCount }}</b></span>
    </div>
    <div v-if="selectedIds.length" class="plant-reviews__bulk">
      <span>已选 {{ selectedIds.length }} 条</span>
      <el-button size="small" type="success" @click="batchDecide('APPROVED')">批量通过</el-button>
      <el-button size="small" type="danger" @click="batchDecide('REJECTED')">批量驳回</el-button>
    </div>
    <el-table v-loading="loading" :data="filteredRows" class="plant-reviews__table" @selection-change="onSelection">
      <el-table-column type="selection" width="44" />
      <el-table-column label="记录" min-width="240">
        <template #default="{ row }">
          <div class="review-row">
            <el-image v-if="row.coverUrl" :src="resolveMediaUrl(row.coverUrl)" fit="cover" class="review-row__cover" @error="onMediaError" />
            <div v-else class="review-row__cover review-row__cover--none">🌿</div>
            <div>
              <b>{{ row.commonName || row.reportedCommonName || "未命名植物" }}</b>
              <p class="review-row__meta">{{ row.submitterName || "" }} · {{ row.className || "-" }} · {{ row.provinceName || "" }}{{ row.cityName ? " " + row.cityName : "" }}</p>
            </div>
          </div>
        </template>
      </el-table-column>
      <el-table-column label="提交时间" width="150">
        <template #default="{ row }">{{ fmtDate(row.submitTime) }}</template>
      </el-table-column>
      <el-table-column label="照片" width="70" align="center">
        <template #default="{ row }">{{ numberValue(row.photoCount) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="220" fixed="right">
        <template #default="{ row }">
          <el-button size="small" @click="openDetail(row)">审核详情</el-button>
          <el-button v-if="row.status === 'APPROVED'" size="small" type="warning" plain @click="toggleFeatured(row)">
            {{ featuredIds.has(String(row.observationId)) ? "取消精选" : "设为优秀" }}
          </el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-pagination v-if="total > 0" v-model:current-page="page" :page-size="pageSize" :total="total" layout="prev, pager, next, total" @current-change="load(page)" />

    <!-- 审核详情 -->
    <el-dialog v-model="dialogVisible" title="审核植物观察" width="860px" top="6vh" append-to-body>
      <el-skeleton v-if="!detail" :rows="8" animated />
      <template v-else>
        <div class="review-detail">
          <div class="review-detail__photos">
            <el-image v-for="p in detail.photos || []" :key="String(p.photoId)" :src="resolveMediaUrl(p.fileUrl)" fit="cover" class="review-detail__photo"
              :preview-src-list="(detail.photos || []).map((x) => x.fileUrl)" @error="onMediaError" />
          </div>
          <div class="review-detail__info">
            <h3>{{ detail.commonName || detail.reportedCommonName || "未命名植物" }} <i>{{ detail.scientificName || "" }}</i></h3>
            <p class="review-detail__meta">提交人：{{ detail.submitterName }} · {{ detail.className || "-" }}<br />地点：{{ detail.provinceName }} {{ detail.cityName }} {{ detail.districtName }} {{ detail.locationText || "" }}<br />观察时间：{{ fmtDate(detail.observedAt) }}</p>
            <p v-if="detail.description" class="review-detail__desc">{{ detail.description }}</p>
            <div v-if="detail.fieldValues && detail.fieldValues.length" class="review-detail__fields">
              <div v-for="f in detail.fieldValues" :key="f.fieldCode" class="review-detail__field"><b>{{ f.fieldLabel }}</b><span>{{ f.valueText || "-" }}</span></div>
            </div>
            <p v-if="detail.reviewComment" class="review-detail__last-review">最近审核意见：{{ detail.reviewComment }}</p>
          </div>
        </div>
        <div class="review-detail__decide">
          <el-input v-model="decisionComment" type="textarea" :rows="3" placeholder="审核意见（驳回必填）" />
          <div class="review-detail__decide-row">
            <el-button v-if="detail.status === 'SUBMITTED'" type="success" @click="decide('APPROVED')">通过审核</el-button>
            <el-button v-if="detail.status === 'SUBMITTED'" type="danger" @click="decide('REJECTED')">驳回（需填写意见）</el-button>
            <el-button v-if="detail.status === 'APPROVED'" type="warning" plain @click="decideFeatured">推荐/取消优秀观察</el-button>
            <span v-else class="review-detail__status">当前状态：{{ statusLabel(detail.status) }}</span>
          </div>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from "vue"
import { ElMessage, ElMessageBox } from "element-plus"
import request from "@/api/request"
import { onMediaError, resolveMediaUrl } from "@/utils/media"
import { fmtDate, numberValue, type ObsDetail, type PageResult, type ReviewRow } from "@/api/plant"

const rows = ref<ReviewRow[]>([])
const dash = ref<{ pendingCount: number; todaySubmittedCount: number; todayApprovedCount: number; todayRejectedCount: number } | null>(null)
const selectedIds = ref<string[]>([])
const total = ref(0)
const page = ref(1)
const pageSize = 10
const loading = ref(false)
const status = ref("SUBMITTED")
const keyword = ref("")
const featuredIds = ref<Set<string>>(new Set())
const dialogVisible = ref(false)
const detail = ref<ObsDetail | null>(null)
const decisionComment = ref("")

const filteredRows = computed(() => {
  const k = keyword.value.trim()
  if (!k) return rows.value
  return rows.value.filter((r) =>
    (r.submitterName || "").includes(k) || (r.commonName || "").includes(k) || (r.reportedCommonName || "").includes(k),
  )
})

function onSelection(selection: ReviewRow[]) {
  selectedIds.value = selection.map((r) => String(r.observationId))
}

async function loadDashboard() {
  const payload = (await request.get("/api/teacher/plant/dashboard")) as { data: typeof dash.value }
  dash.value = payload.data
}

async function batchDecide(action: "APPROVED" | "REJECTED") {
  if (!selectedIds.value.length) return
  let comment = ""
  if (action === "REJECTED") {
    const prompt = await ElMessageBox.prompt("请填写驳回意见", "批量驳回", { inputType: "textarea" }).catch(() => null)
    if (!prompt) return
    comment = prompt.value
  }
  await request.post("/api/teacher/plant/reviews/batch", {
    observationIds: selectedIds.value,
    action,
    comment,
  })
  ElMessage.success(action === "APPROVED" ? "批量通过完成" : "批量驳回完成")
  selectedIds.value = []
  load()
}

const statusLabel = (s?: string | null) =>
  ({ DRAFT: "草稿", SUBMITTED: "待审核", APPROVED: "已通过", REJECTED: "被驳回", OFFLINE: "已下线" } as Record<string, string>)[s || ""] || s || "-"

const load = async (target = 1) => {
  loading.value = true
  try {
    const payload = (await request.get("/api/teacher/plant/reviews", {
      params: { status: status.value, page: target, size: pageSize },
    })) as { data: PageResult<ReviewRow> }
    rows.value = payload.data.records
    total.value = Number(payload.data.total)
    page.value = target
    featuredIds.value = new Set(payload.data.records.filter((r) => r.status === "APPROVED").map((r) => String(r.observationId)))
  } finally {
    loading.value = false
  }
}

const openDetail = async (row: ReviewRow) => {
  dialogVisible.value = true
  detail.value = null
  decisionComment.value = ""
  const payload = (await request.get(`/api/teacher/plant/reviews/${row.observationId}`)) as { data: ObsDetail }
  detail.value = payload.data
  if (detail.value.featured) featuredIds.value.add(String(detail.value.observationId))
}

const decide = async (action: "APPROVED" | "REJECTED") => {
  const d = detail.value
  if (!d) return
  if (action === "REJECTED" && !decisionComment.value.trim()) {
    ElMessage.warning("驳回时必须填写意见")
    return
  }
  await request.post(`/api/teacher/plant/reviews/${d.observationId}/${action === "APPROVED" ? "approve" : "reject"}`, {
    action,
    comment: decisionComment.value.trim(),
  })
  ElMessage.success(action === "APPROVED" ? "已通过审核，记录进入展廊与地图" : "已驳回，学生可修改后重提")
  dialogVisible.value = false
  load()
}

const decideFeatured = async () => {
  const d = detail.value
  if (!d) return
  const on = !featuredIds.value.has(String(d.observationId))
  await request.post(`/api/teacher/plant/observations/${d.observationId}/featured?featured=${on}`)
  ElMessage.success(on ? "已推荐为优秀观察" : "已取消精选")
  dialogVisible.value = false
  load()
}

const toggleFeatured = async (row: ReviewRow) => {
  const on = !featuredIds.value.has(String(row.observationId))
  await request.post(`/api/teacher/plant/observations/${row.observationId}/featured?featured=${on}`)
  ElMessage.success(on ? "已设为优秀观察" : "已取消精选")
  load()
}

onMounted(() => {
  load(1)
  loadDashboard()
})
</script>

<style scoped>
.plant-reviews { max-width: 1100px; margin: 0 auto; padding: 24px; display: flex; flex-direction: column; gap: 16px; }
.plant-reviews__dash { display: flex; gap: 18rpx; }
.plant-reviews__dash span { font-size: 13px; color: var(--text-secondary); }
.plant-reviews__dash b { color: var(--el-color-primary); }
.plant-reviews__bulk { display: flex; gap: 10px; align-items: center; }
.plant-reviews__head { display: flex; justify-content: space-between; align-items: center; }
.plant-reviews__filters { display: flex; gap: 10px; }
.review-row { display: flex; gap: 10px; align-items: center; }
.review-row__cover { width: 52px; height: 52px; border-radius: 10px; flex-shrink: 0; display: flex; align-items: center; justify-content: center; background: #eef4e6; }
.review-row__meta { margin: 2px 0 0; color: var(--text-muted); font-size: 12px; }
.review-detail { display: grid; grid-template-columns: 1fr 1.2fr; gap: 18px; }
.review-detail__photos { display: grid; grid-template-columns: repeat(2, 1fr); gap: 10px; align-content: start; }
.review-detail__photo { width: 100%; aspect-ratio: 1; border-radius: 12px; }
.review-detail__meta { color: var(--text-secondary); line-height: 1.9; }
.review-detail__desc { line-height: 1.8; white-space: pre-wrap; }
.review-detail__fields { display: flex; flex-direction: column; gap: 6px; }
.review-detail__field b { display: inline-block; min-width: 90px; color: var(--text-muted); font-weight: 500; }
.review-detail__last-review { padding: 8px 10px; border-radius: 8px; background: #fff7e6; }
.review-detail__decide { margin-top: 16px; display: flex; flex-direction: column; gap: 10px; }
.review-detail__decide-row { display: flex; gap: 10px; align-items: center; }
.review-detail__status { color: var(--text-muted); }
@media (max-width: 760px) { .review-detail { grid-template-columns: 1fr; } }
</style>