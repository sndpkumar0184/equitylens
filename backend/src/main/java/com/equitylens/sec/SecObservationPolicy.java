package com.equitylens.sec;

import com.equitylens.entity.FinancialMetric;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.Set;

/** Shared rules for selecting comparable SEC facts, independent of input order. */
public final class SecObservationPolicy {
    private static final Set<String> FORMS = Set.of("10-K", "10-Q", "10-K/A", "10-Q/A");
    private static final Set<String> FLOWS = Set.of(
            "revenue", "cost_of_revenue", "gross_profit", "operating_income", "net_income",
            "operating_cash_flow", "capital_expenditures", "investing_cash_flow",
            "financing_cash_flow", "free_cash_flow");

    private SecObservationPolicy() {}

    public static boolean isFlow(String metric) {
        return FLOWS.contains(metric);
    }

    public static boolean isValid(FinancialMetric m) {
        return m != null && m.getMetric() != null && !m.getMetric().isBlank()
                && m.getForm() != null && FORMS.contains(m.getForm())
                && m.getValue() != null && m.getUnit() != null && !m.getUnit().isBlank()
                && m.getPeriodEnd() != null && m.getFilingDate() != null
                && !m.getFilingDate().isBefore(m.getPeriodEnd())
                && (!isFlow(m.getMetric()) || (m.getPeriodStart() != null
                    && !m.getPeriodStart().isAfter(m.getPeriodEnd())));
    }

    public static int quarters(FinancialMetric m) {
        return quarters(m.getPeriodStart(), m.getPeriodEnd());
    }

    /** Duration classification also supports issuers using 52/53-week fiscal years. */
    public static int quarters(LocalDate start, LocalDate end) {
        if (start == null || end == null) return 0;
        long days = ChronoUnit.DAYS.between(start, end) + 1;
        if (days >= 80 && days <= 100) return 1;
        if (days >= 160 && days <= 200) return 2;
        if (days >= 240 && days <= 290) return 3;
        if (days >= 350 && days <= 380) return 4;
        return 0;
    }

    // Date takes precedence over amendment status: a later regular filing may restate a fact.
    // Remaining fields only resolve same-date ties deterministically, without implying freshness.
    public static final Comparator<FinancialMetric> LATEST = Comparator
            .comparing(FinancialMetric::getFilingDate)
            .thenComparing(m -> m.getForm().endsWith("/A"))
            .thenComparing(FinancialMetric::getForm)
            .thenComparing(m -> m.getFrame() == null ? "" : m.getFrame())
            .thenComparing(FinancialMetric::getValue);

    public static FinancialMetric latest(FinancialMetric a, FinancialMetric b) {
        return LATEST.compare(a, b) >= 0 ? a : b;
    }

    public record PeriodKey(String metric, String unit, LocalDate start, LocalDate end) {
        public static PeriodKey of(FinancialMetric m) {
            return new PeriodKey(m.getMetric(), m.getUnit(),
                    isFlow(m.getMetric()) ? m.getPeriodStart() : null, m.getPeriodEnd());
        }
    }
}
