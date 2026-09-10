export type MapStage =
  | "CHINA_OVERVIEW"
  | "PROVINCE_FLYING"
  | "PROVINCE_OVERVIEW"
  | "SPECIES_FOCUS"
  | "OBSERVATION_FOCUS"
  | "RETURNING"

export interface TopSpeciesItem {
  speciesId: number | string
  commonName?: string | null
  coverUrl?: string | null
  observationCount: number | string
}

export interface RegionNode {
  regionCode: string
  regionName: string
  centerLng?: number | null
  centerLat?: number | null
  speciesCount: number | string
  observationCount: number | string
  studentCount: number | string
  topSpecies?: TopSpeciesItem[]
}

export interface ProvinceVisual {
  province: {
    code: string
    name?: string | null
    speciesCount: number | string
    observationCount: number | string
    studentCount: number | string
  }
  regions: RegionNode[]
  topSpecies: TopSpeciesItem[]
}

export interface ChinaStatRow {
  provinceCode: string
  provinceName?: string | null
  speciesCount: number | string
  observationCount: number | string
  studentCount: number | string
}

export interface MapFilter {
  categoryId?: string
  classId?: string
  year?: number
  keyword?: string
  speciesId?: string
}

/** 全国精选作品（地图外围卡片 + 引导线，下一步开发计划 §4.2） */
export interface MapFeaturedWork {
  observationId: number | string
  commonName?: string | null
  reportedCommonName?: string | null
  coverUrl?: string | null
  provinceCode?: string | null
  provinceName?: string | null
  cityName?: string | null
  displayName?: string | null
  submitterName?: string | null
  description?: string | null
  featured?: boolean
  publishedAt?: string | null
}

/** 省份作品（点击省份后加载，§3.3） */
export interface ProvinceWorkRow extends MapFeaturedWork {
  className?: string | null
  observedAt?: string | null
  averageRating?: number | null
  commentCount?: number | string
  viewCount?: number | string
}

export interface SpeciesObsRow {
  observationId: number | string
  coverUrl?: string | null
  commonName?: string | null
  provinceName?: string | null
  cityName?: string | null
  submitterName?: string | null
  className?: string | null
  observedAt?: string | null
  featured?: boolean
  averageRating?: number | null
  commentCount?: number | string
  viewCount?: number | string
}
