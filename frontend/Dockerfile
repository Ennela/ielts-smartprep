# Stage 1: Build
FROM node:20-alpine AS build
WORKDIR /app
COPY package*.json ./
RUN npm ci
COPY . .

# Vite inlines import.meta.env.* at build time, so anything the browser needs has to be
# present as an environment variable during `npm run build` — not at container start.
# Without these two args the published image could never carry a Sentry DSN, which is why
# frontend error reporting was silently dead in every Docker build.
#
# VITE_API_URL defaults to the same-origin path that nginx proxies to the backend. Leave it
# alone unless the API is served from a different host; an absolute URL turns every call
# cross-origin and drags CORS and cookie SameSite rules into play.
ARG VITE_API_URL=/api/v1
ARG VITE_SENTRY_DSN=
ENV VITE_API_URL=$VITE_API_URL
ENV VITE_SENTRY_DSN=$VITE_SENTRY_DSN

RUN npm run build

# Stage 2: Serve
FROM nginx:alpine
COPY --from=build /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
