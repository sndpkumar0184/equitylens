package com.equitylens.repository;

import com.equitylens.entity.MarketData;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MarketDataRepository
        extends JpaRepository<MarketData, Long> {

    Optional<MarketData> findByCompanyId(Long companyId);

    void deleteByCompanyId(Long companyId);
}