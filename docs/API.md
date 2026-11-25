# Athlex — Documentação da API (cURL)

Base: **gateway** `http://localhost:8080` (único ponto de entrada). Corpo/resp. em JSON. Erros: `{"message": "..."}`.
Os exemplos usam Bash/Git Bash. No PowerShell use `curl.exe` e coloque o JSON em arquivo (`-d "@corpo.json"`).

```bash
G=http://localhost:8080
H='Content-Type: application/json'
```

Perfis (`type` no JWT): `0` administrador · `1` comprador · `2` vendedor. Rotas `/ws/**` exigem `Authorization: Bearer <token>`.

| Código | Quando |
|---|---|
| 400 | validação (campo inválido, estoque insuficiente, carrinho vazio) |
| 401 | sem token / token inválido ou expirado / credencial errada |
| 403 | autenticado, mas sem permissão (ex.: comprador cadastrando produto) |
| 404 | recurso inexistente |
| 409 | e-mail já cadastrado |
| 503 | `product-api` indisponível (circuit breaker / timeout) |

## 1. Autenticação (auth-api, pública)

### POST /auth/signup — cria conta
`profile`: `COMPRADOR` (padrão) ou `VENDEDOR`. Nunca cria administrador.
```bash
curl -s -X POST $G/auth/signup -H "$H" -d '{"name":"Loja Atlas","email":"loja@athlex.dev","phone":"51999990000","password":"Senha@123","profile":"VENDEDOR"}'
```
`201` → `{"id":2,"name":"Loja Atlas","email":"loja@athlex.dev","phone":"51999990000","type":2}` · `409` e-mail duplicado · `400` dados inválidos.

### POST /auth/signin — login
```bash
curl -s -X POST $G/auth/signin -H "$H" -d '{"email":"loja@athlex.dev","password":"Senha@123"}'
```
`200` → `{"token":"<JWT>","user":{"id":2,"name":"Loja Atlas","email":"loja@athlex.dev","phone":"51999990000","type":2}}` · `401` credencial inválida.
```bash
TOKEN=<cole o token>      # vendedor (type 2)
```

### GET /ws/auth/me — usuário logado
```bash
curl -s $G/ws/auth/me -H "Authorization: Bearer $TOKEN"
```
`200` → dados do usuário · `401` sem token.

### GET /ws/auth/users — lista usuários (somente administrador)
```bash
curl -s $G/ws/auth/users -H "Authorization: Bearer $TOKEN"
```
`200` lista · `403` `{"message":"Usuário sem permissão para esta operação"}` para comprador/vendedor.

## 2. Produtos (product-api)

### GET /products — catálogo (público)
Filtros opcionais `busca` (texto) e `categoria`.
```bash
curl -s "$G/products?categoria=Bolas&busca=futsal"
```
`200` → `[{"id":"3","nome":"Bola de Futsal Oficial","descricao":"...","preco":149.9,"categoria":"Bolas","tamanhos":["Único"],"cores":["#FFFFFF","#F59E0B"],"imagem":"https://...","personalizavel":false,"estoque":40,"vendedorId":"1"}]`

### GET /products/{id} — detalhe (público)
```bash
curl -s $G/products/1
```
`200` produto · `404` inexistente.

### POST /ws/products — cadastra produto (vendedor/admin)
`imagem`: URL ou data URI base64. Dono = usuário do token.
```bash
curl -s -X POST $G/ws/products -H "$H" -H "Authorization: Bearer $TOKEN" -d '{"nome":"Bola Pro","descricao":"Bola de campo","preco":89.9,"categoria":"Bolas","tamanhos":["5"],"cores":["#FFFFFF"],"personalizavel":false,"estoque":10}'
```
`201` → produto com `"vendedorId":"2"` · `400` validação · `401` sem token · `403` comprador.

### GET /ws/products/mine — produtos do vendedor logado
```bash
curl -s $G/ws/products/mine -H "Authorization: Bearer $TOKEN"
```
`200` lista · `403` comprador.

