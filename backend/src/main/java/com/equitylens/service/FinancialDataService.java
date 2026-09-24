package com.equitylens.service;

import com.equitylens.dto.*;
import com.equitylens.entity.Company;
import com.equitylens.entity.FinancialMetric;
import com.equitylens.repository.CompanyRepository;
import com.equitylens.repository.FinancialMetricRepository;
import com.equitylens.sec.SecCompanyFacts;
import com.equitylens.sec.SecFinancialDataParser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class FinancialDataService {

    private final CompanyRepository companyRepository;
    private final FinancialMetricRepository financialMetricRepository;
    private final SecDataService secDataService;
    private final SecFinancialDataParser parser;
    private final FinancialMetricNormalizer normalizer;

    public FinancialDataService(
            CompanyRepository companyRepository,
            FinancialMetricRepository financialMetricRepository,
            SecDataService secDataService,
            SecFinancialDataParser parser,
            FinancialMetricNormalizer normalizer) {

        this.companyRepository = companyRepository;
        this.financialMetricRepository = financialMetricRepository;
        this.secDataService = secDataService;
        this.parser = parser;
        this.normalizer = normalizer;
    }

    public List<NormalizedFinancialMetricResponse>
    getNormalizedFinancials(String ticker) {

        List<FinancialMetric> raw =
                getCompanyFinancials(ticker);

        return normalizer.normalize(raw);
    }

    @Transactional
    public List<FinancialMetric> importCompanyFinancials(String ticker) {

        Company company = companyRepository
                .findByTickerIgnoreCase(ticker)
                .orElseThrow(() ->
                        new RuntimeException(
                                "Company not found: " + ticker
                        ));

        /*
         * Re-importing should replace the existing
         * financial data for this company.
         */
        SecCompanyFacts facts =
                secDataService.getCompanyFacts(company.getCik());

        List<FinancialMetric> metrics =
                parser.parse(facts, company);

        if (metrics.isEmpty()) {
            throw new IllegalStateException("No valid SEC financial observations for " + ticker);
        }
        financialMetricRepository.deleteByCompanyId(company.getId());
        return financialMetricRepository.saveAll(metrics);
    }

    public List<FinancialMetric> getCompanyFinancials(String ticker) {

        Company company = companyRepository
                .findByTickerIgnoreCase(ticker)
                .orElseThrow(() ->
                        new RuntimeException(
                                "Company not found: " + ticker
                        ));

        return financialMetricRepository
                .findByCompanyIdOrderByPeriodEndDesc(company.getId());
    }

    public List<IncomeStatementResponse> getIncomeStatement(
            String ticker) {

        List<NormalizedFinancialMetricResponse> metrics =
                getNormalizedFinancials(ticker);

        Map<StatementPeriod, List<NormalizedFinancialMetricResponse>> byPeriod =
                metrics.stream()
                        .filter(x -> Set.of("revenue", "cost_of_revenue", "gross_profit",
                                "operating_income", "net_income").contains(x.metric()))
                        .filter(x ->
                                x.period().matches("Q[1-4]")
                                        || "QUARTER".equals(x.period())
                                        || "FY".equals(x.period()))
                        .collect(Collectors.groupingBy(
                                x -> new StatementPeriod(x.period(), x.periodStart(), x.periodEnd(), x.unit())
                        ));

        return byPeriod.values()
                .stream()
                .map(this::toIncomeStatement)
                .sorted(
                        Comparator.comparing(
                                IncomeStatementResponse::periodEnd
                        ).reversed()
                )
                .toList();
    }

    private IncomeStatementResponse toIncomeStatement(
            List<NormalizedFinancialMetricResponse> metrics) {

        NormalizedFinancialMetricResponse first =
                metrics.getFirst();

        return new IncomeStatementResponse(
                first.period(),
                first.periodStart(),
                first.periodEnd(),
                valueFor(metrics, "revenue"),
                valueFor(metrics, "cost_of_revenue"),
                valueFor(metrics, "gross_profit"),
                valueFor(metrics, "operating_income"),
                valueFor(metrics, "net_income"),
                first.unit()
        );
    }

    private java.math.BigDecimal valueFor(
            List<NormalizedFinancialMetricResponse> metrics,
            String metric) {

        return metrics.stream()
                .filter(x -> metric.equals(x.metric()))
                .map(NormalizedFinancialMetricResponse::value)
                .findFirst()
                .orElse(null);
    }

    public List<BalanceSheetResponse> getBalanceSheet(
            String ticker) {

        List<NormalizedFinancialMetricResponse> metrics =
                getNormalizedFinancials(ticker);

        Map<StatementPeriod, List<NormalizedFinancialMetricResponse>> byDate =
                metrics.stream()
                        .filter(x ->
                                x.metric().equals("cash")
                                        || x.metric().equals("short_term_investments")
                                        || x.metric().equals("current_assets")
                                        || x.metric().equals("assets")
                                        || x.metric().equals("current_liabilities")
                                        || x.metric().equals("current_debt")
                                        || x.metric().equals("long_term_debt")
                                        || x.metric().equals("stockholders_equity"))
                        .collect(Collectors.groupingBy(
                                x -> new StatementPeriod(x.period(), null, x.periodEnd(), x.unit())
                        ));

        return byDate.values()
                .stream()
                .map(this::toBalanceSheet)
                .sorted(
                        Comparator.comparing(
                                BalanceSheetResponse::periodEnd
                        ).reversed()
                )
                .toList();
    }

    private BalanceSheetResponse toBalanceSheet(
            List<NormalizedFinancialMetricResponse> metrics) {

        NormalizedFinancialMetricResponse first =
                metrics.getFirst();

        return new BalanceSheetResponse(
                first.period(),
                first.periodEnd(),
                valueFor(metrics, "cash"),
                valueFor(metrics, "short_term_investments"),
                valueFor(metrics, "current_assets"),
                valueFor(metrics, "assets"),
                valueFor(metrics, "current_liabilities"),
                valueFor(metrics, "current_debt"),
                valueFor(metrics, "long_term_debt"),
                valueFor(metrics, "stockholders_equity"),
                first.unit()
        );
    }

    public List<CashFlowResponse> getCashFlow(
            String ticker) {

        List<NormalizedFinancialMetricResponse> metrics =
                getNormalizedFinancials(ticker);

        Map<StatementPeriod, List<NormalizedFinancialMetricResponse>> byPeriod =
                metrics.stream()
                        .filter(x ->
                                x.metric().equals("operating_cash_flow")
                                        || x.metric().equals("capital_expenditures")
                                        || x.metric().equals("investing_cash_flow")
                                        || x.metric().equals("financing_cash_flow"))
                        .filter(x ->
                                x.period().matches("Q[1-4]")
                                        || "QUARTER".equals(x.period())
                                        || "FY".equals(x.period()))
                        .collect(Collectors.groupingBy(
                                x -> new StatementPeriod(x.period(), x.periodStart(), x.periodEnd(), x.unit())
                        ));

        return byPeriod.values()
                .stream()
                .map(this::toCashFlow)
                .sorted(
                        Comparator.comparing(
                                CashFlowResponse::periodEnd
                        ).reversed()
                )
                .toList();
    }

    private CashFlowResponse toCashFlow(
            List<NormalizedFinancialMetricResponse> metrics) {

        NormalizedFinancialMetricResponse first =
                metrics.getFirst();

        var operating =
                valueFor(metrics, "operating_cash_flow");

        var capex =
                valueFor(metrics, "capital_expenditures");

        var investing =
                valueFor(metrics, "investing_cash_flow");

        var financing =
                valueFor(metrics, "financing_cash_flow");

        var freeCashFlow =
                operating != null && capex != null
                        ? operating.subtract(capex)
                        : null;

        return new CashFlowResponse(
                first.period(),
                first.periodStart(),
                first.periodEnd(),
                operating,
                capex,
                investing,
                financing,
                freeCashFlow,
                first.unit()
        );
    }
    private record StatementPeriod(String period, LocalDate start, LocalDate end, String unit) {}
}
