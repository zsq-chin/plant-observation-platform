<template>
  <view class="page">
    <view class="card card--ink">
      <text class="h2">没找到你观察的植物？</text>
      <text class="ink-note">提交建议，教师确认后会补充到标准物种库，其他同学也能选到它。</text>
    </view>

    <view class="card">
      <text class="label">建议中文名</text>
      <input v-model="commonName" class="ipt" placeholder="例如：珙桐" />
      <text class="label">学名（可选）</text>
      <input v-model="scientificName" class="ipt" placeholder="例如：Davidia involucrata" />
      <text class="label">特征描述（可选）</text>
      <textarea v-model="description" class="ipt area" placeholder="叶/花/果特征、生境与发现地点…" />
      <button class="btn-primary btn-block" :loading="saving" @tap="submit">提交建议</button>
    </view>

    <view class="sec"><text class="sec__title">我的建议</text><text class="sec__more">{{ mine.length }} 条</text></view>
    <view v-for="s in mine" :key="String(s.id)" class="card">
      <view class="row">
        <text class="h3 grow ellipsis">{{ s.suggestedCommonName || s.suggestedScientificName || '-' }}</text>
        <text class="chip" :class="statusChip(s.status)">{{ statusLabel(s.status) }}</text>
      </view>
      <text v-if="s.suggestedScientificName" class="muted sci">{{ s.suggestedScientificName }}</text>
      <text v-if="s.reviewComment" class="muted">教师回复：{{ s.reviewComment }}</text>
    </view>
    <EmptyState v-if="!mine.length" icon="💡" title="还没有提交过建议" hint="发现新物种时提交，教师会尽快确认" />
  </view>
</template>

<script setup lang="ts">
import { onShow } from "@dcloudio/uni-app"
import { ref } from "vue"
import { createSuggestion, mySuggestions } from "@/api/plant"
import EmptyState from "@/components/EmptyState.vue"
import type { SuggestionItem } from "@/types/models"

const commonName = ref("")
const scientificName = ref("")
const description = ref("")
const saving = ref(false)
const mine = ref<SuggestionItem[]>([])

const LABELS: Record<string, string> = {
  PENDING: "待教师确认",
  APPROVED: "已采纳",
  REJECTED: "未采纳",
}
function statusLabel(status: string) {
  return LABELS[status] || status
}
function statusChip(status: string) {
  if (status === "APPROVED") return "chip--brand"
  if (status === "REJECTED") return "chip--danger"
  return "chip--warn"
}

onShow(async () => {
  const result = await mySuggestions(1, 20).catch(() => null)
  mine.value = result ? result.records : []
})

async function submit() {
  if (!commonName.value.trim() && !scientificName.value.trim()) {
    uni.showToast({ title: "请至少填写中文名或学名", icon: "none" })
    return
  }
  saving.value = true
  try {
    await createSuggestion({
      suggestedCommonName: commonName.value.trim() || null,
      suggestedScientificName: scientificName.value.trim() || null,
      description: description.value.trim() || null,
    })
    uni.showToast({ title: "建议已提交，感谢贡献", icon: "success" })
    // 提交成功后才清空表单，失败时保留用户输入
    commonName.value = ""
    scientificName.value = ""
    description.value = ""
    const result = await mySuggestions(1, 20).catch(() => null)
    mine.value = result ? result.records : []
  } finally {
    saving.value = false
  }
}
</script>

<style scoped>
.ink-note { font-size: 24rpx; color: rgba(255, 255, 255, 0.85); }
.area { height: 180rpx; width: 100%; box-sizing: border-box; }
.sci { font-style: italic; }
</style>
