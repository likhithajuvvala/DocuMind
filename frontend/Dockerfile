FROM node:20-alpine AS deps
WORKDIR /app
COPY package.json package-lock.json* ./
RUN npm install

FROM node:20-alpine AS build
WORKDIR /app
COPY --from=deps /app/node_modules node_modules
COPY . .
RUN npm run build

FROM node:20-alpine AS runtime
WORKDIR /app
ENV NODE_ENV=production
# Docker sets $HOSTNAME to the container ID by default, and Next's standalone server.js binds to
# process.env.HOSTNAME if set — it then tries to resolve that container ID via DNS instead of
# just listening on all interfaces, which fails and crashes the server on boot. Force it to bind
# broadly instead.
ENV HOSTNAME=0.0.0.0
RUN addgroup -S documind && adduser -S documind -G documind
COPY --from=build /app/public public
COPY --from=build /app/.next/standalone ./
COPY --from=build /app/.next/static .next/static
USER documind
EXPOSE 3000
CMD ["node", "server.js"]
