// eslint-disable
<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from "vue"

export interface ProvinceStat {
  provinceCode: string
  provinceName?: string | null
  observationCount: number | string
}

const props = withDefaults(defineProps<{ stats: ProvinceStat[]; height?: number }>(), {
  height: 320,
})

const emit = defineEmits<{
  (event: "select", provinceCode: string, provinceName: string): void
  (event: "ready"): void
  (event: "failed", message: string): void
}>()

/** 地图是否已就绪 / 是否失败（逻辑层据此决定是否展示省份列表兜底） */
const ready = ref(false)
const failed = ref("")

/** 传给 renderjs 的数据（需可序列化） */
const renderPayload = computed(() => ({
  stats: props.stats.map((item) => ({
    provinceCode: String(item.provinceCode || ""),
    provinceName: String(item.provinceName || ""),
    observationCount: Number(item.observationCount || 0),
  })),
  height: props.height,
}))

/** 去重：callMethod 与 DOM 事件可能同时到达，600ms 内相同省份只处理一次 */
let lastSelect = { code: "", at: 0 }

/** 点击省份（renderjs 通过 callMethod 或 window 事件调用本方法） */
function onProvinceSelect(payload: { code?: string; name?: string }) {
  const code = String(payload?.code || "")
  if (!code) return
  const now = Date.now()
  if (code === lastSelect.code && now - lastSelect.at < 600) return
  lastSelect = { code, at: now }
  emit("select", code, String(payload?.name || code))
}

/** 地图就绪 / 失败事件（原生 App 经 callMethod 进入） */
function onMapEvent(payload: { type?: string; detail?: { message?: string } }) {
  if (payload?.type === "china-map-ready") {
    ready.value = true
    failed.value = ""
    emit("ready")
  } else if (payload?.type === "china-map-error") {
    failed.value = String(payload.detail?.message || "地图加载失败")
    emit("failed", failed.value)
  }
}

defineExpose({ onProvinceSelect, onMapEvent, ready, failed })

function onWindowEvent(event: Event) {
  const type = (event as CustomEvent).type
  const detail = (((event as CustomEvent).detail || {}) as { code?: string; name?: string; message?: string })
  if (type === "china-map-select") onProvinceSelect(detail)
  else if (type === "china-map-ready") onMapEvent({ type: "china-map-ready", detail })
  else if (type === "china-map-error") onMapEvent({ type: "china-map-error", detail })
}

onMounted(() => {
  if (typeof window !== "undefined" && typeof window.addEventListener === "function") {
    window.addEventListener("china-map-select", onWindowEvent)
    window.addEventListener("china-map-ready", onWindowEvent)
    window.addEventListener("china-map-error", onWindowEvent)
  }
})

onUnmounted(() => {
  if (typeof window !== "undefined" && typeof window.removeEventListener === "function") {
    window.removeEventListener("china-map-select", onWindowEvent)
    window.removeEventListener("china-map-ready", onWindowEvent)
    window.removeEventListener("china-map-error", onWindowEvent)
  }
})
</script>

<template>
  <!--
    重要（实测结论，三轮踩坑记录）：
    1) uni-app H5 的 <view> 不会把 id 透传到 DOM，不能在逻辑层用 document.getElementById 取容器；
    2) renderjs 的 this.$el 指向「组件根元素」，承载地图的根节点必须自带高度，
       否则 ECharts 拿到 0 高度容器（canvas 高 0，看起来"地图没出来"）；
    3) **ECharts 会把图表 DOM 插进容器内部**，因此容器内不能再放由 Vue 管理的子节点：
       否则 Vue 打补丁时与 ECharts 插入的节点错位，抛
       "Cannot read properties of null (reading 'insertBefore')"，
       页面切换瞬间可能留下半截图表（真机表现为"地图不见了"）。
       所以把图表容器单独设为 .china-map__canvas（Vue 不在其中渲染任何内容），
       加载遮罩与提示文字都放在它的**兄弟节点**上。
  -->
  <view class="china-map" :style="{ height: height + 'px' }">
    <!-- @vue-ignore renderjs 模块由视图层执行，逻辑层类型系统无法推断 -->
    <view
      class="china-map__canvas"
      :prop="renderPayload"
      :change:prop="mapRender.render"
    ></view>
    <view v-if="!ready" class="china-map__mask">
      <image class="china-map__spin" src="/static/icons/map.svg" mode="aspectFit" />
      <text class="china-map__tip">{{ failed ? '地图加载失败' : '地图加载中…' }}</text>
    </view>
    <view v-else class="china-map__note">点击省份查看该省的植物观察</view>
  </view>
</template>

<script module="mapRender" lang="renderjs" src="./china-map.renderjs.js"></script>

<style scoped>
.china-map {
  position: relative;
  width: 100%;
  background: linear-gradient(180deg, #f4f9ef 0%, #eaf3e3 100%);
  border-radius: 18rpx;
  overflow: hidden;
}
.china-map__canvas { width: 100%; height: 100%; }
.china-map__mask {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 10rpx;
  pointer-events: none;
}
.china-map__spin { width: 56rpx; height: 56rpx; opacity: 0.45; }
.china-map__tip { font-size: 24rpx; color: #8a968c; }
.china-map__note {
  position: absolute;
  left: 0;
  right: 0;
  bottom: 8rpx;
  text-align: center;
  color: #9aa79a;
  font-size: 20rpx;
  pointer-events: none;
}
</style>
