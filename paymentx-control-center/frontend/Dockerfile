# ====================================================================
# ENGLISH
# --------------------------------------------------------------------
# Phase 6 - Docker build instructions for the Control Center frontend.
# What it does: a real multi-stage build - stage 1 runs the real `npm
# ci && npm run build` (tsc -b && vite build) this project's own
# package.json defines, producing the real static dist/ bundle; stage
# 2 serves that bundle from a minimal nginx:alpine image using
# nginx.conf's real SPA-fallback routing. Why it exists: the built
# frontend is static files - it needs no Node.js runtime in production,
# only a static file server, so shipping node_modules/ into a runtime
# image would be pure waste (and directly against this phase's "keep
# the source distribution lightweight" requirement). Build from THIS
# directory as context: `docker build -t
# paymentx-control-center-frontend paymentx-control-center/frontend`
# (matches how paymentx-control-center/docker-compose.yml invokes it).
# VITE_API_BASE_URL is a build-time arg (Vite inlines VITE_* vars at
# build time, not runtime) - set it to wherever the real backend
# container/host is reachable from a browser, not from inside this
# container.
#
# ====================================================================
# HINGLISH
# --------------------------------------------------------------------
# Phase 6 - Control Center frontend ke liye Docker build instructions.
# Ye kya karti hai: ek real multi-stage build - stage 1 real `npm ci
# && npm run build` (tsc -b && vite build) chalata hai jo is project
# ka apna package.json define karta hai, real static dist/ bundle
# produce karte hue; stage 2 us bundle ko ek minimal nginx:alpine
# image se serve karta hai, nginx.conf ke real SPA-fallback routing ka
# use karte hue. Ye dashboard me kyu hai: built frontend static files
# hain - production me isse koi Node.js runtime nahi chahiye, sirf ek
# static file server, isliye runtime image me node_modules/ bhejna
# pure waste hoga (aur directly is phase ke "source distribution
# lightweight rakho" requirement ke khilaaf). ISI directory ko context
# maan kar build karo: `docker build -t
# paymentx-control-center-frontend paymentx-control-center/frontend`
# (matches ki paymentx-control-center/docker-compose.yml ise kaise
# invoke karta hai). VITE_API_BASE_URL ek build-time arg hai (Vite
# VITE_* vars ko build time par hi inline karta hai, runtime par
# nahi) - ise wahi set karo jahan se real backend container/host ek
# browser se reachable ho, is container ke andar se nahi.
# ====================================================================

FROM node:20-alpine AS build
WORKDIR /app

COPY package.json package-lock.json ./
RUN npm ci

COPY . .
ARG VITE_API_BASE_URL=http://localhost:8089
ENV VITE_API_BASE_URL=$VITE_API_BASE_URL
RUN npm run build

FROM nginx:1.27-alpine
COPY --from=build /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80
