package com.equitylens.service;

import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public final class Ticker {
    private Ticker() {}

    public static String normalize(String input) {
        String ticker = input == null ? "" : input.trim().toUpperCase(Locale.ROOT);
        if (!ticker.matches("[A-Z0-9][A-Z0-9.-]{0,9}")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Enter a valid ticker (1–10 letters, numbers, dots or hyphens)");
        }
        return ticker;
    }
}
