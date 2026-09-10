// 学生 App 环境配置
// H5（浏览器访问 / WebView 壳）：留空即使用同源相对路径（/api、/media），部署到 nginx 后无需改配置
// 原生打包（uni-app 原生 / Capacitor 壳）：构建时注入绝对地址，例如
//   VITE_API_BASE=http://10.120.46.175 npm run build:h5
const ENV_API_BASE = import.meta.env.VITE_API_BASE as string | undefined
const ENV_MEDIA_ORIGIN = import.meta.env.VITE_MEDIA_ORIGIN as string | undefined

export const API_BASE = ENV_API_BASE ?? ""
export const MEDIA_ORIGIN = ENV_MEDIA_ORIGIN ?? API_BASE
export const PLANT_PLACEHOLDER = "/static/placeholder.svg"
