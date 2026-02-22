# Stage 1: Build
FROM node:20-alpine AS builder
WORKDIR /app
ARG VITE_GA_MEASUREMENT_ID
ENV VITE_GA_MEASUREMENT_ID=${VITE_GA_MEASUREMENT_ID}
COPY package*.json ./
RUN npm ci
COPY . .
RUN npm run build

# Stage 2: Serve
FROM nginx:1.25-alpine
COPY --from=builder /app/dist /usr/share/nginx/html
COPY nginx-default.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
