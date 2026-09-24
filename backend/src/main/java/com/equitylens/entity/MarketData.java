package com.equitylens.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "market_data",
        indexes = {
                @Index(name = "idx_market_data_company", columnList = "company_id")
        }
)
public class MarketData {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "company_id",
            nullable = false,
            unique = true
    )
    private Company company;

    @Column(precision = 19, scale = 6)
    private BigDecimal currentPrice;

    @Column(precision = 19, scale = 6)
    private BigDecimal previousClose;

    @Column(precision = 24, scale = 6)
    private BigDecimal sharesOutstanding;

    @Column(precision = 24, scale = 6)
    private BigDecimal marketCap;

    @Column(precision = 19, scale = 6)
    private BigDecimal fiftyTwoWeekHigh;

    @Column(precision = 19, scale = 6)
    private BigDecimal fiftyTwoWeekLow;

    @Column(precision = 19, scale = 6)
    private BigDecimal beta;

    @Column(nullable = false)
    private LocalDateTime marketDataTimestamp;

    public MarketData() {
    }

    public Long getId() {
        return id;
    }

    public Company getCompany() {
        return company;
    }

    public void setCompany(Company company) {
        this.company = company;
    }

    public BigDecimal getCurrentPrice() {
        return currentPrice;
    }

    public void setCurrentPrice(BigDecimal currentPrice) {
        this.currentPrice = currentPrice;
    }

    public BigDecimal getPreviousClose() {
        return previousClose;
    }

    public void setPreviousClose(BigDecimal previousClose) {
        this.previousClose = previousClose;
    }

    public BigDecimal getSharesOutstanding() {
        return sharesOutstanding;
    }

    public void setSharesOutstanding(BigDecimal sharesOutstanding) {
        this.sharesOutstanding = sharesOutstanding;
    }

    public BigDecimal getMarketCap() {
        return marketCap;
    }

    public void setMarketCap(BigDecimal marketCap) {
        this.marketCap = marketCap;
    }

    public BigDecimal getFiftyTwoWeekHigh() {
        return fiftyTwoWeekHigh;
    }

    public void setFiftyTwoWeekHigh(BigDecimal fiftyTwoWeekHigh) {
        this.fiftyTwoWeekHigh = fiftyTwoWeekHigh;
    }

    public BigDecimal getFiftyTwoWeekLow() {
        return fiftyTwoWeekLow;
    }

    public void setFiftyTwoWeekLow(BigDecimal fiftyTwoWeekLow) {
        this.fiftyTwoWeekLow = fiftyTwoWeekLow;
    }

    public BigDecimal getBeta() {
        return beta;
    }

    public void setBeta(BigDecimal beta) {
        this.beta = beta;
    }

    public LocalDateTime getMarketDataTimestamp() {
        return marketDataTimestamp;
    }

    public void setMarketDataTimestamp(LocalDateTime marketDataTimestamp) {
        this.marketDataTimestamp = marketDataTimestamp;
    }
}