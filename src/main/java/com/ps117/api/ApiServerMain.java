package com.ps117.api;

import com.ps117.infrastructure.ApplicationConfig;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;

public final class ApiServerMain {
    private ApiServerMain() {}

    public static void main(String[] args) throws Exception {
        int port = parsePort(args);
        Path workspace = Path.of("").toAbsolutePath().normalize();
        Path dashboardDirectory = workspace.resolve("dashboard");
        if (!Files.isDirectory(dashboardDirectory)) {
            throw new IllegalStateException("Dashboard directory is missing: " + dashboardDirectory);
        }

        DashboardApiServer server = new DashboardApiServer(
                ApplicationConfig.load(),
                dashboardDirectory,
                port
        );
        Runtime.getRuntime().addShutdownHook(new Thread(server::close, "dashboard-api-shutdown"));
        server.start();
        System.out.println("PS-117 dashboard available at http://127.0.0.1:" + server.port());
        System.out.println("API bound to loopback only; press Ctrl+C to stop.");
        new CountDownLatch(1).await();
    }

    private static int parsePort(String[] args) {
        if (args.length == 0) return 8080;
        if (args.length != 2 || !"--port".equals(args[0])) {
            throw new IllegalArgumentException("Usage: ApiServerMain [--port 8080]");
        }
        try {
            int port = Integer.parseInt(args[1]);
            if (port < 1024 || port > 65535) throw new NumberFormatException();
            return port;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Port must be an integer between 1024 and 65535.", exception);
        }
    }
}
