package com.equitylens.service;

import com.equitylens.dto.NormalizedFinancialMetricResponse;
import com.equitylens.dto.TTMFinancialResponse;
import com.equitylens.entity.Company;
import com.equitylens.entity.FinancialMetric;
import com.equitylens.repository.CompanyRepository;
import com.equitylens.repository.FinancialMetricRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.time.temporal.ChronoUnit;
import java.util.stream.Collectors;

@Service
public class TTMFinancialService {

    private final CompanyRepository companyRepository;
    private final FinancialMetricRepository financialMetricRepository;
    private final FinancialMetricNormalizer normalizer;

    public TTMFinancialService(
            CompanyRepository companyRepository,
            FinancialMetricRepository financialMetricRepository,
            FinancialMetricNormalizer normalizer
    ) {
        this.companyRepository = companyRepository;
        this.financialMetricRepository = financialMetricRepository;
        this.normalizer = normalizer;
    }

    public List<TTMFinancialResponse> getTTM(String ticker) {

        Company company = companyRepository
                .findByTickerIgnoreCase(ticker)
                .orElseThrow(() ->
                        new RuntimeException(
                                "Company not found: " + ticker
                        ));

        List<FinancialMetric> rawMetrics =
                financialMetricRepository.findByCompanyIdOrderByPeriodEndDesc(company.getId());

        if (rawMetrics.isEmpty()) {
            throw new RuntimeException(
                    "No financial data found for company: " + ticker
            );
        }

        List<NormalizedFinancialMetricResponse> normalized =
                normalizer.normalize(rawMetrics);

        return calculateTTM(normalized);
    }

    private List<TTMFinancialResponse> calculateTTM(
            List<NormalizedFinancialMetricResponse> normalized
    ) {

        return normalized.stream().collect(Collectors.groupingBy(NormalizedFinancialMetricResponse::unit))
                .values().stream().flatMap(facts -> calculateUnitTTM(facts).stream())
                .sorted(Comparator.comparing(TTMFinancialResponse::periodEnd).reversed()
                        .thenComparing(TTMFinancialResponse::unit))
                .toList();
    }

    private List<TTMFinancialResponse> calculateUnitTTM(List<NormalizedFinancialMetricResponse> normalized) {
        // Annual and YTD flow observations must never enter a four-quarter sum.
        Map<LocalDate, Map<String, List<NormalizedFinancialMetricResponse>>>
                byPeriod =
                normalized.stream()
                        .filter(metric -> isQuarter(metric) || "POINT_IN_TIME".equals(metric.period()))
                        .collect(Collectors.groupingBy(
                                NormalizedFinancialMetricResponse::periodEnd,
                                TreeMap::new,
                                Collectors.groupingBy(
                                        NormalizedFinancialMetricResponse::metric
                                )
                        ));

        List<LocalDate> dates =
                new ArrayList<>(normalized.stream().filter(this::isQuarter)
                        .map(NormalizedFinancialMetricResponse::periodEnd).distinct().toList());

        dates.sort(Comparator.naturalOrder());

        /*
         * Only use dates that actually represent quarter-end dates.
         *
         * We also validate that the previous three dates are consecutive
         * quarters before calculating TTM.
         */
        List<TTMFinancialResponse> result =
                new ArrayList<>();

        for (int i = 3; i < dates.size(); i++) {

            LocalDate currentDate = dates.get(i);
            LocalDate previous1 = dates.get(i - 1);
            LocalDate previous2 = dates.get(i - 2);
            LocalDate previous3 = dates.get(i - 3);

            if (!areConsecutiveQuarters(
                    previous3,
                    previous2,
                    previous1,
                    currentDate
            )) {
                continue;
            }

            Map<String, List<NormalizedFinancialMetricResponse>> current =
                    byPeriod.get(currentDate);

            Map<String, List<NormalizedFinancialMetricResponse>> q1 =
                    byPeriod.get(previous1);

            Map<String, List<NormalizedFinancialMetricResponse>> q2 =
                    byPeriod.get(previous2);

            Map<String, List<NormalizedFinancialMetricResponse>> q3 =
                    byPeriod.get(previous3);

            result.add(
                    buildTTMResponse(
                            currentDate,
                            normalized.getFirst().unit(),
                            current,
                            q1,
                            q2,
                            q3
                    )
            );
        }

        result.sort(
                Comparator.comparing(
                        TTMFinancialResponse::periodEnd
                ).reversed()
        );

        return result;
    }

