# Payment API

API REST de pagamentos desenvolvida com Java 21 e Spring Boot. O projeto simula um gateway de pagamentos que recebe requisições de cobrança (cartão ou PIX), persiste no banco e processa de forma assíncrona via RabbitMQ.

## Tecnologias

- Java 21
- Spring Boot 3.2
- PostgreSQL
- RabbitMQ
- Docker / Docker Compose
- Swagger UI

## Como rodar localmente

**Pré-requisitos:** Docker instalado

```bash
# 1. Subir banco e fila
docker compose up postgres rabbitmq -d

# 2. Rodar a aplicação
./mvnw spring-boot:run
```

Ou subir tudo junto:

```bash
docker compose up -d
```

## Endpoints

| Método | Rota | Descrição |
|--------|------|-----------|
| POST | /api/v1/payments | Criar pagamento |
| GET | /api/v1/payments/{id} | Buscar por ID |
| GET | /api/v1/payments?merchantId= | Listar por merchant |
| PATCH | /api/v1/payments/{id}/cancel | Cancelar pagamento pendente |
| POST | /api/v1/payments/{id}/retry | Reprocessar pagamento recusado |

Documentação completa: `http://localhost:8080/swagger-ui.html`

## Cartões para teste

| Final do cartão | Resultado |
|-----------------|-----------|
| `4000` | Recusado |
| `5200` | Recusado |
| `0000` | Recusado |
| qualquer outro | Aprovado (sujeito à taxa de falha configurada) |

## Variáveis de ambiente

Copie o `.env.example` e ajuste conforme necessário:

```bash
cp .env.example .env
```

A variável `ACQUIRER_FAILURE_RATE` controla a taxa de falha simulada do adquirente (entre `0.0` e `1.0`).

## Testes

```bash
./mvnw test
```

## Serviços disponíveis

| Serviço | URL |
|---------|-----|
| API | http://localhost:8080 |
| Swagger UI | http://localhost:8080/swagger-ui.html |
| RabbitMQ Management | http://localhost:15672 |
