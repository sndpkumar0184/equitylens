package com.equitylens.service;

import com.equitylens.dto.TTMFinancialResponse;
import com.equitylens.dto.ValuationResponse;
import com.equitylens.entity.Company;
import com.equitylens.entity.MarketData;
import com.equitylens.repository.MarketDataRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Service
public class ValuationService {

    private static final int SCALE = 6;

    private final CompanyService companyService;
    private final MarketDataRepository marketDataRepository;
    private final TTMFinancialService ttmFinancialService;

    public ValuationService(
            CompanyService companyService,
            MarketDataRepository marketDataRepository,
            TTMFinancialService ttmFinancialService
    ) {
        this.companyService = companyService;
        this.marketDataRepository = marketDataRepository;
        this.ttmFinancialService = ttmFinancialService;
    }

    public ValuationResponse getValuation(String ticker) {

        Company company = companyService.getCompany(ticker);

        MarketData marketData =
                marketDataRepository
                        .findByCompanyId(company.getId())
                        .orElseThrow(() ->
                                new RuntimeException(
                                        "Market data not found for " + ticker
                                )
                        );

        List<TTMFinancialResponse> ttmData =
                ttmFinancialService.getTTM(ticker);

        if (ttmData.isEmpty()) {
            throw new RuntimeException(
                    "No TTM financial data found for " + ticker
            );
        }

        /*
         * TTM service returns newest period first.
         */
        TTMFinancialResponse latest =
                ttmData.get(0);

        BigDecimal marketCap =
                marketData.getMarketCap();

        BigDecimal cash =
                latest.cash();

        BigDecimal currentDebt =
                latest.currentDebt();

        BigDecimal longTermDebt =
                latest.longTermDebt();

        BigDecimal totalDebt =
                add(currentDebt, longTermDebt);

        BigDecimal netDebt =
                subtract(totalDebt, cash);

        BigDecimal enterpriseValue =
                add(marketCap, netDebt);

        BigDecimal peRatio =
                divide(marketCap, latest.netIncome());

        BigDecimal priceToFreeCashFlow =
                divide(marketCap, latest.freeCashFlow());

        BigDecimal evToFreeCashFlow =
                divide(enterpriseValue, latest.freeCashFlow());

        /*
         * EBITDA and PEG are intentionally not calculated yet.
         *
         * We do not want to manufacture EBITDA or a growth rate
         * from insufficient data.
         */
        BigDecimal evToEbitda = null;
        BigDecimal pegRatio = null;

        return new ValuationResponse(
                company.getTicker(),
                latest.periodEnd(),

                marketData.getCurrentPrice(),
                marketData.getSharesOutstanding(),
                marketCap,

                cash,
                totalDebt,
                netDebt,
                enterpriseValue,

                latest.revenue(),
                latest.netIncome(),
                latest.freeCashFlow(),

                peRatio,
                priceToFreeCashFlow,
                evToFreeCashFlow,

                evToEbitda,
                pegRatio
        );
    }

    private BigDecimal add(
            BigDecimal a,
            BigDecimal b
    ) {
        if (a == null && b == null) {
            return null;
        }

        if (a == null) {
            return b;
        }

        if (b == null) {
            return a;
        }

        return a.add(b);
    }

    private BigDecimal subtract(
            BigDecimal a,
            BigDecimal b
    ) {
        if (a == null && b == null) {
            return null;
        }

        if (a == null) {
            return b.negate();
        }

        if (b == null) {
            return a;
        }

        return a.subtract(b);
    }

    private BigDecimal divide(
            BigDecimal numerator,
            BigDecimal denominator
    ) {
        if (numerator == null
                || denominator == null
                || denominator.compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }

        return numerator.divide(
                denominator,
                SCALE,
                RoundingMode.HALF_UP
        );
    }
}
