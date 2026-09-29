package com.equitylens.providers;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

@Configuration
public class MarketDataProviderConfiguration {
    @Bean
    @ConditionalOnProperty(prefix = "market", name = "data-provider", havingValue = "stashgamma", matchIfMissing = true)
    DailyPriceProvider stashGammaMarketDataProvider(RestClient.Builder builder, ObjectMapper mapper, Environment environment) {
        return new StashGammaMarketDataProvider(builder, mapper,
                environment.getProperty("market.stashgamma.base-url", "https://www.stashgamma.com/api/dataapi/v1"),
                environment.getProperty("market.stashgamma.api-key", ""));
    }
}
