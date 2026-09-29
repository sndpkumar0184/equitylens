package com.equitylens.service;

import com.equitylens.dto.MarketDataResponse;
import com.equitylens.entity.Company;
import com.equitylens.entity.MarketData;
import com.equitylens.providers.MarketQuoteProvider;
import com.equitylens.repository.MarketDataRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MarketDataService {

    private final CompanyService companyService;
    private final MarketDataRepository marketDataRepository;
    private final MarketQuoteProvider marketDataProvider;

    public MarketDataService(
            CompanyService companyService,
            MarketDataRepository marketDataRepository,
            MarketQuoteProvider marketDataProvider
    ) {
        this.companyService = companyService;
        this.marketDataRepository = marketDataRepository;
        this.marketDataProvider = marketDataProvider;
    }

    @Transactional
    public MarketDataResponse refresh(String ticker) {

        Company company = companyService.getCompany(ticker);

        MarketQuoteProvider.MarketQuote quote =
                marketDataProvider.getQuote(
                        company.getTicker()
                );

        MarketData marketData =
                marketDataRepository
                        .findByCompanyId(company.getId())
                        .orElseGet(MarketData::new);

        marketData.setCompany(company);
        marketData.setCurrentPrice(
                quote.currentPrice()
        );
        marketData.setPreviousClose(
                quote.previousClose()
        );
        marketData.setSharesOutstanding(
                quote.sharesOutstanding()
        );
        marketData.setMarketCap(
                quote.marketCap()
        );
        marketData.setFiftyTwoWeekHigh(
                quote.fiftyTwoWeekHigh()
        );
        marketData.setFiftyTwoWeekLow(
                quote.fiftyTwoWeekLow()
        );
        marketData.setBeta(
                quote.beta()
        );
        marketData.setMarketDataTimestamp(
                quote.timestamp()
        );

        MarketData saved =
                marketDataRepository.save(marketData);

        return toResponse(saved);
    }

    @Transactional
    public MarketDataResponse getMarketData(
            String ticker
    ) {

        Company company = companyService.getCompany(ticker);

        MarketData marketData =
                marketDataRepository
                        .findByCompanyId(company.getId())
                        .orElseThrow(() ->
                                new RuntimeException(
                                        "Market data not found for "
                                                + ticker
                                )
                        );

        return toResponse(marketData);
    }

    private MarketDataResponse toResponse(
            MarketData marketData
    ) {

        return new MarketDataResponse(
                marketData.getCompany().getTicker(),
                marketData.getCurrentPrice(),
                marketData.getPreviousClose(),
                marketData.getSharesOutstanding(),
                marketData.getMarketCap(),
                marketData.getFiftyTwoWeekHigh(),
                marketData.getFiftyTwoWeekLow(),
                marketData.getBeta(),
                marketData.getMarketDataTimestamp()
        );
    }
}
