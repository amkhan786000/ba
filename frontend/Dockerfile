# ---- Build stage: compile the Angular app ----
FROM node:20-alpine AS build
WORKDIR /app
COPY package*.json ./
RUN npm install
COPY . .
RUN npm run build -- --configuration production

# ---- Serve stage: nginx static hosting + API proxy ----
FROM nginx:1.27-alpine
COPY --from=build /app/dist/rahbar-frontend/browser /usr/share/nginx/html
# nginx runs as a non-root user: make sure every file is readable, whatever permissions the source files had
RUN chmod -R a+rX /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
