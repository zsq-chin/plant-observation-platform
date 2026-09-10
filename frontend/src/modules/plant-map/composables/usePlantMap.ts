import { Map as MlMap, Marker as MlMarker, type MapMouseEvent, type SourceSpecification } from "maplibre-gl"
import type { ChinaStatRow, RegionNode } from "../types"
import { bboxOfFeature, featureCode, featureWithStats, type GeoFeature } from "../utils/geo"

export interface ChinaGeoJson {
  type: "FeatureCollection"
  features: GeoFeature[]
}

export interface PlantMapCallbacks {
  onProvinceClick?: (code: string, name: string, feature: GeoFeature) => void
  onHover?: (code: string | null, name?: string) => void
}

export interface CameraTarget {
  west: number
  south: number
  east: number
  north: number
}

const APP_BASE = (import.meta.env.BASE_URL || "/").endsWith("/") ? import.meta.env.BASE_URL || "/" : (import.meta.env.BASE_URL || "/") + "/"
const GEO_URL = APP_BASE + "geo/china-provinces.json"

export function isWebGlSupported(): boolean {
  try {
    const canvas = document.createElement("canvas")
    return Boolean(window.WebGLRenderingContext && (canvas.getContext("webgl2") || canvas.getContext("webgl")))
  } catch {
    return false
  }
}

export function prefersReducedMotion(): boolean {
  return typeof window.matchMedia === "function" && window.matchMedia("(prefers-reduced-motion: reduce)").matches
}

function chinaBounds(): CameraTarget {
  return { west: 73, south: 17, east: 135.5, north: 54.5 }
}

/** 面积较小、低缩放级别下需要隐藏标签的省级行政区（下一步开发计划 §3.1） */
const SMALL_PROVINCE_CODES = new Set(["110000", "120000", "310000", "810000", "820000"])
/** 低于该缩放级别时隐藏小面积省份标签，避免文字互相遮挡 */
const LABEL_HIDE_ZOOM = 3.4

/**
 * 标签偏移（像素）：面积很小的省区（台湾、香港、澳门）如果标签居中，
 * 会正好压住只有十余像素的本体，导致"看不到岛屿"（实测台湾岛被文字完全遮住）。
 */
const LABEL_OFFSETS: Record<string, [number, number]> = {
  "710000": [0, -22],
  "810000": [-16, -10],
  "820000": [-16, 10],
}

