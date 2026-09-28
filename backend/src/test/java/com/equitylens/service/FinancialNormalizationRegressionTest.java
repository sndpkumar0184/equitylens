package com.equitylens.service;

import com.equitylens.dto.NormalizedFinancialMetricResponse;
import com.equitylens.entity.FinancialMetric;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FinancialNormalizationRegressionTest {
    private final FinancialMetricNormalizer normalizer = new FinancialMetricNormalizer();

    static FinancialMetric fact(String name, String start, String end, String value) {
        FinancialMetric m = new FinancialMetric();
        m.setMetric(name);
        m.setPeriodStart(start == null ? null : LocalDate.parse(start));
        m.setPeriodEnd(LocalDate.parse(end));
        m.setValue(new BigDecimal(value));
        m.setUnit("USD");
        m.setForm("10-Q");
        m.setFilingDate(m.getPeriodEnd().plusDays(40));
        return m;
    }

    @Test
    void acceptsAllStatementFormsAndSelectsLatestRegularFilingAfterAmendment() {
        var observations = new ArrayList<FinancialMetric>();
        int version = 1;
        for (String form : List.of("10-Q", "10-Q/A", "10-K/A", "10-K")) {
            var m = fact("net_income", "2025-01-01", "2025-03-31", String.valueOf(version));
            m.setForm(form);
            m.setFilingDate(LocalDate.of(2025, 5, version++));
            observations.add(m);
        }
        assertEquals(new BigDecimal("4"), normalizer.normalize(observations).getFirst().value());
        var expected = normalizer.normalize(observations);
        Collections.reverse(observations);
        assertEquals(expected, normalizer.normalize(observations));
        assertEquals(1, expected.size());
    }

    @Test
    void amendmentWinsSameDateTie() {
        var original = fact("assets", null, "2025-03-31", "100");
        var amendment = fact("assets", null, "2025-03-31", "90");
        amendment.setForm("10-Q/A");
        assertEquals(new BigDecimal("90"), normalizer.normalize(List.of(original, amendment)).getFirst().value());
    }

    @Test
    void invalidLatestObservationsDoNotReplaceValidFacts() {
        var valid = fact("revenue", "2025-01-01", "2025-03-31", "100");
        var invalid = fact("revenue", "2025-01-01", "2025-03-31", "999");
        invalid.setFilingDate(LocalDate.of(2025, 7, 1));
        invalid.setValue(null);
        var unsupported = fact("revenue", "2025-01-01", "2025-03-31", "999");
        unsupported.setForm("8-K");
        var missingDate = fact("revenue", "2025-01-01", "2025-03-31", "999");
        missingDate.setFilingDate(null);
        var reversed = fact("revenue", "2025-04-01", "2025-03-31", "999");
        var input = new ArrayList<>(List.of(valid, invalid, unsupported, missingDate, reversed));
        input.add(null);
        var result = normalizer.normalize(input);
        assertEquals(1, result.size());
        assertEquals(new BigDecimal("100"), result.getFirst().value());
    }

    @Test
    void preservesAnnualAndYtdWhileDerivingFiscalQuartersAcrossCalendarYears() {
        var q1 = fact("revenue", "2024-10-01", "2024-12-31", "100");
        var half = fact("revenue", "2024-10-01", "2025-03-31", "250");
        var nine = fact("revenue", "2024-10-01", "2025-06-30", "420");
        var annual = fact("revenue", "2024-10-01", "2025-09-30", "600");
        annual.setForm("10-K/A");
        q1.setFrame("CY2024Q4"); // Calendar frame is not fiscal Q4.
        var result = normalizer.normalize(List.of(q1, half, nine, annual));
        assertEquals(7, result.size());
        assertEquals(new BigDecimal("600"), period(result, "FY").value());
        assertEquals(2, result.stream().filter(m -> m.period().equals("YTD")).count());
        assertEquals(LocalDate.of(2025, 1, 1), period(result, "Q2").periodStart());
        assertEquals(LocalDate.of(2025, 9, 30), period(result, "Q4").periodEnd());
        assertEquals(new BigDecimal("180"), period(result, "Q4").value());
        assertEquals(new BigDecimal("100"), period(result, "Q1").value());
    }

    @Test
    void derivesFourthQuarterWithoutFirstTwoQuartersAndPreservesWeekBasedDates() {
        var nine = fact("net_income", "2023-10-01", "2024-06-29", "-20");
        var annual = fact("net_income", "2023-10-01", "2024-09-28", "-10");
        annual.setForm("10-K");
        var result = normalizer.normalize(List.of(nine, annual));
        assertEquals(3, result.size());
        var q4 = period(result, "Q4");
        assertEquals(new BigDecimal("10"), q4.value());
        assertEquals(LocalDate.of(2024, 6, 30), q4.periodStart());
        assertEquals(LocalDate.of(2024, 9, 28), q4.periodEnd());
    }

    @Test
    void keepsStandaloneQuarterWithoutYtdAndDoesNotInventFiscalNumber() {
        var direct = fact("net_income", "2025-04-01", "2025-06-30", "42");
        var result = normalizer.normalize(List.of(direct));
        assertEquals(1, result.size());
        assertEquals("QUARTER", result.getFirst().period());
        assertEquals(direct.getPeriodStart(), result.getFirst().periodStart());
    }

    @Test
    void latestDirectQuarterWinsOverOlderDerivedValueWithoutDuplicates() {
        var q1 = fact("revenue", "2025-01-01", "2025-03-31", "100");
        var half = fact("revenue", "2025-01-01", "2025-06-30", "250");
        var direct = fact("revenue", "2025-04-01", "2025-06-30", "160");
        direct.setForm("10-Q/A");
        direct.setFilingDate(LocalDate.of(2025, 9, 1));
        var result = normalizer.normalize(List.of(q1, half, direct));
        assertEquals(3, result.size());
        assertEquals(new BigDecimal("160"), period(result, "Q2").value());
    }

    @Test
    void newerCumulativeInputsCanReplaceOlderDirectQuarter() {
        var q1 = fact("revenue", "2025-01-01", "2025-03-31", "100");
        var half = fact("revenue", "2025-01-01", "2025-06-30", "250");
        var direct = fact("revenue", "2025-04-01", "2025-06-30", "160");
        q1.setFilingDate(LocalDate.of(2025, 9, 1));
        half.setFilingDate(LocalDate.of(2025, 9, 1));
        assertEquals(new BigDecimal("150"), period(normalizer.normalize(List.of(q1, half, direct)), "Q2").value());
    }

    @Test
    void unitsNeverDeduplicateOrSubtractAcrossCurrencies() {
        var q1 = fact("revenue", "2025-01-01", "2025-03-31", "100");
        var euroQ1 = fact("revenue", "2025-01-01", "2025-03-31", "90");
        euroQ1.setUnit("EUR");
        var half = fact("revenue", "2025-01-01", "2025-06-30", "250");
        half.setUnit("GBP");
        var result = normalizer.normalize(List.of(q1, euroQ1, half));
        assertEquals(3, result.size());
        assertFalse(result.stream().anyMatch(m -> m.period().equals("Q2")));
        assertEquals(3, result.stream().map(NormalizedFinancialMetricResponse::unit).distinct().count());
    }

    @Test
    void mismatchedFiscalStartsAreNotSubtracted() {
        var q1 = fact("revenue", "2025-01-02", "2025-03-31", "100");
        var half = fact("revenue", "2025-01-01", "2025-06-30", "250");
        assertFalse(normalizer.normalize(List.of(q1, half)).stream().anyMatch(m -> m.period().equals("Q2")));
    }

    @Test
    void pointInTimeDeduplicationIgnoresStartDateAndKeepsCurrenciesSeparate() {
        var original = fact("assets", null, "2025-03-31", "100");
        var latest = fact("assets", "2025-01-01", "2025-03-31", "110");
        latest.setFilingDate(LocalDate.of(2025, 8, 1));
        var euro = fact("assets", null, "2025-03-31", "90");
        euro.setUnit("EUR");
        var result = normalizer.normalize(List.of(original, latest, euro));
        assertEquals(2, result.size());
        assertTrue(result.stream().allMatch(m -> m.period().equals("POINT_IN_TIME") && m.periodStart() == null));
        assertEquals(new BigDecimal("110"), result.stream().filter(m -> m.unit().equals("USD")).findFirst().orElseThrow().value());
    }

    @Test
    void unsupportedDurationIsNotMisclassifiedAsYtd() {
        var stub = fact("revenue", "2025-01-01", "2025-01-31", "10");
        stub.setFrame("CY2025Q1");
        assertEquals("UNKNOWN", normalizer.normalize(List.of(stub)).getFirst().period());
    }

    @Test
    void conflictingFiscalCalendarsDoNotInventQuarterNumber() {
        var direct = fact("revenue", "2025-04-01", "2025-06-30", "100");
        var calendar = fact("net_income", "2025-01-01", "2025-12-31", "400");
        var shifted = fact("net_income", "2024-10-01", "2025-09-30", "390");
        var result = normalizer.normalize(List.of(direct, calendar, shifted));
        assertEquals("QUARTER", result.stream().filter(m -> m.metric().equals("revenue"))
                .findFirst().orElseThrow().period());
        assertEquals(result, normalizer.normalize(List.of(shifted, calendar, direct)));
    }

    @Test
    void recognizesFourteenWeekFourthQuarterOfFiftyThreeWeekYear() {
        var nine = fact("revenue", "2023-01-01", "2023-09-30", "300");
        var annual = fact("revenue", "2023-01-01", "2024-01-06", "450");
        annual.setForm("10-K/A");
        var direct = fact("revenue", "2023-10-01", "2024-01-06", "155");
        direct.setFilingDate(annual.getFilingDate().plusDays(1));
        var result = normalizer.normalize(List.of(nine, annual, direct));
        assertEquals(3, result.size());
        assertEquals(new BigDecimal("155"), period(result, "Q4").value());
        assertEquals(new BigDecimal("450"), period(result, "FY").value());
    }

    @Test
    void rollingYearInQuarterlyFilingDoesNotBecomeFiscalYearOrQuarterAnchor() {
        var annual = fact("revenue", "2025-01-01", "2025-12-31", "400");
        annual.setForm("10-K");
        var rolling = fact("net_income", "2025-07-01", "2026-06-30", "80");
        rolling.setForm("10-Q");
        rolling.setFilingDate(LocalDate.of(2026, 8, 1));
        var quarter = fact("revenue", "2025-07-01", "2025-09-30", "100");
        var result = normalizer.normalize(List.of(annual, rolling, quarter));
        assertEquals("ROLLING_YEAR", result.stream().filter(m -> m.metric().equals("net_income")).findFirst().orElseThrow().period());
        assertEquals(new BigDecimal("400"), period(result, "FY").value());
        assertEquals(new BigDecimal("100"), period(result, "Q3").value());
    }

    private NormalizedFinancialMetricResponse period(List<NormalizedFinancialMetricResponse> result, String label) {
        return result.stream().filter(m -> m.period().equals(label)).findFirst().orElseThrow();
    }
}
