import { getMediaOrigin, PLANT_PLACEHOLDER } from "@/config"

const mediaOrigin = () => getMediaOrigin()

/**
 * 图片地址统一出口。
 *
 * 注意：uni-app H5 的 <image> 组件会把以 "/" 开头的 src 当成“应用根”相对路径
 * （实测 /media/plants/x.jpg 被请求成 /jingxuan/app/media/plants/x.jpg，nginx 回退到
 * SPA 的 index.html，返回 200 text/html → 图片必然显示失败）。因此这里一律拼成绝对
 * 地址，绝不把裸的相对路径交给 <image>。
 */
export function resolveMediaUrl(value?: string | null): string {
  if (!value) return PLANT_PLACEHOLDER
  if (value.startsWith("http://") || value.startsWith("https://") || value.startsWith("data:") || value.startsWith("blob:")) {
    return value
  }
  const path = value.startsWith("/") ? value : "/media/plants/" + value
  const origin = mediaOrigin()
  return origin ? origin + path : path
}

export function fmtDate(value?: string | null): string {
  if (!value) return ""
  return String(value).slice(0, 10)
}

export function numberValue(value: number | string | undefined): number {
  if (value === undefined || value === null) return 0
  return typeof value === "number" ? value : Number(value) || 0
}

export const STATUS_LABELS: Record<string, string> = {
  DRAFT: "草稿",
  SUBMITTED: "待审核",
  APPROVED: "已通过",
  REJECTED: "被驳回",
  OFFLINE: "已下线",
}
