# ============================================================
# 菁选校园 — 前端 Docker 构建
# ============================================================
FROM node:24-alpine AS build
WORKDIR /build
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
RUN npm run build
FROM nginx:alpine AS runtime
COPY --from=build /build/dist /usr/share/nginx/html/jingxuan
# 地图边界 GeoJSON 从 public 显式拷贝（vite public 目录产物确定性保障）
COPY --from=build /build/public/geo /usr/share/nginx/html/jingxuan/geo
COPY nginx.conf /etc/nginx/conf.d/default.conf
RUN apk add --no-cache libcap \
    && mkdir -p /var/cache/nginx /var/run \
    && chown -R nginx:nginx /usr/share/nginx/html /var/cache/nginx /var/log/nginx /run \
    && setcap cap_net_bind_service=+ep /usr/sbin/nginx
EXPOSE 80
USER nginx
