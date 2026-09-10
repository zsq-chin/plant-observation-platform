export interface UserInfo {
  id: string
  username: string
  realName?: string | null
  roleCode?: string
  className?: string | null
  classId?: string | null
  avatar?: string | null
}

export interface PageResult<T> {
  records: T[]
  total: number
  pageNum: number
  pageSize: number
}

export interface RegionItem {
  regionCode: string
  regionName: string
  parentCode?: string | null
  regionLevel?: string
}

export interface CategoryItem { id: string; name: string; code: string }
export interface ClassItem { id: string; dictLabel: string }
export interface SpeciesItem {
  id: string
  commonName: string
  scientificName?: string | null
  categoryId?: string | null
  familyName?: string | null
  genusName?: string | null
  coverUrl?: string | null
}

export interface FieldDef {
  id: string
  fieldCode: string
  fieldLabel: string
  fieldType: string
  optionsJson?: string | null
  required?: number
  scopeType?: string
}

export interface PhotoItem {
  photoId?: string
  id?: string
  observationId?: string
  fileUrl: string
  thumbnailUrl?: string | null
  organType?: string | null
  isCover?: boolean
}

export interface MyObservation {
  id: string
  status: string
  speciesId?: string | null
  reportedCommonName?: string | null
  provinceName?: string | null
  cityName?: string | null
  districtName?: string | null
  locationText?: string | null
  observedAt?: string | null
  description?: string | null
  submitTime?: string | null
  createTime?: string | null
  identificationStatus?: string | null
}

export interface ObsDetail {
  observationId: string
  speciesId?: string | null
  commonName?: string | null
  scientificName?: string | null
  categoryName?: string | null
  provinceName?: string | null
  cityName?: string | null
  districtName?: string | null
  locationText?: string | null
  submitterName?: string | null
  className?: string | null
  observedAt?: string | null
  description?: string | null
  featured?: boolean
  photos?: PhotoItem[]
  reviewComment?: string | null
  fieldValues?: { fieldCode: string; fieldLabel: string; fieldType: string; valueText?: string | null }[]
  qualityWarnings?: string[]
  identificationStatus?: string | null
}

export interface GalleryItem {
  observationId: string
  commonName?: string | null
  reportedCommonName?: string | null
  scientificName?: string | null
  coverUrl?: string | null
  provinceName?: string | null
  cityName?: string | null
  submitterName?: string | null
  className?: string | null
  observedAt?: string | null
  featured?: boolean
  averageRating?: number | null
  commentCount?: number | string
  viewCount?: number | string
  provinceCode?: string | null
}

export interface StudentDashboard {
  draftCount: number
  submittedCount: number
  approvedCount: number
  rejectedCount: number
  unreadNotificationCount: number
}

export interface CommentItem {
  commentId: string
  userName?: string | null
  isTeacherComment?: boolean
  isPinned?: boolean
  parentId?: string | null
  content: string
  createTime?: string | null
}

export interface NotificationItem {
  id: string
  title?: string | null
  content?: string | null
  isRead?: number
  createTime?: string | null
}

export interface SuggestionItem {
  id: string
  suggestedCommonName?: string | null
  suggestedScientificName?: string | null
  description?: string | null
  status: string
  reviewComment?: string | null
  createTime?: string | null
}
