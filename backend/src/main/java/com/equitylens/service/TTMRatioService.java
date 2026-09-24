package com.equitylens.service;

import com.equitylens.dto.TTMFinancialResponse;
import com.equitylens.dto.TTMRatiosResponse;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Service
public class TTMRatioService {

    private final TTMFinancialService ttmFinancialService;

    public TTMRatioService(
            TTMFinancialService ttmFinancialService
    ) {
        this.ttmFinancialService = ttmFinancialService;
    }

    public List<TTMRatiosResponse> getRatios(String ticker) {

        List<TTMFinancialResponse> financials =
                ttmFinancialService.getTTM(ticker);

        return financials.stream()
                .map(this::calculateRatios)
                .toList();
    }

    private TTMRatiosResponse calculateRatios(
            TTMFinancialResponse financials
    ) {

        BigDecimal revenue =
                financials.revenue();

        BigDecimal grossProfit =
                financials.grossProfit();

        BigDecimal operatingIncome =
                financials.operatingIncome();

        BigDecimal netIncome =
                financials.netIncome();

        BigDecimal freeCashFlow =
                financials.freeCashFlow();

        BigDecimal assets =
                financials.assets();

        BigDecimal equity =
                financials.stockholdersEquity();

        BigDecimal currentAssets =
                financials.currentAssets();

        BigDecimal currentLiabilities =
                financials.currentLiabilities();

        BigDecimal currentDebt =
                financials.currentDebt();

        BigDecimal longTermDebt =
                financials.longTermDebt();

        /*
         * Profitability
         */

        BigDecimal grossMargin =
                divide(grossProfit, revenue);

        BigDecimal operatingMargin =
                divide(operatingIncome, revenue);

        BigDecimal netMargin =
                divide(netIncome, revenue);

        BigDecimal fcfMargin =
                divide(freeCashFlow, revenue);

        /*
         * Returns
         *
         * For now we use the latest balance-sheet value
         * supplied by the TTM financial layer.
         *
         * Later we can improve ROA/ROE using average
         * beginning/end balances.
         */

        BigDecimal returnOnAssets =
                divide(netIncome, assets);

        BigDecimal returnOnEquity =
                divide(netIncome, equity);

        /*
         * Liquidity
         */

        BigDecimal currentRatio =
                divide(
                        currentAssets,
                        currentLiabilities
                );

        /*
         * Debt
         */

        BigDecimal totalDebt =
                add(
                        currentDebt,
                        longTermDebt
                );

        BigDecimal debtToEquity =
                divide(
                        totalDebt,
                        equity
                );

        BigDecimal debtToAssets =
                divide(
                        totalDebt,
                        assets
                );

        /*
         * Net Debt
         *
         * Net Debt = Total Debt - Cash
         *
         * Short-term investments are not included yet
         * because META's current SEC mapping does not
         * consistently provide that metric.
         */
        BigDecimal netDebt = null;

        if (totalDebt != null
                && financials.cash() != null) {

            netDebt =
                    totalDebt.subtract(
                            financials.cash()
                    );
        }

        return new TTMRatiosResponse(
                financials.period(),
                financials.periodEnd(),

                grossMargin,
                operatingMargin,
                netMargin,
                fcfMargin,

                returnOnAssets,
                returnOnEquity,

                currentRatio,
                debtToEquity,
                debtToAssets,

                netDebt
        );
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
                6,
                RoundingMode.HALF_UP
        );
    }

    private BigDecimal add(
            BigDecimal first,
            BigDecimal second
    ) {

        if (first == null && second == null) {
            return null;
        }

        if (first == null) {
            return second;
        }

        if (second == null) {
            return first;
        }

        return first.add(second);
    }
}