export function usePlantMap() {
  let map: MlMap | null = null
  let geojson: ChinaGeoJson | null = null
  let callbacks: PlantMapCallbacks = {}
  const regionMarkers: MlMarker[] = []
  const provinceLabels: MlMarker[] = []
  let markerClick: ((payload: { region: RegionNode; index: number }) => void) | null = null
  let previousHoverCode: string | null = null

  async function fetchGeo(): Promise<ChinaGeoJson> {
    const response = await fetch(GEO_URL)
    if (!response.ok) throw new Error("中国地图边界数据加载失败")
    return (await response.json()) as ChinaGeoJson
  }

  function featuresMap(): Map<string, GeoFeature> {
    const result = new Map<string, GeoFeature>()
    if (!geojson) return result
    for (const feature of geojson.features) {
      const code = featureCode(feature)
      if (!code) continue
      result.set(code, feature)
    }
    return result
  }

  function addProvinceLayers() {
    if (!map) return
    map.addSource("provinces", { type: "geojson", data: geojson } as unknown as SourceSpecification)
    map.addLayer({
      id: "province-fill",
      type: "fill",
      source: "provinces",
      paint: {
        // 注意：分级着色必须用 step/match；此前误把多标签写在 case 中，
        // 会导致整层样式校验失败被 MapLibre 丢弃（省份填充与台湾岛都不可见）。
        "fill-color": [
          "case",
          ["boolean", ["feature-state", "featured"], false],
          "#ffd166",
          ["boolean", ["feature-state", "hovered"], false],
          "#f5c542",
          ["boolean", ["feature-state", "selected"], false],
          "#e8a33d",
          [
            "step",
            ["number", ["get", "observationCount"], 0],
            // 无数据省份也要与海面背景形成明显对比（此前 #d8e7cc 与背景色差仅 13，台湾岛肉眼难辨）
            "#c6dfa8",
            3, "#a9cf88",
            10, "#86bb63",
            30, "#639a4a",
            100, "#3f7a36",
          ],
        ],
        "fill-opacity": 0.9,
      },
    })
    map.addLayer({
      id: "province-extrusion",
      type: "fill-extrusion",
      source: "provinces",
      paint: {
        "fill-extrusion-color": [
          "case",
          ["boolean", ["feature-state", "featured"], false],
          "#ffcf5c",
          ["boolean", ["feature-state", "hovered"], false],
          "#f0b429",
          ["boolean", ["feature-state", "selected"], false],
          "#e07b39",
          "#7fb069",
        ],
        // 无公开观察的省份不生成柱体：否则被光照照亮的柱顶会盖住填充面，
        // 使台湾等无数据省份在浅色背景上几乎不可见（实测色差仅 13）。
        "fill-extrusion-height": [
          "case",
          ["==", ["number", ["get", "observationCount"], 0], 0],
          0,
          ["number", ["get", "visualHeight"], 2000],
        ],
        "fill-extrusion-opacity": 0.9,
      },
    })
    // 深色外描边 + 白色内描边：保证浅色填充与深色填充下省界都清晰（台湾/港澳等小区域尤其重要）
    map.addLayer({
      id: "province-outline",
      type: "line",
      source: "provinces",
      paint: {
        "line-color": "#3d5c2e",
        "line-width": 2.2,
        "line-opacity": 0.45,
      },
    })
    map.addLayer({
      id: "province-border",
      type: "line",
      source: "provinces",
      paint: {
        "line-color": "#ffffff",
        "line-width": 1.4,
      },
    })
  }

  function layerIds(): string[] {
    return ["province-fill", "province-extrusion", "province-outline", "province-border"]
  }

  function featureAt(event: MapMouseEvent): { code: string; name: string } | null {
    if (!map) return null
    const features = map.queryRenderedFeatures(event.point, { layers: layerIds() })
    if (!features.length) return null
    const hit = features[0]
    const props = (hit.properties ?? {}) as Record<string, unknown>
    const code = featureCode({ id: hit.id as string | number | undefined, properties: { adcode: props.adcode as string | number | undefined } })
    return { code, name: String(props.name ?? "") }
  }

  function hoverFeature(event: MapMouseEvent) {
    const hit = featureAt(event)
    const code = hit ? hit.code : null
    const previous = previousHoverCode
    if (previous !== code) {
      if (previous) setHoverState(previous, false)
      if (code) setHoverState(code, true)
      previousHoverCode = code
      callbacks.onHover?.(code, hit?.name)
    }
  }

  function bindEvents() {
    if (!map) return
    map.on("mousemove", hoverFeature)
    map.on("mouseleave", () => {
      if (previousHoverCode) setHoverState(previousHoverCode, false)
      previousHoverCode = null
      callbacks.onHover?.(null)
    })
    map.on("click", (event: MapMouseEvent) => {
      const hit = featureAt(event)
      if (!hit || !hit.code) return
      const stored = featuresMap().get(hit.code)
      callbacks.onProvinceClick?.(hit.code, hit.name, stored as GeoFeature)
    })
  }

  async function mount(container: HTMLElement, nextCallbacks: PlantMapCallbacks): Promise<void> {
    callbacks = nextCallbacks
    const data = await fetchGeo()
    geojson = data
    const instance = new MlMap({
      container,
      style: { version: 8, sources: {}, layers: [{ id: "background", type: "background", paint: { "background-color": "#eaf2e2" } }] },
      center: [104.5, 35.2],
      zoom: 2.4,
      pitch: 42,
      bearing: -2,
      attributionControl: false,
      maxPitch: 60,
      minZoom: 2,
      maxZoom: 9,
    })
    map = instance
    // 暴露实例供自动化验证与线上排查（只读使用，勿在业务代码依赖）
    ;(window as unknown as Record<string, unknown>).__plantMap = instance
    await new Promise<void>((resolve, reject) => {
      instance.once("load", () => resolve())
      instance.once("error", () => reject(new Error("地图初始化失败")))
    })
    addProvinceLabelsDebug()
    addProvinceLayers()
    bindEvents()
    renderProvinceLabels()
    instance.on("zoom", updateLabelVisibility)
  }

  function setStats(rows: ChinaStatRow[]) {
    if (!map || !geojson) return
    const byCode = new Map(rows.map((row) => [row.provinceCode, row]))
    geojson = {
      ...geojson,
      features: geojson.features.map((feature) => {
        const stat = byCode.get(featureCode(feature))
        if (!stat) return featureWithStats(feature, 0, 0, 0)
        return featureWithStats(feature, Number(stat.observationCount), Number(stat.speciesCount), Number(stat.studentCount))
      }),
    }
    const source = map.getSource("provinces") as { setData: (data: unknown) => void } | undefined
    source?.setData(geojson)
  }

  /** 省份中心点（取边界 bbox 中心，用于标签与引导线锚点）。 */
  function provinceCenter(code: string): { lng: number; lat: number } | null {
    const feature = featuresMap().get(code)
    if (!feature) return null
    const bounds = bboxOfFeature(feature)
    if (!bounds) return null
    return { lng: (bounds.west + bounds.east) / 2, lat: (bounds.south + bounds.north) / 2 }
  }

  /** 经纬度 → 屏幕坐标（精选作品引导线用）。 */
  function project(lngLat: [number, number]): { x: number; y: number } | null {
    if (!map) return null
    const point = map.project(lngLat)
    return { x: point.x, y: point.y }
  }

  /** 相机变化回调（移动/缩放后重算引导线），返回取消订阅函数。 */
  function onCameraChange(handler: () => void): () => void {
    if (!map) return () => undefined
    map.on("move", handler)
    map.on("zoom", handler)
    return () => {
      map?.off("move", handler)
      map?.off("zoom", handler)
    }
  }

  /** 精选作品所属省份高亮（卡片与地图联动）。 */
  function setFeatured(codes: string[]) {
    if (!map) return
    const active = new Set(codes)
    for (const key of featuresMap().keys()) {
      map.setFeatureState({ source: "provinces", id: featureStateId(key) }, { featured: active.has(key) })
    }
  }

  /** 调试：输出省份编码清单，便于确认台湾省 710000 已进入地图数据（详细修复方案 §24.4） */
  function addProvinceLabelsDebug() {
    if (!geojson) return
    const codes = geojson.features.map((feature) => featureCode(feature)).filter(Boolean)
    if (!codes.includes("710000")) {
      console.warn("地图数据缺少台湾省(710000)几何，请检查 frontend/public/geo/china-provinces.json")
    }
  }

  /** 省份名称常驻标签（DOM 文本，随缩放自动显隐，低缩放隐藏小面积省份）。 */
  function renderProvinceLabels() {
    clearProvinceLabels()
    if (!map || !geojson) return
    for (const feature of geojson.features) {
      const code = featureCode(feature)
      if (!code) continue
      const center = provinceCenter(code)
      if (!center) continue
      const name = String(feature.properties?.name ?? "")
      const element = document.createElement("button")
      element.type = "button"
      element.className = "plant-province-label"
      element.dataset.code = code
      element.textContent = name
      element.title = name
      element.addEventListener("click", (event) => {
        event.stopPropagation()
        callbacks.onProvinceClick?.(code, name, feature)
      })
      element.addEventListener("mouseenter", () => {
        setHoverState(code, true)
        callbacks.onHover?.(code, name)
      })
      element.addEventListener("mouseleave", () => {
        setHoverState(code, false)
        callbacks.onHover?.(null)
      })
      const marker = new MlMarker({ element, anchor: "center", offset: LABEL_OFFSETS[code] ?? [0, 0] })
      marker.setLngLat([center.lng, center.lat]).addTo(map as MlMap)
      provinceLabels.push(marker)
    }
    updateLabelVisibility()
  }

  function updateLabelVisibility() {
    if (!map) return
    const zoom = map.getZoom()
    for (const marker of provinceLabels) {
      const code = (marker.getElement() as HTMLElement).dataset.code ?? ""
      const hidden = zoom < LABEL_HIDE_ZOOM && SMALL_PROVINCE_CODES.has(code)
      marker.getElement().style.display = hidden ? "none" : ""
    }
  }

  function clearProvinceLabels() {
    for (const marker of provinceLabels) marker.remove()
    provinceLabels.length = 0
  }

  /** 高亮：业务用 provinceCode，落到 MapLibre 时用 GeoJSON 的 feature.id（详细修复方案 §21） */
  function featureStateId(code: string): string | number {
    const feature = featuresMap().get(code)
    return feature?.id ?? code
  }

  function setHoverState(code: string | null, active: boolean) {
    if (!map || !code) return
    map.setFeatureState({ source: "provinces", id: featureStateId(code) }, { hovered: active })
  }

  function setSelected(code: string | null) {
    if (!map) return
    for (const key of featuresMap().keys()) {
      map.setFeatureState({ source: "provinces", id: featureStateId(key) }, { selected: key === code })
    }
  }

  function boundsOf(code: string): CameraTarget | null {
    const feature = featuresMap().get(code)
    if (!feature) return null
    const bounds = bboxOfFeature(feature)
    return bounds
  }

  async function flyToBounds(bounds: CameraTarget, duration: number): Promise<void> {
    if (!map) return
    const done = new Promise<void>((resolve) => map?.once("moveend", () => resolve()))
    map.fitBounds(
      [[bounds.west, bounds.south], [bounds.east, bounds.north]] as [[number, number], [number, number]],
      { padding: { top: 80, right: 440, bottom: 80, left: 80 }, duration: Math.max(1, Math.round(duration)), maxZoom: 6.2 },
    )
    await done
  }

  function flyChina(duration: number) {
    const bounds = chinaBounds()
    if (!map) return Promise.resolve()
    return flyToBounds(bounds, duration)
  }

  function renderRegionMarkers(regions: RegionNode[], resolveUrl: (value?: string | null) => string) {
    clearRegionMarkers()
    if (!map || !regions.length) return
    const maxCount = Math.max(1, ...regions.map((r) => Number(r.observationCount || 0)))
    regions.forEach((region, index) => {
      if (region.centerLng == null || region.centerLat == null) return
      const element = document.createElement("button")
      element.type = "button"
      element.className = "plant-region-node plant-region-node--enter"
      const cover = region.topSpecies?.[0]?.coverUrl
      const imgSrc = resolveUrl(cover || null)
      element.innerHTML =
        "<span class=\"plant-region-node__img\"><img src=\"" +
        imgSrc +
        "\" alt=\"\" loading=\"lazy\" /></span>" +
        "<span class=\"plant-region-node__name\">" + region.regionName + "</span>" +
        "<span class=\"plant-region-node__count\">" + Number(region.observationCount) + "</span>"
      element.style.animationDelay = Math.min(index * 40, 800) + "ms"
      const scale = 0.6 + 0.4 * (Number(region.observationCount) / maxCount)
      element.style.setProperty("--node-scale", scale.toFixed(2))
      element.addEventListener("click", () => markerClick?.({ region, index }))
      const marker = new MlMarker({ element, anchor: "bottom" })
      marker.setLngLat([region.centerLng, region.centerLat]).addTo(map as MlMap)
      regionMarkers.push(marker)
    })
  }

  function clearRegionMarkers() {
    for (const marker of regionMarkers) marker.remove()
    regionMarkers.length = 0
  }

  function destroy() {
    clearRegionMarkers()
    clearProvinceLabels()
    if (map) {
      map.remove()
      map = null
    }
    geojson = null
  }

  return {
    mount,
    destroy,
    setStats,
    setSelected,
    setHoverState,
    boundsOf,
    flyToBounds,
    flyChina,
    renderRegionMarkers,
    clearRegionMarkers,
    renderProvinceLabels,
    clearProvinceLabels,
    provinceCenter,
    project,
    onCameraChange,
    setFeatured,
    setMarkerClick(handler: ((payload: { region: RegionNode; index: number }) => void) | null) { markerClick = handler },
    getMap: () => map,
    getFeatureCount: () => (geojson ? geojson.features.length : 0),
  }
}