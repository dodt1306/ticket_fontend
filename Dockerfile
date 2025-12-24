# ======================
# Stage 1: Build
# ======================
FROM node:20-alpine AS build

WORKDIR /app

# Copy dependency files trước (để cache)
COPY package*.json ./
RUN npm install

# Copy toàn bộ source
COPY . .

# Build production
RUN npm run build


# ======================
# Stage 2: Run (Nginx)
# ======================
FROM nginx:alpine

# Xóa config mặc định
RUN rm /etc/nginx/conf.d/default.conf

# Copy nginx config
COPY nginx.conf /etc/nginx/conf.d/default.conf

# Copy build output từ stage 1
COPY --from=build /app/dist /usr/share/nginx/html

EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
