package com.equitylens.providers;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;
import static org.assertj.core.api.Assertions.assertThat;

class MarketDataProviderConfigurationTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(MarketDataProviderConfiguration.class)
            .withBean(RestClient.Builder.class, RestClient::builder)
            .withBean(ObjectMapper.class, ObjectMapper::new);

    @Test void defaultsToStashGammaAndKeepsItsCredentialsServerSide() {
        runner.run(context -> {
            assertThat(context).hasSingleBean(MarketDataProvider.class);
            assertThat(context.getBean(MarketDataProvider.class)).isInstanceOf(StashGammaMarketDataProvider.class);
        });
    }

    @Test void explicitStashGammaSelectionUsesProviderNeutralContract() {
        runner.withPropertyValues("market.data-provider=stashgamma", "market.stashgamma.api-key=server-only")
                .run(context -> assertThat(context.getBean(MarketDataProvider.class).source()).isEqualTo("STASHGAMMA"));
    }

    @Test void unknownProviderDoesNotSilentlySelectStashGamma() {
        runner.withPropertyValues("market.data-provider=unknown")
                .run(context -> assertThat(context).doesNotHaveBean(MarketDataProvider.class));
    }
}
