package com.equitylens.service;

import com.equitylens.entity.Company;
import com.equitylens.repository.CompanyRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CompanyService {
    private final CompanyRepository companyRepository;
    private final SecDataService secDataService;

    public CompanyService(CompanyRepository companyRepository, SecDataService secDataService) {
        this.companyRepository = companyRepository;
        this.secDataService = secDataService;
    }

    public Company getCompany(String input) {
        String ticker = Ticker.normalize(input);
        return companyRepository.findByTickerIgnoreCase(ticker).orElseGet(() -> {
            var resolved = secDataService.resolveTicker(ticker);
            companyRepository.insertIfAbsent(ticker, resolved.cik(), resolved.name());
            return companyRepository.findByTickerIgnoreCase(ticker).orElseThrow(() ->
                    new ResponseStatusException(HttpStatus.CONFLICT, "Could not register this ticker; please try again"));
        });
    }

    public Company saveCompany(Company company) {
        // Preserve the existing endpoint, but resolve identity through SEC and never overwrite it from input.
        Company stored = getCompany(company.getTicker());
        stored.setSector(company.getSector());
        stored.setIndustry(company.getIndustry());
        return companyRepository.save(stored);
    }
}
