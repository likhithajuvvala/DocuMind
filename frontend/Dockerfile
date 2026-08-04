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
RUN addgroup -S documind && adduser -S documind -G documind
COPY --from=build /app/public public
COPY --from=build /app/.next/standalone ./
COPY --from=build /app/.next/static .next/static
USER documind
EXPOSE 3000
CMD ["node", "server.js"]
