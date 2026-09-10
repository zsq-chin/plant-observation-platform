# 学生 Android App（student-app）构建与发布说明

> 依据 V4 §2-§20 实现。技术栈：uni-app（vue3 + TypeScript + Pinia）+ 后端 REST API。
> 已通过 npm run build:h5 编译验证（uni compiler 5.24）；Android APK 需 HBuilderX / Android SDK（见下）。

## 项目结构（与文档 §3 对齐）
- src/pages/：login、home（dashboard）、gallery（展廊）、capture（现场采集）、observation-edit（补充/动态字段/省市区）、my-observations（状态 Tab + 本地草稿同步）、observation-detail（多图/评论/评分/质量提示）、map（全国→省份→物种简化列表）、notification、profile、suggestion（新物种建议）
- src/components/：ObservationCard、RegionPicker（省市区三级选择，不获取定位）、DynamicPlantForm（教师动态字段渲染）
- src/api/：request（JWT 注入/401 退出/统一提示/uploadFile/图片压缩）、plant（业务 API）
- src/stores/：auth（Pinia；仅存 token 与必要 profile，不存明文密码）
- src/utils/：storage（token + 本地待同步草稿，V4 §11/§14）、media（URL 解析/占位图）
- src/types/models.ts 类型集中

## 本地开发
```bash
cd student-app
npm install
npm run dev:h5        # H5 预览（后端 API 默认 http://127.0.0.1:8080）
# 真机/正式环境请设置环境变量或在 src/config.ts 调整 API_BASE/MEDIA_ORIGIN
```

## H5 构建（已验证）
```bash
npm run build:h5
# 产物 dist/build/h5
```

## Android APK 打包步骤
1. 用 HBuilderX 打开 student-app 目录（manifest.json 填写 AppID：正式打包需注册 DCloud AppID；云打包可使用测试 AppID）。
2. 权限核对（manifest.json → App 模块/权限）：
   - 保留：相机、相册（uni.chooseImage / uni.uploadFile 需要）
   - 不要申请定位/位置权限（系统不读取学生位置；请勿勾选定位相关权限）
   - 网络权限默认。
3. 发行 → 原生 App-云打包（本地打包需 Android Studio + SDK），选择 Android 证书（正式版需自有证书；测试可公共测试证书）。
4. 产出 APK → 真机安装：
   - 验收路径：登录 → 首页 dashboard → 采集：拍摄/相册 3 张 → 选省市区 → 保存草稿 → 我的植物“待完善”→ 补充植物/描述 → 提交 → 教师 Web 审核 → 展廊/详情/评论/评分。
5. 发布前再次确认 src/config.ts 的 API_BASE 指向生产 HTTPS 域名。

## 本地草稿/弱网策略（V4 §11/§13/§14）
- 采集页保存失败（无网）→ 自动写入本地草稿（照片本地路径 + payload），提示“已存本地草稿”；
- “我的植物 → 本地草稿”页签显示待同步条目，点击“联网同步”→ 先建服务端草稿 → 逐张上传 → 成功后清除本地副本；
- 上传中断时服务端草稿保留，可回到“补充观察”页重传图片。
- 图片上传前 uni.compressImage（quality 75）控制体积；服务端仍做白名单/魔数/尺寸上限二次校验。

## 说明与限制
- 本仓库已验证 H5 构建；Android APK 需在安装 HBuilderX/Android SDK 的环境执行，本开发环境不具备该工具链。
- 通知采用应用内消息列表（后端通知 API），第一版不做系统级 Push（V4 §18）。

## 7. 手机上快速体验（局域网，无需打包）

同一 WiFi 下把已构建的 H5 部署到 nginx，手机浏览器直接打开即可（含拍照上传、登录、审核全流程）：

    # 1) 构建（H5 同源相对路径，资源前缀 /jingxuan/app/）
    cd student-app && npm run build:h5

    # 2) 部署到 nginx 静态目录
    robocopy dist\build\h5 ..\frontend\public\app /MIR
    cd .. && docker compose up -d --build nginx

    # 3) 手机浏览器访问（把 IP 换成电脑的局域网地址）
    http://<电脑局域网IP>/jingxuan/app/

登录演示账号 stu1 / admin123 即可采集上传；浏览器菜单「添加到主屏幕」可获得类 App 图标体验。
接口与页面同源（都经 nginx），因此无需任何额外配置。

## 8. 打包可安装的 Android APK

仓库内置 Capacitor 壳工程（student-app/android），产出可安装 APK 有两种方式：

### 方式 A：Capacitor 壳（无需 DCloud 账号，本地或 CI 可自动构建）

    cd student-app
    npm i            # 已含 @capacitor/core|cli|android 6.x
    npx cap sync android
    cd android
    # Windows：先准备 Android SDK（cmdline-tools + platform-tools + platforms;android-34 + build-tools;34.0.0）
    # 并在 android/local.properties 写入 sdk.dir=<SDK 路径>
    gradlew.bat assembleDebug        # 产物 app/build/outputs/apk/debug/app-debug.apk

壳默认以「实时模式」加载局域网 H5（capacitor.config.ts 的 server.url），因此改完 H5 手机端刷新即生效；
若需离线内置模式，见 capacitor.config.ts 注释（设置 UNI_PUBLIC_PATH=/ 与 VITE_API_BASE=http://<电脑IP> 重新构建并去掉 server.url）。
Android 明文 HTTP 已在 AndroidManifest.xml 打开 usesCleartextTraffic，相机与相册权限已声明。

### 方式 B：uni-app 原生云打包（需 DCloud 账号，产出正式包）

1. 安装 HBuilderX，导入 student-app 目录；
2. manifest.json 填写 appid（DCloud 申请）与版本号；
3. 发行 → 原生App-云打包 → Android，选择自有证书或 DCloud 公共证书；
4. 打包完成后下载 APK 安装即可（同样需在 manifest 中允许明文 HTTP 访问局域网后端）。

### 安装提示（华为 P40 / HarmonyOS）

- 允许安装未知来源应用：设置 → 安全 → 更多安全设置 → 安装外部来源应用 → 允许浏览器/文件管理；
- 手机与电脑需处于同一 WiFi；后端地址为电脑局域网 IP（示例 http://10.120.46.175）；
- 若打不开，检查 Windows 防火墙是否允许 80 端口入站。
