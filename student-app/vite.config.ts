import { defineConfig } from "vite";
import uni from "@dcloudio/vite-plugin-uni";

// H5 部署在 nginx 的 /jingxuan/app/ 子路径下；开发时把 /api 与 /media 代理到本地后端
export default defineConfig({
  base: process.env.UNI_PUBLIC_PATH || "/jingxuan/app/",
  plugins: [uni()],
  server: {
    port: 5174,
    proxy: {
      "/api": { target: "http://127.0.0.1:8080", changeOrigin: true },
      "/media": { target: "http://127.0.0.1:8080", changeOrigin: true },
    },
  },
});
