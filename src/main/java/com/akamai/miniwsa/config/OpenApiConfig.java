package com.akamai.miniwsa.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.examples.Example;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.RequestBody;
import java.util.List;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    private static final ObjectMapper EXAMPLES = new ObjectMapper();

    private static final String SINGLE_EVENT_JSON =
            """
            {
              "eventId": "evt-00132",
              "timestamp": "2026-05-20T14:32:10Z",
              "configId": 14227,
              "policyId": "pol_web1",
              "clientIp": "203.0.113.42",
              "hostname": "www.example.com",
              "path": "/api/v1/login",
              "method": "POST",
              "statusCode": 403,
              "userAgent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64)",
              "rule": {
                "id": "950001",
                "name": "SQL_INJECTION",
                "message": "SQL Injection Attack Detected",
                "severity": "CRITICAL",
                "category": "INJECTION"
              },
              "action": "DENY",
              "geoLocation": { "country": "CN", "city": "Beijing" },
              "requestSize": 1024,
              "responseSize": 256
            }
            """;

    private static final String BATCH_EVENT_JSON =
            """
            [{
              "eventId": "evt-00133",
              "timestamp": "2026-05-20T14:32:10Z",
              "configId": 14227,
              "clientIp": "203.0.113.42",
              "path": "/login",
              "rule": { "severity": "HIGH", "category": "BOT" },
              "action": "ALERT"
            }]
            """;

    @Bean
    OpenAPI miniWsaOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Mini WSA")
                        .description("Mini Web Security Analytics API")
                        .version("0.6.0"));
    }

    @Bean
    OpenApiCustomizer ingestRequestBodyCustomizer() {
        return openApi -> {
            var pathItem = openApi.getPaths().get("/v1/events/ingest");
            if (pathItem == null || pathItem.getPost() == null) {
                return;
            }

            var eventRef = new Schema<>().$ref("#/components/schemas/EventIngestRequest");
            var oneOf = new Schema<>().oneOf(List.of(eventRef, new ArraySchema().items(eventRef)));
            var mediaType = new MediaType()
                    .schema(oneOf)
                    .addExamples(
                            "single",
                            new Example().summary("Single event").value(json(SINGLE_EVENT_JSON)))
                    .addExamples(
                            "batch", new Example().summary("Batch").value(json(BATCH_EVENT_JSON)));

            var requestBody = pathItem.getPost().getRequestBody();
            if (requestBody == null) {
                requestBody = new RequestBody();
                pathItem.getPost().setRequestBody(requestBody);
            }
            requestBody.setRequired(true);
            if (requestBody.getDescription() == null) {
                requestBody.setDescription(
                        "A single event object or an array of events. The server sets receivedAt, attackType, and threatScore.");
            }
            var content = requestBody.getContent();
            if (content == null) {
                content = new Content();
                requestBody.setContent(content);
            }
            content.addMediaType(org.springframework.http.MediaType.APPLICATION_JSON_VALUE, mediaType);

            var schemas = openApi.getComponents() == null ? null : openApi.getComponents().getSchemas();
            if (schemas != null) {
                schemas.remove("JsonNode");
            }
        };
    }

    private static Object json(String text) {
        try {
            return EXAMPLES.readValue(text, Object.class);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
