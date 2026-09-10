<template>
  <view class="page">
    <view v-if="detail" class="detail">
      <view class="name">{{ detail.commonName || detail.reportedCommonName || '待鉴定植物' }}
        <text v-if="detail.featured" class="badge">★ 优秀</text>
      </view>
      <text v-if="detail.scientificName" class="muted">{{ detail.scientificName }}</text>
      <scroll-view scroll-x class="photos">
        <image v-for="p in detail.photos || []" :key="String(p.photoId)" :src="resolveMediaUrl(p.fileUrl)" mode="aspectFill" class="big-photo" />
      </scroll-view>
      <view class="info muted">
        {{ detail.provinceName || '' }} {{ detail.cityName || '' }} {{ detail.districtName || '' }} {{ detail.locationText || '' }}
      </view>
      <view class="info muted">观察者：{{ detail.submitterName || '' }} {{ detail.className || '' }} · {{ fmtDate(detail.observedAt) }}</view>
      <view v-if="detail.identificationStatus === 'PENDING'" class="warn">未知植物 · 待教师鉴定</view>
      <view v-if="detail.qualityWarnings && detail.qualityWarnings.length" class="warn" v-for="w in detail.qualityWarnings" :key="w">⚠ {{ w }}</view>
      <view class="desc">{{ detail.description || '暂无描述' }}</view>
      <view v-for="f in detail.fieldValues || []" :key="f.fieldCode" class="info">
        <text class="muted">{{ f.fieldLabel }}：</text>{{ f.valueText || '-' }}
      </view>
      <view v-if="detail.reviewComment" class="review">教师审核意见：{{ detail.reviewComment }}</view>
      <view class="rate">
        <text class="muted">我的评分：</text>
        <text v-for="i in 5" :key="i" @tap="rate(i)" :style="{ color: myScore >= i ? '#f5a623' : '#ccc' }">★</text>
      </view>
      <view class="comments">
        <view class="sec">评论（{{ comments.length }}）</view>
        <textarea class="ipt" v-model="commentText" placeholder="友善评论…" />
        <button size="mini" type="primary" @tap="comment">发表评论</button>
        <view v-for="c in comments" :key="String(c.commentId)" class="comment">
          <view><text class="name">{{ c.userName || '匿名' }}</text><text v-if="c.isTeacherComment" class="teacher">教师</text><text v-if="c.isPinned" class="teacher">置顶</text></view>
          <view class="muted">{{ c.content }}</view>
        </view>
      </view>
    </view>
    <view v-else class="empty">加载中…</view>
  </view>
</template>

<script setup lang="ts">
import { onLoad } from "@dcloudio/uni-app"
import { ref } from "vue"
import { fetchComments, fetchPublicObservation, postComment, postRating } from "@/api/plant"
import { fmtDate, resolveMediaUrl } from "@/utils/media"
import type { CommentItem, ObsDetail } from "@/types/models"

const id = ref("")
const detail = ref<ObsDetail | null>(null)
const comments = ref<CommentItem[]>([])
const commentText = ref("")
const myScore = ref(0)

onLoad(async (query) => {
  id.value = String(query?.id || "")
  const [d, c] = await Promise.all([
    fetchPublicObservation(id.value).catch(() => null),
    fetchComments(id.value).catch(() => []),
  ])
  detail.value = d
  comments.value = c
})

async function rate(score: number) {
  myScore.value = score
  await postRating(id.value, score).catch(() => undefined)
  uni.showToast({ title: "评分成功", icon: "success" })
}

async function comment() {
  if (!commentText.value.trim()) return
  await postComment(id.value, commentText.value.trim()).catch(() => undefined)
  commentText.value = ""
  comments.value = await fetchComments(id.value).catch(() => comments.value)
}
</script>

<style scoped>
.page { padding: 20rpx; }
.detail { display: flex; flex-direction: column; gap: 12rpx; }
.name { font-size: 40rpx; font-weight: 700; }
.badge { font-size: 22rpx; color: #b7791f; margin-left: 8rpx; }
.muted { color: #888; font-size: 26rpx; }
.photos { white-space: nowrap; }
.big-photo { width: 480rpx; height: 360rpx; margin-right: 12rpx; border-radius: 14rpx; }
.info { font-size: 28rpx; }
.desc { line-height: 1.8; }
.review { background: #fff7e6; padding: 14rpx; border-radius: 10rpx; color: #8a6d1f; }
.warn { background: #fff0f0; color: #c0392b; padding: 10rpx; border-radius: 10rpx; font-size: 24rpx; }
.rate { font-size: 44rpx; }
.comments { display: flex; flex-direction: column; gap: 12rpx; margin-top: 20rpx; }
.sec { font-weight: 600; }
.ipt { border: 1rpx solid #ddd; border-radius: 10rpx; padding: 12rpx; background: #fff; }
.comment { border-bottom: 1rpx solid #eee; padding: 10rpx 0; display: flex; flex-direction: column; gap: 6rpx; }
.name { font-weight: 600; font-size: 28rpx; }
.teacher { background: #3f9b3f; color: #fff; font-size: 20rpx; border-radius: 6rpx; padding: 2rpx 10rpx; margin-left: 8rpx; }
.empty { color: #999; text-align: center; padding: 80rpx 0; }
</style>
