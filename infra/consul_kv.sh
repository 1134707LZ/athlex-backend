#!/bin/sh
# Grava no Consul KV a configuração centralizada de cada microservice (config/<serviço>/...).
# Nenhum segredo é versionado: DB_USER, DB_PASSWORD e JWT_SECRET vêm do ambiente / arquivo .env.
# Uso local:   set -a; . ./.env; set +a; CONSUL_HTTP_ADDR=localhost:8500 sh infra/consul_kv.sh
# No Docker:   executado pelo container "consul-init" (docker-compose.yml).
set -eu
: "${DB_USER:?defina DB_USER}" "${DB_PASSWORD:?defina DB_PASSWORD}" "${JWT_SECRET:?defina JWT_SECRET}" "${ADMIN_PASSWORD:?defina ADMIN_PASSWORD}"
DB_HOST="${DB_HOST:-localhost}"
AUTH_DB_HOST="${AUTH_DB_HOST:-$DB_HOST}"
PRODUCT_DB_HOST="${PRODUCT_DB_HOST:-$DB_HOST}"
ORDER_DB_HOST="${ORDER_DB_HOST:-$DB_HOST}"
put() { consul kv put "$1" "$2" >/dev/null && echo "ok $1"; }

for svc in auth product order; do
  case $svc in auth) host=$AUTH_DB_HOST; db=db_user;; product) host=$PRODUCT_DB_HOST; db=db_product;; order) host=$ORDER_DB_HOST; db=db_order;; esac
  put "config/$svc-api/spring.datasource.url"      "jdbc:postgresql://$host:5432/$db"
  put "config/$svc-api/spring.datasource.username" "$DB_USER"
  put "config/$svc-api/spring.datasource.password" "$DB_PASSWORD"
done
put config/auth-api/jwt.secret    "$JWT_SECRET"
put config/auth-api/admin.password "$ADMIN_PASSWORD"
put config/gateway-api/jwt.secret "$JWT_SECRET"