### PUT /ws/products/{id} — altera (dono ou admin)
```bash
curl -s -X PUT $G/ws/products/9 -H "$H" -H "Authorization: Bearer $TOKEN" -d '{"nome":"Bola Pro","descricao":"Bola de campo","preco":79.9,"categoria":"Bolas","tamanhos":["5"],"cores":["#FFFFFF"],"personalizavel":false,"estoque":10}'
```
`200` produto atualizado · `403` `{"message":"Você não é o dono deste recurso"}` · `404`.

### DELETE /ws/products/{id} — remove (dono ou admin)
```bash
curl -s -X DELETE $G/ws/products/9 -H "Authorization: Bearer $TOKEN" -w "%{http_code}\n"
```
`204` · `403` não é o dono · `404`.

## 3. Pedidos (order-api)
Use o token de um **comprador** (`type 1`) para criar/consultar; vendedor para vendas.

### POST /ws/orders — cria pedido
O preço é **recalculado no servidor** (a partir do `product-api`; +R$ 10 por nome e +R$ 10 por número na personalização). Baixa o estoque; se algo falhar, devolve o que reservou.
`pagamento`: `CARTAO` | `PIX` | `BOLETO`. `personalizacao` (opcional): `{"modelo":"...","nome":"...","numero":"..."}`.
```bash
curl -s -X POST $G/ws/orders -H "$H" -H "Authorization: Bearer $TOKEN_COMPRADOR" -d '{"endereco":{"cep":"90000000","rua":"Rua A","numero":"10","cidade":"Porto Alegre","uf":"RS"},"pagamento":"PIX","itens":[{"produtoId":9,"quantidade":2,"tamanho":"5","cor":"#FFFFFF"}]}'
```
`201` → `{"id":"PED-1001","data":"2026-11-24T22:49:13Z","total":159.8,"status":"AGUARDANDO_PAGAMENTO","itens":[{"nome":"Bola Pro","quantidade":2,"preco":79.9}]}`
`400` CEP/endereço/pagamento inválido, carrinho vazio, estoque insuficiente, produto inexistente · `401` · `503` serviço de produtos indisponível.

### GET /ws/orders — histórico do comprador (admin vê todos)
```bash
curl -s $G/ws/orders -H "Authorization: Bearer $TOKEN_COMPRADOR"
```
`200` lista de pedidos.

### GET /ws/orders/{id} — detalhe (dono ou admin)
```bash
curl -s $G/ws/orders/1 -H "Authorization: Bearer $TOKEN_COMPRADOR"
```
`200` · `403` pedido de outro usuário · `404`.

### GET /ws/orders/sales — histórico de vendas (vendedor/admin)
Apenas os itens dos produtos do vendedor.
```bash
curl -s $G/ws/orders/sales -H "Authorization: Bearer $TOKEN"
```
`200` → `[{"pedidoId":"PED-1001","data":"...","status":"AGUARDANDO_PAGAMENTO","subtotal":159.8,"itens":[{"nome":"Bola Pro","quantidade":2,"preco":79.9}]}]` · `403` comprador.

### GET /ws/orders/sales/summary — painel do vendedor
```bash
curl -s $G/ws/orders/sales/summary -H "Authorization: Bearer $TOKEN"
```
`200` → `{"vendas":1,"faturamento":159.8}` · `403` comprador.

## 4. Interno (não exposto no gateway)
Chamado só pelo `order-api` → `product-api` (rede interna). Pelo gateway responde `404`.

| Endpoint | Função |
|---|---|
| `POST /internal/products/{id}/reserve` `{"quantity":n}` | baixa estoque de forma atômica (`409` se insuficiente) |
| `POST /internal/products/{id}/release` `{"quantity":n}` | devolve estoque (compensação) |

```bash
curl -s -X POST $G/internal/products/1/reserve   # 404 pelo gateway (correto)
```

## 5. Operação (cada serviço, não passa pelo gateway)
| Endpoint | Porta | Uso |
|---|---|---|
| `GET /actuator/health` | 8080 gateway · 8200 auth · 8100 product · 8400 order | saúde (Consul usa para registrar) |
| `GET /actuator/prometheus` | idem | métricas para o Prometheus |
```bash
curl -s http://localhost:8200/actuator/health      # {"groups":["liveness","readiness"],"status":"UP"}
```
