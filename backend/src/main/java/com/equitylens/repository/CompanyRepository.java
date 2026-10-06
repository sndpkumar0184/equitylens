package com.equitylens.repository;

import com.equitylens.entity.Company;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;
import java.util.Optional;

public interface CompanyRepository extends JpaRepository<Company, Long> {
    Optional<Company> findByTickerIgnoreCase(String ticker);

    java.util.List<Company> findByTickerContainingIgnoreCaseOrNameContainingIgnoreCaseOrderByTickerAsc(
            String ticker, String name, org.springframework.data.domain.Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Company c where c.id = :id")
    Optional<Company> lockById(@Param("id") Long id);

    // PostgreSQL resolves simultaneous requests without a failed transaction or duplicate row.
    @Transactional
    @Modifying
    @Query(value = "insert into companies (ticker, cik, name) values (:ticker, :cik, :name) on conflict do nothing", nativeQuery = true)
    int insertIfAbsent(@Param("ticker") String ticker, @Param("cik") String cik, @Param("name") String name);
}
