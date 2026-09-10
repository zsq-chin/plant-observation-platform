// uni.request 封装：自动带 JWT、统一 Result/Problem 处理
import { API_BASE } from "@/config"
import { clearAuth } from "@/utils/storage"

type HttpMethod = "GET" | "POST" | "PUT" | "DELETE"

interface Result<T> { code?: number; data?: T; message?: string }

export interface RequestOptions {
  token?: string | null
  silent?: boolean
  data?: unknown
  header?: Record<string, string>
}

function readToken(): string | null {
  try {
    return uni.getStorageSync("jingxuan_token") || null
  } catch {
    return null
  }
}

function toast(message: string) {
  uni.showToast({ title: message, icon: "none" })
}

export function request<T>(method: HttpMethod, path: string, options: RequestOptions = {}): Promise<T> {
  const token = options.token === undefined ? readToken() : options.token
  const header: Record<string, string> = { ...(options.header || {}) }
  if (token) header.Authorization = "Bearer " + token
  if (options.data !== undefined) header["Content-Type"] = "application/json"
  return new Promise<T>((resolve, reject) => {
    uni.request({
      url: API_BASE + path,
      method,
      data: options.data as never,
      header,
      timeout: 30000,
      success: (res) => {
        const status = res.statusCode
        const payload = res.data as Result<T> | { detail?: string }
        if (status >= 200 && status < 300 && payload && (payload as Result<T>).code !== undefined) {
          const result = payload as Result<T>
          if (result.code === 200 || result.code === 0) {
            resolve(result.data as T)
            return
          }
          if (result.code === 401) {
            clearAuth()
            uni.reLaunch({ url: "/pages/login/index" })
          }
          if (!options.silent) toast(result.message || "请求失败")
          reject(new Error(result.message || "请求失败"))
          return
        }
        if (status === 401) {
          clearAuth()
          uni.reLaunch({ url: "/pages/login/index" })
        }
        const message = (payload as { message?: string }).message || (payload as { detail?: string }).detail || "网络错误(" + status + ")"
        if (!options.silent) toast(message)
        reject(new Error(message))
      },
      fail: (err) => {
        if (!options.silent) toast("网络异常，请稍后重试")
        reject(new Error(err.errMsg || "network error"))
      },
    })
  })
}

export function get<T>(path: string, options: RequestOptions = {}) {
  return request<T>("GET", path, options)
}

export function post<T>(path: string, data?: unknown, options: RequestOptions = {}) {
  return request<T>("POST", path, { ...options, data })
}

export function put<T>(path: string, data?: unknown, options: RequestOptions = {}) {
  return request<T>("PUT", path, { ...options, data })
}

export function del<T>(path: string, options: RequestOptions = {}) {
  return request<T>("DELETE", path, options)
}

export function uploadFile(path: string, filePath: string, form: Record<string, string>): Promise<{ data: { fileUrl: string; thumbnailUrl?: string | null; id?: string } }> {
  const token = readToken()
  return new Promise((resolve, reject) => {
    uni.uploadFile({
      url: API_BASE + path,
      filePath,
      name: "file",
      formData: form,
      header: token ? { Authorization: "Bearer " + token } : {},
      success: (res) => {
        if (res.statusCode >= 200 && res.statusCode < 300) {
          try {
            const payload = JSON.parse(res.data) as { code?: number; data?: unknown; message?: string }
            if (payload.code === 200 || payload.code === 0) {
              resolve({ data: payload.data as never })
              return
            }
            toast(payload.message || "上传失败")
            reject(new Error(payload.message || "upload failed"))
          } catch {
            reject(new Error("上传响应解析失败"))
          }
          return
        }
        toast("上传失败(" + res.statusCode + ")")
        reject(new Error("upload " + res.statusCode))
      },
      fail: (err) => {
        toast("上传失败，请检查网络")
        reject(new Error(err.errMsg || "upload network error"))
      },
    })
  })
}

export function chooseAndCompressImages(max: number, source: "camera" | "album"): Promise<string[]> {
  return new Promise((resolve, reject) => {
    uni.chooseImage({
      count: Math.max(1, max),
      sourceType: [source],
      sizeType: ["compressed"],
      success: async (res) => {
        const paths = res.tempFilePaths || []
        if (!paths.length) {
          reject(new Error("未选择图片"))
          return
        }
        const results: string[] = []
        for (const item of paths) {
          const compressed = await compressImage(item).catch(() => item)
          results.push(compressed)
        }
        resolve(results)
      },
      fail: () => reject(new Error("未选择图片")),
    })
  })
}

function compressImage(path: string): Promise<string> {
  return new Promise((resolve, reject) => {
    uni.compressImage({
      src: path,
      quality: 75,
      success: (r) => resolve(r.tempFilePath),
      fail: () => reject(new Error("compress fail")),
    })
  })
}
