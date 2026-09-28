package com.equitylens.repository;

import com.equitylens.entity.MarketPriceImport;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface MarketPriceImportRepository extends JpaRepository<MarketPriceImport, Long> {
    Optional<MarketPriceImport> findByCompanyIdAndSource(Long companyId, String source);
}
