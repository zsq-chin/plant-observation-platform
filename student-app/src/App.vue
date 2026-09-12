<script setup lang="ts">
import { onLaunch, onShow, onHide } from "@dcloudio/uni-app";
onLaunch(() => {
  console.log("App Launch");
});
onShow(() => {
  console.log("App Show");
});
onHide(() => {
  console.log("App Hide");
});
</script>

<style>
/* ==========================================================================
   学生端全局视觉系统（自然观察风格）
   页面只写结构与少量局部样式，配色 / 圆角 / 阴影 / 通用块统一在这里定义。
   ========================================================================== */

page {
  background: #f5f8f2;
  color: #1f2d24;
  font-size: 28rpx;
  line-height: 1.55;
  -webkit-font-smoothing: antialiased;
}

/* ---------- 布局 ---------- */
.page {
  padding: 24rpx 24rpx 40rpx;
  display: flex;
  flex-direction: column;
  gap: 20rpx;
}

/* 自定义导航页：顶部与状态栏留白 */
.page--hero {
  padding: 0 0 40rpx;
  gap: 18rpx;
}

.card {
  background: #ffffff;
  border-radius: 24rpx;
  padding: 26rpx;
  box-shadow: 0 8rpx 24rpx rgba(31, 45, 36, 0.06);
  display: flex;
  flex-direction: column;
  gap: 12rpx;
}

.card--flat {
  box-shadow: none;
  border: 1rpx solid #e8efe4;
}

.card--ink {
  background: linear-gradient(135deg, #3f9b3f 0%, #2f7a34 100%);
  color: #ffffff;
}

.row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16rpx;
}

.row--tight {
  gap: 8rpx;
}

.grow {
  flex: 1;
  min-width: 0;
}

/* ---------- 文字 ---------- */
.h1 {
  font-size: 46rpx;
  font-weight: 700;
  letter-spacing: 0.5rpx;
}

.h2 {
  font-size: 32rpx;
  font-weight: 600;
}

.h3 {
  font-size: 28rpx;
  font-weight: 600;
}

.muted {
  color: #7b8a80;
  font-size: 24rpx;
}

.muted-strong {
  color: #55645a;
  font-size: 26rpx;
}

