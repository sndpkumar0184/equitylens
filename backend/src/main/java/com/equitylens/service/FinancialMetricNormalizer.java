package com.equitylens.service;

import com.equitylens.dto.NormalizedFinancialMetricResponse;
import com.equitylens.entity.FinancialMetric;
import com.equitylens.sec.SecObservationPolicy;
import com.equitylens.sec.SecObservationPolicy.PeriodKey;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class FinancialMetricNormalizer {
    /** Normalize one company's facts. Never combine different units or fiscal start dates. */
    public List<NormalizedFinancialMetricResponse> normalize(List<FinancialMetric> metrics) {
        if (metrics == null || metrics.isEmpty()) return List.of();

        Map<PeriodKey, FinancialMetric> selected = new HashMap<>();
        metrics.stream().filter(SecObservationPolicy::isValid)
                .forEach(m -> selected.merge(PeriodKey.of(m), m, SecObservationPolicy::latest));
        List<FinancialMetric> facts = new ArrayList<>(selected.values());
        // Quarterly filings can also disclose trailing-year flows. They are not fiscal years.
        // Use original observations so a later comparative filing cannot erase a 10-K anchor.
        List<FinancialMetric> annualAnchors = metrics.stream().filter(SecObservationPolicy::isValid)
                .filter(m -> SecObservationPolicy.isFlow(m.getMetric()))
                .filter(m -> m.getForm().startsWith("10-K") && SecObservationPolicy.quarters(m) == 4)
                .toList();
        List<FinancialMetric> fiscalFacts = facts.stream()
                .filter(m -> !isRollingYear(m, annualAnchors)).toList();
        List<NormalizedFinancialMetricResponse> result = new ArrayList<>();
        Map<PeriodKey, Candidate> quarters = new HashMap<>();

        for (FinancialMetric fact : facts) {
            if (!SecObservationPolicy.isFlow(fact.getMetric())) {
                result.add(response(fact, "POINT_IN_TIME", null, fact.getValue()));
                continue;
            }
            int length = SecObservationPolicy.quarters(fact);
            if (isRollingYear(fact, annualAnchors)) {
                result.add(response(fact, "ROLLING_YEAR", fact.getPeriodStart(), fact.getValue()));
            } else if (length == 0) {
                result.add(response(fact, "UNKNOWN", fact.getPeriodStart(), fact.getValue()));
            } else if (length == 1) {
                quarters.put(PeriodKey.of(fact), new Candidate(
                        response(fact, quarterLabel(fact, fiscalFacts), fact.getPeriodStart(), fact.getValue()),
                        fact.getFilingDate(), true));
            } else {
                result.add(response(fact, length == 4 ? "FY" : "YTD",
                        fact.getPeriodStart(), fact.getValue()));
            }
        }

        // Subtract only adjacent cumulative periods with the exact same fiscal start and unit.
        // In particular, Q4 = FY - nine months; Q1/Q2 need not be present to derive Q4.
        for (FinancialMetric total : fiscalFacts) {
            int length = SecObservationPolicy.quarters(total);
            if (!SecObservationPolicy.isFlow(total.getMetric()) || length < 2) continue;
            FinancialMetric previous = facts.stream()
                    .filter(m -> m.getMetric().equals(total.getMetric()) && m.getUnit().equals(total.getUnit()))
                    .filter(m -> total.getPeriodStart().equals(m.getPeriodStart()))
                    .filter(m -> SecObservationPolicy.quarters(m) == length - 1)
                    .filter(m -> SecObservationPolicy.quarters(m.getPeriodEnd().plusDays(1), total.getPeriodEnd()) == 1)
                    .max(Comparator.comparing(FinancialMetric::getPeriodEnd)
                            .thenComparing(SecObservationPolicy.LATEST)).orElse(null);
            if (previous == null) continue;
            LocalDate start = previous.getPeriodEnd().plusDays(1);
            PeriodKey key = new PeriodKey(total.getMetric(), total.getUnit(), start, total.getPeriodEnd());
            // A derivation is only as recent as its least-recent input.
            LocalDate filed = total.getFilingDate().isBefore(previous.getFilingDate())
                    ? total.getFilingDate() : previous.getFilingDate();
            Candidate derived = new Candidate(response(total, "Q" + length, start,
                    total.getValue().subtract(previous.getValue())), filed, false);
            quarters.merge(key, derived, (a, b) -> CANDIDATE_ORDER.compare(a, b) >= 0 ? a : b);
        }
        quarters.values().forEach(q -> result.add(q.response()));
        return result.stream().sorted(Comparator
                .comparing(NormalizedFinancialMetricResponse::periodEnd).reversed()
                .thenComparing(NormalizedFinancialMetricResponse::metric)
                .thenComparing(NormalizedFinancialMetricResponse::unit)
                .thenComparing(NormalizedFinancialMetricResponse::period)
                .thenComparing(NormalizedFinancialMetricResponse::periodStart,
                        Comparator.nullsFirst(Comparator.naturalOrder())))
                .toList();
    }

    private boolean isRollingYear(FinancialMetric fact, List<FinancialMetric> annualAnchors) {
        if (!SecObservationPolicy.isFlow(fact.getMetric()) || SecObservationPolicy.quarters(fact) != 4
                || !fact.getForm().startsWith("10-Q") || annualAnchors.isEmpty()) return false;
        // Reject only when a confirmed fiscal-year end lies strictly inside this twelve-month window.
        // This leaves partial datasets and true comparative fiscal years unchanged.
        boolean matchesAnnual = annualAnchors.stream().anyMatch(a ->
                a.getPeriodStart().equals(fact.getPeriodStart()) && a.getPeriodEnd().equals(fact.getPeriodEnd()));
        return !matchesAnnual && annualAnchors.stream().anyMatch(a ->
                a.getPeriodEnd().isAfter(fact.getPeriodStart()) && a.getPeriodEnd().isBefore(fact.getPeriodEnd()));
    }

    private static final Comparator<Candidate> CANDIDATE_ORDER = Comparator
            .comparing(Candidate::filed).thenComparing(Candidate::direct);

    private record Candidate(NormalizedFinancialMetricResponse response, LocalDate filed, boolean direct) {}

    private String quarterLabel(FinancialMetric quarter, List<FinancialMetric> facts) {
        // CY frames and filing form describe neither the issuer's fiscal quarter nor
        // the period of a comparative observation. Use cumulative period boundaries.
        List<Integer> numbers = facts.stream()
                .filter(m -> SecObservationPolicy.isFlow(m.getMetric()))
                .filter(m -> SecObservationPolicy.quarters(m) >= 2)
                .filter(m -> !quarter.getPeriodStart().isBefore(m.getPeriodStart())
                        && !quarter.getPeriodEnd().isAfter(m.getPeriodEnd()))
                .map(m -> {
                    int number = SecObservationPolicy.quarters(m.getPeriodStart(), quarter.getPeriodEnd());
                    int preceding = SecObservationPolicy.quarters(
                            m.getPeriodStart(), quarter.getPeriodStart().minusDays(1));
                    boolean aligned = number == 1
                            ? m.getPeriodStart().equals(quarter.getPeriodStart())
                            : number > 1 && preceding == number - 1;
                    return aligned ? number : 0;
                })
                .filter(number -> number > 0)
                .distinct()
                .toList();
        // Fiscal calendar changes can produce conflicting anchors. Preserve the
        // standalone quarter without inventing a fiscal number in that case.
        return numbers.size() == 1 ? "Q" + numbers.getFirst() : "QUARTER";
    }

    private NormalizedFinancialMetricResponse response(
            FinancialMetric fact, String period, LocalDate start, BigDecimal value) {
        return new NormalizedFinancialMetricResponse(fact.getMetric(), period, start,
                fact.getPeriodEnd(), value, fact.getUnit());
    }
}
