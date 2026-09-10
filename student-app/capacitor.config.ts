import type { CapacitorConfig } from "@capacitor/cli"

/**
 * 学生端 Android 壳（Capacitor）配置。
 *
 * 当前采用「实时模式」：WebView 直接加载局域网内已部署的 H5（需手机与电脑同一 WiFi），
 * 页面与接口同源，因此无需额外配置 API 地址，改动 H5 后手机端刷新即生效。
 *
 * 若要改为「离线内置模式」（把 H5 打进 APK，离线也能打开）：
 *   1) 构建：起 UNI_PUBLIC_PATH=/ 与 VITE_API_BASE=http://<电脑IP> 重新 build:h5
 *   2) 删除下面的 server.url 配置，保留 webDir
 *   3) 重新执行 npx cap sync android && gradlew assembleDebug
 */
const config: CapacitorConfig = {
  appId: "com.jingxuan.plants",
  appName: "植物观察",
  webDir: "dist/build/h5",
  android: {
    allowMixedContent: true,
  },
  server: {
    url: "http://10.120.46.175/jingxuan/app/",
    cleartext: true,
    androidScheme: "http",
  },
}

export default config
