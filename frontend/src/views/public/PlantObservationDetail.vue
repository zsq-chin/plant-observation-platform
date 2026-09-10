<template>
  <div class="obs-detail">
    <div class="obs-detail__back">
      <router-link to="/plant/gallery">← 返回展廊</router-link>
    </div>

    <el-skeleton v-if="!detail" :rows="10" animated />
    <template v-else>
      <div class="obs-detail__hero">
        <h1 class="obs-detail__title">
          {{ detail.commonName || detail.reportedCommonName || "未命名植物" }}
          <span v-if="detail.featured" class="obs-detail__badge">★ 优秀观察</span>
        </h1>
        <p v-if="detail.scientificName" class="obs-detail__sci">{{ detail.scientificName }}</p>
        <p class="obs-detail__location">
          📍 {{ detail.provinceName || "" }} {{ detail.cityName || "" }} {{ detail.districtName || "" }}
          <template v-if="detail.locationText"> · {{ detail.locationText }}</template>
        </p>
      </div>

      <div class="obs-detail__layout">
        <div class="obs-detail__main">
          <!-- 多图 -->
          <div v-if="detail.photos && detail.photos.length" class="obs-detail__photos">
            <el-image v-for="(p, idx) in detail.photos" :key="String(p.photoId)" :src="resolveMediaUrl(p.fileUrl)"
              :preview-src-list="(detail.photos || []).map((x) => x.fileUrl)" fit="cover"
              :alt="(detail.commonName || '植物') + '照片' + (detail.photos.length > 1 ? '（第' + (idx + 1) + '张）' : '')"
              @error="onMediaError"
              class="obs-detail__photo" />
          </div>
          <el-empty v-else description="该记录暂无照片" />

          <section class="obs-detail__card">
            <h2>观察描述</h2>
            <p v-if="detail.description" class="obs-detail__desc">{{ detail.description }}</p>
            <p v-else class="obs-detail__muted">暂无文字描述</p>
            <div v-if="detail.fieldValues && detail.fieldValues.length" class="obs-detail__fields">
              <div v-for="f in detail.fieldValues" :key="f.fieldCode" class="obs-detail__field">
                <span class="obs-detail__field-label">{{ f.fieldLabel }}</span>
                <span>{{ f.valueText || "-" }}</span>
              </div>
            </div>
            <p v-if="detail.reviewComment" class="obs-detail__review">
              💬 教师审核意见：{{ detail.reviewComment }}
            </p>
          </section>

          <!-- 评论 -->
          <section class="obs-detail__card">
            <h2>评论与点评（{{ numberValue(detail.commentCount) }}）</h2>
            <div v-if="isLoggedIn()" class="obs-detail__compose">
              <el-input v-model="commentDraft" type="textarea" :rows="3" maxlength="1000" show-word-limit
                placeholder="友善交流，记录植物观察心得……" />
              <div class="obs-detail__compose-row">
                <el-rate v-model="myScore" :max="5" allow-half disabled />
                <el-button type="primary" @click="submitComment()">发表评论</el-button>
              </div>
            </div>
            <p v-else class="obs-detail__muted"><router-link to="/login">登录</router-link> 后可评论与评分</p>
            <div class="obs-detail__comments">
              <article v-for="c in comments" :key="String(c.commentId)" class="comment-item"
                :class="{ 'comment-item--pinned': c.isPinned }">
                <header class="comment-item__head">
                  <b>{{ c.userName || "匿名" }}</b>
                  <span v-if="c.isTeacherComment" class="comment-item__tag">教师</span>
                  <span v-if="c.isPinned" class="comment-item__tag comment-item__tag--pin">置顶点评</span>
                  <span class="comment-item__time">{{ fmtDate(c.createTime) }}</span>
                </header>
                <p class="comment-item__content">{{ c.content }}</p>
                <div v-if="isLoggedIn() && !c.parentId" class="comment-item__actions">
                  <el-button link type="primary" size="small" @click="startReply(c)">回复</el-button>
                </div>
                <div v-if="replyTarget && replyTarget.commentId === c.commentId" class="comment-item__reply-box">
                  <el-input v-model="replyDraft" size="small" placeholder="写下回复…" maxlength="1000" />
                  <el-button size="small" type="primary" @click="submitComment(c)">发送回复</el-button>
                  <el-button size="small" @click="cancelReply">取消</el-button>
                </div>
              </article>
              <el-empty v-if="!comments.length" description="还没有评论，来抢沙发" :image-size="60" />
            </div>
          </section>
        </div>

        <aside class="obs-detail__side">
          <section class="obs-detail__card">
            <h2>植物信息</h2>
            <dl class="obs-detail__dl">
              <div><dt>中文名</dt><dd>{{ detail.commonName || detail.reportedCommonName || "-" }}</dd></div>
              <div v-if="detail.scientificName"><dt>学名</dt><dd>{{ detail.scientificName }}</dd></div>
              <div v-if="detail.familyName"><dt>科</dt><dd>{{ detail.familyName }}</dd></div>
              <div v-if="detail.genusName"><dt>属</dt><dd>{{ detail.genusName }}</dd></div>
              <div v-if="detail.categoryName"><dt>类别</dt><dd>{{ detail.categoryName }}</dd></div>
              <div><dt>学生上报名</dt><dd>{{ detail.reportedCommonName || "-" }}</dd></div>
            </dl>
          </section>
          <section class="obs-detail__card">
            <h2>观察信息</h2>
            <dl class="obs-detail__dl">
              <div><dt>观察者</dt><dd>{{ detail.submitterName || "-" }}</dd></div>
              <div v-if="detail.className"><dt>班级</dt><dd>{{ detail.className }}</dd></div>
              <div><dt>观察时间</dt><dd>{{ fmtDate(detail.observedAt) || "-" }}</dd></div>
              <div><dt>发布时间</dt><dd>{{ fmtDate(detail.publishedAt) || "-" }}</dd></div>
            </dl>
          </section>
          <section class="obs-detail__card obs-detail__rating">
            <h2>综合参考价值</h2>
            <div class="obs-detail__rating-score">
              <b>{{ detail.averageRating === null || detail.averageRating === undefined ? "-" : Number(detail.averageRating).toFixed(1) }}</b>
              <el-rate :model-value="Number(detail.averageRating || 0)" disabled />
            </div>
            <p class="obs-detail__muted">{{ numberValue(detail.ratingCount) }} 人评价 · {{ numberValue(detail.commentCount) }} 条评论 · {{ numberValue(detail.viewCount) }} 次浏览</p>
            <el-button v-if="isLoggedIn()" type="warning" plain @click="submitRating">我要评分</el-button>
          </section>
        </aside>
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from "vue"
import { useRoute } from "vue-router"
import { ElMessage } from "element-plus"
import { onMediaError, resolveMediaUrl } from "@/utils/media"
import {
  fetchComments,
  fetchObsDetail,
  fmtDate,
  isLoggedIn,
  numberValue,
  postComment,
  postRating,
  type CommentItem,
  type ObsDetail,
} from "@/api/plant"

