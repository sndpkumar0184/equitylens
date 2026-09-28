package com.equitylens.service;

import com.equitylens.dto.IncomeStatementResponse;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.temporal.ChronoUnit;
import java.util.List;

final class DashboardCalculations {
    private DashboardCalculations() {}

    static BigDecimal margin(BigDecimal numerator, BigDecimal revenue) {
        if (numerator == null || revenue == null || revenue.signum() <= 0) return null;
        return numerator.multiply(BigDecimal.valueOf(100)).divide(revenue, 4, RoundingMode.HALF_UP);
    }

    static BigDecimal growth(IncomeStatementResponse current, List<IncomeStatementResponse> history) {
        if (current.revenue() == null) return null;
        var comparable = history.stream().filter(prior -> comparable(current, prior)).toList();
        if (comparable.size() != 1 || comparable.getFirst().revenue() == null
                || comparable.getFirst().revenue().signum() <= 0) return null;
        BigDecimal previous = comparable.getFirst().revenue();
        return current.revenue().subtract(previous).multiply(BigDecimal.valueOf(100))
                .divide(previous, 4, RoundingMode.HALF_UP);
    }

    private static boolean comparable(IncomeStatementResponse current, IncomeStatementResponse prior) {
        if (!current.unit().equals(prior.unit())) return false;
        boolean annual = "FY".equals(current.period());
        if (annual != "FY".equals(prior.period())) return false;
        if (!annual && !"QUARTER".equals(current.period()) && !"QUARTER".equals(prior.period())
                && !current.period().equals(prior.period())) return false;
        // Require both boundaries to move by roughly one year (supports 52/53-week calendars).
        long startGap = ChronoUnit.DAYS.between(prior.periodStart(), current.periodStart());
        long endGap = ChronoUnit.DAYS.between(prior.periodEnd(), current.periodEnd());
        return startGap >= 350 && startGap <= 380 && endGap >= 350 && endGap <= 380
                && Math.abs(startGap - endGap) <= 8;
    }

    static BigDecimal capexSpend(BigDecimal reported) {
        // PaymentsToAcquirePropertyPlantAndEquipment is an outflow magnitude in SEC XBRL.
        // Some observations use a negative cash-flow presentation. Display cash spent as positive
        // in either convention, and subtract that magnitude once. Never turn missing CapEx into zero.
        return reported == null ? null : reported.abs();
    }

    static BigDecimal freeCashFlow(BigDecimal operatingCashFlow, BigDecimal capexSpend) {
        return operatingCashFlow == null || capexSpend == null ? null : operatingCashFlow.subtract(capexSpend);
    }

    static BigDecimal debt(BigDecimal shortTermBorrowings, BigDecimal currentLongTermDebt, BigDecimal noncurrentLongTermDebt) {
        // LongTermDebtCurrent + LongTermDebtNoncurrent alone omits short-term borrowings.
        // Require all three disjoint components at exactly the same date/unit; missing is not zero.
        if (shortTermBorrowings == null || currentLongTermDebt == null || noncurrentLongTermDebt == null
                || shortTermBorrowings.signum() < 0 || currentLongTermDebt.signum() < 0 || noncurrentLongTermDebt.signum() < 0) return null;
        return shortTermBorrowings.add(currentLongTermDebt).add(noncurrentLongTermDebt);
    }
}
