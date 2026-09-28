package com.equitylens.repository;

import com.equitylens.entity.FinancialMetric;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Set;
import java.time.LocalDate;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FinancialMetricRepository
        extends JpaRepository<FinancialMetric, Long> {

    List<FinancialMetric> findByCompanyIdOrderByPeriodEndDesc(
            Long companyId
    );

    List<FinancialMetric> findByCompanyIdAndMetric(
            Long companyId,
            String metric
    );

    long countByCompanyId(Long companyId);

    @Query("select distinct f.metric from FinancialMetric f where f.company.id = :companyId")
    Set<String> findMetricNames(@Param("companyId") Long companyId);

    @Query("select max(f.periodEnd) from FinancialMetric f where f.company.id = :companyId and f.unit = :unit and f.metric in :metrics")
    LocalDate latestDashboardPeriod(@Param("companyId") Long companyId, @Param("unit") String unit, @Param("metrics") Set<String> metrics);

    List<FinancialMetric> findByCompanyIdAndMetricInAndUnitAndPeriodEndGreaterThanEqualOrderByPeriodEndDesc(
            Long companyId, Set<String> metrics, String unit, LocalDate since);

    void deleteByCompanyId(Long companyId);
}