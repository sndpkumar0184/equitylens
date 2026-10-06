package com.equitylens.realtime;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;
import org.apache.kafka.clients.producer.KafkaProducer;
import tools.jackson.databind.ObjectMapper;
import java.util.*;
import java.util.stream.Collectors;

@Configuration
public class RealTimeConfiguration {
    @Bean(destroyMethod="close")
    @ConditionalOnProperty(name="realtime.enabled",havingValue="true")
    CanonicalKafkaPublisher marketPublisher(Environment env,ObjectMapper mapper) {
        return new CanonicalKafkaPublisher(new KafkaProducer<>(CanonicalKafkaPublisher.settings(env.getProperty("realtime.kafka.bootstrap","localhost:9092"))),mapper);
    }
    @Bean(destroyMethod="disconnect")
    @ConditionalOnProperty(name="realtime.enabled",havingValue="true")
    RealTimeMarketDataProvider realTimeProvider(Environment env,ObjectMapper mapper,CanonicalKafkaPublisher publisher) {
        int max=env.getProperty("realtime.max-symbols",Integer.class,30);
        var selected=env.getProperty("realtime.provider","alpaca");
        WebSocketMarketProvider provider=switch(selected) {
            case "alpaca" -> new AlpacaRealTimeMarketDataProvider(mapper,env.getRequiredProperty("realtime.alpaca.url"),
                env.getProperty("realtime.alpaca.key",""),env.getProperty("realtime.alpaca.secret",""),env.getProperty("realtime.alpaca.feed","iex"),max,
                Arrays.stream(env.getProperty("realtime.alpaca.channels","TRADES,QUOTES,BARS").split(","))
                    .map(String::trim).map(RealTimeMarketDataProvider.Capability::valueOf).collect(Collectors.toSet()));
            case "tiingo" -> new TiingoRealTimeMarketDataProvider(mapper,env.getRequiredProperty("realtime.tiingo.url"),
                env.getProperty("realtime.tiingo.token",""),env.getProperty("realtime.tiingo.threshold",Integer.class,6),max);
            default -> throw new IllegalArgumentException("Unsupported realtime provider");
        };
        Set<String> symbols=Arrays.stream(env.getProperty("realtime.symbols","").split(",")).filter(s->!s.isBlank()).collect(Collectors.toSet());
        if(symbols.isEmpty())throw new IllegalArgumentException("Configure realtime symbols explicitly");
        provider.subscribe(symbols,provider.capabilities().supported());provider.connect(publisher::publish);return provider;
    }
}
