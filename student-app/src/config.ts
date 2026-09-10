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

export function getMediaOrigin(): string {
  return normalize(ENV_MEDIA_ORIGIN ?? "") || getApiBase()
}

export const API_BASE = ENV_API_BASE ?? ""
export const MEDIA_ORIGIN = ENV_MEDIA_ORIGIN ?? API_BASE
export const PLANT_PLACEHOLDER = "/static/placeholder.svg"
