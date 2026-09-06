FROM node:20-alpine AS deps
WORKDIR /app
COPY package.json package-lock.json* ./
RUN if [ -f package-lock.json ]; then npm ci --omit=dev; else npm install --omit=dev; fi \
 && mkdir -p /app/node_modules

FROM node:20-alpine
WORKDIR /app
ENV NODE_ENV=production
COPY --from=deps /app/node_modules ./node_modules
COPY package.json ./
COPY src ./src
# Diretorio de runtime das credenciais: precisa pertencer ao usuario node,
# pois o servico executa como nao-root e cria os arquivos de sessao no boot.
RUN mkdir -p /app/.sessions && chown node:node /app/.sessions
USER node
EXPOSE 3001
CMD ["node", "src/server.js"]
