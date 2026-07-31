FROM node:22-alpine AS build
WORKDIR /app
COPY frontend-angular/package*.json ./
RUN npm ci
COPY frontend-angular/ .
RUN npm run build

FROM nginx:1.27-alpine
COPY frontend-angular/nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist/defect-prediction/browser /usr/share/nginx/html
EXPOSE 80
