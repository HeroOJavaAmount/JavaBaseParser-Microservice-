package ru.netology.petach;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import ru.netology.audit.AuditEvent;
import ru.netology.audit.AuditStorage;
import ru.netology.auth.AuthResult;
import ru.netology.auth.NoAuthTokenService;
import ru.netology.auth.TokenService;
import ru.netology.config.AppVersion;
import ru.netology.dto.Document;
import ru.netology.exception.PdfParserException;
import ru.netology.serialize.DocumentSerializer;
import ru.netology.service.DocumentService;
import ru.netology.storage.DocumentStorage;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executors;

/**
 * HTTP-сервер для интеграции с 1С.
 *
 * <p><b>Аутентификация</b>: JWT (HS256). Все эндпоинты кроме {@code /health}
 * требуют {@code Authorization: Bearer <jwt>}. В dev-режиме (без секрета)
 * используется {@link NoAuthTokenService} — пропускает всех.
 *
 * <p><b>Эндпоинты</b>:
 * <ul>
 *   <li>{@code POST /api/parse} — тело: PDF; заголовки: X-User-Id, X-File-Name</li>
 *   <li>{@code GET /api/documents/{hash}} — JSON Document</li>
 *   <li>{@code GET /api/documents/{hash}/audit} — история событий</li>
 *   <li>{@code GET /health} — 200 OK, без авторизации</li>
 * </ul>
 */
public class HttpServerRunner {

