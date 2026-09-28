package com.equitylens.dto;

import com.equitylens.entity.Company;

public record CompanyResponse(String ticker, String cik, String name, String sector, String industry, Long id) {
    public static CompanyResponse from(Company company) {
        return new CompanyResponse(company.getTicker(), company.getCik(), company.getName(),
                company.getSector(), company.getIndustry(), company.getId());
    }
}
