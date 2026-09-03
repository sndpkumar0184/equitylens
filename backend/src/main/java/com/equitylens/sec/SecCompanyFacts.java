package com.equitylens.sec;

import tools.jackson.databind.JsonNode;

public class SecCompanyFacts {

    private final JsonNode facts;

    public SecCompanyFacts(JsonNode facts) {
        this.facts = facts;
    }

    public JsonNode getFacts() {
        return facts;
    }
}