FROM node:22-alpine AS build
WORKDIR /app
RUN corepack enable
COPY package.json pnpm-lock.yaml ./
RUN pnpm install --frozen-lockfile
COPY . .
ARG VITE_API_BASE_URL=/api
ARG VITE_KAKAO_MAP_KEY=
ARG VITE_NAVER_CLIENT_ID=
ARG VITE_KAKAO_CLIENT_ID=
ENV VITE_API_BASE_URL=$VITE_API_BASE_URL \
    VITE_KAKAO_MAP_KEY=$VITE_KAKAO_MAP_KEY \
    VITE_NAVER_CLIENT_ID=$VITE_NAVER_CLIENT_ID \
    VITE_KAKAO_CLIENT_ID=$VITE_KAKAO_CLIENT_ID
RUN pnpm build

FROM nginx:1.27-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
