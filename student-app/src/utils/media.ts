import { MEDIA_ORIGIN } from "@/config"
import { PLANT_PLACEHOLDER } from "@/config"

export function resolveMediaUrl(value?: string | null): string {
  if (!value) return PLANT_PLACEHOLDER
  if (value.startsWith("http://") || value.startsWith("https://") || value.startsWith("data:") || value.startsWith("blob:")) {
    return value
  }
  if (value.startsWith("/")) {
    return MEDIA_ORIGIN + value
  }
  return MEDIA_ORIGIN + "/media/plants/" + value
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
