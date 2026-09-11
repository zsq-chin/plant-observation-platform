/**
 * 手机端 2D 中国地图：renderjs 视图层实现（H5 与 uni-app 原生 App 通用）。
 *
 * 为什么用 renderjs：
 *  - H5：this.$el 是真实的 DOM 节点（uni-view），可安全交给 ECharts；
 *    若在逻辑层用 document.getElementById 拿 uni 组件节点再初始化，会因 Vue 重渲染
 *    出现容器 id 丢失、高度为 0 的问题。
 *  - App：逻辑层没有 DOM，renderjs 运行在 WebView 视图层，可直接加载 ECharts。
 * 点击省份：H5 用 window 自定义事件，原生 App 用 this.$ownerInstance.callMethod（双通道）。
 *
 * 真机可用性（实测教训）：
 *  - roam 必须关闭：手机上用户想滑动页面时手指按在地图上，ECharts 会把地图拖走，
 *    结果整块区域变空白，看起来"地图没了"；
 *  - 初始化前必须确认容器已有尺寸（部分 WebView 首帧高度为 0，ECharts 会画出空图）；
 *  - 失败必须上报逻辑层，由逻辑层展示省份列表兜底，绝不能让页面出现空白区域。
 */
import * as echarts from "echarts"

// 地图边界内容指纹（构建期注入）；文件名不带哈希，靠查询串破长缓存
const GEO_VERSION = typeof __GEO_VERSION__ === "string" ? __GEO_VERSION__ : "dev"
const GEO_URL = "static/geo/china-provinces.json?v=" + GEO_VERSION

