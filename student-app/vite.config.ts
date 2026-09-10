import { defineConfig } from "vite";
import uni from "@dcloudio/vite-plugin-uni";
import { createHash } from "node:crypto";
import { readFileSync } from "node:fs";
import { resolve } from "node:path";

/** 地图边界文件不带内容哈希，必须把内容指纹拼进请求地址，否则老客户端会一直用旧副本 */
function geoVersion(file: string): string {
  try {
    return createHash("sha1").update(readFileSync(file)).digest("hex").slice(0, 10);
  } catch {
    return "dev";
  }
}

// H5 部署在 nginx 的 /jingxuan/app/ 子路径下；开发时把 /api 与 /media 代理到本地后端
export default defineConfig({
  base: process.env.UNI_PUBLIC_PATH || "/jingxuan/app/",
  define: {
    __GEO_VERSION__: JSON.stringify(geoVersion(resolve(__dirname, "src/static/geo/china-provinces.json"))),
  },
  plugins: [uni()],
  server: {
    port: 5174,
    proxy: {
      "/api": { target: "http://127.0.0.1:8080", changeOrigin: true },
      "/media": { target: "http://127.0.0.1:8080", changeOrigin: true },
    },
  },
});
