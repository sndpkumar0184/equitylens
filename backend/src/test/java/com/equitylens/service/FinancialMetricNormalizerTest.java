package com.equitylens.service;

import com.equitylens.dto.NormalizedFinancialMetricResponse;
import com.equitylens.entity.FinancialMetric;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class FinancialMetricNormalizerTest {

    private final FinancialMetricNormalizer normalizer =
            new FinancialMetricNormalizer();

    @Test
    void shouldNormalizeYtdFlowMetricsIntoStandaloneQuarters() {

        FinancialMetric q1 = metric(
                "revenue",
                "2026-01-01",
                "2026-03-31",
                "10-Q",
                "CY2026Q1",
                "100"
        );

        FinancialMetric q2Ytd = metric(
                "revenue",
                "2026-01-01",
                "2026-06-30",
                "10-Q",
                null,
                "250"
        );

        FinancialMetric q3Ytd = metric(
                "revenue",
                "2026-01-01",
                "2026-09-30",
                "10-Q",
                null,
                "420"
        );

        FinancialMetric fy = metric(
                "revenue",
                "2026-01-01",
                "2026-12-31",
                "10-K",
                "CY2026",
                "600"
        );

        List<NormalizedFinancialMetricResponse> result =
                normalizer.normalize(
                        List.of(q1, q2Ytd, q3Ytd, fy)
                );

        Map<String, NormalizedFinancialMetricResponse> byPeriod =
                result.stream().filter(r -> !r.period().equals("YTD"))
                        .collect(Collectors.toMap(
                                NormalizedFinancialMetricResponse::period,
                                Function.identity()
                        ));

        assertEquals(7, result.size());

        assertEquals(
                new BigDecimal("100"),
                byPeriod.get("Q1").value()
        );

        assertEquals(
                new BigDecimal("150"),
                byPeriod.get("Q2").value()
        );

        assertEquals(
                new BigDecimal("170"),
                byPeriod.get("Q3").value()
        );

        assertEquals(
                new BigDecimal("180"),
                byPeriod.get("Q4").value()
        );
    }

    @Test
    void shouldKeepPointInTimeMetricsUnchanged() {

        FinancialMetric q1 = metric(
                "assets",
                "2026-01-01",
                "2026-03-31",
                "10-Q",
                null,
                "1000"
        );

        FinancialMetric q2 = metric(
                "assets",
                "2026-01-01",
                "2026-06-30",
                "10-Q",
                null,
                "1200"
        );

        FinancialMetric q3 = metric(
                "assets",
                "2026-01-01",
                "2026-09-30",
                "10-Q",
                null,
                "1400"
        );

        List<NormalizedFinancialMetricResponse> result =
                normalizer.normalize(
                        List.of(q1, q2, q3)
                );

        assertEquals(3, result.size());

        Map<String, NormalizedFinancialMetricResponse> byDate =
                result.stream()
                        .collect(Collectors.toMap(
                                r -> r.periodEnd().toString(),
                                Function.identity()
                        ));

        assertEquals(
                new BigDecimal("1000"),
                byDate.get("2026-03-31").value()
        );

        assertEquals(
                new BigDecimal("1200"),
                byDate.get("2026-06-30").value()
        );

        assertEquals(
                new BigDecimal("1400"),
                byDate.get("2026-09-30").value()
        );
    }

    @Test
    void shouldCalculateQ4FromFiscalYear() {

        FinancialMetric q1 = metric(
                "net_income",
                "2026-01-01",
                "2026-03-31",
                "10-Q",
                "CY2026Q1",
                "20"
        );

        FinancialMetric q2Ytd = metric(
                "net_income",
                "2026-01-01",
                "2026-06-30",
                "10-Q",
                null,
                "50"
        );

        FinancialMetric q3Ytd = metric(
                "net_income",
                "2026-01-01",
                "2026-09-30",
                "10-Q",
                null,
                "90"
        );

        FinancialMetric fy = metric(
                "net_income",
                "2026-01-01",
                "2026-12-31",
                "10-K",
                "CY2026",
                "140"
        );

        List<NormalizedFinancialMetricResponse> result =
                normalizer.normalize(
                        List.of(q1, q2Ytd, q3Ytd, fy)
                );

        NormalizedFinancialMetricResponse q4 =
                result.stream()
                        .filter(r -> r.period().equals("Q4"))
                        .findFirst()
                        .orElseThrow();

        /*
         * Q1 = 20
         * Q2 = 50 - 20 = 30
         * Q3 = 90 - 50 = 40
         *
         * Q4 = 140 - 20 - 30 - 40
         *     = 50
         */
        assertEquals(
                new BigDecimal("50"),
                q4.value()
        );

        assertEquals(
                LocalDate.of(2026, 10, 1),
                q4.periodStart()
        );

        assertEquals(
                LocalDate.of(2026, 12, 31),
                q4.periodEnd()
        );
    }

    @Test
    void shouldNotCreateStandaloneQ2WithoutQ1() {

        FinancialMetric q2Ytd = metric(
                "revenue",
                "2026-01-01",
                "2026-06-30",
                "10-Q",
                null,
                "250"
        );

        List<NormalizedFinancialMetricResponse> result =
                normalizer.normalize(
                        List.of(q2Ytd)
                );

        assertEquals(1, result.size());
        assertEquals("YTD", result.getFirst().period());
    }

    @Test
    void shouldReturnEmptyListForEmptyInput() {

        assertTrue(
                normalizer.normalize(List.of()).isEmpty()
        );

        assertTrue(
                normalizer.normalize(null).isEmpty()
        );
    }

    @Test
    void shouldPreferAmendedFiling() {

        FinancialMetric original = metric(
                "assets",
                "2026-01-01",
                "2026-03-31",
                "10-Q",
                null,
                "1000"
        );

        original.setFilingDate(
                LocalDate.of(2026, 5, 1)
        );

        FinancialMetric amended = metric(
                "assets",
                "2026-01-01",
                "2026-03-31",
                "10-Q/A",
                null,
                "1100"
        );

        amended.setFilingDate(
                LocalDate.of(2026, 6, 1)
        );

        List<NormalizedFinancialMetricResponse> result =
                normalizer.normalize(
                        List.of(original, amended)
                );

        assertEquals(1, result.size());

        assertEquals(
                new BigDecimal("1100"),
                result.get(0).value()
        );
    }

    private FinancialMetric metric(
            String metric,
            String periodStart,
            String periodEnd,
            String form,
            String frame,
            String value) {

        FinancialMetric result = new FinancialMetric();

        result.setMetric(metric);
        result.setPeriodStart(
                LocalDate.parse(periodStart)
        );
        result.setPeriodEnd(
                LocalDate.parse(periodEnd)
        );
        result.setForm(form);
        result.setFrame(frame);
        result.setValue(
                new BigDecimal(value)
        );
        result.setUnit("USD");
        result.setFilingDate(
                LocalDate.parse(periodEnd).plusDays(30)
        );

        return result;
    }
}
