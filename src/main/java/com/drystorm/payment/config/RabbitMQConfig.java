package com.drystorm.payment.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

/**
 * Topologia RabbitMQ:
 *
 *  payments.exchange (direct)
 *    └─► payments.process          ← fila principal (novo pagamento)
 *    └─► payments.retry.1s         ← retry TTL 1s   → volta para process
 *    └─► payments.retry.5s         ← retry TTL 5s
 *    └─► payments.retry.30s        ← retry TTL 30s
 *    └─► payments.retry.2min       ← retry TTL 2min
 *    └─► payments.retry.10min      ← retry TTL 10min
 *    └─► payments.dlq              ← dead letter (esgotou tentativas)
 *
 *  Cada fila de retry tem x-dead-letter-exchange apontando de volta para
 *  payments.exchange com routing key "payments.process", implementando
 *  Exponential Backoff sem scheduler externo.
 */
@Configuration
public class RabbitMQConfig {

    public static final String EXCHANGE        = "payments.exchange";
    public static final String QUEUE_PROCESS   = "payments.process";
    public static final String QUEUE_DLQ       = "payments.dlq";

    // Routing keys para filas de retry (cada uma tem TTL diferente)
    public static final String RK_PROCESS      = "payments.process";
    public static final String RK_RETRY_1S     = "payments.retry.1s";
    public static final String RK_RETRY_5S     = "payments.retry.5s";
    public static final String RK_RETRY_30S    = "payments.retry.30s";
    public static final String RK_RETRY_2MIN   = "payments.retry.2min";
    public static final String RK_RETRY_10MIN  = "payments.retry.10min";
    public static final String RK_DLQ          = "payments.dlq";

    // Delays em ms para cada nível de retry (Exponential Backoff)
    public static final long[] RETRY_DELAYS_MS = {1_000, 5_000, 30_000, 120_000, 600_000};
    public static final String[] RETRY_ROUTING_KEYS = {
        RK_RETRY_1S, RK_RETRY_5S, RK_RETRY_30S, RK_RETRY_2MIN, RK_RETRY_10MIN
    };

    // ─── Exchange ─────────────────────────────────────────────────────────────
    @Bean
    public DirectExchange paymentsExchange() {
        return ExchangeBuilder.directExchange(EXCHANGE).durable(true).build();
    }

    // ─── Fila principal ───────────────────────────────────────────────────────
    @Bean
    public Queue processQueue() {
        return QueueBuilder.durable(QUEUE_PROCESS)
                .withArgument("x-dead-letter-exchange", EXCHANGE)
                .withArgument("x-dead-letter-routing-key", RK_DLQ)
                .build();
    }

    @Bean
    public Binding processBinding() {
        return BindingBuilder.bind(processQueue()).to(paymentsExchange()).with(RK_PROCESS);
    }

    // ─── Dead Letter Queue ────────────────────────────────────────────────────
    @Bean
    public Queue dlqQueue() {
        return QueueBuilder.durable(QUEUE_DLQ).build();
    }

    @Bean
    public Binding dlqBinding() {
        return BindingBuilder.bind(dlqQueue()).to(paymentsExchange()).with(RK_DLQ);
    }

    // ─── Retry queues (TTL → volta para process) ──────────────────────────────
    @Bean
    public Queue retryQueue1s()   { return retryQueue(RK_RETRY_1S,    1_000L); }
    @Bean
    public Queue retryQueue5s()   { return retryQueue(RK_RETRY_5S,    5_000L); }
    @Bean
    public Queue retryQueue30s()  { return retryQueue(RK_RETRY_30S,  30_000L); }
    @Bean
    public Queue retryQueue2min() { return retryQueue(RK_RETRY_2MIN, 120_000L); }
    @Bean
    public Queue retryQueue10min(){ return retryQueue(RK_RETRY_10MIN,600_000L); }

    @Bean public Binding retryBinding1s()   { return retryBinding(retryQueue1s(),    RK_RETRY_1S);   }
    @Bean public Binding retryBinding5s()   { return retryBinding(retryQueue5s(),    RK_RETRY_5S);   }
    @Bean public Binding retryBinding30s()  { return retryBinding(retryQueue30s(),   RK_RETRY_30S);  }
    @Bean public Binding retryBinding2min() { return retryBinding(retryQueue2min(),  RK_RETRY_2MIN); }
    @Bean public Binding retryBinding10min(){ return retryBinding(retryQueue10min(), RK_RETRY_10MIN);}

    // ─── Jackson converter ────────────────────────────────────────────────────
    @Bean
    public Jackson2JsonMessageConverter jsonConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory cf) {
        RabbitTemplate tmpl = new RabbitTemplate(cf);
        tmpl.setMessageConverter(jsonConverter());
        return tmpl;
    }

    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory cf) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(cf);
        factory.setMessageConverter(jsonConverter());
        factory.setAcknowledgeMode(AcknowledgeMode.MANUAL);
        factory.setPrefetchCount(5);
        return factory;
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────
    private Queue retryQueue(String name, long ttlMs) {
        Map<String, Object> args = new HashMap<>();
        args.put("x-message-ttl", ttlMs);
        args.put("x-dead-letter-exchange", EXCHANGE);
        args.put("x-dead-letter-routing-key", RK_PROCESS); // volta para fila principal
        return QueueBuilder.durable(name).withArguments(args).build();
    }

    private Binding retryBinding(Queue queue, String routingKey) {
        return BindingBuilder.bind(queue).to(paymentsExchange()).with(routingKey);
    }
}
