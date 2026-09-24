package com.equitylens.sec;

import com.equitylens.entity.Company;
import com.equitylens.entity.FinancialMetric;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SecFinancialDataParserTest {
    private final SecFinancialDataParser parser = new SecFinancialDataParser();
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void prefersPreferredRevenuePerPeriodAndRetainsOlderFallbackHistory() {
        var result = parse("""
            {"RevenueFromContractWithCustomerExcludingAssessedTax":{"units":{"USD":[
              {"start":"2025-01-01","end":"2025-03-31","val":100,"form":"10-Q","filed":"2025-05-01"},
              {"start":"2025-01-01","end":"2025-03-31","val":110,"form":"10-Q/A","filed":"2025-06-01"}
            ]}},"Revenues":{"units":{"USD":[
              {"start":"2025-01-01","end":"2025-03-31","val":999,"form":"10-K","filed":"2026-02-01"},
              {"start":"2024-01-01","end":"2024-12-31","val":400,"form":"10-K/A","filed":"2025-03-01"}
            ]}}}
            """);
        assertEquals(2, result.size());
        assertEquals(List.of(new BigDecimal("400"), new BigDecimal("110")),
                result.stream().map(FinancialMetric::getValue).toList());
        assertTrue(result.stream().allMatch(m -> m.getMetric().equals("revenue")));
    }

    @Test
    void invalidPreferredObservationDoesNotSuppressValidFallbackOrOtherCurrency() {
        var result = parse("""
            {"RevenueFromContractWithCustomerExcludingAssessedTax":{"units":{"USD":[
              {"start":"bad","end":"2025-03-31","val":999,"form":"10-Q","filed":"2025-05-01"},
              {"start":"2025-01-01","end":"2025-03-31","val":null,"form":"10-Q","filed":"2025-05-01"}
            ],"EUR":[
              {"start":"2025-01-01","end":"2025-03-31","val":90,"form":"10-Q","filed":"2025-05-01"}
            ]}},"Revenues":{"units":{"USD":[
              {"start":"2025-01-01","end":"2025-03-31","val":100,"form":"10-Q","filed":"2025-05-01"}
            ]}}}
            """);
        assertEquals(2, result.size());
        assertEquals(List.of("EUR", "USD"), result.stream().map(FinancialMetric::getUnit).toList());
    }

    @Test
    void rejectsInvalidAndUnsupportedFactsAndUsesLatestValidRegularFiling() {
        var result = parse("""
            {"Assets":{"units":{"USD":[
              {"end":"2025-03-31","val":100,"form":"10-Q","filed":"2025-05-01"},
              {"end":"2025-03-31","val":110,"form":"10-Q/A","filed":"2025-06-01"},
              {"end":"2025-03-31","val":120,"form":"10-K","filed":"2026-02-01"},
              {"end":"2025-03-31","val":999,"form":"8-K","filed":"2026-03-01"},
              {"end":"2025-03-31","val":"bad","form":"10-K/A","filed":"2026-03-01"},
              {"end":"2025-03-31","val":999,"form":"10-K/A","filed":"bad"},
              {"end":"2025-03-31","val":999,"form":"10-K/A"},
              {"val":999,"form":"10-K/A","filed":"2026-03-01"}
            ]}}}
            """);
        assertEquals(1, result.size());
        assertEquals(new BigDecimal("120"), result.getFirst().getValue());
        assertNotNull(result.getFirst().getCompany());
    }

    @Test
    void absentTaxonomyReturnsNoFacts() {
        assertTrue(parser.parse(new SecCompanyFacts(mapper.readTree("{}")), new Company()).isEmpty());
    }

    @Test
    void normalizesAmendedCompanyFactsWithoutCollapsingDifferentDurations() {
        var facts = parse("""
            {"RevenueFromContractWithCustomerExcludingAssessedTax":{"units":{"USD":[
              {"start":"2025-01-01","end":"2025-03-31","val":100,"form":"10-Q","filed":"2025-05-01"},
              {"start":"2025-01-01","end":"2025-06-30","val":250,"form":"10-Q","filed":"2025-08-01"},
              {"start":"2025-04-01","end":"2025-06-30","val":150,"form":"10-Q","filed":"2025-08-01"},
              {"start":"2025-04-01","end":"2025-06-30","val":160,"form":"10-Q/A","filed":"2025-09-01"},
              {"start":"2025-01-01","end":"2025-12-31","val":600,"form":"10-K","filed":"2026-02-01"},
              {"start":"2025-01-01","end":"2025-12-31","val":610,"form":"10-K/A","filed":"2026-03-01"}
            ]}},"Revenues":{"units":{"USD":[
              {"start":"2025-01-01","end":"2025-12-31","val":999,"form":"10-K/A","filed":"2026-04-01"},
              {"start":"2024-01-01","end":"2024-12-31","val":400,"form":"10-K","filed":"2025-02-01"}
            ]}}}
            """);
        assertEquals(5, facts.size());
        var normalizer = new com.equitylens.service.FinancialMetricNormalizer();
        var result = normalizer.normalize(facts);
        assertEquals(5, result.size());
        assertEquals(new BigDecimal("160"), result.stream()
                .filter(m -> m.period().equals("Q2")).findFirst().orElseThrow().value());
        assertEquals(new BigDecimal("250"), result.stream()
                .filter(m -> m.period().equals("YTD")).findFirst().orElseThrow().value());
        assertEquals(List.of(new BigDecimal("610"), new BigDecimal("400")), result.stream()
                .filter(m -> m.period().equals("FY")).map(m -> m.value()).toList());
        var reversed = new java.util.ArrayList<>(facts);
        java.util.Collections.reverse(reversed);
        assertEquals(result, normalizer.normalize(reversed));
    }

    private List<FinancialMetric> parse(String gaap) {
        return parser.parse(new SecCompanyFacts(mapper.readTree("{\"facts\":{\"us-gaap\":" + gaap + "}}")), new Company());
    }
}
