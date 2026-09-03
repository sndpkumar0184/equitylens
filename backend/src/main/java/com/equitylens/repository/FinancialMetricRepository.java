package com.equitylens.repository;

import com.equitylens.entity.FinancialMetric;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FinancialMetricRepository
        extends JpaRepository<FinancialMetric, Long> {

    List<FinancialMetric> findByCompanyId(Long companyId);

    List<FinancialMetric> findByCompanyIdAndMetric(
            Long companyId,
            String metric);
}