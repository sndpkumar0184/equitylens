package com.equitylens.service;

import com.equitylens.entity.Company;
import com.equitylens.providers.MarketQuoteProvider;
import com.equitylens.repository.MarketDataRepository;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MarketDataServiceTest {
    @ParameterizedTest
    @ValueSource(strings={"META","AAPL","AMZN","NVDA"})
    void missingStoredQuoteIsExplicitlyUnavailableWithoutFetchingOrInventingPrices(String ticker) {
        var companies=mock(CompanyService.class);
        var repository=mock(MarketDataRepository.class);
        var provider=mock(MarketQuoteProvider.class);
        var company=new Company();
        company.setTicker(ticker);
        when(companies.getCompany(ticker)).thenReturn(company);
        when(repository.findByCompanyId(company.getId())).thenReturn(Optional.empty());
        var service=new MarketDataService(companies,repository,provider);
        var error=assertThrows(ResponseStatusException.class,()->service.getMarketData(ticker));
        assertEquals(HttpStatus.NOT_FOUND,error.getStatusCode());
        verifyNoInteractions(provider);
        verify(repository,never()).save(any());
    }
}
