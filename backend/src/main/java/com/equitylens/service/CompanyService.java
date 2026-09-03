package com.equitylens.service;

import com.equitylens.entity.Company;
import com.equitylens.repository.CompanyRepository;
import org.springframework.stereotype.Service;

@Service
public class CompanyService {

    private final CompanyRepository companyRepository;

    public CompanyService(CompanyRepository companyRepository) {
        this.companyRepository = companyRepository;
    }

    public Company getCompany(String ticker) {

        return companyRepository
                .findByTickerIgnoreCase(ticker)
                .orElseThrow(() ->
                        new RuntimeException("Company not found: " + ticker));
    }

    public Company saveCompany(Company company) {
        return companyRepository.save(company);
    }
}