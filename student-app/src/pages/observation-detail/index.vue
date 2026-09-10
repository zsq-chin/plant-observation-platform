<template>
  <view class="page">
    <view v-if="ready" class="detail">
      <view class="name">
        {{ viewName }}
        <text v-if="statusText" class="badge">{{ statusText }}</text>
        <text v-if="featured" class="badge badge--star">★ 优秀</text>
      </view>
      <text v-if="viewScientific" class="muted">{{ viewScientific }}</text>

      <scroll-view scroll-x class="photos">
        <image
          v-for="p in viewPhotos"
          :key="String(p.key)"
          :src="resolveMediaUrl(p.thumbnailUrl || p.fileUrl)"
          mode="aspectFill"
          class="big-photo"
        />
      </scroll-view>
      <view v-if="!viewPhotos.length" class="muted">暂无照片</view>

      <view class="info muted">{{ viewPlace }}</view>
      <view v-if="observerText" class="info muted">观察者：{{ observerText }}</view>
      <view v-if="viewObservedAt" class="info muted">观察时间：{{ fmtDate(viewObservedAt) }}</view>
      <view v-if="identificationPending" class="warn">未知植物 · 待教师鉴定</view>

      <view v-if="reviewText" class="review" :class="{ 'review--reject': reviewRejected }">{{ reviewText }}</view>
      <view v-else-if="statusText === '待审核'" class="muted">已提交，等待教师审核。</view>
      <view v-else-if="statusText === '被驳回'" class="warn">该观察已被驳回：修改后可在「我的植物观察」重新提交。</view>

      <view class="desc">{{ viewDescription }}</view>
      <view v-for="f in viewFields" :key="f.fieldCode" class="info">
        <text class="muted">{{ f.fieldLabel }}：</text>{{ f.valueText || '-' }}
      </view>

      <view v-if="isMine" class="actions">
        <button size="mini" @tap="goEdit">继续编辑</button>
        <button v-if="canSubmit" size="mini" class="btn-primary" @tap="submitNow">提交审核</button>
        <button v-if="statusText === '待审核'" size="mini" @tap="withdrawNow">撤回</button>
      </view>

      <template v-if="canInteract">
        <view class="rate">
          <text class="muted">我的评分：</text>
          <text v-for="i in 5" :key="i" @tap="rate(i)" :style="{ color: myScore >= i ? '#f5a623' : '#ccc' }">★</text>
        </view>
        <view class="comments">
          <view class="sec">评论（{{ comments.length }}）</view>
          <textarea class="ipt" v-model="commentText" placeholder="友善评论…" />
          <button size="mini" class="btn-primary" @tap="sendComment">发表评论</button>
          <view v-for="c in comments" :key="String(c.commentId)" class="comment">
            <view>
              <text class="name">{{ c.userName || '匿名' }}</text>
              <text v-if="c.isTeacherComment" class="teacher">教师</text>
              <text v-if="c.isPinned" class="teacher">置顶</text>
            </view>
            <view class="muted">{{ c.content }}</view>
          </view>
        </view>
      </template>
      <view v-else-if="isMine" class="muted locked">审核通过后，其他同学即可在展廊看到这条观察并留言评分。</view>
    </view>
    <view v-else-if="loading" class="empty">加载中…</view>
    <view v-else class="empty">记录不存在或未公开</view>
  </view>
</template>

<script setup lang="ts">
import { onLoad } from "@dcloudio/uni-app"
import { computed, ref } from "vue"
import {
  fetchComments,
  fetchPublicObservation,
  myObservationDetail,
  myObservationPhotos,
  myObservationReview,
  postComment,
  postRating,
  submitObservation,
  withdrawObservation,
  type ObservationReview,
} from "@/api/plant"
import { fmtDate, resolveMediaUrl, STATUS_LABELS } from "@/utils/media"
import type { CommentItem, MyObservation, ObsDetail, PhotoItem } from "@/types/models"

