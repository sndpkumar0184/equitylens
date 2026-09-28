package com.equitylens.service;

import com.equitylens.controller.CompanyController;
import com.equitylens.entity.Company;
import com.equitylens.entity.FinancialMetric;
import com.equitylens.exception.ApiExceptionHandler;
import com.equitylens.repository.CompanyRepository;
import com.equitylens.repository.FinancialMetricRepository;
import com.equitylens.sec.SecCompanyFacts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Real PostgreSQL persistence, parser, normalizer, and TTM; only SEC HTTP is replaced.
// All test mutations roll back, including changes to pre-existing META data.
@SpringBootTest
@Transactional
class TickerFinancialIntegrationTest {
    @Autowired CompanyService companies;
    @Autowired CompanyRepository companyRepository;
    @Autowired FinancialMetricRepository metrics;
    @Autowired FinancialDataService financials;
    @Autowired TTMFinancialService ttm;
    @Autowired CompanyController controller;
    @MockitoBean SecDataService sec;

    @BeforeEach void fixtures() {
        when(sec.resolveTicker("META")).thenReturn(new SecDataService.SecCompany("META", "0001326801", "Meta Platforms, Inc."));
        when(sec.resolveTicker("AAPL")).thenReturn(new SecDataService.SecCompany("AAPL", "0000320193", "Apple Inc."));
        when(sec.getCompanyFacts("0001326801")).thenReturn(facts(100));
        when(sec.getCompanyFacts("0000320193")).thenReturn(facts(200));
    }

    @Test void repeatedLookupNormalizesAndNeverDuplicatesCompany() {
        Company first = companies.getCompany(" aapl ");
        long count = companyRepository.count();
        assertEquals(first.getId(), companies.getCompany("AaPl").getId());
        assertEquals("AAPL", first.getTicker());
        assertEquals(count, companyRepository.count());
    }

    @Test void shareClassTickersCanResolveToTheSameSecIssuer() {
        Company apple = companies.getCompany("AAPL");
        when(sec.resolveTicker("TEST.CLASS")).thenReturn(new SecDataService.SecCompany("TEST.CLASS", apple.getCik(), "Test share class"));
        Company shareClass = companies.getCompany("TEST.CLASS");
        assertEquals(apple.getCik(), shareClass.getCik());
        assertNotEquals(apple.getId(), shareClass.getId());
        assertEquals("TEST.CLASS", shareClass.getTicker());
    }

