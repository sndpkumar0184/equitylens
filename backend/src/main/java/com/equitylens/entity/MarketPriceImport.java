package com.equitylens.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;

/** Persist both successful coverage and retry timing so empty/error responses are not fetched per page view. */
@Entity
@Table(name = "market_price_imports", uniqueConstraints = @UniqueConstraint(
        name = "uk_market_import_company_source", columnNames = {"company_id", "source"}))
public class MarketPriceImport {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;
    @Column(nullable = false, length = 32)
    private String source;
    private LocalDate fromDate;
    private LocalDate toDate;
    private Instant fetchedAt;
    private Instant retryAfter;
    private String status;
    public MarketPriceImport() {}
    public MarketPriceImport(Company company, String source) { this.company = company; this.source = source; }
    public LocalDate getFromDate() { return fromDate; }
    public LocalDate getToDate() { return toDate; }
    public Instant getFetchedAt() { return fetchedAt; }
    public Instant getRetryAfter() { return retryAfter; }
    public String getStatus() { return status; }
    public void succeeded(LocalDate from, LocalDate to, Instant now, boolean empty) {
        fromDate = from; toDate = to; fetchedAt = now;
        status = empty ? "EMPTY" : "OK"; retryAfter = now.plusSeconds(21600);
    }
    public void failed(String status, Instant retryAfter) { this.status = status; this.retryAfter = retryAfter; }
}
