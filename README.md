# Athlex Backend — Microservices Java (Spring Boot 4 / Spring Cloud)

API do e-commerce Athlex (app React Native). Documentação de cada endpoint com cURL: **[docs/API.md](docs/API.md)**.

```
                      ┌────────────── Consul (discovery + config central KV) ──────────────┐
App ─► gateway-api :8080 ─(lb://)─► auth-api    :8200 ─► postgres-auth    (db_user)
        valida JWT                 ├► product-api :8100 ─► postgres-product (db_product)
        propaga X-User-*           └► order-api   :8400 ─► postgres-order   (db_order)
                                         └─ HTTP (timeout + retry + circuit breaker) ─► product-api
Observabilidade:  Prometheus (métricas) · Zipkin (traces) · Loki (logs) · Grafana (painel)
```

Java 21 · Spring Boot 4.1.0 · Spring Cloud 2025.1.4 (Gateway WebFlux, Consul Config + Discovery) · PostgreSQL 16 · Flyway · Resilience4j · Micrometer · JJWT 0.13.

## Requisitos do trabalho × onde estão
| Requisito | Implementação |
|---|---|
| Microservices independentes | `auth-api`, `product-api`, `order-api` (+ `gateway-api`) |
| Database per Service | um PostgreSQL/volume por serviço no compose (`db_user`, `db_product`, `db_order`); sem FK entre bancos |
| Migrações de banco | **Flyway** em `src/main/resources/db/migration/V1__*.sql`; `ddl-auto=validate` |
| Config centralizada, sem segredos no código | **Consul KV** `config/<serviço>/...`, gravado por `infra/consul_kv.sh` a partir do `.env` (não versionado) |
| Service discovery / gateway | Consul + Spring Cloud Gateway (`lb://`), JWT validado no gateway |
| Comunicação entre serviços | `order-api` → `product-api` (preço/estoque), via `ProductClient` |
| Resiliência | Timeout (conexão 1 s, leitura 3 s), Retry (2x, só GET), Circuit Breaker (Resilience4j) → 503 amigável |
| Observabilidade | métricas `/actuator/prometheus`, traces (Zipkin), logs com `traceId` enviados ao Loki, dashboard no Grafana |
| Docker | `Dockerfile` por serviço (multi-stage, usuário não-root) + `docker-compose.yml` completo |
| Documentação de endpoints | [docs/API.md](docs/API.md) (cURL) |

## Subir com Docker (recomendado)
```bash
cd backend
cp .env.example .env        # edite: DB_PASSWORD, JWT_SECRET (32+ caracteres), ADMIN_PASSWORD, GRAFANA_PASSWORD
docker compose up -d --build
docker compose ps           # aguarde tudo "healthy" (1ª vez: alguns minutos, baixa imagens e compila)
```
| Serviço | URL |
|---|---|
| **Gateway (use no app)** | http://localhost:8080 |
| Consul (serviços + KV) | http://localhost:8500 |
| Zipkin (traces) | http://localhost:9411 |
| Prometheus | http://localhost:9090 |
| Grafana (admin / `GRAFANA_PASSWORD`) | http://localhost:3000 → pasta *Athlex* |

Parar: `docker compose down` (mantém dados) · `docker compose down -v` (apaga bancos).
Ordem de subida garantida por `depends_on` + healthchecks: Consul → `consul-init` (grava KV) → bancos → serviços → gateway.

## Subir sem Docker (Windows, Consul e PostgreSQL instalados)
1. `cp .env.example .env` e edite as senhas (use a senha do seu PostgreSQL em `DB_PASSWORD`).
2. Crie os bancos vazios `db_user`, `db_product`, `db_order` (`infra/init-db.sql`).
3. Rode `infra\start_consul.bat` (inicia o Consul e grava a configuração central a partir do `.env`).
4. Inicie, nesta ordem (IntelliJ ou `./mvnw spring-boot:run`): `auth-api`, `product-api`, `order-api`, `gateway-api`.
5. Confira em http://localhost:8500. O app usa **http://localhost:8080**.

Observabilidade é opcional nesse modo (Zipkin em `localhost:9411` se existir; sem ele os serviços funcionam normalmente).

## Configuração e segredos
- `application.properties` tem **apenas** nome/porta e o endereço do Consul (`CONSUL_HOST`, `CONSUL_PORT`).
- Datasource (URL/usuário/senha), `jwt.secret` e `admin.password` vêm **somente** do Consul KV. Sem eles o serviço não sobe (falha rápida).
- `jwt.secret` é a mesma chave no `auth-api` (assina) e no `gateway-api` (valida).
- Admin `admin@admin.dev` é criado na 1ª execução com a senha `ADMIN_PASSWORD`; sem ela, não é criado.
- Consul em modo dev guarda o KV em memória: o `consul-init` regrava a cada `up` (no Windows, rode o `.bat` de novo se reiniciar o Consul).

