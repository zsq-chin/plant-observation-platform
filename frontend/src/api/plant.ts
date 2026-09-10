import request from "@/api/request"

// ---------- 类型（Long 全局序列化为字符串，id/count 字段可能是 string） ----------
export interface PageResult<T> {
  records: T[]
  total: number
  pageNum: number
  pageSize: number
}

export interface RegionItem {
  regionCode: string
  regionName: string
  regionLevel?: string
  parentCode?: string | null
}

export interface CategoryItem {
  id: number | string
  name: string
  code: string
}

export interface ClassItem {
  id: number | string
  dictLabel: string
}

export interface SpeciesItem {
  id: number | string
  commonName: string
  scientificName?: string | null
  familyName?: string | null
  genusName?: string | null
  categoryId?: number | string | null
  coverUrl?: string | null
  description?: string | null
}

export interface GalleryItem {
  observationId: number | string
  speciesId?: number | string | null
  commonName?: string | null
  scientificName?: string | null
  reportedCommonName?: string | null
  categoryName?: string | null
  coverUrl?: string | null
  provinceName?: string | null
  cityName?: string | null
  submitterName?: string | null
  className?: string | null
  observedAt?: string | null
  description?: string | null
  featured?: boolean
  averageRating?: number | null
  ratingCount?: number | string
  commentCount?: number | string
  viewCount?: number | string
  classId?: number | string | null
  categoryId?: number | string | null
  provinceCode?: string | null
}

export interface HomeData {
  statistics: {
    speciesCount: number
    observationCount: number | string
    studentCount: number
    provinceCount: number
    categoryCount: number
  }
  featuredObservations: GalleryItem[]
  latestObservations: GalleryItem[]
  teacherComments: {
    commentId: number | string
    observationId: number | string
    plantName?: string | null
    teacherName?: string | null
    content: string
    createTime: string
  }[]
}

export interface CommentItem {
  commentId: number | string
  observationId: number | string
  userId?: number | string | null
  userName?: string | null
  isTeacherComment?: boolean
  isPinned?: boolean
  parentId?: number | string | null
  rootId?: number | string | null
  content: string
  createTime?: string | null
}

export interface PhotoItem {
  photoId: number | string
  fileUrl: string
  thumbnailUrl?: string | null
  organType?: string | null
  isCover?: boolean
}

export interface FieldDef {
  id: number | string
  fieldCode: string
  fieldLabel: string
  fieldType: string
  scopeType?: string
  optionsJson?: string | null
  required?: number
}

export interface ObsDetail {
  observationId: number | string
  speciesId?: number | string | null
  commonName?: string | null
  scientificName?: string | null
  categoryId?: number | string | null
  categoryName?: string | null
  familyName?: string | null
  genusName?: string | null
  reportedCommonName?: string | null
  photos?: PhotoItem[]
  provinceName?: string | null
  cityName?: string | null
  districtName?: string | null
  locationText?: string | null
  submitterName?: string | null
  className?: string | null
  observedAt?: string | null
  description?: string | null
  featured?: boolean
  publishedAt?: string | null
  viewCount?: number | string
  averageRating?: number | null
  ratingCount?: number | string
  commentCount?: number | string
  fieldValues?: { fieldCode: string; fieldLabel: string; fieldType: string; valueText?: string | null }[]
  reviewComment?: string | null
  status?: string | null
}

export interface ReviewRow {
  observationId: number | string
  coverUrl?: string | null
  speciesId?: number | string | null
  commonName?: string | null
  reportedCommonName?: string | null
  submitterId?: number | string | null
  submitterName?: string | null
  className?: string | null
  provinceName?: string | null
  cityName?: string | null
  districtName?: string | null
  observedAt?: string | null
  submitTime?: string | null
  status?: string | null
  photoCount?: number | string
  classId?: number | string | null
}

