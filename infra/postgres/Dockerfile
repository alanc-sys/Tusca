FROM postgres:15-alpine

COPY init/ /docker-entrypoint-initdb.d/
RUN sed -i 's/\r$//' /docker-entrypoint-initdb.d/*.sh && chmod +x /docker-entrypoint-initdb.d/*.sh
