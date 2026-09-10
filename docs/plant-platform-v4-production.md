# 植物平台 v1.0 生产部署 · 监控告警 · 安全核对 · 隐私与无障碍（V4 上线基线）

> 配套发布说明：docs/plant-platform-v4-release-notes.md；基础运维/备份见 docs/plant-platform-operations.md。
> 覆盖《V4_学生App与正式上线》部署上线类要求：HTTPS/Nginx、环境分层与 Staging、监控告警、
> 安全核对、错误码与无障碍/Alt、隐私姓名策略、Redis 键规范。

## 1. 环境分层与部署拓扑

| 环境 | Profile | 说明 | 入口 |
|---|---|---|---|
| 开发 dev | SPRING_PROFILES_ACTIVE=dev | 本地 docker compose（127.0.0.1:8080 / 80），DeepSeek 失败放行 bypass | .env |
| 预演 staging | prod profile + 独立 compose project | 与生产同配置（DeepSeek reject），连 staging 数据，全量回归通过后再切生产 | docker compose -p jingxuan-staging up -d --build |
| 生产 prod | prod（compose 固定） | 仅 127.0.0.1 回环暴露，Nginx 对外 80/443 | 生产 .env（不入库） |

拓扑（docker-compose.yml）：mysql(内网, 回环 6380) → redis(内网 6379, 回环 6380) → backend(8080 仅回环, prod profile, /actuator/health 健康检查) → nginx(80 对外, /api 反代、/media/plants 静态图片)。
后端 healthcheck 30s 探测 /actuator/health 含 UP，start_period 40s；卷 ./data/plant-media 由 backend 读写、nginx 只读共享。

部署命令（release/plant-platform-1.0-rc1）：

    git pull && git checkout release/plant-platform-1.0-rc1
    cp .env.example .env        # 填写 DB_PASSWORD / JWT_SECRET / DEEPSEEK_API_KEY / MAIL_*
    docker compose up -d --build
    docker compose ps           # mysql/redis/backend/nginx 均 healthy
    curl -fsS http://127.0.0.1:8080/actuator/health    # 期望 {"status":"UP"}
    python -X utf8 scripts/plant-v4-smoke.py http://127.0.0.1:8080   # 上线门禁

## 2. HTTPS 与 Nginx（生产域名）

仓库两套 Nginx：
- 容器 nginx（frontend/nginx.conf）：80 对外；/api→backend、/jingxuan/ 前端静态、/media/plants/ 静态图片（长缓存）。
- 裸机 nginx（nginx-jingxuan.conf）：listen 80 + location /media/plants/ 反代 8080（或改 alias 指共享图片目录）。

启用 HTTPS：
1. 域名解析到服务器；certbot --nginx -d plant.example.edu.cn（或上传现有证书）。
2. 裸机：listen 443 ssl + 证书路径 + 80 端口 301 跳转 https；安全头（CSP/X-Frame-Options/X-Content-Type-Options）已随配置。
3. 容器化证书方案：443 映射挂载证书，或由裸机 nginx 反代容器 80（基线推荐，最简）。
4. 后端仅信任 JINGXUAN_SECURITY_TRUSTED_PROXY_CIDRS 白名单（默认回环；Docker 固定 172.31.250.2）。8080/3306/6379 严禁公网直连（compose 已绑定 127.0.0.1）。

## 3. Redis 键规范

统一前缀 jingxuan:（业务:用途:对象），全部带 TTL：

| 键模式 | 用途 | 出处 |
|---|---|---|
| jingxuan:verify:{email}:{roleId} | 注册邮箱验证码 | VerifyCodeService |
| jingxuan:verify:cooldown:{email}:{roleId} | 验证码发送冷却 | VerifyCodeService |
| jingxuan:token:blacklist:{jti} | 登出黑名单 | TokenBlacklistService |
| jingxuan:v2:refresh:token/family/user | v2 刷新令牌族 | RefreshToken |
| jingxuan:v2:rate-limit:{scope} | 接口限流计数 | 限流器 |
| jingxuan:v2:challenge:{id} | 登录挑战码 | 挑战服务 |
| jingxuan:login:fail:{username} | 登录失败计数，>=5 锁 10 分钟 | LoginThrottleService |
| jingxuan:plant:comment:{userId}:{epoch分钟} | 评论防刷 >20/分钟拒绝 | PlantCommunityService |

巡检：docker exec jingxuan-redis-1 redis-cli --scan --pattern jingxuan:* | wc -l   （shell 中注意引号包裹通配）

## 4. 监控与告警

