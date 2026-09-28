package com.equitylens.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Provider-neutral daily observation; absent fields stay null. */
public record DailyPrice(LocalDate date, BigDecimal open, BigDecimal high, BigDecimal low,
        BigDecimal close, BigDecimal adjustedClose, Long volume) {}