const route = useRoute()
const obsId = String(route.params.id)
const detail = ref<ObsDetail | null>(null)
const comments = ref<CommentItem[]>([])
const commentDraft = ref("")
const replyDraft = ref("")
const replyTarget = ref<CommentItem | null>(null)
const myScore = ref(0)

const load = async () => {
  const [d, c] = await Promise.all([fetchObsDetail(obsId), fetchComments(obsId)])
  detail.value = d
  comments.value = c
}

const startReply = (c: CommentItem) => {
  replyTarget.value = c
  replyDraft.value = ""
}
const cancelReply = () => {
  replyTarget.value = null
  replyDraft.value = ""
}

const submitComment = async (target?: CommentItem) => {
  const text = (target ? replyDraft.value : commentDraft.value).trim()
  if (!text) {
    ElMessage.warning("请输入评论内容")
    return
  }
  await postComment(obsId, text, target ? target.commentId : null)
  if (target) {
    cancelReply()
  } else {
    commentDraft.value = ""
  }
  comments.value = await fetchComments(obsId)
  detail.value = await fetchObsDetail(obsId)
}

const submitRating = async () => {
  const score = myScore.value
  if (score < 1) {
    ElMessage.warning("请先选择星级")
    return
  }
  await postRating(obsId, score)
  detail.value = await fetchObsDetail(obsId)
  ElMessage.success("评分成功")
}

