# Build stage
FROM node:22-alpine AS build
WORKDIR /app
COPY package.json package-lock.json* ./
RUN npm install
COPY . .
ARG VITE_API_BASE=http://localhost:8080
ARG VITE_KEYCLOAK_BASE=http://localhost:8180
ENV VITE_API_BASE=$VITE_API_BASE
ENV VITE_KEYCLOAK_BASE=$VITE_KEYCLOAK_BASE
RUN npm run build

# Runtime
FROM nginx:1.27-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