    @Test void unknownTickerReturns404AndInvalidTicker400() throws Exception {
        when(sec.resolveTicker("UNKNOWN")).thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown stock ticker"));
        var mvc = MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new ApiExceptionHandler()).build();
        mvc.perform(get("/api/companies/UNKNOWN")).andExpect(status().isNotFound());
        mvc.perform(get("/api/companies/bad!symbol")).andExpect(status().isBadRequest());
    }

    @Test void importsAppleOnDemandAndKeepsMetaFinancialsSeparate() {
        Company apple = empty("AAPL");
        Company meta = empty("META");
        List<FinancialMetric> appleFacts = financials.getCompanyFinancials(" aapl ");
        List<FinancialMetric> metaFacts = financials.getCompanyFinancials("META");
        assertFalse(appleFacts.isEmpty());
        assertTrue(appleFacts.stream().allMatch(m -> m.getCompany().getId().equals(apple.getId())));
        assertTrue(metaFacts.stream().allMatch(m -> m.getCompany().getId().equals(meta.getId())));
        assertNotEquals(apple.getId(), meta.getId());
        assertTrue(appleFacts.stream().allMatch(m -> m.getForm() != null && m.getFilingDate() != null && m.getPeriodEnd() != null));
        assertNotEquals(financials.getIncomeStatement("AAPL").getFirst().revenue(), financials.getIncomeStatement("META").getFirst().revenue());
    }

    @Test void repeatedReadsAndExplicitImportsAreIdempotent() {
        Company apple = empty("AAPL");
        int count = financials.getCompanyFinancials("AAPL").size();
        assertEquals(count, financials.getCompanyFinancials("AAPL").size());
        verify(sec, times(1)).getCompanyFacts(apple.getCik());
        assertEquals(count, financials.importCompanyFinancials("AAPL").size());
        assertEquals(count, metrics.findByCompanyIdOrderByPeriodEndDesc(apple.getId()).size());
        var rows = metrics.findByCompanyIdOrderByPeriodEndDesc(apple.getId());
        assertEquals(count, rows.stream().map(m -> List.of(m.getMetric(), m.getUnit(), String.valueOf(m.getPeriodStart()), m.getPeriodEnd())).distinct().count());
    }

    @Test void incompleteImportIsRepairedButLegitimatelyMissingMetricsDoNotLoop() {
        Company apple = empty("AAPL");
        var first = financials.getCompanyFinancials("AAPL");
        metrics.delete(first.getFirst());
        metrics.flush();
        assertEquals(first.size(), financials.getCompanyFinancials("AAPL").size());
        financials.getCompanyFinancials("AAPL");
        verify(sec, times(2)).getCompanyFacts(apple.getCik());
    }

    @Test void completeLegacyMetaDataIsUsedWithoutSecRequest() {
        Company meta = empty("META");
        financials.importCompanyFinancials("META");
        meta.setFinancialImportCount(null);
        clearInvocations(sec);
        assertFalse(financials.getCompanyFinancials("meta").isEmpty());
        verifyNoInteractions(sec);
    }

    @Test void ttmStillSumsFourQuartersAndDoesNotSumCash() {
        empty("AAPL");
        var latest = ttm.getTTM("AAPL").getFirst();
        assertEquals(0, new BigDecimal("800").compareTo(latest.revenue()));
        assertEquals(0, new BigDecimal("40").compareTo(latest.cash()));
        assertEquals("USD", latest.unit());
    }

    @Test void failedRefreshDoesNotDeleteExistingRows() {
        Company apple = empty("AAPL");
        int count = financials.getCompanyFinancials("AAPL").size();
        when(sec.getCompanyFacts(apple.getCik())).thenThrow(new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "SEC unavailable"));
        assertThrows(ResponseStatusException.class, () -> financials.importCompanyFinancials("AAPL"));
        assertEquals(count, metrics.findByCompanyIdOrderByPeriodEndDesc(apple.getId()).size());
    }

    private Company empty(String ticker) {
        Company company = companies.getCompany(ticker);
        metrics.deleteByCompanyId(company.getId());
        metrics.flush();
        company.setFinancialImportCount(null);
        return company;
    }

    private SecCompanyFacts facts(int revenue) {
        StringBuilder flows = new StringBuilder();
        String[] starts = {"2025-01-01", "2025-04-01", "2025-07-01", "2025-10-01"};
        String[] ends = {"2025-03-31", "2025-06-30", "2025-09-30", "2025-12-31"};
        for (int i = 0; i < 4; i++) {
            if (i > 0) flows.append(',');
            flows.append("{\"start\":\"%s\",\"end\":\"%s\",\"val\":%d,\"form\":\"10-Q\",\"filed\":\"2026-02-01\"}".formatted(starts[i], ends[i], revenue));
        }
        String json = """
          {"facts":{"us-gaap":{
            "Revenues":{"units":{"USD":[%s]}},
            "NetIncomeLoss":{"units":{"USD":[%s]}},
            "Assets":{"units":{"USD":[{"end":"2025-12-31","val":1000,"form":"10-K","filed":"2026-02-01"}]}},
            "CashAndCashEquivalentsAtCarryingValue":{"units":{"USD":[{"end":"2025-12-31","val":40,"form":"10-K","filed":"2026-02-01"}]}}
          }}}
          """.formatted(flows, flows);
        return new SecCompanyFacts(new ObjectMapper().readTree(json));
    }
}
