package com.drystorm.payment.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Payment Gateway API")
                        .description("""
                            Gateway de Pagamentos com Resiliência — DryStorm
                            
                            **Padrões implementados:**
                            - **Outbox Pattern**: consistência entre banco e mensageria
                            - **Circuit Breaker** (Resilience4j): proteção contra falhas em cascata
                            - **Exponential Backoff**: retentativas inteligentes via filas TTL
                            - **Idempotência**: chave única por transação evita duplicatas
                            - **Bulkhead**: limita concorrência ao adquirente
                            - **Rate Limiter**: proteção contra burst de requisições
                            """)
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("DryStorm")
                                .url("https://drystorm.com.br"))
                        .license(new License().name("MIT")));
    }
}
