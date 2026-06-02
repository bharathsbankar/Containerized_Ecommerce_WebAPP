# =========================================================================
# WEB FRONTEND DOCKERFILE
# =========================================================================
FROM node:18-alpine
WORKDIR /app

# Install dependencies (cached layers)
COPY package.json ./
RUN npm install --production

# Copy source code
COPY server.js ./
COPY views ./views
COPY public ./public

EXPOSE 5000

ENV PORT=5000
ENV CATALOG_SERVICE_URL=http://catalog-service:8081/api/products

CMD ["npm", "start"]