const id = ref("")
const isPublicView = ref(false)
const mine = ref<MyObservation | null>(null)
const publicDetail = ref<ObsDetail | null>(null)
const myPhotos = ref<PhotoItem[]>([])
const review = ref<ObservationReview | null>(null)
const comments = ref<CommentItem[]>([])
const commentText = ref("")
const myScore = ref(0)
const loading = ref(true)

const ready = computed(() => (isPublicView.value ? !!publicDetail.value : !!mine.value))
const isMine = computed(() => !isPublicView.value && !!mine.value)
const isApproved = computed(() => mine.value?.status === "APPROVED")
const statusText = computed(() => (isPublicView.value ? "" : STATUS_LABELS[mine.value?.status || ""] || mine.value?.status || ""))
const canSubmit = computed(() => !!mine.value && (mine.value.status === "DRAFT" || mine.value.status === "REJECTED"))
const canInteract = computed(() => (isPublicView.value ? true : isApproved.value))
const identificationPending = computed(() =>
  isPublicView.value
    ? publicDetail.value?.identificationStatus === "PENDING"
    : mine.value?.identificationStatus === "PENDING",
)
const featured = computed(() => (isPublicView.value ? !!publicDetail.value?.featured : Boolean(mine.value?.featured)))
const viewName = computed(
  () => publicDetail.value?.commonName || mine.value?.reportedCommonName || publicDetail.value?.reportedCommonName || "待鉴定植物",
)
const viewScientific = computed(() => publicDetail.value?.scientificName || mine.value?.reportedScientificName || "")
const viewPhotos = computed(() =>
  isPublicView.value
    ? (publicDetail.value?.photos || []).map((p) => ({
        key: p.photoId,
        fileUrl: p.fileUrl,
        thumbnailUrl: p.thumbnailUrl || p.fileUrl,
      }))
    : myPhotos.value.map((p) => ({ key: p.photoId || p.fileUrl, fileUrl: p.fileUrl, thumbnailUrl: p.thumbnailUrl || p.fileUrl })),
)
const viewPlace = computed(() => {
  if (isPublicView.value) {
    const d = publicDetail.value
    return [d?.provinceName, d?.cityName, d?.districtName, d?.locationText].filter(Boolean).join(" ") || "地点未填写"
  }
  return [mine.value?.provinceName, mine.value?.cityName, mine.value?.districtName, mine.value?.locationText].filter(Boolean).join(" ") || "地点未填写"
})
const observerText = computed(() => {
  if (!isPublicView.value) return ""
  const d = publicDetail.value
  return [d?.displayName || d?.submitterName, d?.className].filter(Boolean).join(" · ")
})
const viewObservedAt = computed(() => (isPublicView.value ? publicDetail.value?.observedAt : mine.value?.observedAt) || "")
const viewDescription = computed(
  () => publicDetail.value?.description || mine.value?.description || "暂无描述",
)
const viewFields = computed(() => publicDetail.value?.fieldValues || [])
const reviewText = computed(() => {
  if (isPublicView.value) {
    const text = publicDetail.value?.reviewComment
    return text ? "教师审核意见：" + text : ""
  }
  if (review.value?.comment) {
    return (review.value.action === "REJECTED" ? "驳回意见：" : "教师审核意见：") + review.value.comment
  }
  return ""
})
const reviewRejected = computed(() => review.value?.action === "REJECTED")

async function loadDetail() {
  loading.value = true
  try {
    if (isPublicView.value) {
      const [pub, list] = await Promise.all([
        fetchPublicObservation(id.value),
        fetchComments(id.value).catch(() => [] as CommentItem[]),
      ])
      publicDetail.value = pub
      comments.value = list
    } else {
      const [detail, photoList] = await Promise.all([
        myObservationDetail(id.value),
        myObservationPhotos(id.value).catch(() => [] as PhotoItem[]),
      ])
      mine.value = detail
      myPhotos.value = photoList
      myObservationReview(id.value)
        .then((r) => {
          review.value = r
        })
        .catch(() => undefined)
      if (detail.status === "APPROVED") {
        const [pub, list] = await Promise.all([
          fetchPublicObservation(id.value).catch(() => null),
          fetchComments(id.value).catch(() => [] as CommentItem[]),
        ])
        publicDetail.value = pub
        comments.value = list
      }
    }
  } catch (error) {
    console.error("加载观察详情失败", error)
    uni.showToast({ title: "加载失败，请返回重试", icon: "none" })
  } finally {
    loading.value = false
  }
}

