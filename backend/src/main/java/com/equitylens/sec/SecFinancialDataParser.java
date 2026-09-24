package com.equitylens.sec;

import com.equitylens.entity.Company;
import com.equitylens.entity.FinancialMetric;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.time.format.DateTimeParseException;

@Component
public class SecFinancialDataParser {

    public List<FinancialMetric> parse(
            SecCompanyFacts companyFacts,
            Company company) {

        List<FinancialMetric> metrics = new ArrayList<>();

        JsonNode facts = companyFacts.getFacts();
        JsonNode usGaap = facts
                .path("facts")
                .path("us-gaap");

        parseRevenue(usGaap, company, metrics);

        parseMetric(
                usGaap,
                "CostOfRevenue",
                "cost_of_revenue",
                company,
                metrics
        );

        parseMetric(
                usGaap,
                "GrossProfit",
                "gross_profit",
                company,
                metrics
        );

        parseMetric(
                usGaap,
                "OperatingIncomeLoss",
                "operating_income",
                company,
                metrics
        );

        parseMetric(
                usGaap,
                "NetIncomeLoss",
                "net_income",
                company,
                metrics
        );

        parseMetric(
                usGaap,
                "Assets",
                "assets",
                company,
                metrics
        );

        parseMetric(
                usGaap,
                "CashAndCashEquivalentsAtCarryingValue",
                "cash",
                company,
                metrics
        );

        parseMetric(
                usGaap,
                "ShortTermInvestments",
                "short_term_investments",
                company,
                metrics
        );

        parseMetric(
                usGaap,
                "AssetsCurrent",
                "current_assets",
                company,
                metrics
        );

        parseMetric(
                usGaap,
                "LiabilitiesCurrent",
                "current_liabilities",
                company,
                metrics
        );

        parseMetric(
                usGaap,
                "LongTermDebtCurrent",
                "current_debt",
                company,
                metrics
        );

        parseMetric(
                usGaap,
                "LongTermDebtNoncurrent",
                "long_term_debt",
                company,
                metrics
        );

        parseMetric(
                usGaap,
                "StockholdersEquity",
                "stockholders_equity",
                company,
                metrics
        );

        parseMetric(
                usGaap,
                "NetCashProvidedByUsedInOperatingActivities",
                "operating_cash_flow",
                company,
                metrics
        );

        parseMetric(
                usGaap,
                "PaymentsToAcquirePropertyPlantAndEquipment",
                "capital_expenditures",
                company,
                metrics
        );

        parseMetric(
                usGaap,
                "NetCashProvidedByUsedInInvestingActivities",
                "investing_cash_flow",
                company,
                metrics
        );

        parseMetric(
                usGaap,
                "NetCashProvidedByUsedInFinancingActivities",
                "financing_cash_flow",
                company,
                metrics
        );

        Map<SecObservationPolicy.PeriodKey, FinancialMetric> selected = new HashMap<>();
        metrics.forEach(m -> selected.merge(SecObservationPolicy.PeriodKey.of(m), m,
                SecObservationPolicy::latest));
        return selected.values().stream()
                .sorted(java.util.Comparator.comparing(FinancialMetric::getMetric)
                        .thenComparing(FinancialMetric::getUnit)
                        .thenComparing(FinancialMetric::getPeriodEnd)
                        .thenComparing(FinancialMetric::getPeriodStart,
                                java.util.Comparator.nullsFirst(java.util.Comparator.naturalOrder())))
                .toList();
    }

    private void parseRevenue(
            JsonNode usGaap,
            Company company,
            List<FinancialMetric> metrics) {

        List<FinancialMetric> preferred = new ArrayList<>();
        List<FinancialMetric> fallback = new ArrayList<>();
        parseMetric(usGaap, "RevenueFromContractWithCustomerExcludingAssessedTax",
                "revenue", company, preferred);
        parseMetric(usGaap, "Revenues", "revenue", company, fallback);
        var preferredPeriods = preferred.stream()
                .map(SecObservationPolicy.PeriodKey::of)
                .collect(java.util.stream.Collectors.toSet());
        metrics.addAll(preferred);
        fallback.stream()
                .filter(m -> !preferredPeriods.contains(SecObservationPolicy.PeriodKey.of(m)))
                .forEach(metrics::add);
    }

    private void parseMetric(
            JsonNode usGaap,
            String secTag,
            String metricName,
            Company company,
            List<FinancialMetric> metrics) {

        JsonNode metric = usGaap.path(secTag);

        if (metric.isMissingNode()) {
            return;
        }

        JsonNode units = metric.path("units");

        units.properties().forEach(unitEntry -> {

            String unit = unitEntry.getKey();
            JsonNode values = unitEntry.getValue();

            if (!values.isArray()) {
                return;
            }

            for (JsonNode item : values) {
                // Malformed observations must not hide an older valid filing or revenue fallback.
                try {
                    if (!item.path("val").isNumber()) continue;
                    FinancialMetric fact = new FinancialMetric();
                    fact.setCompany(company);
                    fact.setMetric(metricName);
                    fact.setUnit(unit);
                    fact.setValue(item.get("val").decimalValue());
                    fact.setPeriodStart(date(item, "start"));
                    fact.setPeriodEnd(date(item, "end"));
                    fact.setFilingDate(date(item, "filed"));
                    fact.setForm(text(item, "form"));
                    fact.setFrame(text(item, "frame"));
                    if (SecObservationPolicy.isValid(fact)) metrics.add(fact);
                } catch (DateTimeParseException | NumberFormatException ignored) {
                    // Ignore just this invalid observation, not the remainder of the company facts.
                }
            }
        });
    }

    private LocalDate date(JsonNode item, String field) {
        String value = text(item, field);
        return value == null ? null : LocalDate.parse(value);
    }

    private String text(JsonNode item, String field) {
        JsonNode value = item.get(field);
        return value != null && value.isString() ? value.asString() : null;
    }
}