export interface MyObsRow {
  id: number | string
  status: string
  speciesId?: number | string | null
  reportedCommonName?: string | null
  provinceName?: string | null
  cityName?: string | null
  districtName?: string | null
  locationText?: string | null
  observedAt?: string | null
  description?: string | null
  submitTime?: string | null
  createTime?: string | null
  rejected?: boolean
}

function unwrap<T>(payload: unknown): T {
  return (payload as { data: T }).data
}

export interface GalleryQuery {
  page?: number
  size?: number
  keyword?: string
  provinceCode?: string
  categoryId?: number | string
  classId?: number | string
  year?: number
  featured?: boolean
  sort?: "featured" | "latest" | "view"
}

export async function fetchHome(): Promise<HomeData> {
  return unwrap<HomeData>(await request.get("/api/public/plant/home"))
}

export async function fetchGallery(query: GalleryQuery = {}): Promise<PageResult<GalleryItem>> {
  const params: Record<string, unknown> = {
    page: query.page ?? 1,
    size: query.size ?? 12,
  }
  if (query.keyword) params.keyword = query.keyword
  if (query.provinceCode) params.provinceCode = query.provinceCode
  if (query.categoryId) params.categoryId = query.categoryId
  if (query.classId) params.classId = query.classId
  if (query.year) params.year = query.year
  if (query.featured !== undefined) params.featured = query.featured
  if (query.sort) params.sort = query.sort
  return unwrap<PageResult<GalleryItem>>(await request.get("/api/public/plant/gallery", { params }))
}

export async function fetchObsDetail(id: number | string): Promise<ObsDetail> {
  return unwrap<ObsDetail>(await request.get(`/api/public/plant/observations/${id}`))
}

export async function fetchComments(id: number | string): Promise<CommentItem[]> {
  return unwrap<CommentItem[]>(await request.get(`/api/public/plant/observations/${id}/comments`))
}

export async function postComment(id: number | string, content: string, parentId?: number | string | null): Promise<void> {
  await request.post(`/api/community/plant/observations/${id}/comments`, { content, parentId: parentId ?? null })
}

export async function postRating(id: number | string, score: number): Promise<void> {
  await request.post(`/api/community/plant/observations/${id}/rating`, { score })
}

export async function fetchProvinces(): Promise<RegionItem[]> {
  return unwrap<RegionItem[]>(await request.get("/api/public/plant/regions/provinces"))
}

export async function fetchRegionChildren(parentCode: string): Promise<RegionItem[]> {
  return unwrap<RegionItem[]>(await request.get(`/api/public/plant/regions/${parentCode}/children`))
}

export async function fetchCategories(): Promise<CategoryItem[]> {
  return unwrap<CategoryItem[]>(await request.get("/api/public/plant/categories"))
}

export async function fetchPublicClasses(): Promise<ClassItem[]> {
  return unwrap<ClassItem[]>(await request.get("/api/public/classes"))
}

export async function searchSpecies(keyword: string, categoryId?: number | string): Promise<PageResult<SpeciesItem>> {
  return unwrap<PageResult<SpeciesItem>>(
    await request.get("/api/public/plant/species/search", { params: { keyword, categoryId, page: 1, size: 20 } }),
  )
}

export async function fetchMapChina(params: { classId?: number | string; categoryId?: number | string; speciesId?: number | string; year?: number } = {}): Promise<{
  provinceCode: string
  provinceName: string
  speciesCount: number | string
  observationCount: number | string
  studentCount: number | string
}[]> {
  return unwrap<{ provinceCode: string; provinceName: string; speciesCount: number | string; observationCount: number | string; studentCount: number | string }[]>(
    await request.get("/api/public/plant/map/china", { params }),
  )
}

export function numberValue(value: unknown): number {
  if (value === null || value === undefined) return 0
  if (typeof value === "number") return value
  const parsed = Number(String(value))
  return Number.isFinite(parsed) ? parsed : 0
}

export function fmtDate(value?: string | null): string {
  if (!value) return ""
  return String(value).slice(0, 10)
}

export function isLoggedIn(): boolean {
  return Boolean(localStorage.getItem("token") || sessionStorage.getItem("token"))
}
