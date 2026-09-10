<template>
  <view class="page">
    <template v-if="ready">
      <scroll-view v-if="viewPhotos.length" scroll-x class="photos">
        <view class="photos__inner">
          <image
            v-for="p in viewPhotos"
            :key="String(p.key)"
            :src="resolveMediaUrl(p.thumbnailUrl || p.fileUrl)"
            mode="aspectFill"
            class="big-photo"
          />
        </view>
      </scroll-view>
      <view v-else class="card photo-empty"><text class="muted">暂无照片</text></view>

      <view class="card">
        <view class="row">
          <text class="h1 grow">{{ viewName }}</text>
          <text v-if="statusText" class="chip" :class="statusChipClass">{{ statusText }}</text>
        </view>
        <view class="row row--tight">
          <text v-if="viewScientific" class="sci">{{ viewScientific }}</text>
          <text v-if="featured" class="chip chip--warn">★ 优秀</text>
        </view>
        <view class="meta">
          <text class="meta__row">📍 {{ viewPlace }}</text>
          <text v-if="observerText" class="meta__row">👤 {{ observerText }}</text>
          <text v-if="viewObservedAt" class="meta__row">🗓 {{ fmtDate(viewObservedAt) }}</text>
        </view>
        <view v-if="identificationPending" class="alert alert--warn">🔍 未知植物 · 待教师鉴定</view>
      </view>

      <view v-if="reviewText" class="card" :class="{ 'alert-card--reject': reviewRejected }">
        <text class="h3">{{ reviewRejected ? '❌ 驳回意见' : '📝 教师审核意见' }}</text>
        <text class="muted-strong">{{ reviewText }}</text>
      </view>
      <view v-else-if="statusText === '待审核'" class="card">
        <text class="muted">⏳ 已提交，等待教师审核。</text>
      </view>
      <view v-else-if="statusText === '被驳回'" class="card">
        <text class="muted">该观察已被驳回：修改后可在「我的植物」重新提交。</text>
      </view>

      <view class="card">
        <text class="h3">观察描述</text>
        <text class="muted-strong">{{ viewDescription }}</text>
      </view>

      <view v-if="viewFields.length" class="card">
        <text class="h3">观察记录</text>
        <view v-for="f in viewFields" :key="f.fieldCode" class="row field-row">
          <text class="muted">{{ f.fieldLabel }}</text>
          <text class="field-row__value">{{ f.valueText || '-' }}</text>
        </view>
      </view>

      <view v-if="isMine" class="actions">
        <button size="mini" class="btn-ghost" @tap="goEdit">继续编辑</button>
        <button v-if="canSubmit" size="mini" class="btn-primary" @tap="submitNow">提交审核</button>
        <button v-if="statusText === '待审核'" size="mini" class="btn-warn" @tap="withdrawNow">撤回</button>
      </view>

      <template v-if="canInteract">
        <view class="card">
          <text class="h3">我的评分</text>
          <view class="rate">
            <text v-for="i in 5" :key="i" class="rate__star" :class="{ 'rate__star--on': myScore >= i }" @tap="rate(i)">★</text>
            <text class="muted rate__text">{{ myScore ? myScore + ' 星' : '点击星星评分' }}</text>
          </view>
        </view>

        <view class="card">
          <text class="h3">评论（{{ comments.length }}）</text>
          <textarea class="ipt area" v-model="commentText" placeholder="友善评论，交流观察心得…" />
          <button size="mini" class="btn-primary" @tap="sendComment">发表评论</button>
          <view v-for="c in comments" :key="String(c.commentId)" class="comment">
            <view class="row row--tight">
              <text class="comment__name">{{ c.userName || '匿名' }}</text>
              <text v-if="c.isTeacherComment" class="chip chip--brand">教师</text>
              <text v-if="c.isPinned" class="chip chip--warn">置顶</text>
            </view>
            <text class="muted-strong">{{ c.content }}</text>
          </view>
        </view>
      </template>
      <view v-else-if="isMine" class="card">
        <text class="muted">🔒 审核通过后，其他同学即可在展廊看到这条观察并留言评分。</text>
      </view>
    </template>

    <EmptyState v-else-if="loading" icon="⏳" title="加载中…" />
    <EmptyState v-else icon="🍂" title="记录不存在或未公开" hint="它可能已被删除或下线" />
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
import EmptyState from "@/components/EmptyState.vue"
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
const statusChipClass = computed(() => {
  const status = mine.value?.status || ""
  if (status === "APPROVED") return "chip--brand"
  if (status === "REJECTED") return "chip--danger"
  if (status === "SUBMITTED") return "chip--info"
  return "chip--plain"
})
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
.photos { width: 100%; white-space: nowrap; }
.photos__inner { display: inline-flex; gap: 16rpx; padding: 4rpx 24rpx; }
.big-photo {
  width: 460rpx;
  height: 340rpx;
  border-radius: 24rpx;
  background: #eef4ea;
  box-shadow: 0 8rpx 24rpx rgba(31, 45, 36, 0.08);
}
.photo-empty { align-items: center; }
.sci { font-size: 26rpx; color: #7b8a80; font-style: italic; }
.meta { display: flex; flex-direction: column; gap: 4rpx; margin-top: 6rpx; }
.meta__row { font-size: 25rpx; color: #55645a; }
.alert { padding: 14rpx 18rpx; border-radius: 16rpx; font-size: 24rpx; }
.alert--warn { background: #fdf3e3; color: #b7791f; }
.alert-card--reject { border-left: 8rpx solid #d24a4a; }
.actions { display: flex; gap: 16rpx; }
.rate { display: flex; align-items: center; gap: 10rpx; margin-top: 6rpx; }
.rate__star { font-size: 52rpx; color: #d9e0d8; line-height: 1; }
.rate__star--on { color: #f5a623; }
.rate__text { margin-left: 12rpx; }
.area { height: 160rpx; width: 100%; box-sizing: border-box; margin-top: 8rpx; }
.comment { padding-top: 18rpx; margin-top: 8rpx; border-top: 1rpx solid #f1f5ef; display: flex; flex-direction: column; gap: 6rpx; }
.comment__name { font-size: 26rpx; font-weight: 600; }
.field-row { padding: 10rpx 0; }
.field-row__value { font-size: 26rpx; color: #1f2d24; }
</style>
