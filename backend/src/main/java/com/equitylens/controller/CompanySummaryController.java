package com.equitylens.controller;

import com.equitylens.dto.CompanySummaryResponse;
import com.equitylens.service.CompanySummaryService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/companies")
public class CompanySummaryController {

    private final CompanySummaryService companySummaryService;

    public CompanySummaryController(
            CompanySummaryService companySummaryService
    ) {
        this.companySummaryService = companySummaryService;
    }

    @GetMapping("/{ticker}/summary")
    public ResponseEntity<CompanySummaryResponse> getSummary(
            @PathVariable String ticker
    ) {
        return ResponseEntity.ok(
                companySummaryService.getSummary(ticker)
        );
    }
}