## Migrações (Flyway)
`V1__create_*.sql` em cada serviço, idempotentes (`IF NOT EXISTS`). `baseline-on-migrate` permite adotar bancos que já existiam. Mudança de schema = **novo arquivo** `V2__...sql`; nunca edite um já aplicado. Hibernate só **valida** o mapeamento (`ddl-auto=validate`).

## Resiliência (order-api → product-api)
| Mecanismo | Configuração | Observação |
|---|---|---|
| Timeout | conexão 1000 ms, leitura 3000 ms (`product-client.*`) | evita thread presa |
| Retry | até 2 novas tentativas, 200 ms, só `GET` e só falha de rede/5xx | `POST` de estoque **não** repete (evita baixar duas vezes) |
| Circuit Breaker | janela 10, abre com ≥50% de falha (mín. 5), aberto 10 s, half-open 3 | aberto → 503 imediato; 4xx de negócio não contam |
| Compensação | falha ao reservar o 2º item → devolve o 1º | consistência sem transação distribuída |

Teste manual: derrube o `product-api` e faça `POST /ws/orders` → `503 {"message":"Serviço de produtos indisponível..."}`; após 5 falhas a resposta vira imediata (circuito aberto) e `resilience4j_circuitbreaker_state{state="open"}` vai a 1 no Prometheus.

## Observabilidade
- **Métricas:** `GET :<porta>/actuator/prometheus` (HTTP, JVM, circuit breaker). Prometheus coleta a cada 10 s; painel *Athlex — Microservices* no Grafana.
- **Traces:** Micrometer Tracing (Brave) → Zipkin. Um pedido aparece como um único trace `gateway-api → order-api → product-api`.
- **Logs:** cada linha tem `[serviço,traceId,spanId]`; com o perfil `docker` seguem para o Loki (consulta no Grafana → Explore → Loki: `{app="order-api"}`). Copie o `traceId` do log e procure no Zipkin.

## Perfis (claim `type` do JWT)
`0` administrador · `1` **comprador** · `2` **vendedor**. O cadastro público escolhe `COMPRADOR` (padrão) ou `VENDEDOR` e **nunca** cria administrador. Autenticação no gateway (`AuthFilter`, protege `/ws/**`); autorização nos microservices (`AuthorizationService`). O filtro remove `X-User-*` enviados pelo cliente e os recria a partir do JWT.

## Testes
| Módulo | Classes | Casos |
|---|---|---|
| gateway-api | `AuthFilterTest` | 4 |
| auth-api | `AuthServiceTest`, `JwtServiceTest` | 9 |
| product-api | `AuthorizationServiceTest`, `ProductServiceTest` (Mockito) | 16 |
| order-api | `PricingCalculatorTest`, `AuthorizationServiceTest`, `ProductClientResilienceTest` (servidor falso: retry, timeout, 4xx, POST sem retry, circuito aberto) | 14 |

`./mvnw test` em cada módulo (43 casos). Ponta a ponta (ambiente no ar): `ADMIN_PASSWORD=... python infra/roteiro_api.py` — 29 verificações pelo gateway. O app tem teste de integração do cliente HTTP: `EXPO_PUBLIC_API_URL=http://localhost:8080 npm run test:integration` (no repositório athlex-mobile).

## Problemas comuns
- **`config data resource 'consul:' does not exist`:** Consul parado. Inicie-o e rode o `.bat`/`consul-init`.
- **`Could not resolve placeholder 'jwt.secret'` / datasource sem URL:** a configuração central não foi gravada (rode `start_consul.bat`).
- **Serviço responde direto, mas 500 pelo gateway:** registro no Consul com hostname inválido; local usa `SERVICE_IP=127.0.0.1` (padrão), Docker usa o IP do container.
- **Porta 8300 em uso:** é do Consul; o `order-api` usa 8400.
- **`Schema-validation: missing table` / banco de outro projeto:** recrie `db_user`, `db_product`, `db_order` vazios.
- **Erros de Flyway ao trocar de máquina:** nunca altere `V1`; crie `V2`.

## Decisões e limitações (citar no relatório)
- Em produção os serviços internos não devem ser publicados: só o gateway recebe tráfego externo (no compose, só o gateway e as ferramentas de operação têm porta publicada).
- Um PostgreSQL por serviço aumenta o consumo de memória, mas deixa a separação de dados explícita (Database per Service).
- Consul em modo dev (KV volátil, regravado no `up`); em produção usar Consul com ACL/volume ou Vault para segredos.
- Imagens de produto ficam no banco como URL ou data URI base64; em produção usar storage de objetos.
- Sem broker de mensagens: a consistência de estoque usa chamada síncrona + compensação (adequado ao escopo; evolução natural: Saga com eventos).
- Dados iniciais (admin, 8 produtos) são criados por *seed* em Java na 1ª execução, não por migração.
