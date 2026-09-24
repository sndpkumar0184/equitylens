package com.equitylens.controller;

import com.equitylens.dto.TTMRatiosResponse;
import com.equitylens.service.TTMRatioService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/companies")
public class TTMRatioController {

    private final TTMRatioService ttmRatioService;

    public TTMRatioController(
            TTMRatioService ttmRatioService
    ) {
        this.ttmRatioService = ttmRatioService;
    }

    @GetMapping("/{ticker}/ratios")
    public ResponseEntity<List<TTMRatiosResponse>> getRatios(
            @PathVariable String ticker
    ) {

        return ResponseEntity.ok(
                ttmRatioService.getRatios(ticker)
        );
    }
}