onMounted(() => {
  load().catch(() => undefined)
})
</script>

<style scoped>
.obs-detail {
  max-width: 1160px;
  margin: 0 auto;
  padding: 28px 24px 60px;
  display: flex;
  flex-direction: column;
  gap: 20px;
}
.obs-detail__back a { color: var(--el-color-primary); text-decoration: none; }
.obs-detail__title { margin: 0; font-size: 30px; font-family: var(--font-display); }
.obs-detail__badge { display: inline-block; margin-left: 10px; font-size: 12px; padding: 3px 10px; border-radius: 999px; background: #faad14; color: #fff; vertical-align: middle; }
.obs-detail__sci { color: var(--text-muted); font-style: italic; margin: 6px 0; }
.obs-detail__location { color: var(--text-secondary); margin: 0 0 4px; }
.obs-detail__layout { display: grid; grid-template-columns: minmax(0, 1fr) 300px; gap: 22px; align-items: start; }
.obs-detail__main, .obs-detail__side { display: flex; flex-direction: column; gap: 18px; }
.obs-detail__photos { display: grid; grid-template-columns: repeat(auto-fill, minmax(160px, 1fr)); gap: 12px; }
.obs-detail__photo { width: 100%; aspect-ratio: 1; border-radius: 14px; }
.obs-detail__card { border: 1px solid var(--border-subtle); background: var(--surface-elevated); border-radius: 18px; padding: 18px 20px; }
.obs-detail__card h2 { margin: 0 0 12px; font-size: 17px; }
.obs-detail__desc { line-height: 1.8; white-space: pre-wrap; }
.obs-detail__fields { display: flex; flex-direction: column; gap: 8px; margin-top: 14px; }
.obs-detail__field { display: flex; gap: 12px; font-size: 14px; }
.obs-detail__field-label { color: var(--text-muted); min-width: 90px; }
.obs-detail__review { margin-top: 14px; padding: 10px 12px; border-radius: 10px; background: color-mix(in srgb, var(--brand-soft) 55%, transparent); }
.obs-detail__dl { margin: 0; display: flex; flex-direction: column; gap: 8px; }
.obs-detail__dl div { display: flex; gap: 10px; }
.obs-detail__dl dt { color: var(--text-muted); min-width: 64px; }
.obs-detail__dl dd { margin: 0; }
.obs-detail__compose { display: flex; flex-direction: column; gap: 10px; margin-bottom: 16px; }
.obs-detail__compose-row { display: flex; justify-content: space-between; align-items: center; }
.obs-detail__comments { display: flex; flex-direction: column; gap: 10px; }
.comment-item { padding: 12px 14px; border-radius: 12px; background: color-mix(in srgb, var(--card-bg) 70%, transparent); }
.comment-item--pinned { outline: 1px solid #faad14; }
.comment-item__head { display: flex; align-items: center; gap: 8px; margin-bottom: 6px; }
.comment-item__tag { font-size: 11px; color: var(--el-color-primary); border: 1px solid currentColor; border-radius: 999px; padding: 0 8px; }
.comment-item__tag--pin { color: #faad14; }
.comment-item__time { margin-left: auto; color: var(--text-muted); font-size: 12px; }
.comment-item__content { margin: 0; line-height: 1.7; white-space: pre-wrap; }
.comment-item__actions { margin-top: 6px; }
.comment-item__reply-box { display: flex; gap: 8px; margin-top: 10px; }
.obs-detail__muted { color: var(--text-muted); font-size: 13px; }
.obs-detail__rating-score { display: flex; align-items: center; gap: 12px; }
.obs-detail__rating-score b { font-size: 34px; color: #faad14; }
@media (max-width: 900px) { .obs-detail__layout { grid-template-columns: 1fr; } }
</style>
