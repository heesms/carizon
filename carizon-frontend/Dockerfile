# Stage 1: Build
FROM node:20-alpine AS build
WORKDIR /app

COPY package*.json ./
RUN npm ci

COPY index.html ./
COPY tsconfig.json ./
COPY tsconfig.node.json ./
COPY vite.config.ts ./
COPY postcss.config.js ./
COPY tailwind.config.js ./

COPY public ./public
COPY src ./src

RUN npm run build

# Stage 2: Serve
FROM nginx:1.25-alpine
COPY --from=build /app/dist /usr/share/nginx/html
COPY nginx-default.conf /etc/nginx/conf.d/default.conf

EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
