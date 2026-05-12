FROM node:24-alpine

WORKDIR /app
COPY --chown=node:node package.json server.js ./

USER node
EXPOSE 8090
CMD ["node", "server.js"]