export default {
  data() {
    return {
      chart: null,
      nameToCode: {},
      ready: false,
      loading: false,
      payload: null,
      retryTimer: null,
    }
  },
  mounted() {
    this.init()
  },
  beforeDestroy() {
    if (this.retryTimer) clearTimeout(this.retryTimer)
    if (this.chart) {
      this.chart.dispose()
      this.chart = null
    }
  },
  methods: {
    /** 图表容器：$el 通常是绑定 :change:prop 的节点，兼容其作为组件根的情况 */
    container() {
      const el = this.$el
      if (!el) return null
      if (el.classList && el.classList.contains("china-map__canvas")) return el
      return (el.querySelector && el.querySelector(".china-map__canvas")) || el
    },

    /** 等容器有真实尺寸再初始化；最多重试 20 次（约 4 秒） */
    waitForSize(attempt) {
      const el = this.container()
      if (!el) return
      if (el.clientWidth > 0 && el.clientHeight > 0) {
        this.init()
        return
      }
      if (attempt >= 20) {
        this.reportError("地图容器尺寸为 0")
        return
      }
      this.retryTimer = setTimeout(() => this.waitForSize(attempt + 1), 200)
    },

    async init() {
      // 并发保护：change:prop 与 mounted 可能同时触发，避免重复 init 同一个容器
      if (this.chart || this.loading) return
      const el = this.container()
      if (!el) return
      if (el.clientWidth === 0 || el.clientHeight === 0) {
        this.waitForSize(0)
        return
      }
      this.loading = true
      try {
        const response = await fetch(GEO_URL)
        if (!response.ok) throw new Error("地图数据加载失败 " + response.status)
        const geo = await response.json()
        const map = {}
        let taiwanFound = false
        for (const feature of geo.features || []) {
          const props = feature.properties || {}
          const name = String(props.name || "")
          // 编码兜底：properties.adcode 优先，其次 feature.id（不同来源 GeoJSON 字段不一致）
          const code = String(props.adcode || feature.id || "")
          if (name && code) map[name] = code
          if (code === "710000" || name === "台湾省") taiwanFound = true
        }
        this.nameToCode = map
        echarts.registerMap("china", geo)
        this.chart = echarts.init(el, null, {
          width: el.clientWidth,
          height: el.clientHeight,
          renderer: "canvas",
        })
        this.chart.on("click", (params) => {
          const name = String((params && params.name) || "")
          const code = this.nameToCode[name]
          if (!code) return
          this.emitSelect(code, name)
        })
        this.ready = true
        this.applyOption(this.payload)
        this.reportReady(Object.keys(map).length, taiwanFound)
      } catch (error) {
        this.reportError((error && error.message) || String(error))
      } finally {
        this.loading = false
      }
    },

    /** 上报逻辑层：地图已就绪 */
    reportReady(provinceCount, taiwanFound) {
      this.dispatch("china-map-ready", { provinceCount: provinceCount, hasTaiwan: !!taiwanFound })
    },
    /** 上报逻辑层：地图不可用（逻辑层据此展示省份列表兜底） */
    reportError(message) {
      console.error("[china-map] 初始化失败:", message)
      this.dispatch("china-map-error", { message: String(message || "") })
    },
    dispatch(type, detail) {
      if (this.$ownerInstance && typeof this.$ownerInstance.callMethod === "function") {
        try {
          this.$ownerInstance.callMethod("onMapEvent", { type: type, detail: detail })
        } catch (error) {
          /* H5 下 callMethod 不可用，走 window 事件 */
        }
      }
      if (typeof window !== "undefined" && typeof window.dispatchEvent === "function") {
        window.dispatchEvent(new CustomEvent(type, { detail: detail }))
      }
    },

    /**
     * 回传逻辑层（双通道）：
     *  - 原生 App：视图层与逻辑层分离，用 $ownerInstance.callMethod；
     *  - H5：renderjs 与逻辑层同处一个 window，但 uni 对象在 renderjs 中不可见，
     *    改用 window 自定义事件通知逻辑层（逻辑层会做去重，避免双通道重复触发）。
     */
    emitSelect(code, name) {
      const payload = { code: code, name: name }
      if (this.$ownerInstance && typeof this.$ownerInstance.callMethod === "function") {
        try {
          this.$ownerInstance.callMethod("onProvinceSelect", payload)
        } catch (error) {
          /* 走 window 事件 */
        }
      }
      if (typeof window !== "undefined" && typeof window.dispatchEvent === "function") {
        window.dispatchEvent(new CustomEvent("china-map-select", { detail: payload }))
      }
    },

    applyOption(payload) {
      if (!this.chart) return
      const stats = (payload && payload.stats) || []
      const data = stats.map((item) => ({ name: item.provinceName, value: Number(item.observationCount || 0) }))
      // 台湾面积小：数据项标签上移，避免文字压住岛屿本体（与 PC 端一致）
      data.push({ name: "台湾省", value: 0, label: { show: true, position: "top" } })
      const maxValue = Math.max(1, ...data.map((d) => d.value))
      this.chart.setOption({
        tooltip: { trigger: "item", confine: true },
        visualMap: {
          min: 0,
          max: maxValue,
          left: 8,
          bottom: 8,
          itemWidth: 10,
          itemHeight: 60,
          text: ["多", "少"],
          textStyle: { fontSize: 10, color: "#5b6b50" },
          inRange: { color: ["#bfd9a4", "#9cc97c", "#78b055", "#548f3c", "#2f6b31"] },
        },
        series: [
          {
            type: "map",
            map: "china",
            // 关闭拖拽缩放：手机上手指滑动会落到地图上，roam 会把地图拖出可视区，
            // 用户看到的就是一片空白（"地图不见了"）。全国图固定展示更稳妥。
            roam: false,
            zoom: 1.16,
            label: { show: true, fontSize: 9, color: "#33512a" },
            itemStyle: { borderColor: "#4f7a3c", borderWidth: 1.1, areaColor: "#bfd9a4" },
            emphasis: { label: { show: true, fontWeight: "bold" }, itemStyle: { areaColor: "#ffd166" } },
            data,
          },
        ],
      })
      this.chart.resize()
    },

    render(newValue) {
      this.payload = newValue
      if (this.ready) this.applyOption(newValue)
      else this.init()
    },
  },
}
