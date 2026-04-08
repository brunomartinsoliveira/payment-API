# ⚡ Payment Gateway

> Sistema de Gateway de Pagamentos com retentativas inteligentes — DryStorm

![Java](https://img.shields.io/badge/Java-21-ED8B00?style=flat&logo=java)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2.5-6DB33F?style=flat&logo=spring)
![RabbitMQ](https://img.shields.io/badge/RabbitMQ-3.13-FF6600?style=flat&logo=rabbitmq)
![Resilience4j](https://img.shields.io/badge/Resilience4j-2.2-4CAF50?style=flat)

---

## Padrões implementados

| Padrão | Implementação | Objetivo |
|--------|--------------|----------|
| **Outbox Pattern** | `outbox_events` + `OutboxPublisher` | Consistência entre banco e mensageria |
| **Circuit Breaker** | Resilience4j `@CircuitBreaker` | Protege contra falhas em cascata |
| **Exponential Backoff** | Filas RabbitMQ com TTL | Retentativas espaçadas inteligentemente |
| **Idempotência** | `idempotency_key` único | Evita cobranças duplicadas |
| **Bulkhead** | Resilience4j `@Bulkhead` | Limita concorrência ao adquirente |
| **Rate Limiter** | Resilience4j `@RateLimiter` | Protege contra burst de requisições |
| **Dead Letter Queue** | `payments.dlq` | Pagamentos sem solução após 5 tentativas |

---

## Como rodar

```bash
# 1. Subir a infra (PostgreSQL + RabbitMQ)
docker compose up postgres rabbitmq -d

# 2. Rodar a API
./mvnw spring-boot:run

# 3. Subir tudo junto (incluindo API no Docker)
docker compose up -d

# 4. Com PgAdmin e ferramentas extras
docker compose --profile tools up -d
```

| Serviço | URL |
|---------|-----|
| API | http://localhost:8080 |
| Swagger UI | http://localhost:8080/swagger-ui.html |
| RabbitMQ Management | http://localhost:15672 (guest/guest) |
| PgAdmin | http://localhost:5050 |

---

## Fluxo de pagamento

```
POST /api/v1/payments
        │
        ▼
┌─────────────────┐
│  PaymentService │  ← valida idempotência
│                 │
│  BEGIN TX       │
│  save Payment   │  ← status: PENDING
│  save OutboxEvt │  ← MESMA transação (Outbox Pattern)
│  COMMIT TX      │
└────────┬────────┘
         │
         ▼ (3s depois — OutboxPublisher)
┌─────────────────────┐
│  payments.exchange  │
│  routing: process   │
└────────┬────────────┘
         │
         ▼
┌─────────────────────────────────────┐
│  PaymentProcessor (consumer)        │
│                                     │
│  CircuitBreaker → Retry → Acquirer  │
│                                     │
│  ✅ Aprovado  → status: APPROVED    │
│  ❌ Recusado  → status: DECLINED    │
│  ⏳ Falha tmp → retry queue (TTL)   │
│  💀 Esgotou   → DLQ                 │
└─────────────────────────────────────┘
```

### Exponential Backoff — filas de retry

| Tentativa | Fila | Delay |
|-----------|------|-------|
| 1ª | `payments.retry.1s` | 1 segundo |
| 2ª | `payments.retry.5s` | 5 segundos |
| 3ª | `payments.retry.30s` | 30 segundos |
| 4ª | `payments.retry.2min` | 2 minutos |
| 5ª | `payments.retry.10min` | 10 minutos |
| > 5 | `payments.dlq` | Dead Letter |

---

## Endpoints

### Público
```
POST   /api/v1/payments              # Criar pagamento
GET    /api/v1/payments/{id}         # Consultar (com histórico de tentativas)
GET    /api/v1/payments?merchantId=  # Listar por merchant
PATCH  /api/v1/payments/{id}/cancel  # Cancelar
POST   /api/v1/payments/{id}/retry   # Forçar retry manual
```

### Monitoramento Circuit Breaker
```
GET    /api/v1/circuit-breaker       # Estado atual + métricas
POST   /api/v1/circuit-breaker/reset # Resetar para CLOSED
POST   /api/v1/circuit-breaker/open  # Forçar OPEN (testes)
```

### Actuator
```
GET    /actuator/health
GET    /actuator/metrics
GET    /actuator/prometheus
GET    /actuator/circuitbreakers
```

---

## Exemplo de requisição

```bash
curl -X POST http://localhost:8080/api/v1/payments \
  -H "Content-Type: application/json" \
  -d '{
    "idempotencyKey": "order-12345-attempt-1",
    "merchantId": "drystorm-001",
    "amount": 150.00,
    "currency": "BRL",
    "paymentMethod": "CREDIT_CARD",
    "description": "Lavagem Completa — Honda Civic",
    "card": {
      "holder": "João Silva",
      "number": "4111111111111111",
      "expiry": "12/26",
      "cvv": "123",
      "brand": "VISA"
    }
  }'
```

**Cartões para teste:**

| Número final | Resultado |
|-------------|-----------|
| `4000` | Recusado (definitivo, sem retry) |
| `5200` | Recusado (definitivo, sem retry) |
| `0000` | Recusado (definitivo, sem retry) |
| qualquer outro | Aprovado ou falha temporária aleatória |

---

## Variável de ambiente `ACQUIRER_FAILURE_RATE`

Controla a taxa de falha do banco simulado:
- `0.0` — nunca falha (Circuit Breaker sempre CLOSED)
- `0.3` — 30% de falha (comportamento padrão)
- `0.7` — 70% de falha (Circuit Breaker abre rapidamente)
- `1.0` — sempre falha (testa esgotamento de tentativas)

---

## Testes

```bash
./mvnw test
```

Cobertura principal:
- `PaymentServiceTest` — idempotência, criação, cancelamento, retry manual
- `SimulatedBankAcquirerTest` — recusa, falha, transições do Circuit Breaker
- `OutboxPublisherTest` — publicação, falha no RabbitMQ, esgotamento de tentativas

---

## Stack

- **Java 21** + **Spring Boot 3.2**
- **RabbitMQ 3.13** com filas TTL para Exponential Backoff
- **Resilience4j 2.2** — Circuit Breaker, Retry, Rate Limiter, Bulkhead
- **PostgreSQL 16** + **Flyway** para migrations
- **Outbox Pattern** com `FOR UPDATE SKIP LOCKED` para multi-instância
- **Docker** + **Docker Compose**