Actuator 暴露 health,info,metrics,prometheus；health 探针启用且 show-details=never。
安全：/actuator/health(/**) 与 /actuator/info 匿名可读；其余 /actuator/** 仅 ADMIN（冒烟实测匿名 401）。

| 指标 | 含义 | 建议告警 |
|---|---|---|
| /actuator/health | 应用存活（容器已内置） | P1 持续不可用 1min |
| jvm 堆使用 | OOM 风险 | P2 >85% 持续 10min |
| http.server.requests 5xx / p95 | 错误率与慢请求 | P2 5xx>1% 持续 10min |
| hikaricp.connections.active/max | 连接池（prod max=20 实测） | P2 active/max>80% 持续 10min |
| mysql / redis 可达性 | 依赖健康 | P1 |
| /api/admin/plant-media/consistency | 图片一致性 | 每日 cron，不一致>0 出工单（只报告不删） |
| ./data/plant-media 磁盘 | 图片容量 | P3 >85% |
| 防爆破/评论防刷命中 | 攻击信号 | P3 单账号 1h 锁定>10 次 |
| 备份 manifest 新鲜度 | 备份作业 | P1 >24h 未备份 |

Prometheus 抓取：http://127.0.0.1:8080/actuator/prometheus（限制来源 IP）。

## 5. 安全加固核对

- [x] 登录防爆破：Redis 5 次/10 分钟锁定，提示明确（冒烟实测）。
- [x] 评论防刷：>20 条/分钟拒绝。
- [x] 图片炸弹：真实尺寸/像素超限拒收；魔数+扩展名白名单；本地落盘 644。
- [x] actuator 收敛 + RBAC（health/info 公开、其余 ADMIN）；健康详情关闭。
- [x] 越权防护：一切操作身份取 JWT；学生访问教师接口 403（E2E 实测）。
- [x] 内容安全：评论/建议过敏感词+DeepSeek，prod fallback=reject。
- [x] 公开数据仅 APPROVED 且 is_public=1；下线即移出公开展示。
- [ ] 生产 .env 真值（JWT_SECRET 32+ 随机、DB_PASSWORD、DEEPSEEK_API_KEY；compose 强校验）。
- [ ] HTTPS 全站 + HSTS；8080/3306/6379 不外泄。
- [ ] 依赖审计（npm audit 等）纳入 CI 门禁/发版前。

## 6. 隐私姓名策略

- 默认显示真实姓名：plant.privacy.show-real-name=true（application.yml 已接线，环境变量 PLANT_SHOW_REAL_NAME 可覆盖；.env.example 与 docker-compose 均已注入）。
- 开启掩码：plant.privacy.show-real-name=false。掩码规则（PlantPrivacy）：单字→*；两字→张*；三字以上首尾保留中间打星（欧阳娜娜→欧**娜）。
- 生效范围：展廊/详情/评论评分署名/审核列表姓名均经 displayName；教师内部审核可见真名。
- 隐私基线：不采集 GPS/定位（App 未申请权限）；地点=手选省市区（GB/T 2260）；照片公开须先过审。

## 7. 错误码与无障碍

业务统一 Result{code,message,data}：200 成功 / 400 业务与参数 / 401 未认证 / 403 越权 / 500 系统错误；HTTP 层另保留 404/405/429(限流)。前端 code===0||200 判成功。

无障碍（v1.0）：
- 图片替代文本：展廊卡片/首页/物种卡/地图抽屉/省面板/公开详情照片均带描述性 alt（如 银杏在浙江省的观察照片、植物照片（第 1 张）），加载失败回落 🌿 占位与提示（已过 typecheck/lint/vitest）。
- 按钮文字化、Element Plus 键盘可操作；语义文本优先图标。
- 地图 WebGL 不可用自动降级二维列表；不依赖悬停。
- 后续：lang=zh-CN、焦点样式、视频替代说明（1.1 迭代）。

## 8. 备份恢复演练（上线前至少演练一次）

数据=MySQL 全库 + ./data/plant-media + manifest：

    # 备份（脚本写入 manifest.json：时间/文件清单/校验和）
    python -X utf8 scripts/backup-plant.py backup/plant-media
    # 或手工：
    docker exec jingxuan-mysql-1 sh -c "MYSQL_PWD=$DB_PASSWORD mysqldump -ujingxuan jingxuan --single-transaction" > backup/database/jingxuan-$(date +%F).sql
    tar -C data -czf backup/plant-media/plant-media-$(date +%F).tar.gz plant-media

    # 恢复演练（新机）：
    python -X utf8 scripts/restore-plant.py --backup-dir backup/plant-media
    docker exec -i jingxuan-mysql-1 sh -c "MYSQL_PWD=$DB_PASSWORD mysql -ujingxuan jingxuan" < backup/database/jingxuan-*.sql
    # 验证：/consistency 扫描 + 随机图片 GET 200 + 展廊/地图抽查 + E2E/冒烟各一遍

生产节奏：每日全量（mysqldump + 图片增量），保留 14 天+月度归档，备份异地存放，manifest 新鲜度入监控（§4）。

## 9. 升级与回滚

- 升级：checkout 新 tag → docker compose up -d --build；Flyway V1-V12 只增不改自动迁移（clean-disabled）。
- 回滚：切回旧 tag 重建镜像；V12 纯增量（加列加表），旧代码兼容，不回退数据。
- 数据损坏回滚：用 §8 整库备份恢复（窗口数据丢失需公告）。

## 10. 上线前检查单

1. .env 生产真值完整（compose 强校验 DB_PASSWORD/JWT_SECRET）；2. HTTPS 证书与跳转；3. actuator 公网不可达；4. DEEPSEEK_API_KEY 已配且 fallback=reject；5. 账号 admin/tea1/stu1/stu2 就绪；6. 备份 cron+manifest 监控；7. 全量回归（发布说明 §3）；8. 隐私开关按校方要求；9. 地图边界合规（plant-map-data-source-and-compliance.md）；10. 试点就绪（plant-platform-pilot.md）。
