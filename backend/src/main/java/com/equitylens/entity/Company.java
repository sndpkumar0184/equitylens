package com.equitylens.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "companies")
public class Company {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 10)
    private String cik;

    @Column(nullable = false, unique = true, length = 10)
    private String ticker;

    @Column(nullable = false)
    private String name;

    private String sector;

    private String industry;

    public Company() {
    }

    public Company(
            String ticker,
            String cik,
            String name,
            String sector,
            String industry) {

        this.ticker = ticker;
        this.cik = cik;
        this.name = name;
        this.sector = sector;
        this.industry = industry;
    }

    public Long getId() {
        return id;
    }

    public String getCik() {
        return cik;
    }

    public void setCik(String cik) {
        this.cik = cik;
    }

    public String getTicker() {
        return ticker;
    }

    public void setTicker(String ticker) {
        this.ticker = ticker;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getSector() {
        return sector;
    }

    public void setSector(String sector) {
        this.sector = sector;
    }

    public String getIndustry() {
        return industry;
    }

    public void setIndustry(String industry) {
        this.industry = industry;
    }
}