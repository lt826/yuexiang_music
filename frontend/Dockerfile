# ============================================================
# 悦享音乐 前端 Dockerfile
# 阶段 1：npm build → 阶段 2：Nginx 托管
# ============================================================

# ---------- 阶段 1：构建 ----------
    FROM node:22-alpine AS builder
    WORKDIR /src
    
    # 利用缓存：先复制依赖描述文件
    COPY package.json package-lock.json ./
    RUN npm ci --silent
    
    # 复制源码并构建
    COPY . .
    RUN npm run build
    
    # ---------- 阶段 2：Nginx ----------
    FROM nginx:1.27-alpine
    
    # 覆盖默认配置：SPA 路由都走 index.html，API 代理到网关
    COPY --from=builder /src/dist /usr/share/nginx/html
    
    RUN echo 'server { \
        listen 80; \
        root /usr/share/nginx/html; \
        index index.html; \
        # 音频最大 50MB，对齐 file 服务 multipart 上限（nginx 默认 1MB 会直接 413）
        client_max_body_size 60m; \
        location / { try_files $uri $uri/ /index.html; } \
        location /api/ { proxy_pass http://yuexiang-gateway:8080; \
            proxy_read_timeout 120s; \
            proxy_send_timeout 120s; } \
        location /data/ { proxy_pass http://yuexiang-gateway:8080; } \
    }' > /etc/nginx/conf.d/default.conf
    
    EXPOSE 80