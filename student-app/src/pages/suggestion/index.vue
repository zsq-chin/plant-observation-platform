<template>
  <view class="page">
    <view class="intro">没找到你观察的植物？提交建议，教师确认后会补充到标准物种库。</view>
    <input v-model="commonName" class="ipt" placeholder="建议中文名（必填或填学名）" />
    <input v-model="scientificName" class="ipt" placeholder="学名（可选）" />
    <textarea v-model="description" class="ipt area" placeholder="描述叶/花/果特征、生境与发现地点…" />
    <button class="btn-primary" :loading="saving" @tap="submit">提交建议</button>
    <view class="sec">我的建议</view>
    <view v-for="s in mine" :key="String(s.id)" class="card">
      <view>{{ s.suggestedCommonName || s.suggestedScientificName || '-' }} <text class="tag">{{ s.status }}</text></view>
      <view v-if="s.reviewComment" class="muted">教师回复：{{ s.reviewComment }}</view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { onShow } from "@dcloudio/uni-app"
import { ref } from "vue"
import { createSuggestion, mySuggestions } from "@/api/plant"
import type { SuggestionItem } from "@/types/models"

const commonName = ref("")
const scientificName = ref("")
const description = ref("")
const saving = ref(false)
const mine = ref<SuggestionItem[]>([])

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
.page { padding: 24rpx; display: flex; flex-direction: column; gap: 16rpx; }
.btn-primary { background: #3f9b3f; color: #fff; }
.intro { color: #777; }
.ipt { border: 1rpx solid #ddd; border-radius: 10rpx; padding: 16rpx; background: #fff; }
.area { height: 160rpx; }
.sec { font-weight: 600; margin-top: 10rpx; }
.card { background: #fff; border-radius: 12rpx; padding: 16rpx; display: flex; flex-direction: column; gap: 6rpx; }
.tag { font-size: 22rpx; background: #eee; border-radius: 8rpx; padding: 2rpx 10rpx; margin-left: 10rpx; }
.muted { color: #999; font-size: 24rpx; }
</style>
