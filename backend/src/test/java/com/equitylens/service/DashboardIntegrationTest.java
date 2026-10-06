package com.equitylens.service;

import com.equitylens.controller.DashboardController;
import com.equitylens.entity.Company;
import com.equitylens.entity.FinancialMetric;
import com.equitylens.exception.ApiExceptionHandler;
import com.equitylens.repository.CompanyRepository;
import com.equitylens.repository.FinancialMetricRepository;
import com.equitylens.sec.SecFinancialDataParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.LocalDate;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {"realtime.live.enabled=true", "realtime.kafka.bootstrap=127.0.0.1:1"})
@Transactional
class DashboardIntegrationTest {
    @Autowired CompanyService companies;
    @Autowired CompanyRepository companyRepository;
    @Autowired DashboardController controller;
    @MockitoSpyBean FinancialMetricRepository metrics;
    @MockitoBean SecDataService sec;

    @ParameterizedTest
    @ValueSource(strings = {"META", "AAPL", "AMZN", "NVDA"})
    void dashboardUsesRequestedCompanyAndBoundedDatabaseQuery(String ticker) throws Exception {
        String cik = switch (ticker) { case "META" -> "0001326801"; case "AAPL" -> "0000320193"; case "NVDA" -> "0001045810"; default -> "0001018724"; };
        when(sec.resolveTicker(ticker)).thenReturn(new SecDataService.SecCompany(ticker, cik, ticker + " Company"));
        Company company = companies.getCompany(ticker);
        metrics.deleteByCompanyId(company.getId());
        metrics.flush();
        metrics.save(fact(company, "revenue", 2024, "100"));
        metrics.save(fact(company, "revenue", 2025, "120"));
        metrics.save(fact(company, "net_income", 2025, "12"));
        metrics.save(fact(company, "revenue", 1999, "1"));
        metrics.save(fact(company, "unrelated_metric", 2025, "999"));
        metrics.flush();
        company.setFinancialImportCount(5L);
        company.setFinancialImportVersion(SecFinancialDataParser.MAPPING_VERSION);
        companyRepository.flush();
        clearInvocations(metrics, sec);
        var mvc = MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new ApiExceptionHandler()).build();
        String json = mvc.perform(get("/api/companies/" + ticker.toLowerCase() + "/dashboard"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        var result = new ObjectMapper().readTree(json);
        assertEquals(ticker, result.path("company").path("ticker").asString());
        assertEquals(2, result.path("annual").path("income").size());
        assertEquals(120, result.path("annual").path("summary").path("revenue").path("value").asInt());
        assertEquals(12, result.path("annual").path("summary").path("netIncome").path("value").asInt());
        assertTrue(result.path("annual").path("summary").path("freeCashFlow").path("value").isNull());
        verify(metrics, never()).findByCompanyIdOrderByPeriodEndDesc(anyLong());
        verify(metrics).findByCompanyIdAndMetricInAndUnitAndPeriodEndGreaterThanEqualOrderByPeriodEndDesc(
                eq(company.getId()), eq(DashboardService.METRICS), eq("USD"), eq(LocalDate.of(2015,12,31)));
        verifyNoInteractions(sec);
    }

    @Test void oldMappingIsUpgradedOnceThroughExistingImporter() {
        when(sec.resolveTicker("META")).thenReturn(new SecDataService.SecCompany("META", "0001326801", "Meta Platforms, Inc."));
        Company company = companies.getCompany("META");
        metrics.deleteByCompanyId(company.getId());
        metrics.flush();
        metrics.save(fact(company, "revenue", 2025, "100"));
        metrics.flush();
        company.setFinancialImportCount(1L);
        company.setFinancialImportVersion(1);
        when(sec.getCompanyFacts(company.getCik())).thenReturn(new com.equitylens.sec.SecCompanyFacts(new ObjectMapper().readTree("""
            {"facts":{"us-gaap":{
              "Revenues":{"units":{"USD":[{"start":"2025-01-01","end":"2025-12-31","val":100,"form":"10-K","filed":"2026-02-01"}]}},
              "Liabilities":{"units":{"USD":[{"end":"2025-12-31","val":60,"form":"10-K","filed":"2026-02-01"}]}}
            }}}
            """)));
        assertEquals(0, new BigDecimal("60").compareTo(controller.getDashboard("META").annual().summary().totalLiabilities().value()));
        controller.getDashboard("META");
        verify(sec, times(1)).getCompanyFacts(company.getCik());
        assertEquals(SecFinancialDataParser.MAPPING_VERSION, company.getFinancialImportVersion());
        assertEquals(2, metrics.countByCompanyId(company.getId()));
    }

    @Test void unknownTickerStillReturns404() throws Exception {
        when(sec.resolveTicker("ZZZZZZZZZZ")).thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown ticker"));
        MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new ApiExceptionHandler()).build()
                .perform(get("/api/companies/ZZZZZZZZZZ/dashboard")).andExpect(status().isNotFound());
    }

    private FinancialMetric fact(Company company, String metric, int year, String value) {
        FinancialMetric row = new FinancialMetric();
        row.setCompany(company); row.setMetric(metric); row.setUnit("USD"); row.setValue(new BigDecimal(value));
        row.setPeriodStart(LocalDate.of(year,1,1)); row.setPeriodEnd(LocalDate.of(year,12,31));
        row.setForm("10-K"); row.setFilingDate(LocalDate.of(year+1,2,1));
        return row;
    }
}