    private TTMFinancialResponse buildTTMResponse(
            LocalDate periodEnd,
            String unit,
            Map<String, List<NormalizedFinancialMetricResponse>> current,
            Map<String, List<NormalizedFinancialMetricResponse>> previous1,
            Map<String, List<NormalizedFinancialMetricResponse>> previous2,
            Map<String, List<NormalizedFinancialMetricResponse>> previous3
    ) {

        /*
         * FLOW METRICS
         *
         * TTM = current quarter + previous 3 quarters.
         */

        BigDecimal revenue =
                sumFlow("revenue", current, previous1, previous2, previous3);

        BigDecimal costOfRevenue =
                sumFlow("cost_of_revenue", current, previous1, previous2, previous3);

        BigDecimal grossProfit =
                sumFlow("gross_profit", current, previous1, previous2, previous3);

        /*
         * If SEC did not provide gross profit, derive it.
         *
         * Gross Profit = Revenue - Cost of Revenue
         */
        if (grossProfit == null
                && revenue != null
                && costOfRevenue != null) {

            grossProfit =
                    revenue.subtract(costOfRevenue);
        }

        BigDecimal operatingIncome =
                sumFlow("operating_income", current, previous1, previous2, previous3);

        BigDecimal netIncome =
                sumFlow("net_income", current, previous1, previous2, previous3);

        BigDecimal operatingCashFlow =
                sumFlow("operating_cash_flow", current, previous1, previous2, previous3);

        BigDecimal capitalExpenditures =
                sumFlow("capital_expenditures", current, previous1, previous2, previous3);

        BigDecimal investingCashFlow =
                sumFlow("investing_cash_flow", current, previous1, previous2, previous3);

        BigDecimal financingCashFlow =
                sumFlow("financing_cash_flow", current, previous1, previous2, previous3);

        BigDecimal freeCashFlow = null;

        if (operatingCashFlow != null
                && capitalExpenditures != null) {

            freeCashFlow =
                    operatingCashFlow
                            .subtract(capitalExpenditures);
        }

        /*
         * POINT-IN-TIME METRICS
         *
         * These must NEVER be summed.
         *
         * We only take the latest/current quarter.
         */
        BigDecimal cash =
                pointInTimeValue(current, "cash");

        BigDecimal shortTermInvestments =
                pointInTimeValue(
                        current,
                        "short_term_investments"
                );

        BigDecimal currentAssets =
                pointInTimeValue(
                        current,
                        "current_assets"
                );

        if (currentAssets == null) {
            currentAssets =
                    pointInTimeValue(
                            current,
                            "assets_current"
                    );
        }

        BigDecimal assets =
                pointInTimeValue(
                        current,
                        "assets"
                );

        BigDecimal currentLiabilities =
                pointInTimeValue(
                        current,
                        "current_liabilities"
                );

        if (currentLiabilities == null) {
            currentLiabilities =
                    pointInTimeValue(
                            current,
                            "liabilities_current"
                    );
        }

        BigDecimal currentDebt =
                pointInTimeValue(
                        current,
                        "current_debt"
                );

        if (currentDebt == null) {
            currentDebt =
                    pointInTimeValue(
                            current,
                            "long_term_debt_current"
                    );
        }

        BigDecimal longTermDebt =
                pointInTimeValue(
                        current,
                        "long_term_debt"
                );

        if (longTermDebt == null) {
            longTermDebt =
                    pointInTimeValue(
                            current,
                            "long_term_debt_noncurrent"
                    );
        }

        BigDecimal stockholdersEquity =
                pointInTimeValue(
                        current,
                        "stockholders_equity"
                );

        return new TTMFinancialResponse(
                current.values().stream().flatMap(List::stream).filter(this::isQuarter)
                        .map(m -> "TTM " + m.period()).findFirst().orElse("TTM"),
                periodEnd,
                revenue,
                costOfRevenue,
                grossProfit,
                operatingIncome,
                netIncome,
                operatingCashFlow,
                capitalExpenditures,
                investingCashFlow,
                financingCashFlow,
                freeCashFlow,
                cash,
                shortTermInvestments,
                currentAssets,
                assets,
                currentLiabilities,
                currentDebt,
                longTermDebt,
                stockholdersEquity,
                unit
        );
    }

    @SafeVarargs
    private final BigDecimal sumFlow(String metric,
            Map<String, List<NormalizedFinancialMetricResponse>>... periods) {
        BigDecimal total = BigDecimal.ZERO;
        LocalDate nextStart = null;
        for (var period : periods) {
            List<NormalizedFinancialMetricResponse> candidates = period.getOrDefault(metric, List.of())
                    .stream().filter(this::isQuarter).toList();
            if (candidates.size() != 1) return null;
            var observation = candidates.getFirst();
            if (nextStart != null && !observation.periodEnd().plusDays(1).equals(nextStart)) return null;
            nextStart = observation.periodStart();
            total = total.add(observation.value());
        }
        return total;
    }

    private boolean isQuarter(NormalizedFinancialMetricResponse metric) {
        return metric.period().matches("Q[1-4]") || "QUARTER".equals(metric.period());
    }

    /**
     * Get the latest point-in-time value for the current period.
     */
    private BigDecimal pointInTimeValue(
            Map<String, List<NormalizedFinancialMetricResponse>> metrics,
            String metric
    ) {

        if (metrics == null) {
            return null;
        }

        List<NormalizedFinancialMetricResponse> values =
                metrics.get(metric);

        if (values == null || values.isEmpty()) {
            return null;
        }

        return values.get(0).value();
    }

    private boolean areConsecutiveQuarters(LocalDate q0, LocalDate q1, LocalDate q2, LocalDate q3) {
        return quarterGap(q0, q1) && quarterGap(q1, q2) && quarterGap(q2, q3);
    }

    private boolean quarterGap(LocalDate earlier, LocalDate later) {
        long days = ChronoUnit.DAYS.between(earlier, later);
        return days >= 80 && days <= 100;
    }
}
