import { get, post, put, del, uploadFile } from "./request"
import type {
  ClassItem, CategoryItem, CommentItem, FieldDef, GalleryItem, MyObservation,
  NotificationItem, ObsDetail, PageResult, PhotoItem, RegionItem, SpeciesItem,
  StudentDashboard, SuggestionItem, UserInfo,
} from "@/types/models"

// ---------- 认证 ----------
export function login(username: string, password: string) {
  return post<{ token: string; userInfo: UserInfo }>("/api/auth/login", { username, password, rememberMe: false })
}
export function fetchMe() {
  return get<UserInfo>("/api/auth/me")
}

// ---------- 工作台/通知 ----------
export function fetchStudentDashboard() {
  return get<StudentDashboard>("/api/student/plant/dashboard")
}
export function fetchNotifications(page = 1, size = 20) {
  return get<PageResult<NotificationItem>>("/api/student/notify/list?page=" + page + "&size=" + size)
}
export function markNotificationRead(id: string) {
  return post("/api/student/notify/read/" + id, {})
}

// ---------- 观察记录 ----------
export function createObservation(body: Record<string, unknown>) {
  return post<MyObservation>("/api/student/plant/observations", body)
}
export function updateObservation(id: string, body: Record<string, unknown>) {
  return put<MyObservation>("/api/student/plant/observations/" + id, body)
}
export function myObservations(status: string | undefined, page = 1, size = 20) {
  const query = status ? "?status=" + status : ""
  return get<PageResult<MyObservation>>("/api/student/plant/observations" + query + (query ? "&page=" : "?page=") + page + "&size=" + size)
}
export function myObservationDetail(id: string) {
  return get<MyObservation>("/api/student/plant/observations/" + id)
}
export function myObservationPhotos(id: string) {
  return get<PhotoItem[]>("/api/student/plant/observations/" + id + "/photos")
}
export function submitObservation(id: string) {
  return post("/api/student/plant/observations/" + id + "/submit", {})
}
export function withdrawObservation(id: string) {
  return post("/api/student/plant/observations/" + id + "/withdraw", {})
}
export function deleteObservation(id: string) {
  return del("/api/student/plant/observations/" + id)
}
export function uploadPhoto(id: string, filePath: string, organType: string) {
  return uploadFile("/api/student/plant/observations/" + id + "/photos", filePath, { organType })
}
export function deletePhoto(id: string, photoId: string) {
  return del("/api/student/plant/observations/" + id + "/photos/" + photoId)
}
export function setCoverPhoto(id: string, photoId: string) {
  return put("/api/student/plant/observations/" + id + "/photos/cover?photoId=" + photoId, {})
}
export function reorderPhotos(id: string, photoIds: string[]) {
  return put("/api/student/plant/observations/" + id + "/photos/order", photoIds)
}
export function updatePhotoOrgan(id: string, photoId: string, organType: string) {
  return put("/api/student/plant/observations/" + id + "/photos/" + photoId + "/organ?organType=" + encodeURIComponent(organType), {})
}
export function fetchPlantFields() {
  return get<FieldDef[]>("/api/student/plant/plant-fields")
}

// ---------- 参考数据 ----------
export function fetchProvinces() {
  return get<RegionItem[]>("/api/public/plant/regions/provinces")
}
export function fetchChildren(parentCode: string) {
  return get<RegionItem[]>("/api/public/plant/regions/" + parentCode + "/children")
}
export function searchSpecies(keyword: string) {
  return get<PageResult<SpeciesItem>>("/api/public/plant/species/search?keyword=" + encodeURIComponent(keyword) + "&page=1&size=20")
}
export function fetchCategories() {
  return get<CategoryItem[]>("/api/public/plant/categories")
}
export function fetchClasses() {
  return get<ClassItem[]>("/api/public/classes")
}

// ---------- 公开内容 ----------
export function fetchHome() {
  return get<{ statistics: Record<string, number>; featuredObservations: GalleryItem[]; latestObservations: GalleryItem[] }>("/api/public/plant/home")
}
export function fetchGallery(page = 1, size = 12, keyword?: string) {
  const q = keyword ? "&keyword=" + encodeURIComponent(keyword) : ""
  return get<PageResult<GalleryItem>>("/api/public/plant/gallery?page=" + page + "&size=" + size + q)
}
export function fetchSearch(keyword: string) {
  return get<{ species: SpeciesItem[]; observations: GalleryItem[] }>("/api/public/plant/search?keyword=" + encodeURIComponent(keyword))
}
export function fetchPublicObservation(id: string) {
  return get<ObsDetail>("/api/public/plant/observations/" + id)
}
export function fetchProvinceSpecies(provinceCode: string) {
  return get<{ speciesId: string; commonName: string; observationCount: number | string }[]>("/api/public/plant/map/provinces/" + provinceCode + "/species")
}
export function fetchMapChina() {
  return get<{ provinceCode: string; provinceName: string; speciesCount: number | string; observationCount: number | string }[]>("/api/public/plant/map/china")
}

// ---------- 社区/建议 ----------
export function fetchComments(id: string) {
  return get<CommentItem[]>("/api/public/plant/observations/" + id + "/comments")
}
export function postComment(id: string, content: string, parentId?: string) {
  return post("/api/community/plant/observations/" + id + "/comments", { content, parentId: parentId || null })
}
export function postRating(id: string, score: number) {
  return post("/api/community/plant/observations/" + id + "/rating", { score })
}
export function createSuggestion(body: Record<string, unknown>) {
  return post<SuggestionItem>("/api/student/plant/species-suggestions", body)
}
export function mySuggestions(page = 1, size = 20) {
  return get<PageResult<SuggestionItem>>("/api/student/plant/species-suggestions?page=" + page + "&size=" + size)
}