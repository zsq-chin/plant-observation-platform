// Token/Profile 与本地草稿存储（不存明文密码）
export function saveAuth(token: string, userInfo: Record<string, unknown>) {
  uni.setStorageSync("jingxuan_token", token)
  uni.setStorageSync("jingxuan_user", JSON.stringify(userInfo))
}

export function readToken(): string | null {
  try { return uni.getStorageSync("jingxuan_token") || null } catch { return null }
}

export function readUser(): Record<string, unknown> | null {
  try {
    const raw = uni.getStorageSync("jingxuan_user")
    return raw ? JSON.parse(raw) : null
  } catch { return null }
}

export function clearAuth() {
  uni.removeStorageSync("jingxuan_token")
  uni.removeStorageSync("jingxuan_user")
}

// 本地待同步草稿（V4 §11/§14）：离线采集 → 有网重试
export interface LocalDraft {
  localId: string
  savedAt: number
  payload: Record<string, unknown>
  localPhotoPaths: string[]
  syncedObservationId?: string
}

export function saveLocalDraft(draft: LocalDraft) {
  const drafts = readLocalDrafts()
  const idx = drafts.findIndex((d) => d.localId === draft.localId)
  if (idx >= 0) drafts[idx] = draft
  else drafts.unshift(draft)
  uni.setStorageSync("jingxuan_local_drafts", JSON.stringify(drafts.slice(0, 20)))
}

export function readLocalDrafts(): LocalDraft[] {
  try {
    const raw = uni.getStorageSync("jingxuan_local_drafts")
    return raw ? (JSON.parse(raw) as LocalDraft[]) : []
  } catch { return [] }
}

export function removeLocalDraft(localId: string) {
  uni.setStorageSync("jingxuan_local_drafts", JSON.stringify(readLocalDrafts().filter((d) => d.localId !== localId)))
}
