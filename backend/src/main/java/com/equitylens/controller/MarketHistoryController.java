package com.equitylens.controller;

import com.equitylens.dto.MarketHistoryResponse;
import com.equitylens.service.MarketHistoryService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;

@RestController
@RequestMapping("/api/companies")
public class MarketHistoryController {
    private final MarketHistoryService market;
    public MarketHistoryController(MarketHistoryService market) { this.market = market; }

    @GetMapping("/{ticker}/market")
    public MarketHistoryResponse getMarket(@PathVariable String ticker,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return market.getMarket(ticker, from, to);
    }
}
