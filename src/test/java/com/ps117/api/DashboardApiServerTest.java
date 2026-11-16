package com.ps117.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ps117.infrastructure.ApplicationConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DashboardApiServerTest {
    private static final Path WORKSPACE = Path.of("").toAbsolutePath().normalize();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .build();

    @Test
    void servesDashboardAndReadOnlyDataEndpoints(@TempDir Path temporaryDirectory) throws Exception {
        try (DashboardApiServer server = startServer(temporaryDirectory, WORKSPACE.resolve("sample-data/applications.csv"))) {
            server.start();

            JsonNode health = getJson(server, "/api/health", 200);
            assertEquals("ok", health.get("status").asText());

            JsonNode settings = getJson(server, "/api/settings", 200);
            assertEquals(server.port(), settings.get("port").asInt());
            assertTrue(settings.get("commands").size() > 0);

            JsonNode summary = getJson(server, "/api/dashboard/summary", 200);
            assertEquals(3, summary.get("totalApplications").asInt());
            assertFalse(summary.get("processingRunAvailable").asBoolean());
            assertTrue(summary.get("feesCollected").isNull());

            JsonNode applications = getJson(server, "/api/applications", 200);
            assertEquals(3, applications.get("applications").size());
            assertEquals("NOT_PROCESSED", applications.at("/applications/0/applicationStatus").asText());

            JsonNode appointments = getJson(server, "/api/appointments", 200);
            assertFalse(appointments.get("processed").asBoolean());
            assertEquals(0, appointments.get("appointments").size());

            JsonNode allocationResults = getJson(server, "/api/allocation-results", 200);
            assertFalse(allocationResults.get("processed").asBoolean());

            JsonNode documents = getJson(server, "/api/documents", 200);
            assertEquals(9, documents.get("documents").size());
            assertTrue(documents.at("/documents/0/verificationDate").isNull());

            JsonNode centres = getJson(server, "/api/centres", 200);
            assertEquals(2, centres.get("centres").size());
            assertTrue(centres.at("/centres/0/availableSlots").isNull());

            JsonNode priorities = getJson(server, "/api/priorities", 200);
            assertEquals("TATKAL", priorities.at("/categories/0/name").asText());
            assertEquals(5, priorities.get("categories").size());

            JsonNode billing = getJson(server, "/api/billing", 200);
            assertFalse(billing.get("paymentStatusAvailable").asBoolean());
            assertTrue(billing.get("totalCollected").isNull());

            JsonNode reports = getJson(server, "/api/reports", 200);
            assertEquals(0, reports.get("reports").size());

            JsonNode staticPage = getJson(server, "/", 200);
            assertTrue(staticPage.isTextual());
            assertTrue(staticPage.asText().contains("PS-117"));
        }
    }

    @Test
    void processesCsvThroughJavaEngineAndExposesGeneratedReports(@TempDir Path temporaryDirectory) throws Exception {
        try (DashboardApiServer server = startServer(temporaryDirectory, WORKSPACE.resolve("sample-data/applications.csv"))) {
            server.start();

            HttpResponse<String> process = postJson(server, "/api/process", Map.of("asOf", "2026-11-16T09:00:00"));
            assertEquals(200, process.statusCode(), process.body());
            JsonNode result = objectMapper.readTree(process.body());
            assertEquals("completed", result.get("status").asText());
            assertEquals(3, result.at("/summary/totalInputRecords").asInt());

            JsonNode summary = getJson(server, "/api/dashboard/summary", 200);
            assertTrue(summary.get("processingRunAvailable").asBoolean());
            assertEquals(3, summary.get("allocatedApplications").asInt());
            assertTrue(summary.get("availableSlots").asLong() > 0);

            JsonNode appointments = getJson(server, "/api/appointments", 200);
            assertEquals(3, appointments.get("appointments").size());

            JsonNode reports = getJson(server, "/api/reports", 200);
            assertTrue(reports.get("processed").asBoolean());
            assertTrue(reports.get("reports").size() >= 5);

            HttpResponse<String> report = get(server, "/api/reports/allocation-results");
            assertEquals(200, report.statusCode());
            assertTrue(report.body().contains("application_id,allocated"));

            JsonNode allocations = getJson(server, "/api/allocation-results", 200);
            assertEquals(3, allocations.get("results").size());

            JsonNode billing = getJson(server, "/api/billing", 200);
            assertTrue(billing.get("feeEstimatesAvailable").asBoolean());
            assertFalse(billing.get("paymentStatusAvailable").asBoolean());
            assertEquals(3, billing.get("transactions").size());
        }
    }

    @Test
    void rejectsUnsupportedProcessFieldsAndUnavailableInputs(@TempDir Path temporaryDirectory) throws Exception {
        try (DashboardApiServer server = startServer(temporaryDirectory, temporaryDirectory.resolve("missing.csv"))) {
            server.start();

            HttpResponse<String> invalid = postJson(server, "/api/process", Map.of("path", "/etc/passwd"));
            assertEquals(400, invalid.statusCode());
            assertEquals("INVALID_REQUEST", objectMapper.readTree(invalid.body()).at("/error/code").asText());

            HttpResponse<String> unavailable = get(server, "/api/applications");
            assertEquals(503, unavailable.statusCode());
            assertEquals("INPUT_UNAVAILABLE", objectMapper.readTree(unavailable.body()).at("/error/code").asText());

            HttpResponse<String> unknown = get(server, "/api/reports/not-a-report");
            assertEquals(404, unknown.statusCode());
        }
    }

    private DashboardApiServer startServer(Path temporaryDirectory, Path applicationsPath) throws Exception {
        ApplicationConfig base = ApplicationConfig.load();
        ApplicationConfig testConfig = new ApplicationConfig(
                base.applicationName(),
                base.applicationVersion(),
                base.commands(),
                WORKSPACE.resolve("sample-data/centres.csv"),
                applicationsPath,
                WORKSPACE.resolve("sample-data/documents.csv"),
                WORKSPACE.resolve("sample-data/events.csv"),
                temporaryDirectory.resolve("output")
        );
        return new DashboardApiServer(testConfig, WORKSPACE.resolve("dashboard"), 0);
    }

    private JsonNode getJson(DashboardApiServer server, String path, int expectedStatus) throws Exception {
        HttpResponse<String> response = get(server, path);
        assertEquals(expectedStatus, response.statusCode());
        if (path.equals("/")) return objectMapper.getNodeFactory().textNode(response.body());
        return objectMapper.readTree(response.body());
    }

    private HttpResponse<String> get(DashboardApiServer server, String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(uri(server, path)).GET().build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> postJson(DashboardApiServer server, String path, Object body) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(uri(server, path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private URI uri(DashboardApiServer server, String path) {
        return URI.create("http://127.0.0.1:" + server.port() + path);
    }
}
