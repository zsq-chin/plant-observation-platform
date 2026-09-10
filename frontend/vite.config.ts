import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import path from 'path'
import { createHash } from 'node:crypto'
import { readFileSync } from 'node:fs'
import Components from 'unplugin-vue-components/vite'
import { ElementPlusResolver } from 'unplugin-vue-components/resolvers'
import viteCompression from 'vite-plugin-compression'

/**
 * 地图边界文件（public/geo/china-provinces.json）文件名不带内容哈希，
 * 一旦 nginx 把它当成长缓存资源，改完 GeoJSON 老浏览器可能一年都拿不到新数据
 * （台湾省就是这么丢的）。把内容指纹写进请求地址即可自动破缓存。
 */
function geoVersion(file: string): string {
  try {
    return createHash('sha1').update(readFileSync(file)).digest('hex').slice(0, 10)
  } catch {
    return 'dev'
  }
}

export default defineConfig({
  base: '/jingxuan/',
  define: {
    __GEO_VERSION__: JSON.stringify(geoVersion(path.resolve(__dirname, 'public/geo/china-provinces.json'))),
  },
  plugins: [
    vue(),

    // Element Plus 按需导入
    Components({
      resolvers: [ElementPlusResolver({ importStyle: 'css' })],
    }),

    // gzip 预压缩（需部署环境开启静态 .gz 优先服务）
    viteCompression({
      algorithm: 'gzip',
      ext: '.gz',
      threshold: 10240,
      deleteOriginFile: false,
    }),
  ],
  resolve: {
    alias: {
      '@': path.resolve(__dirname, 'src'),
    },
  },
  server: {
    port: 5173,
    proxy: {
      '/api/file': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
      '/media': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
      '/uploads': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
  build: {
    chunkSizeWarningLimit: 750,
    rollupOptions: {
      output: {
        manualChunks(id: string) {
          // Vue 生态核心（vue / pinia / vue-router / axios）
          if (
            id.includes('node_modules/vue') ||
            id.includes('node_modules/pinia') ||
            id.includes('node_modules/vue-router') ||
            id.includes('node_modules/axios')
          ) {
            return 'vendor-vue'
          }
          // Element Plus（按需导入后仍集中放置，避免碎片化）
          if (id.includes('node_modules/element-plus')) {
            return 'vendor-element'
          }
          // ECharts（仅在管理端控制台使用，独立 chunk）
          if (id.includes('node_modules/echarts')) {
            return 'vendor-echarts'
          }
        },
      },
    },
  },
})