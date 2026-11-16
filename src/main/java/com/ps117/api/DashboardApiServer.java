package com.ps117.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ps117.infrastructure.ApplicationConfig;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class DashboardApiServer implements AutoCloseable {
    private static final int MAX_REQUEST_BYTES = 8 * 1024;
    private static final Pattern REPORT_PATH = Pattern.compile("^/api/reports/([a-z-]+)$");

    private final HttpServer server;
    private final ExecutorService executor;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final DashboardDataService dataService;
    private final Path dashboardRoot;
    private final AtomicBoolean started = new AtomicBoolean();

    public DashboardApiServer(ApplicationConfig config, Path dashboardRoot, int port) throws IOException {
        this.server = HttpServer.create(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), port), 32);
        this.executor = Executors.newFixedThreadPool(8);
        this.dataService = new DashboardDataService(config);
        this.dashboardRoot = dashboardRoot.toRealPath();
        server.setExecutor(executor);
        server.createContext("/", this::handle);
    }

    public void start() {
        if (started.compareAndSet(false, true)) {
            server.start();
        }
    }

    public int port() {
        return server.getAddress().getPort();
    }

    @Override
    public void close() {
        if (started.compareAndSet(true, false)) {
            server.stop(1);
        }
        executor.shutdownNow();
    }

    private void handle(HttpExchange exchange) throws IOException {
        applySecurityHeaders(exchange.getResponseHeaders());
        try {
            String path = exchange.getRequestURI().getPath();
            if (path.startsWith("/api/")) {
                handleApi(exchange, path);
            } else {
                handleStatic(exchange, path);
            }
        } catch (ApiException exception) {
            sendJson(exchange, exception.statusCode(), Map.of("error", Map.of(
                    "code", exception.code(),
                    "message", exception.getMessage()
            )));
        } catch (JsonProcessingException exception) {
            sendJson(exchange, 400, Map.of("error", Map.of(
                    "code", "INVALID_JSON",
                    "message", "Request body must contain valid JSON."
            )));
        } catch (IOException exception) {
            System.err.println("Dashboard request I/O failed: " + exception.getMessage());
            if (exchange.getResponseCode() == -1) {
                sendJson(exchange, 500, Map.of("error", Map.of(
                        "code", "IO_ERROR",
                        "message", "The request could not be completed."
                )));
            }
        } catch (Exception exception) {
            System.err.println("Dashboard request failed: " + exception);
            sendJson(exchange, 500, Map.of("error", Map.of(
                    "code", "INTERNAL_ERROR",
                    "message", "The request could not be completed."
            )));
        } finally {
            exchange.close();
        }
    }

    private void handleApi(HttpExchange exchange, String path) throws Exception {
        String method = exchange.getRequestMethod();
        if (path.equals("/api/health")) {
            requireMethod(method, "GET");
            sendJson(exchange, 200, dataService.health());
        } else if (path.equals("/api/settings")) {
            requireMethod(method, "GET");
            sendJson(exchange, 200, dataService.settings(port()));
        } else if (path.equals("/api/dashboard/summary")) {
            requireMethod(method, "GET");
            sendJson(exchange, 200, dataService.summary());
        } else if (path.equals("/api/applications")) {
            requireMethod(method, "GET");
            sendJson(exchange, 200, dataService.applications());
        } else if (path.equals("/api/appointments")) {
            requireMethod(method, "GET");
            sendJson(exchange, 200, dataService.appointments());
        } else if (path.equals("/api/allocation-results")) {
            requireMethod(method, "GET");
            sendJson(exchange, 200, dataService.allocationResults());
        } else if (path.equals("/api/documents")) {
            requireMethod(method, "GET");
            sendJson(exchange, 200, dataService.documents());
        } else if (path.equals("/api/centres")) {
            requireMethod(method, "GET");
            sendJson(exchange, 200, dataService.centres());
        } else if (path.equals("/api/priorities")) {
            requireMethod(method, "GET");
            sendJson(exchange, 200, dataService.priorities());
        } else if (path.equals("/api/billing")) {
            requireMethod(method, "GET");
            sendJson(exchange, 200, dataService.billing());
        } else if (path.equals("/api/reports")) {
            requireMethod(method, "GET");
            sendJson(exchange, 200, dataService.reports());
        } else if (path.equals("/api/process")) {
            requireMethod(method, "POST");
            enforceSameOrigin(exchange);
            LocalDateTime asOf = readProcessRequest(exchange);
            sendJson(exchange, 200, dataService.process(asOf));
        } else {
            Matcher reportPath = REPORT_PATH.matcher(path);
            if (reportPath.matches()) {
                requireMethod(method, "GET");
                sendCsv(exchange, dataService.reportFile(reportPath.group(1)));
            } else {
                throw new ApiException(404, "NOT_FOUND", "API route not found.");
            }
        }
    }

    private void handleStatic(HttpExchange exchange, String path) throws IOException {
        String method = exchange.getRequestMethod();
        if (!"GET".equals(method) && !"HEAD".equals(method)) {
            exchange.getResponseHeaders().set("Allow", "GET, HEAD");
            throw new ApiException(405, "METHOD_NOT_ALLOWED", "Only GET and HEAD are supported for dashboard files.");
        }
        String relativePath = path.equals("/") ? "index.html" : path.substring(1);
        Path file = dashboardRoot.resolve(relativePath).normalize();
        if (!file.startsWith(dashboardRoot) || !Files.isRegularFile(file)) {
            throw new ApiException(404, "NOT_FOUND", "Dashboard resource not found.");
        }
        Path realFile = file.toRealPath();
        if (!realFile.startsWith(dashboardRoot) || !Files.isReadable(realFile)) {
            throw new ApiException(404, "NOT_FOUND", "Dashboard resource not found.");
        }
        Headers headers = exchange.getResponseHeaders();
        headers.set("Content-Type", contentType(realFile));
        headers.set("Cache-Control", "no-cache");
        long length = Files.size(realFile);
        if ("HEAD".equals(method)) {
            exchange.sendResponseHeaders(200, -1);
            return;
        }
        exchange.sendResponseHeaders(200, length);
        Files.copy(realFile, exchange.getResponseBody());
    }

    private LocalDateTime readProcessRequest(HttpExchange exchange) throws IOException {
        String contentType = exchange.getRequestHeaders().getFirst("Content-Type");
        if (contentType == null || !contentType.toLowerCase().startsWith("application/json")) {
            throw new ApiException(415, "UNSUPPORTED_MEDIA_TYPE", "POST /api/process requires application/json.");
        }
        byte[] body = exchange.getRequestBody().readNBytes(MAX_REQUEST_BYTES + 1);
        if (body.length > MAX_REQUEST_BYTES) {
            throw new ApiException(413, "REQUEST_TOO_LARGE", "Request body exceeds the allowed size.");
        }
        LocalDateTime defaultAsOf = LocalDateTime.now();
        if (body.length == 0) return defaultAsOf;

        JsonNode node = objectMapper.readTree(body);
        if (node == null || !node.isObject()) {
            throw new ApiException(400, "INVALID_REQUEST", "Request body must be a JSON object.");
        }
        if (node.size() > 1 || (node.size() == 1 && !node.has("asOf"))) {
            throw new ApiException(400, "INVALID_REQUEST", "Only the optional asOf field is accepted.");
        }
        JsonNode asOfNode = node.get("asOf");
        if (asOfNode == null) return defaultAsOf;
        if (!asOfNode.isTextual()) {
            throw new ApiException(400, "INVALID_AS_OF", "asOf must be an ISO local date-time string.");
        }
        try {
            return LocalDateTime.parse(asOfNode.asText());
        } catch (DateTimeParseException exception) {
            throw new ApiException(400, "INVALID_AS_OF", "asOf must be an ISO local date-time string.");
        }
    }

    private void enforceSameOrigin(HttpExchange exchange) {
        String origin = exchange.getRequestHeaders().getFirst("Origin");
        if (origin == null) return;
        int port = port();
        if (!origin.equals("http://127.0.0.1:" + port) && !origin.equals("http://localhost:" + port)) {
            throw new ApiException(403, "ORIGIN_NOT_ALLOWED", "Cross-origin processing requests are not allowed.");
        }
    }

    private void requireMethod(String actual, String expected) {
        if (!expected.equals(actual)) {
            throw new ApiException(405, "METHOD_NOT_ALLOWED", "This route requires " + expected + ".");
        }
    }

    private void sendJson(HttpExchange exchange, int status, Object body) throws IOException {
        byte[] bytes = objectMapper.writeValueAsBytes(body);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
    }

    private void sendCsv(HttpExchange exchange, Path file) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "text/csv; charset=utf-8");
        exchange.getResponseHeaders().set("Content-Disposition",
                "attachment; filename=\"" + file.getFileName() + "\"");
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(200, Files.size(file));
        Files.copy(file, exchange.getResponseBody());
    }

    private void applySecurityHeaders(Headers headers) {
        headers.set("X-Content-Type-Options", "nosniff");
        headers.set("X-Frame-Options", "DENY");
        headers.set("Referrer-Policy", "no-referrer");
        headers.set("Content-Security-Policy",
                "default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' data:; connect-src 'self'; object-src 'none'; base-uri 'self'; frame-ancestors 'none'");
    }

    private String contentType(Path file) {
        String name = file.getFileName().toString().toLowerCase();
        if (name.endsWith(".html")) return "text/html; charset=utf-8";
        if (name.endsWith(".css")) return "text/css; charset=utf-8";
        if (name.endsWith(".js")) return "text/javascript; charset=utf-8";
        if (name.endsWith(".svg")) return "image/svg+xml";
        if (name.endsWith(".json")) return "application/json; charset=utf-8";
        return "application/octet-stream";
    }
}
