package com.equitylens.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(
        name = "market_prices",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_market_price_company_date_source",
                        columnNames = {"company_id", "price_date", "source"}
                )
        },
        indexes = {
                @Index(
                        name = "idx_market_price_company_date",
                        columnList = "company_id, price_date"
                )
        }
)
public class MarketPrice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "company_id",
            nullable = false
    )
    private Company company;

    @Column(
            name = "price_date",
            nullable = false
    )
    private LocalDate priceDate;

    @Column(
            nullable = false,
            precision = 19,
            scale = 6
    )
    private BigDecimal closePrice;

    @Column(nullable = false, length = 32, columnDefinition = "varchar(32) default 'LEGACY'")
    private String source = "LEGACY";
    @Column(precision = 19, scale = 6)
    private BigDecimal openPrice;
    @Column(precision = 19, scale = 6)
    private BigDecimal highPrice;
    @Column(precision = 19, scale = 6)
    private BigDecimal lowPrice;
    @Column(precision = 19, scale = 6)
    private BigDecimal adjustedClose;
    private Long volume;

    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public BigDecimal getOpenPrice() { return openPrice; }
    public void setOpenPrice(BigDecimal value) { openPrice = value; }
    public BigDecimal getHighPrice() { return highPrice; }
    public void setHighPrice(BigDecimal value) { highPrice = value; }
    public BigDecimal getLowPrice() { return lowPrice; }
    public void setLowPrice(BigDecimal value) { lowPrice = value; }
    public BigDecimal getAdjustedClose() { return adjustedClose; }
    public void setAdjustedClose(BigDecimal value) { adjustedClose = value; }
    public Long getVolume() { return volume; }
    public void setVolume(Long value) { volume = value; }

    public com.equitylens.dto.DailyPrice toDailyPrice() {
        return new com.equitylens.dto.DailyPrice(priceDate, openPrice, highPrice, lowPrice, closePrice, adjustedClose, volume);
    }

    public MarketPrice() {
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

    public LocalDate getPriceDate() {
        return priceDate;
    }

    public void setPriceDate(LocalDate priceDate) {
        this.priceDate = priceDate;
    }

    public BigDecimal getClosePrice() {
        return closePrice;
    }

    public void setClosePrice(BigDecimal closePrice) {
        this.closePrice = closePrice;
    }
}
