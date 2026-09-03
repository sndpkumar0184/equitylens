package com.equitylens.service;

import com.equitylens.sec.SecCompanyFacts;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class SecDataService {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public SecDataService(
            RestClient.Builder builder,
            ObjectMapper objectMapper) {

        this.restClient = builder
                .baseUrl("https://data.sec.gov")
                .build();

        this.objectMapper = objectMapper;
    }

    public SecCompanyFacts getCompanyFacts(String cik) {

        String json = restClient.get()
                .uri("/api/xbrl/companyfacts/CIK" + cik + ".json")
                .header(
                        "User-Agent",
                        "EquityLens your-email@example.com"
                )
                .retrieve()
                .body(String.class);

        try {
            JsonNode root = objectMapper.readTree(json);
            return new SecCompanyFacts(root);
        } catch (Exception e) {
            throw new RuntimeException(
                    "Failed to parse SEC company facts",
                    e
            );
        }
    }
}