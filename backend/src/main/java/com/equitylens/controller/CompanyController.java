package com.equitylens.controller;

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
}