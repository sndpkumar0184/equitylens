package com.equitylens.controller;

import com.equitylens.dto.DashboardResponse;
import com.equitylens.service.DashboardService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/companies")
public class DashboardController {
    private final DashboardService dashboards;

    public DashboardController(DashboardService dashboards) { this.dashboards = dashboards; }

    @GetMapping("/{ticker}/dashboard")
    public DashboardResponse getDashboard(@PathVariable String ticker) {
        return dashboards.getDashboard(ticker);
    }
}