onLoad(async (query) => {
  id.value = String(query?.id || "")
  isPublicView.value = String(query?.public || "") === "1"
  if (!id.value) {
    loading.value = false
    return
  }
  await loadDetail()
})

function goEdit() {
  uni.navigateTo({ url: "/pages/observation-edit/index?id=" + id.value })
}

async function submitNow() {
  try {
    await submitObservation(id.value)
    uni.showToast({ title: "已提交审核", icon: "success" })
    await loadDetail()
  } catch (error) {
    console.error("提交审核失败", error)
    uni.showToast({ title: "提交失败，请检查照片与观察信息后重试", icon: "none" })
  }
}

async function withdrawNow() {
  try {
    await withdrawObservation(id.value)
    uni.showToast({ title: "已撤回为草稿", icon: "success" })
    await loadDetail()
  } catch (error) {
    console.error("撤回失败", error)
    uni.showToast({ title: "撤回失败，请重试", icon: "none" })
  }
}

async function rate(score: number) {
  try {
    await postRating(id.value, score)
    myScore.value = score
    uni.showToast({ title: "评分成功", icon: "success" })
  } catch (error) {
    console.error("评分失败", error)
    uni.showToast({ title: "评分失败，请重试", icon: "none" })
  }
}

async function sendComment() {
  const content = commentText.value.trim()
  if (!content) return
  try {
    await postComment(id.value, content)
    commentText.value = ""
    comments.value = await fetchComments(id.value).catch(() => comments.value)
    uni.showToast({ title: "评论已发表", icon: "success" })
  } catch (error) {
    console.error("发表评论失败", error)
    uni.showToast({ title: "评论发送失败，请重试", icon: "none" })
  }
}
</script>

<style scoped>
.page { padding: 20rpx; }
.detail { display: flex; flex-direction: column; gap: 12rpx; }
.name { font-size: 40rpx; font-weight: 700; }
.badge { font-size: 22rpx; color: #b7791f; margin-left: 8rpx; }
.badge--star { color: #e6a23c; }
.muted { color: #888; font-size: 26rpx; }
.photos { white-space: nowrap; }
.big-photo { width: 480rpx; height: 360rpx; margin-right: 12rpx; border-radius: 14rpx; }
.info { font-size: 28rpx; }
.desc { line-height: 1.8; }
.review { background: #fff7e6; padding: 14rpx; border-radius: 10rpx; color: #8a6d1f; }
.review--reject { background: #fff0f0; color: #c0392b; }
.warn { background: #fff0f0; color: #c0392b; padding: 10rpx; border-radius: 10rpx; font-size: 24rpx; }
.actions { display: flex; gap: 16rpx; margin-top: 8rpx; }
.rate { font-size: 44rpx; }
.comments { display: flex; flex-direction: column; gap: 12rpx; margin-top: 20rpx; }
.sec { font-weight: 600; }
.ipt { border: 1rpx solid #ddd; border-radius: 10rpx; padding: 12rpx; background: #fff; }
.comment { border-bottom: 1rpx solid #eee; padding: 10rpx 0; display: flex; flex-direction: column; gap: 6rpx; }
.name { font-weight: 600; font-size: 28rpx; }
.teacher { background: #3f9b3f; color: #fff; font-size: 20rpx; border-radius: 6rpx; padding: 2rpx 10rpx; margin-left: 8rpx; }
.locked { margin-top: 20rpx; }
.empty { color: #999; text-align: center; padding: 80rpx 0; }
</style>
