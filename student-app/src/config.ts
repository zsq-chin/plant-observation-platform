// 学生 App 环境配置：真机/打包时按环境修改
export const API_BASE = import.meta.env.VITE_API_BASE || "http://127.0.0.1:8080"
export const MEDIA_ORIGIN = import.meta.env.VITE_MEDIA_ORIGIN || API_BASE
export const PLANT_PLACEHOLDER = "/static/placeholder.svg"
