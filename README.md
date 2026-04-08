# Payment Gateway - API drystorm

![esquemazitaç](https://github.com/user-attachments/assets/8cfceb81-ab90-4d3b-876f-f15201741cfc)


> Após 8 meses de desenvolvimento eu projetei o fluxo de pagamentos da minha API/Landing Page.

---

## O que é isso?

Drystorm é um projeto full stack com backend em Java, contemplando API REST, autenticação de usuários (login) e fluxo de pagamentos. No frontend, foi desenvolvida uma landing page responsiva com HTML5 e CSS3, priorizando usabilidade e performance. O projeto reforça minha experiência em integração entre camadas, boas práticas de desenvolvimento e construção de aplicações web com foco em qualidade e escalabilidade.

---

## Como executar o projeto

```bash
# 1. clonar repositório
git clone https://github.com/brunomartinsoliveira/payment-API

# 2. entrar na pasta do projeto backend
cd backend

# 3. subir a infra (PostgreSQL + RabbitMQ)
docker compose up postgres rabbitmq -d

# 4. rodar a API
./mvnw spring-boot:run

# 5. subir tudo junto (incluindo API no Docker)
docker compose up -d

# 6. com PgAdmin e ferramentas extras
docker compose --profile tools up -d
```

---

## Padrões implementados:

| Serviço | URL |
|---------|-----|
| API | http://localhost:8080 |
| Swagger UI | http://localhost:8080/swagger-ui.html |
| RabbitMQ Management | http://localhost:15672 (guest/guest) |
| PgAdmin | http://localhost:5050 |

---

## Como funciona?

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

## Endpoints

### Público
```
POST   /api/v1/payments              # Criar pagamento
GET    /api/v1/payments/{id}         # Consultar (com histórico de tentativas)
GET    /api/v1/payments?merchantId=  # Listar por merchant
PATCH  /api/v1/payments/{id}/cancel  # Cancelar
POST   /api/v1/payments/{id}/retry   # Forçar retry manual
```
**Cartões para teste:**

| Número final | Resultado |
|-------------|-----------|
| `4000` | Recusado (definitivo, sem retry) |
| `5200` | Recusado (definitivo, sem retry) |
| `0000` | Recusado (definitivo, sem retry) |
| qualquer outro | Aprovado ou falha temporária aleatória |

---

## Variável de ambiente:

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

## Stack Tecnológico

- **Java 21** + **Spring Boot**
- **PostgreSQL**
- **RabbitMQ**
- **Docker** + **Docker Compose**