    private final DocumentService service;
    private final DocumentSerializer serializer;
    private final DocumentStorage storage;
    private final AuditStorage audit;
    private final String parserName;
    private final boolean repairMojibake;
    private final TokenService tokenService;
    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());

    public HttpServerRunner(DocumentService service,
                            DocumentSerializer serializer,
                            DocumentStorage storage,
                            AuditStorage audit,
                            String parserName,
                            boolean repairMojibake,
                            TokenService tokenService) {
        this.service = service;
        this.serializer = serializer;
        this.storage = storage;
        this.audit = audit;
        this.parserName = parserName;
        this.repairMojibake = repairMojibake;
        this.tokenService = tokenService;
    }

    /** Запустить сервер и блокировать поток до Ctrl+C. */
    public void run(int port) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.setExecutor(Executors.newFixedThreadPool(4));

        server.createContext("/health", this::handleHealth);
        server.createContext("/api/parse", this::handleParse);
        server.createContext("/api/documents", this::handleDocuments);
        server.createContext("/version", this::handleVersion);
        server.start();
        System.out.println("HTTP server started on http://0.0.0.0:" + port);
        System.out.println("  POST /api/parse   (headers: X-User-Id, X-File-Name, Authorization)");
        System.out.println("  GET  /api/documents/{hash}");
        System.out.println("  GET  /api/documents/{hash}/audit");
        System.out.println("  GET  /health");
        System.out.println("Auth: " + (tokenService instanceof NoAuthTokenService
                ? "DISABLED (dev mode)"
                : "JWT (HS256)"));
        System.out.println("Press Ctrl+C to stop.");

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("Stopping HTTP server...");
            server.stop(1);
        }));
        try {
            Thread.currentThread().join();   // держим main живым до Ctrl+C
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            server.stop(1);
        }
    }

    // ────────────────────────────────────────────────────────────
    //  Auth
    // ────────────────────────────────────────────────────────────

    /** Проверяет Bearer-токен через {@link TokenService}. */
    private boolean checkAuth(HttpExchange ex) {
        String header = ex.getRequestHeaders().getFirst("Authorization");
        String token = (header != null && header.startsWith("Bearer "))
                ? header.substring("Bearer ".length()).trim()
                : null;

        AuthResult result = tokenService.validate(token);
        if (!result.valid()) {
            System.out.println("Auth failed: " + result.error());
            return false;
        }
        return true;
    }

    private void respondUnauthorized(HttpExchange ex) throws IOException {
        ex.getResponseHeaders().set("WWW-Authenticate", "Bearer realm=\"pdf-parser\"");
        respond(ex, 401, "application/json", "{\"error\":\"Unauthorized\"}");
    }

    // ────────────────────────────────────────────────────────────
    //  Handlers
    // ────────────────────────────────────────────────────────────

    /** {@code GET /health} — всегда открыт (для LB/K8s). */
    private void handleHealth(HttpExchange ex) throws IOException {
        respond(ex, 200, "text/plain", "OK");
    }

    /** {@code POST /api/parse} — PDF → JSON Document. */
    private void handleParse(HttpExchange ex) throws IOException {
        if (!"POST".equals(ex.getRequestMethod())) {
            respond(ex, 405, "text/plain", "Method Not Allowed");
            return;
        }
        if (!checkAuth(ex)) {
            respondUnauthorized(ex);
            return;
        }

        String userId = firstOr(ex, "X-User-Id", "anonymous");
        String fileName = ex.getRequestHeaders().getFirst("X-File-Name");

        byte[] body;
        try (InputStream in = ex.getRequestBody()) {
            body = in.readAllBytes();
        }
        if (body.length == 0) {
            respond(ex, 400, "text/plain", "Empty body");
            return;
        }

        try {
            Document doc = service.parse(body, repairMojibake, fileName);
            String json = serializer.serialize(doc);
            storage.save(doc.hash(), json);
            audit.record(AuditEvent.parsed(doc.hash(), userId, parserName, null));
            respond(ex, 200, "application/json", json);
        } catch (PdfParserException pex) {
            audit.record(AuditEvent.failed("unknown", userId, parserName, null, pex.getMessage()));
            respond(ex, 422, "application/json",
                    "{\"error\":\"" + escape(pex.getMessage()) + "\"}");
        } catch (Exception e) {
            respond(ex, 500, "application/json",
                    "{\"error\":\"" + escape(e.getMessage()) + "\"}");
        }
    }

    /** {@code GET /api/documents/{hash}} и {@code .../audit}. */
    private void handleDocuments(HttpExchange ex) throws IOException {
        if (!"GET".equals(ex.getRequestMethod())) {
            respond(ex, 405, "text/plain", "Method Not Allowed");
            return;
        }
        if (!checkAuth(ex)) {
            respondUnauthorized(ex);
            return;
        }

        String[] parts = ex.getRequestURI().getPath().split("/");
        if (parts.length < 4) {
            respond(ex, 400, "text/plain", "Missing hash");
            return;
        }
        String hash = parts[3];
        boolean auditRequest = parts.length >= 5 && "audit".equals(parts[4]);

        if (auditRequest) {
            List<AuditEvent> events = audit.findByDocumentHash(hash);
            respond(ex, 200, "application/json", mapper.writeValueAsString(events));
            return;
        }

        Optional<String> doc = storage.findByHash(hash);
        if (doc.isEmpty()) {
            respond(ex, 404, "text/plain", "Not found: " + hash);
            return;
        }
        respond(ex, 200, "application/json", doc.get());
    }

    /**
     * GET /version — версия сервиса.
     * Returns {"title": "...", "version": "..."}.
     */
    private void handleVersion(HttpExchange ex) throws IOException {
        String json = "{\"title\":\"" + AppVersion.title()
                + "\",\"version\":\"" + AppVersion.version() + "\"}";
        respond(ex, 200, "application/json", json);
    }

    // ────────────────────────────────────────────────────────────
    //  Helpers
    // ────────────────────────────────────────────────────────────

    private void respond(HttpExchange ex, int code, String type, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", type + "; charset=utf-8");
        ex.sendResponseHeaders(code, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }

    private static String firstOr(HttpExchange ex, String header, String fallback) {
        String v = ex.getRequestHeaders().getFirst(header);
        return (v == null || v.isBlank()) ? fallback : v;
    }

    private static String escape(String s) {
        return s == null ? "" : s.replace("\"", "\\\"");
    }
}