package com.equitylens.sec;

import com.equitylens.entity.Company;
import com.equitylens.entity.FinancialMetric;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

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

        parseMetric(
                usGaap,
                "Revenues",
                "revenue",
                company,
                metrics
        );

        parseMetric(
                usGaap,
                "RevenueFromContractWithCustomerExcludingAssessedTax",
                "revenue",
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

        return metrics;
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

        // Jackson 3: use properties() instead of fields()
        units.properties().forEach(unitEntry -> {

            String unit = unitEntry.getKey();
            JsonNode values = unitEntry.getValue();

            if (!values.isArray()) {
                return;
            }

            for (JsonNode item : values) {

                if (!item.has("val") || item.get("val").isNull()) {
                    continue;
                }

                // Only process financial statements
                if (!item.has("form") || item.get("form").isNull()) {
                    continue;
                }

                String form = item.get("form").asString();

                if (!form.equals("10-Q")
                        && !form.equals("10-K")
                        && !form.equals("10-Q/A")
                        && !form.equals("10-K/A")) {
                    continue;
                }

                FinancialMetric financialMetric =
                        new FinancialMetric();

                financialMetric.setCompany(company);
                financialMetric.setMetric(metricName);
                financialMetric.setUnit(unit);

                financialMetric.setValue(
                        new BigDecimal(
                                item.get("val").asString()
                        )
                );

                // Financial period start
                if (item.has("start")
                        && !item.get("start").isNull()) {

                    financialMetric.setPeriodStart(
                            LocalDate.parse(
                                    item.get("start").asString()
                            )
                    );
                }

                // Financial period end
                if (item.has("end")
                        && !item.get("end").isNull()) {

                    financialMetric.setPeriodEnd(
                            LocalDate.parse(
                                    item.get("end").asString()
                            )
                    );
                }

                // SEC filing date
                if (item.has("filed")
                        && !item.get("filed").isNull()) {

                    financialMetric.setFilingDate(
                            LocalDate.parse(
                                    item.get("filed").asString()
                            )
                    );
                }

                // SEC filing form: 10-Q, 10-K, etc.
                if (item.has("form")
                        && !item.get("form").isNull()) {

                    financialMetric.setForm(
                            item.get("form").asString()
                    );
                }

                if (item.has("frame")
                        && !item.get("frame").isNull()) {

                    financialMetric.setFrame(
                            item.get("frame").asText()
                    );
                }

                metrics.add(financialMetric);
            }
        });
    }
}