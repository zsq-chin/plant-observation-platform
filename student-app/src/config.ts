// 学生 App 环境配置
// - 默认：同源相对路径（H5 部署在 nginx 上与接口同源；WebView 壳加载同源页面时同样适用）
// - 原生打包：可用构建变量 VITE_API_BASE 注入绝对地址，例如
//     VITE_API_BASE=http://10.120.46.175 npm run build:h5
// - 运行期：用户可在登录页「服务器设置」里自行填写后端地址（保存于本地存储，优先级最高）
const ENV_API_BASE = import.meta.env.VITE_API_BASE as string | undefined
const ENV_MEDIA_ORIGIN = import.meta.env.VITE_MEDIA_ORIGIN as string | undefined
const STORAGE_KEY = "jingxuan_api_base"

function normalize(value: string): string {
  return (value || "").trim().replace(/\/+$/, "")
}

/** 当前生效的接口地址：本地设置 > 构建变量 > 同源（空字符串） */
export function getApiBase(): string {
  try {
    const saved = uni.getStorageSync(STORAGE_KEY)
    if (typeof saved === "string" && saved.trim()) return normalize(saved)
  } catch {
    /* 读取失败按未设置处理 */
  }
  return normalize(ENV_API_BASE ?? "")
}

/** 保存/清空自定义接口地址，返回保存后的值（空字符串表示恢复默认） */
export function setApiBase(value: string): string {
  const normalized = normalize(value)
  try {
    if (normalized) uni.setStorageSync(STORAGE_KEY, normalized)
    else uni.removeStorageSync(STORAGE_KEY)
  } catch {
    /* 存储不可用时忽略 */
  }
  return normalized
}

/**
 * 当前页面源（形如 http://10.120.46.175）。
 *
 * 必须返回**绝对地址**：uni-app H5 的 <image> 会把以 "/" 开头的路径按“应用根”解析，
 * 即 /media/plants/x.jpg 会请求成 /jingxuan/app/media/plants/x.jpg（实测返回 SPA 的
 * index.html，HTTP 200 text/html），图片永远显示不出来。拼成绝对地址后不再经过该解析。
 */
function pageOrigin(): string {
  try {
    const loc = typeof window === "undefined" ? null : window.location
    if (!loc || !/^https?:$/.test(loc.protocol) || !loc.host) return ""
    return loc.protocol + "//" + loc.host
  } catch {
    /* 非 H5 环境（原生 App 逻辑层）没有 window.location */
    return ""
  }
}

/** 应用部署子路径（H5 为 "/jingxuan/app/"）；取不到时回退到当前页面目录 */
function appBasePath(): string {
  const envBase = String((import.meta.env && import.meta.env.BASE_URL) || "")
  if (envBase.startsWith("/")) return envBase.endsWith("/") ? envBase : envBase + "/"
  try {
    const pathname = window.location.pathname || "/"
    return pathname.endsWith("/") ? pathname : pathname.replace(/[^/]*$/, "")
  } catch {
    return "/"
  }
}

/** 图片资源的最终来源：本地设置 > 构建变量 > 当前页面源；都取不到时返回空串（相对路径） */
export function getMediaOrigin(): string {
  const envOrigin = normalize(ENV_MEDIA_ORIGIN ?? "")
  if (envOrigin) return envOrigin
  return getApiBase() || pageOrigin()
}

export const API_BASE = ENV_API_BASE ?? ""
export const MEDIA_ORIGIN = ENV_MEDIA_ORIGIN ?? API_BASE
/** 占位图同样拼成绝对地址，避免被 uni-app 的“应用根”解析规则吃掉 */
export const PLANT_PLACEHOLDER = pageOrigin() + appBasePath() + "static/placeholder.svg"
