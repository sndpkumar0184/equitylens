package com.equitylens.repository;

import com.equitylens.entity.MarketPrice;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface MarketPriceRepository
        extends JpaRepository<MarketPrice, Long> {

    Optional<MarketPrice> findByCompanyIdAndPriceDate(
            Long companyId,
            LocalDate priceDate
    );

    List<MarketPrice> findByCompanyIdOrderByPriceDateDesc(
            Long companyId
    );
}
