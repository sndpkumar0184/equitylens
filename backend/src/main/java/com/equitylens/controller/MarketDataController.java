package com.equitylens.controller;

import com.equitylens.dto.MarketDataResponse;
import com.equitylens.service.MarketDataService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/companies")
public class MarketDataController {

    private final MarketDataService marketDataService;

    public MarketDataController(
            MarketDataService marketDataService
    ) {
        this.marketDataService = marketDataService;
    }

    @GetMapping("/{ticker}/market-data")
    public ResponseEntity<MarketDataResponse> getMarketData(
            @PathVariable String ticker
    ) {

        return ResponseEntity.ok(
                marketDataService.getMarketData(ticker)
        );
    }

    @PostMapping("/{ticker}/market-data/refresh")
    public ResponseEntity<MarketDataResponse> refreshMarketData(
            @PathVariable String ticker
    ) {

        return ResponseEntity.ok(
                marketDataService.refresh(ticker)
        );
    }
}