package com.equitylens.repository;

import com.equitylens.entity.Company;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CompanyRepository extends JpaRepository<Company, Long> {

    Optional<Company> findByTickerIgnoreCase(String ticker);
}