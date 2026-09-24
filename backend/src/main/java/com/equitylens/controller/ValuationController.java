package com.equitylens.controller;

import com.equitylens.dto.ValuationResponse;
import com.equitylens.service.ValuationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/companies")
public class ValuationController {

    private final ValuationService valuationService;

    public ValuationController(
            ValuationService valuationService
    ) {
        this.valuationService = valuationService;
    }

    @GetMapping("/{ticker}/valuation")
    public ResponseEntity<ValuationResponse> getValuation(
            @PathVariable String ticker
    ) {
        return ResponseEntity.ok(
                valuationService.getValuation(ticker)
        );
    }
}
