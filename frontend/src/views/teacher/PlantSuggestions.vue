<template>
  <div class="suggestions">
    <div class="suggestions__head">
      <div>
        <p class="page-kicker">Species Suggestions</p>
        <h1>新物种建议（待处理 {{ total }}）</h1>
        <p class="suggestions__sub">学生找不到的植物：确认后自动建为标准物种，或驳回并说明原因。</p>
      </div>
    </div>
    <el-table v-loading="loading" :data="rows">
      <el-table-column label="建议内容" min-width="220">
        <template #default="{ row }">
          <b>{{ row.suggestedCommonName || '-' }}</b>
          <i v-if="row.suggestedScientificName" style="color: var(--text-muted)">{{ row.suggestedScientificName }}</i>
          <p v-if="row.description" class="desc">{{ row.description }}</p>
        </template>
      </el-table-column>
      <el-table-column label="学生" width="110">
        <template #default="{ row }">{{ row.submitterId }}</template>
      </el-table-column>
      <el-table-column label="状态" width="100">
        <template #default="{ row }">{{ row.status }}</template>
      </el-table-column>
      <el-table-column label="时间" width="150">
        <template #default="{ row }">{{ fmtDate(row.createTime) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="220" fixed="right">
        <template #default="{ row }">
          <el-button size="small" type="success" @click="approve(row)">通过（自动建档）</el-button>
          <el-button size="small" type="danger" plain @click="reject(row)">驳回</el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-pagination v-if="total > 0" v-model:current-page="page" :page-size="pageSize" :total="total" layout="prev, pager, next, total" @current-change="load(page)" />
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from "vue"
import { ElMessage, ElMessageBox } from "element-plus"
import request from "@/api/request"
import { fmtDate, type PageResult } from "@/api/plant"

interface SuggestionRow {
  id: number | string
  suggestedCommonName?: string | null
  suggestedScientificName?: string | null
  description?: string | null
  submitterId?: number | string | null
  status: string
  createTime?: string | null
}

const rows = ref<SuggestionRow[]>([])
const total = ref(0)
const page = ref(1)
const pageSize = 10
const loading = ref(false)

const load = async (target = 1) => {
  loading.value = true
  try {
    const payload = (await request.get("/api/teacher/plant/species-suggestions", {
      params: { page: target, size: pageSize },
    })) as { data: PageResult<SuggestionRow> }
    rows.value = payload.data.records
    total.value = Number(payload.data.total)
    page.value = target
  } finally {
    loading.value = false
  }
}

const approve = async (row: SuggestionRow) => {
  const confirm = await ElMessageBox.confirm(
    "确认通过该建议？系统将使用建议名称自动创建标准物种。",
    "通过建议",
    { type: "warning" },
  ).catch(() => null)
  if (!confirm) return
  await request.post("/api/teacher/plant/species-suggestions/" + String(row.id) + "/decide", {
    status: "APPROVED",
    comment: "教师确认建档",
  })
  ElMessage.success("已通过并建档")
  load()
}

const reject = async (row: SuggestionRow) => {
  const prompt = await ElMessageBox.prompt("请填写驳回说明", "驳回建议").catch(() => null)
  if (!prompt) return
  await request.post("/api/teacher/plant/species-suggestions/" + String(row.id) + "/decide", {
    status: "REJECTED",
    comment: prompt.value,
  })
  ElMessage.success("已驳回")
  load()
}

onMounted(() => load(1))
</script>

<style scoped>
.suggestions { max-width: 1100px; margin: 0 auto; padding: 24px; display: flex; flex-direction: column; gap: 16px; }
.suggestions__sub { color: var(--text-secondary); }
.desc { color: var(--text-muted); font-size: 12px; margin: 4px 0 0; }
</style>
