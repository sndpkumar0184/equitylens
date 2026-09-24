package com.equitylens.controller;

import com.equitylens.dto.BalanceSheetResponse;
import com.equitylens.dto.CashFlowResponse;
import com.equitylens.dto.IncomeStatementResponse;
import com.equitylens.dto.NormalizedFinancialMetricResponse;
import com.equitylens.entity.Company;
import com.equitylens.entity.FinancialMetric;
import com.equitylens.service.CompanyService;
import com.equitylens.service.FinancialDataService;
import com.equitylens.service.SecDataService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/companies")
public class CompanyController {

    private final CompanyService companyService;
    private final SecDataService secDataService;
    private final FinancialDataService financialDataService;

    public CompanyController(
            CompanyService companyService,
            SecDataService secDataService,
            FinancialDataService financialDataService) {

        this.companyService = companyService;
        this.secDataService = secDataService;
        this.financialDataService = financialDataService;
    }

    @GetMapping("/{ticker}")
    public Company getCompany(@PathVariable String ticker) {
        return companyService.getCompany(ticker);
    }

    @PostMapping
    public Company createCompany(@RequestBody Company company) {
        return companyService.saveCompany(company);
    }

    @PostMapping("/{ticker}/financials/import")
    public List<FinancialMetric> importFinancials(@PathVariable String ticker) {
        return financialDataService.importCompanyFinancials(ticker);
    }

    @GetMapping("/{ticker}/financials")
    public List<FinancialMetric> getFinancials(@PathVariable String ticker) {
        return financialDataService.getCompanyFinancials(ticker);
    }

    @GetMapping("/{ticker}/financials/normalized")
    public List<NormalizedFinancialMetricResponse>
    getNormalizedFinancials(
            @PathVariable String ticker) {

        return financialDataService
                .getNormalizedFinancials(ticker);
    }

    @GetMapping("/{ticker}/financials/quarterly")
    public List<NormalizedFinancialMetricResponse>
    getQuarterlyFinancials(
            @PathVariable String ticker) {

        return financialDataService
                .getNormalizedFinancials(ticker)
                .stream()
                .filter(x -> x.period().matches("Q[1-4]") || "QUARTER".equals(x.period()))
                .toList();
    }

    @GetMapping("/{ticker}/financials/annual")
    public List<NormalizedFinancialMetricResponse>
    getAnnualFinancials(
            @PathVariable String ticker) {

        return financialDataService
                .getNormalizedFinancials(ticker)
                .stream()
                .filter(x -> "FY".equals(x.period()))
                .toList();
    }

    @GetMapping("/{ticker}/financials/income-statement")
    public List<IncomeStatementResponse> getIncomeStatement(
            @PathVariable String ticker) {

        return financialDataService
                .getIncomeStatement(ticker);
    }

    @GetMapping("/{ticker}/financials/balance-sheet")
    public List<BalanceSheetResponse> getBalanceSheet(
            @PathVariable String ticker) {

        return financialDataService
                .getBalanceSheet(ticker);
    }

    @GetMapping("/{ticker}/financials/cash-flow")
    public List<CashFlowResponse> getCashFlow(
            @PathVariable String ticker) {

        return financialDataService
                .getCashFlow(ticker);
    }
}