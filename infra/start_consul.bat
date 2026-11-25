@echo off
REM Windows (Consul e PostgreSQL instalados localmente, sem Docker).
REM 1) copie ..\.env.example para ..\.env e troque as senhas
REM 2) crie os bancos db_user, db_product e db_order
REM 3) execute este arquivo (ele inicia o Consul e grava a configuracao central)
setlocal
if not exist "%~dp0..\.env" (
  echo Crie backend\.env a partir de .env.example
  exit /b 1
)
for /f "usebackq eol=# tokens=1,* delims==" %%a in ("%~dp0..\.env") do set "%%a=%%b"

start "consul" consul agent -dev
timeout /t 5 >nul

consul kv put config/auth-api/spring.datasource.url "jdbc:postgresql://localhost:5432/db_user"
consul kv put config/auth-api/spring.datasource.username "%DB_USER%"
consul kv put config/auth-api/spring.datasource.password "%DB_PASSWORD%"
consul kv put config/auth-api/jwt.secret "%JWT_SECRET%"
consul kv put config/auth-api/admin.password "%ADMIN_PASSWORD%"

consul kv put config/product-api/spring.datasource.url "jdbc:postgresql://localhost:5432/db_product"
consul kv put config/product-api/spring.datasource.username "%DB_USER%"
consul kv put config/product-api/spring.datasource.password "%DB_PASSWORD%"

consul kv put config/order-api/spring.datasource.url "jdbc:postgresql://localhost:5432/db_order"
consul kv put config/order-api/spring.datasource.username "%DB_USER%"
consul kv put config/order-api/spring.datasource.password "%DB_PASSWORD%"

REM gateway-api valida o JWT com a MESMA chave do auth-api
consul kv put config/gateway-api/jwt.secret "%JWT_SECRET%"
endlocal
