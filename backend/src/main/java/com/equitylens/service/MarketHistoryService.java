package com.equitylens.service;

import com.equitylens.dto.*;
import com.equitylens.entity.MarketPrice;
import com.equitylens.entity.MarketPriceImport;
import com.equitylens.providers.MarketDataProvider;
import com.equitylens.repository.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.*;
import java.util.List;

@Service
public class MarketHistoryService {
    private final CompanyService companies;
    private final CompanyRepository companyRepository;
    private final MarketPriceRepository prices;
    private final MarketPriceImportRepository imports;
    private final MarketDataProvider provider;
    private final TransactionTemplate transactions;
    private final JdbcTemplate jdbc;

    public MarketHistoryService(CompanyService companies, CompanyRepository companyRepository,
            MarketPriceRepository prices, MarketPriceImportRepository imports, MarketDataProvider provider,
            PlatformTransactionManager transactionManager, JdbcTemplate jdbc) {
        this.companies = companies; this.companyRepository = companyRepository; this.prices = prices;
        this.imports = imports; this.provider = provider; this.jdbc = jdbc;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    public BigDecimal getLatestPrice(String ticker) { return getMarket(ticker, null, null).latestPrice(); }
    public List<DailyPrice> getPriceHistory(String ticker, LocalDate from, LocalDate to) {
        return getMarket(ticker, from, to).history();
    }

    public MarketHistoryResponse getMarket(String ticker, LocalDate requestedFrom, LocalDate requestedTo) {
        return getMarket(ticker, requestedFrom, requestedTo, MarketDataProvider.Interval.DAILY);
    }

    public MarketHistoryResponse getMarket(String ticker, LocalDate requestedFrom, LocalDate requestedTo, MarketDataProvider.Interval interval) {
        var supportedIntervals = supportedIntervals();
        if (!supportedIntervals.contains(interval))
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, interval + " market data is unavailable from the selected provider");
        LocalDate today = LocalDate.now(ZoneId.of("America/New_York"));
        LocalDate from = requestedFrom == null ? today.minusYears(1) : requestedFrom;
        LocalDate to = requestedTo == null ? today : requestedTo;
        if (from.isAfter(to) || to.isAfter(today) || from.isBefore(today.minusYears(15)))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Use an ordered date range within the past 15 years, ending no later than today");
        var company = companies.getCompany(ticker);
        // The company lock serializes first loads and refreshes across backend instances.
        // Fetch timeout bounds the transaction; the SEC importer is not invoked here.
        return transactions.execute(tx -> {
            companyRepository.lockById(company.getId()).orElseThrow();
            String source = provider.source();
            var state = imports.findByCompanyIdAndSource(company.getId(), source)
                    .orElseGet(() -> new MarketPriceImport(company, source));
            LocalDate requiredFrom = from.isBefore(today.minusYears(1).minusDays(14))
                    ? from : today.minusYears(1).minusDays(14);
            boolean covered = state.getFromDate() != null && !state.getFromDate().isAfter(requiredFrom)
                    && state.getToDate() != null && !state.getToDate().isBefore(today);
            boolean success = "OK".equals(state.getStatus()) || "EMPTY".equals(state.getStatus());
            Instant now = Instant.now();
            boolean coolingDown = state.getRetryAfter() != null && now.isBefore(state.getRetryAfter());
            if (!coolingDown || success && !covered) {
                LocalDate fetchFrom = state.getFromDate() != null && state.getFromDate().isBefore(requiredFrom)
                        ? state.getFromDate() : requiredFrom;
                if (fetchFrom.isBefore(today.minusYears(15))) fetchFrom = today.minusYears(15);
                try {
                    var fetched = provider.getHistory(company.getTicker(), fetchFrom, today);
                    // Refresh the entire covered window, so provider corrections update existing rows.
                    for (DailyPrice row : fetched) {
                        jdbc.update("""
                            insert into market_prices (company_id, price_date, source, open_price, high_price,
                                low_price, close_price, adjusted_close, volume)
                            values (?, ?, ?, ?, ?, ?, ?, ?, ?)
                            on conflict (company_id, price_date, source) do update set
                                open_price = excluded.open_price, high_price = excluded.high_price,
                                low_price = excluded.low_price, close_price = excluded.close_price,
                                adjusted_close = excluded.adjusted_close, volume = excluded.volume
                            """, company.getId(), row.date(), source, row.open(), row.high(), row.low(),
                                row.close(), row.adjustedClose(), row.volume());
                    }
                    // Empty provider results never delete previously imported prices.
                    state.succeeded(fetchFrom, today, now, fetched.isEmpty());
                } catch (MarketDataProvider.Unavailable ex) {
                    state.failed(ex.status(), now.plusSeconds(Math.max(60, ex.retrySeconds())));
                }
                imports.saveAndFlush(state);
            }
            var rows = prices.findByCompanyIdAndSourceAndPriceDateBetweenOrderByPriceDateAsc(
                    company.getId(), source, requiredFrom, today).stream().map(MarketPrice::toDailyPrice).toList();
            var latest = rows.isEmpty() ? null : rows.getLast();
            var previous = latest == null ? null : rows.stream().filter(p -> p.date().isBefore(latest.date())).reduce((a, b) -> b).orElse(null);
            BigDecimal change = latest == null || previous == null ? null : latest.close().subtract(previous.close());
            BigDecimal changePercent = change == null || previous.close().signum() == 0 ? null : change.multiply(BigDecimal.valueOf(100)).divide(previous.close(), 4, java.math.RoundingMode.HALF_UP);
            String status = state.getStatus();
            if (!rows.isEmpty() && (!"OK".equals(status) || latest.date().isBefore(today.minusDays(7)))) status = "STALE";
            var chartRows = rows.stream().filter(p -> !p.date().isBefore(from) && !p.date().isAfter(to)).toList();
            if (interval == MarketDataProvider.Interval.WEEKLY || interval == MarketDataProvider.Interval.MONTHLY || interval == MarketDataProvider.Interval.YEARLY)
                chartRows = MarketPriceAggregation.aggregate(chartRows, interval);
            return new MarketHistoryResponse(company.getTicker(), source, "USD", status,
                    state.getFetchedAt(), state.getRetryAfter(), "PROVIDER_CLOSE_UNVERIFIED_ADJUSTMENTS",
                    latest == null ? null : latest.close(), latest == null ? null : latest.date(), change, changePercent,
                    latest == null ? null : latest.volume(),
                    MarketAnalytics.extreme(rows, latest, true), MarketAnalytics.extreme(rows, latest, false),
                    MarketAnalytics.priceReturn(rows, latest, 1), MarketAnalytics.priceReturn(rows, latest, 3),
                    MarketAnalytics.priceReturn(rows, latest, 6), MarketAnalytics.priceReturn(rows, latest, 12),
                    interval, supportedIntervals, chartRows);
        });
    }

    private java.util.Set<MarketDataProvider.Interval> supportedIntervals() {
        var supported = java.util.EnumSet.noneOf(MarketDataProvider.Interval.class);
        supported.addAll(provider.supportedIntervals());
        if (supported.contains(MarketDataProvider.Interval.DAILY)) {
            supported.add(MarketDataProvider.Interval.WEEKLY);
            supported.add(MarketDataProvider.Interval.MONTHLY);
            supported.add(MarketDataProvider.Interval.YEARLY);
        }
        return java.util.Set.copyOf(supported);
    }
}
