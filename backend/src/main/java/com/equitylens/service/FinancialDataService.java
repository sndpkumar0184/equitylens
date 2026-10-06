package com.equitylens.service;

import com.equitylens.dto.*;
import com.equitylens.entity.Company;
import com.equitylens.entity.FinancialMetric;
import com.equitylens.repository.CompanyRepository;
import com.equitylens.repository.FinancialMetricRepository;
import com.equitylens.sec.SecCompanyFacts;
import com.equitylens.sec.SecFinancialDataParser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class FinancialDataService {

    private final CompanyRepository companyRepository;
    private final CompanyService companyService;
    private final TransactionTemplate transactions;
    private final FinancialMetricRepository financialMetricRepository;
    private final SecDataService secDataService;
    private final SecFinancialDataParser parser;
    private final FinancialMetricNormalizer normalizer;

    public FinancialDataService(
            CompanyRepository companyRepository,
            FinancialMetricRepository financialMetricRepository,
            SecDataService secDataService,
            SecFinancialDataParser parser,
            FinancialMetricNormalizer normalizer,
            CompanyService companyService, PlatformTransactionManager transactionManager) {

        this.companyService = companyService;
        this.transactions = new TransactionTemplate(transactionManager);

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

    public List<FinancialMetric> importCompanyFinancials(String ticker) {
        return loadFinancials(ticker, true);
    }

    public List<FinancialMetric> getCompanyFinancials(String ticker) {
        return loadFinancials(ticker, false);
    }

    public List<FinancialMetric> getDashboardFinancials(String ticker, Set<String> metrics) {
        return loadFinancials(ticker, false, metrics);
    }

    private List<FinancialMetric> loadFinancials(String ticker, boolean force) {
        return loadFinancials(ticker, force, null);
    }

    private List<FinancialMetric> loadFinancials(String ticker, boolean force, Set<String> dashboardMetrics) {
        Company resolved = companyService.getCompany(ticker);
        // Both readers and replace-imports lock the same company row across app instances.
        return transactions.execute(status -> {
            Company company = companyRepository.lockById(resolved.getId()).orElseThrow();
            boolean upgradeMappings = dashboardMetrics != null
                    && !Integer.valueOf(SecFinancialDataParser.MAPPING_VERSION).equals(company.getFinancialImportVersion());
            ensureFinancials(company, force || upgradeMappings);
            if (dashboardMetrics == null) {
                return financialMetricRepository.findByCompanyIdOrderByPeriodEndDesc(company.getId());
            }
            LocalDate latest = financialMetricRepository.latestDashboardPeriod(company.getId(), "USD", dashboardMetrics);
            if (latest == null) return List.of();
            // Eight displayed annual periods plus prior-year comparison/cumulative derivation context.
            // Query only dashboard metrics, USD, and a bounded reporting window, never the full history.
            return financialMetricRepository.findByCompanyIdAndMetricInAndUnitAndPeriodEndGreaterThanEqualOrderByPeriodEndDesc(
                    company.getId(), dashboardMetrics, "USD", latest.minusYears(10));
        });
    }

    private void ensureFinancials(Company company, boolean force) {
        long count = financialMetricRepository.countByCompanyId(company.getId());
        boolean complete = count > 0 && (company.getFinancialImportCount() != null
                ? company.getFinancialImportCount() == count
                : financialMetricRepository.findMetricNames(company.getId())
                    .containsAll(Set.of("revenue", "net_income", "cash", "assets")));
        if (!force && complete) {
            if (company.getFinancialImportCount() == null) company.setFinancialImportCount(count);
            return;
        }
        SecCompanyFacts facts = secDataService.getCompanyFacts(company.getCik());
        List<FinancialMetric> metrics = parser.parse(facts, company);
        if (metrics.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT,
                    "No supported SEC financial observations for " + company.getTicker());
        }
        // Existing importer: fetch and parse before replacing, atomically, only for this company.
        financialMetricRepository.deleteByCompanyId(company.getId());
        financialMetricRepository.flush();
        financialMetricRepository.saveAll(metrics);
        company.setFinancialImportCount((long) metrics.size());
        company.setFinancialImportVersion(SecFinancialDataParser.MAPPING_VERSION);
    }

    public record FilingSource(String metric, LocalDate periodStart, LocalDate periodEnd,
            LocalDate filingDate, String form, String frame, String unit) {}

    /** Source observations, not a claim that every observation is the selected/derived output. */
    public List<FilingSource> getResearchSources(String ticker, LocalDate from, LocalDate to) {
        return getDashboardFinancials(ticker, DashboardService.METRICS).stream()
                .filter(com.equitylens.sec.SecObservationPolicy::isValid)
                .filter(f -> !f.getPeriodEnd().isBefore(from) && !f.getPeriodEnd().isAfter(to))
                .sorted(Comparator.comparing(FinancialMetric::getPeriodEnd).reversed()
                        .thenComparing(FinancialMetric::getFilingDate, Comparator.reverseOrder()))
                .map(f -> new FilingSource(f.getMetric(), f.getPeriodStart(), f.getPeriodEnd(),
                        f.getFilingDate(), f.getForm(), f.getFrame(), f.getUnit()))
                .distinct().limit(101).toList();
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
