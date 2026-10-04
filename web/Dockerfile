FROM node:24-bookworm-slim AS build
WORKDIR /build
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
ARG NEXT_PUBLIC_API_BASE_URL
ARG ALLOW_LOCAL_API=false
# Public URL is bundled at build time. No default and no secret build arguments.
ENV NEXT_PUBLIC_API_BASE_URL=${NEXT_PUBLIC_API_BASE_URL} NEXT_TELEMETRY_DISABLED=1
RUN node -e 'const u=new URL(process.env.NEXT_PUBLIC_API_BASE_URL); const local=["127.0.0.1","localhost"].includes(u.hostname); if(u.username||u.password||u.search||u.hash||u.pathname!=="/"||!(u.protocol==="https:"&&!local||process.argv[1]==="true"&&local&&u.protocol==="http:"))throw Error("Supply an approved HTTPS backend origin; local verification requires ALLOW_LOCAL_API=true")' "$ALLOW_LOCAL_API"
RUN npm run build

FROM node:24-bookworm-slim AS runtime
WORKDIR /app
ENV NODE_ENV=production NEXT_TELEMETRY_DISABLED=1 PORT=3000 HOSTNAME=0.0.0.0
RUN groupadd --gid 1001 app && useradd --uid 1001 --gid app --no-create-home --shell /usr/sbin/nologin app
COPY --from=build --chown=app:app /build/.next/standalone ./
COPY --from=build --chown=app:app /build/.next/static ./.next/static
COPY --from=build --chown=app:app /build/public ./public
USER 1001:1001
EXPOSE 3000
STOPSIGNAL SIGTERM
CMD ["node", "server.js"]
