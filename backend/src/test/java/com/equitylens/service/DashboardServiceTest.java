package com.equitylens.service;

import com.equitylens.dto.NormalizedFinancialMetricResponse;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class DashboardServiceTest {
    private final DashboardService service = new DashboardService(null, null, null);

    @Test void calculatesComparableAnnualGrowthAndReportedMargins() {
        var data = service.build(List.of(fy("revenue", 2024, "100"), fy("revenue", 2025, "120"),
                fy("gross_profit", 2025, "48"), fy("operating_income", 2025, "24"), fy("net_income", 2025, "12")), true);
        assertNumber("20", data.growth().getLast().revenueGrowth());
        assertNumber("40", data.profitability().getLast().grossMargin());
        assertNumber("20", data.profitability().getLast().operatingMargin());
        assertNumber("10", data.profitability().getLast().netMargin());
        assertNull(data.growth().getFirst().revenueGrowth());
    }

    @Test void capexIsCashSpentRegardlessOfPresentationSign() {
        for (String capex : List.of("12", "-12", "0")) {
            var data = service.build(List.of(fy("operating_cash_flow", 2025, "40"), fy("capital_expenditures", 2025, capex)), true);
            assertEquals(new BigDecimal(capex).abs(), data.cashFlow().getFirst().capitalExpenditures());
            assertEquals(new BigDecimal("40").subtract(new BigDecimal(capex).abs()), data.summary().freeCashFlow().value());
        }
    }

    @Test void missingSourcesStayMissingInsteadOfBeingInvented() {
        var data = service.build(List.of(fy("revenue", 2025, "100"), fy("cost_of_revenue", 2025, "60"),
                fy("operating_cash_flow", 2025, "40"), point("cash", 2025, "10"), point("long_term_debt", 2025, "20")), true);
        assertNull(data.profitability().getFirst().grossMargin());
        assertNull(data.profitability().getFirst().operatingMargin());
        assertNull(data.summary().freeCashFlow().value());
        assertNull(data.balances().getFirst().debt());
        assertNull(data.summary().totalLiabilities().value());
        assertNumber("10", data.balances().getFirst().cash());
    }

    @Test void debtRequiresAllDisjointComponentsAtSameDateAndUnit() {
        var data = service.build(List.of(fy("revenue", 2025, "100"), point("short_term_borrowings", 2025, "0"),
                point("current_debt", 2025, "5"), point("long_term_debt", 2025, "20"), point("liabilities", 2025, "60")), true);
        assertNumber("25", data.balances().getFirst().debt());
        assertNumber("60", data.summary().totalLiabilities().value());
        var mismatch = service.build(List.of(fy("revenue", 2025, "100"), point("short_term_borrowings", 2024, "0"),
                point("current_debt", 2025, "5"), point("long_term_debt", 2025, "20")), true);
        assertNull(mismatch.balances().getFirst().debt());
    }

    @Test void doesNotCompareAcrossMissingYearsOrDivideByZero() {
        var data = service.build(List.of(fy("revenue", 2023, "100"), fy("revenue", 2025, "120")), true);
        assertNull(data.growth().getLast().revenueGrowth());
        data = service.build(List.of(fy("revenue", 2024, "0"), fy("revenue", 2025, "120")), true);
        assertNull(data.growth().getLast().revenueGrowth());
        assertNull(DashboardCalculations.margin(BigDecimal.TEN, BigDecimal.ZERO));
    }

    @Test void quarterlyGrowthIsYearOverYearNotQuarterOverQuarter() {
        var facts = List.of(flow("revenue", "Q1", "2024-01-01", "2024-03-31", "100"),
                flow("revenue", "Q4", "2024-10-01", "2024-12-31", "500"),
                flow("revenue", "Q1", "2025-01-01", "2025-03-31", "120"), fy("revenue", 2024, "900"));
        var quarterly = service.build(facts, false);
        assertNumber("20", quarterly.growth().getLast().revenueGrowth());
        assertEquals(3, quarterly.income().size());
        assertEquals(1, service.build(facts, true).income().size());
    }

    @Test void supportsFiftyThreeWeekFiscalCalendarButRejectsShiftedDurations() {
        var data = service.build(List.of(flow("revenue", "FY", "2023-01-01", "2024-01-06", "100"),
                flow("revenue", "FY", "2024-01-07", "2025-01-04", "110")), true);
        assertNumber("10", data.growth().getLast().revenueGrowth());
        data = service.build(List.of(flow("revenue", "FY", "2023-01-01", "2023-12-31", "100"),
                flow("revenue", "FY", "2024-02-01", "2024-12-31", "110")), true);
        assertNull(data.growth().getLast().revenueGrowth());
    }

    @Test void latestAvailableCardsRetainTheirOwnActualReportingDates() {
        var data = service.build(List.of(fy("revenue", 2024, "100"), fy("net_income", 2025, "20"), point("cash", 2024, "10")), true);
        assertEquals(LocalDate.of(2024, 12, 31), data.summary().revenue().periodEnd());
        assertEquals(LocalDate.of(2025, 12, 31), data.summary().netIncome().periodEnd());
        assertEquals(LocalDate.of(2024, 12, 31), data.summary().cash().periodEnd());
    }

    @Test void limitsDisplayButRetainsPriorYearForFirstVisibleGrowthPoint() {
        var facts = new ArrayList<NormalizedFinancialMetricResponse>();
        for (int year = 2015; year <= 2025; year++) facts.add(fy("revenue", year, "100"));
        var data = service.build(facts, true);
        assertEquals(8, data.income().size());
        assertNumber("0", data.growth().getFirst().revenueGrowth());
    }

    @Test void filtersOtherCurrenciesYtdAndRollingYearsAndHandlesEmptyData() {
        var data = service.build(List.of(flow("revenue", "YTD", "2025-01-01", "2025-06-30", "10"),
                flow("revenue", "ROLLING_YEAR", "2024-07-01", "2025-06-30", "20"),
                new NormalizedFinancialMetricResponse("revenue", "FY", LocalDate.of(2025,1,1), LocalDate.of(2025,12,31), BigDecimal.TEN, "EUR")), true);
        assertTrue(data.income().isEmpty());
        assertNull(data.summary().revenue().value());
        assertTrue(service.build(List.of(), false).balances().isEmpty());
    }

    private NormalizedFinancialMetricResponse fy(String metric, int year, String value) {
        return flow(metric, "FY", year + "-01-01", year + "-12-31", value);
    }
    private NormalizedFinancialMetricResponse point(String metric, int year, String value) {
        return new NormalizedFinancialMetricResponse(metric, "POINT_IN_TIME", null, LocalDate.of(year, 12, 31), new BigDecimal(value), "USD");
    }
    private NormalizedFinancialMetricResponse flow(String metric, String period, String start, String end, String value) {
        return new NormalizedFinancialMetricResponse(metric, period, LocalDate.parse(start), LocalDate.parse(end), new BigDecimal(value), "USD");
    }
    private void assertNumber(String expected, BigDecimal actual) {
        assertNotNull(actual);
        assertEquals(0, new BigDecimal(expected).compareTo(actual));
    }
}
