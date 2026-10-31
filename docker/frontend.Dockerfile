# Build context is the repo root (so the nginx config in docker/ is reachable).
# NGINX_CONF picks the server block: docker/nginx.local.conf (default) or docker/nginx.prod.conf.
FROM node:22-alpine AS build
WORKDIR /app
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci
# Theme tests (type-checked by tsc -b) import these shared files from outside frontend/.
COPY backend/src/main/resources/themes/theme-presets.json /backend/src/main/resources/themes/theme-presets.json
COPY specs/039-user-themes/contracts/contrast-vectors.json /specs/039-user-themes/contracts/contrast-vectors.json
COPY frontend/ .
RUN npm run build

FROM nginx:alpine
ARG NGINX_CONF=docker/nginx.local.conf
COPY docker/nginx.limits.conf /etc/nginx/conf.d/00-limits.conf
COPY docker/nginx.locations.conf /etc/nginx/hls-locations.conf
COPY ${NGINX_CONF} /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 80
