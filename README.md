# Payment API

Esse sistema de API é utilizado em empresas que processam pagamentos, e-commerce, aplicativos de delivery, plataformas SaaS. Nele eu quis aplicar o fluxo prático levando em consideração que é um dos domínios mais comuns no mercado Java. Usei como base gateways reais como PagSeguro e Stripe funcionam e modelei os requisitos com base nisso — criação de cobrança, consulta de status, cancelamento e reprocessamento de pagamentos recusados. A estrutura está preparada para escalar — o processamento é assíncrono justamente por isso. O fluxo foi pensado para um merchant — uma loja — que envia cobranças e consulta o status dos pagamentos.

## Tecnologias

- Java 21 
- Spring Boot 3.2
- Spring Data JPA
- PostgreSQL
- RabbitMQ
- Docker / Docker Compose
- Swagger UI
- JUnit 5


## Como rodar localmente

**Pré-requisitos:** Docker instalado

```bash
# 1. Clonar o repositório
git clone https://github.com/brunomartinsoliveira/payment-API
cd payment-API

# 2. Subir o banco de dados e o RabbitMQ
docker compose up postgres rabbitmq -d

# 3. Rodar a aplicação
mvn spring-boot:run

# 4. Acessar
Swagger UI = http://localhost:8080/swagger-ui.html
RabbitMQ Management = http://localhost:15672

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
