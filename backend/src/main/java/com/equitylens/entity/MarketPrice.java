package com.equitylens.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(
        name = "market_prices",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_market_price_company_date",
                        columnNames = {"company_id", "price_date"}
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
