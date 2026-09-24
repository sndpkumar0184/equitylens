package com.equitylens.controller;

import com.equitylens.dto.TTMFinancialResponse;
import com.equitylens.service.TTMFinancialService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/companies")
public class TTMFinancialController {

    private final TTMFinancialService ttmFinancialService;

    public TTMFinancialController(
            TTMFinancialService ttmFinancialService
    ) {
        this.ttmFinancialService = ttmFinancialService;
    }

    @GetMapping("/{ticker}/financials/ttm")
    public ResponseEntity<List<TTMFinancialResponse>> getTTM(
            @PathVariable String ticker
    ) {

        return ResponseEntity.ok(
                ttmFinancialService.getTTM(ticker)
        );
    }
}