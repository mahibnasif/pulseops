ARG APP_VERSION=0.0.1
ARG BUILD_DATE=unknown
ARG SOURCE_URL=https://github.com/mahibnasif/pulseops
ARG VCS_REF=unknown

FROM node:24-alpine AS build

WORKDIR /workspace

COPY package.json package-lock.json ./
RUN npm ci

COPY . .
RUN npm run build

FROM nginx:1.29-alpine AS runtime

ARG APP_VERSION
ARG BUILD_DATE
ARG SOURCE_URL
ARG VCS_REF

LABEL org.opencontainers.image.title="PulseOps frontend" \
      org.opencontainers.image.description="PulseOps React web application" \
      org.opencontainers.image.version="${APP_VERSION}" \
      org.opencontainers.image.created="${BUILD_DATE}" \
      org.opencontainers.image.revision="${VCS_REF}" \
      org.opencontainers.image.source="${SOURCE_URL}"

COPY --from=build --chown=nginx:nginx /workspace/dist /usr/share/nginx/html

USER nginx

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=10s --retries=3 \
    CMD wget -q --spider http://127.0.0.1:8080/healthz || exit 1

CMD ["nginx", "-g", "daemon off;"]

FROM runtime AS development

COPY nginx.conf /etc/nginx/nginx.conf

FROM runtime AS production

COPY nginx.production.conf /etc/nginx/nginx.conf
