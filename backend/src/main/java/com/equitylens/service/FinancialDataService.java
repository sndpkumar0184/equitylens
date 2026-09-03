package com.equitylens.service;

import com.equitylens.entity.Company;
import com.equitylens.entity.FinancialMetric;
import com.equitylens.repository.CompanyRepository;
import com.equitylens.repository.FinancialMetricRepository;
import com.equitylens.sec.SecCompanyFacts;
import com.equitylens.sec.SecFinancialDataParser;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class FinancialDataService {

    private final CompanyRepository companyRepository;
    private final FinancialMetricRepository financialMetricRepository;
    private final SecDataService secDataService;
    private final SecFinancialDataParser parser;

    public FinancialDataService(
            CompanyRepository companyRepository,
            FinancialMetricRepository financialMetricRepository,
            SecDataService secDataService,
            SecFinancialDataParser parser) {

        this.companyRepository = companyRepository;
        this.financialMetricRepository = financialMetricRepository;
        this.secDataService = secDataService;
        this.parser = parser;
    }

    public List<FinancialMetric> importCompanyFinancials(
            String ticker) {

        Company company = companyRepository
                .findByTickerIgnoreCase(ticker)
                .orElseThrow(() ->
                        new RuntimeException(
                                "Company not found: " + ticker
                        ));

        SecCompanyFacts facts =
                secDataService.getCompanyFacts(
                        company.getCik()
                );

        List<FinancialMetric> metrics =
                parser.parse(facts, company);

        return financialMetricRepository.saveAll(metrics);
    }

    public List<FinancialMetric> getCompanyFinancials(String ticker) {
        Company company = companyRepository
                .findByTickerIgnoreCase(ticker)
                .orElseThrow(() -> new RuntimeException("Company not found: " + ticker));

        return financialMetricRepository.findByCompanyId(company.getId());
    }
}