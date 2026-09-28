package com.equitylens.controller;

import com.equitylens.dto.BalanceSheetResponse;
import com.equitylens.dto.CashFlowResponse;
import com.equitylens.dto.IncomeStatementResponse;
import com.equitylens.dto.NormalizedFinancialMetricResponse;
import com.equitylens.entity.Company;
import com.equitylens.dto.CompanyResponse;
import com.equitylens.dto.FinancialMetricResponse;
import com.equitylens.service.CompanyService;
import com.equitylens.service.FinancialDataService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/companies")
public class CompanyController {

    private final CompanyService companyService;
    private final FinancialDataService financialDataService;

    public CompanyController(
            CompanyService companyService,
            FinancialDataService financialDataService) {

        this.companyService = companyService;
        this.financialDataService = financialDataService;
    }

    @GetMapping("/{ticker}")
    public CompanyResponse getCompany(@PathVariable String ticker) {
        return CompanyResponse.from(companyService.getCompany(ticker));
    }

    @PostMapping
    public CompanyResponse createCompany(@RequestBody Company company) {
        return CompanyResponse.from(companyService.saveCompany(company));
    }

    @PostMapping("/{ticker}/financials/import")
    public List<FinancialMetricResponse> importFinancials(@PathVariable String ticker) {
        return financialDataService.importCompanyFinancials(ticker).stream().map(FinancialMetricResponse::from).toList();
    }

    @GetMapping("/{ticker}/financials")
    public List<FinancialMetricResponse> getFinancials(@PathVariable String ticker) {
        return financialDataService.getCompanyFinancials(ticker).stream().map(FinancialMetricResponse::from).toList();
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