.ellipsis {
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

/* ---------- 区块标题 ---------- */
.sec {
  display: flex;
  align-items: center;
  gap: 12rpx;
  margin: 6rpx 0 -4rpx;
}

.sec::before {
  content: "";
  width: 8rpx;
  height: 30rpx;
  border-radius: 8rpx;
  background: linear-gradient(180deg, #6cc46a, #2f7a34);
}

.sec__title {
  font-size: 32rpx;
  font-weight: 600;
  flex: 1;
}

.sec__more {
  font-size: 24rpx;
  color: #3f9b3f;
}

/* ---------- 徽标 / 标签 ---------- */
.chip {
  display: inline-flex;
  align-items: center;
  gap: 6rpx;
  padding: 4rpx 16rpx;
  border-radius: 999rpx;
  font-size: 22rpx;
  background: #eef4ea;
  color: #4d6b52;
}

.chip--brand { background: rgba(63, 155, 63, 0.12); color: #2f7a34; }
.chip--warn { background: #fdf3e3; color: #b7791f; }
.chip--info { background: #e8f3ff; color: #2a7de1; }
.chip--danger { background: #fdecec; color: #d24a4a; }
.chip--plain { background: #f2f4f1; color: #6b786e; }

/* ---------- 按钮 ---------- */
.btn-primary {
  background: linear-gradient(135deg, #4aa64a 0%, #2f7a34 100%);
  color: #ffffff;
  border-radius: 999rpx;
  font-size: 30rpx;
  font-weight: 600;
  border: none;
  box-shadow: 0 8rpx 20rpx rgba(47, 122, 52, 0.24);
}

.btn-primary[disabled] {
  background: #b9d6b9;
  color: #ffffff;
  box-shadow: none;
}

.btn-warn {
  background: linear-gradient(135deg, #f0b357 0%, #dd9430 100%);
  color: #ffffff;
  border-radius: 999rpx;
}

.btn-ghost {
  background: #ffffff;
  color: #2f7a34;
  border: 1rpx solid #cfe3cd;
  border-radius: 999rpx;
  font-size: 28rpx;
}

.btn-block {
  width: 100%;
  display: block;
}

/* ---------- 输入 ---------- */
.ipt {
  background: #ffffff;
  border: 1rpx solid #e3ebe0;
  border-radius: 18rpx;
  padding: 22rpx 24rpx;
  font-size: 28rpx;
  color: #1f2d24;
}

.ipt--search {
  border-radius: 999rpx;
  padding-left: 34rpx;
  background: #ffffff;
  box-shadow: 0 6rpx 18rpx rgba(31, 45, 36, 0.05);
}

.label {
  font-size: 26rpx;
  color: #55645a;
  font-weight: 600;
}

/* ---------- 空状态 ---------- */
.empty {
  color: #8a968c;
  text-align: center;
  padding: 70rpx 0;
  font-size: 26rpx;
}

.empty__icon {
  font-size: 64rpx;
  display: block;
  margin-bottom: 12rpx;
  opacity: 0.7;
}

/* ---------- 头像 ---------- */
.avatar {
  width: 84rpx;
  height: 84rpx;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.22);
  color: #ffffff;
  font-size: 34rpx;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.avatar--plain {
  background: #eef4ea;
  color: #2f7a34;
}

/* ---------- 统计块 ---------- */
.stats {
  display: flex;
  gap: 16rpx;
}

.stat {
  flex: 1;
  background: #ffffff;
  border-radius: 22rpx;
  padding: 22rpx 0 18rpx;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 2rpx;
  box-shadow: 0 8rpx 24rpx rgba(31, 45, 36, 0.06);
}

.stat__num {
  font-size: 42rpx;
  font-weight: 700;
  line-height: 1.1;
}

.stat__label {
  font-size: 22rpx;
  color: #7b8a80;
}

.stat--draft .stat__num { color: #8a968c; }
.stat--submitted .stat__num { color: #2a7de1; }
.stat--approved .stat__num { color: #3f9b3f; }
.stat--rejected .stat__num { color: #d24a4a; }

/* ---------- 快捷入口网格 ---------- */
.tiles {
  display: flex;
  flex-wrap: wrap;
  gap: 16rpx;
}

.tile {
  width: calc((100% - 32rpx) / 3);
  background: #ffffff;
  border-radius: 22rpx;
  padding: 24rpx 8rpx;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10rpx;
  box-shadow: 0 8rpx 24rpx rgba(31, 45, 36, 0.06);
}

.tile__badge {
  width: 78rpx;
  height: 78rpx;
  border-radius: 24rpx;
  background: linear-gradient(180deg, #f1f7ec, #e5f0de);
  display: flex;
  align-items: center;
  justify-content: center;
}

.tile__icon {
  width: 42rpx;
  height: 42rpx;
}

.tile__text {
  font-size: 24rpx;
  color: #55645a;
}

/* ---------- 列表项 ---------- */
.item {
  display: flex;
  align-items: center;
  gap: 20rpx;
  padding: 26rpx;
  background: #ffffff;
  border-radius: 22rpx;
  box-shadow: 0 8rpx 24rpx rgba(31, 45, 36, 0.06);
}

.item__icon {
  width: 68rpx;
  height: 68rpx;
  border-radius: 20rpx;
  background: linear-gradient(180deg, #f1f7ec, #e5f0de);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.item__icon-img {
  width: 38rpx;
  height: 38rpx;
}

.item--press {
  background: #f7fbf4;
}

.item__arrow {
  color: #c3ccc4;
  font-size: 30rpx;
}

/* ---------- 头图 ---------- */
.hero {
  padding: 40rpx 30rpx 74rpx;
  background: linear-gradient(135deg, #4aa64a 0%, #2f7a34 62%, #276b2e 100%);
  border-bottom-left-radius: 40rpx;
  border-bottom-right-radius: 40rpx;
  color: #ffffff;
  display: flex;
  flex-direction: column;
  gap: 10rpx;
  position: relative;
  overflow: hidden;
}

.hero::after {
  content: "";
  position: absolute;
  right: -70rpx;
  top: -80rpx;
  width: 300rpx;
  height: 300rpx;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.1);
}

.hero::before {
  content: "";
  position: absolute;
  left: -60rpx;
  bottom: -110rpx;
  width: 220rpx;
  height: 220rpx;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.07);
}

.hero__title {
  font-size: 44rpx;
  font-weight: 700;
  position: relative;
}

.hero__sub {
  font-size: 25rpx;
  color: rgba(255, 255, 255, 0.82);
  position: relative;
}

.hero__body {
  position: relative;
  margin-top: 24rpx;
  padding: 0 24rpx;
}

/* 头图下方的内容区：不留负边距，避免卡片与头图互相压住 */
.lift {
  margin-top: 0;
  padding: 20rpx 24rpx 0;
  display: flex;
  flex-direction: column;
  gap: 18rpx;
}
</style>
