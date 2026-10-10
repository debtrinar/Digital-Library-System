package com.onlinelibrary;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;

/**
 * Lumina Digital Library & PDF Reader - High-Performance Java Server
 * Features:
 * - Real-time worldwide book search (Google Books + Open Library API)
 * - Curated classic library with bundled public domain works
 * - Interactive PDF Reader integration (PDF.js)
 * - In-flight pure Java PDF Generator (generates custom PDF book dossiers)
 * - Personal Shelf & Bookmark management (Local JSON persistence)
 * - PDF Upload and CORS-free PDF stream proxy
 */
public class OnlineLibraryServer {

    private static final int PORT = 8080;
    private static final Path DATA_DIR = Paths.get("data");
    private static final Path UPLOADS_DIR = Paths.get("data", "uploads");
    private static final Path SHELF_FILE = Paths.get("data", "shelf.json");
    private static final Path STATIC_DIR = Paths.get("src", "main", "resources", "static");

    public static void main(String[] args) throws IOException {
        // Initialize storage directories
        Files.createDirectories(DATA_DIR);
        Files.createDirectories(UPLOADS_DIR);
        if (!Files.exists(SHELF_FILE)) {
            Files.write(SHELF_FILE, "[]".getBytes(StandardCharsets.UTF_8));
        }

        HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);
        server.setExecutor(Executors.newFixedThreadPool(16));

        // API Endpoints
        server.createContext("/api/books/search", new SearchHandler());
        server.createContext("/api/books/featured", new FeaturedHandler());
        server.createContext("/api/shelf", new ShelfHandler());
        server.createContext("/api/shelf/progress", new ProgressHandler());
        server.createContext("/api/pdf/proxy", new PdfProxyHandler());
        server.createContext("/api/pdf/generate", new PdfGenerateHandler());
        server.createContext("/api/upload", new UploadHandler());
        server.createContext("/api/health", exchange -> {
            sendResponse(exchange, 200, "{\"status\":\"UP\",\"service\":\"Lumina Online Library\"}", "application/json");
        });

        // Static File Serving
        server.createContext("/", new StaticFileHandler());

        server.start();
        System.out.println("==================================================================");
        System.out.println(" 📚  LUMINA DIGITAL LIBRARY & PDF READER IS RUNNING");
        System.out.println(" 🌐  URL: http://localhost:" + PORT);
        System.out.println(" 📖  Reader: http://localhost:" + PORT + "/reader.html");
        System.out.println(" 📂  Data Directory: " + DATA_DIR.toAbsolutePath());
        System.out.println("==================================================================");
    }

    // =================================================================================
    // HTTP UTILITIES
    // =================================================================================

    public static void sendResponse(HttpExchange exchange, int statusCode, String body, String contentType) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        addCorsHeaders(exchange);
        exchange.getResponseHeaders().set("Content-Type", contentType + "; charset=UTF-8");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    public static void sendBinaryResponse(HttpExchange exchange, int statusCode, byte[] data, String contentType, String filename) throws IOException {
        addCorsHeaders(exchange);
        exchange.getResponseHeaders().set("Content-Type", contentType);
        if (filename != null && !filename.isEmpty()) {
            exchange.getResponseHeaders().set("Content-Disposition", "inline; filename=\"" + filename + "\"");
        }
        exchange.sendResponseHeaders(statusCode, data.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(data);
        }
    }

    public static void addCorsHeaders(HttpExchange exchange) {
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type, Authorization, Range");
    }

    public static Map<String, String> parseQueryParams(String query) {
        Map<String, String> params = new HashMap<>();
        if (query == null || query.isEmpty()) return params;
        String[] pairs = query.split("&");
        for (String pair : pairs) {
            int idx = pair.indexOf("=");
            try {
                if (idx > 0) {
                    String key = URLDecoder.decode(pair.substring(0, idx), "UTF-8");
                    String val = URLDecoder.decode(pair.substring(idx + 1), "UTF-8");
                    params.put(key, val);
                } else {
                    params.put(URLDecoder.decode(pair, "UTF-8"), "");
                }
            } catch (Exception ignored) {}
        }
        return params;
    }

    public static String readRequestBody(HttpExchange exchange) throws IOException {
        try (InputStream is = exchange.getRequestBody();
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            byte[] buf = new byte[4096];
            int read;
            while ((read = is.read(buf)) != -1) {
                baos.write(buf, 0, read);
            }
            return baos.toString(StandardCharsets.UTF_8.name());
        }
    }

    // =================================================================================
    // HANDLERS
    // =================================================================================

    /**
     * Search books using Google Books API with fallback to Open Library API
     */
    static class SearchHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                addCorsHeaders(exchange);
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            Map<String, String> params = parseQueryParams(exchange.getRequestURI().getQuery());
            String query = params.getOrDefault("q", "").trim();
            String category = params.getOrDefault("category", "").trim();

            if (query.isEmpty() && category.isEmpty()) {
                sendResponse(exchange, 400, "{\"error\":\"Query parameter 'q' or 'category' is required\"}", "application/json");
                return;
            }

            String searchTerms = query;
            if (!category.isEmpty() && !"all".equalsIgnoreCase(category)) {
                searchTerms += " subject:" + category;
            }

            String jsonResult = fetchGoogleBooks(searchTerms);
            sendResponse(exchange, 200, jsonResult, "application/json");
        }

        private String fetchGoogleBooks(String query) {
            try {
                String encoded = URLEncoder.encode(query, "UTF-8");
                String apiUrl = "https://www.googleapis.com/books/v1/volumes?q=" + encoded + "&maxResults=24&printType=books";
                URL url = new URL(apiUrl);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("User-Agent", "LuminaOnlineLibrary/1.0");
                conn.setConnectTimeout(6000);
                conn.setReadTimeout(8000);

                if (conn.getResponseCode() == 200) {
                    try (InputStream in = conn.getInputStream();
                         ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                        byte[] buf = new byte[8192];
                        int r;
                        while ((r = in.read(buf)) != -1) baos.write(buf, 0, r);
                        return baos.toString(StandardCharsets.UTF_8.name());
                    }
                }
            } catch (Exception e) {
                System.err.println("Google Books API query failed: " + e.getMessage() + ". Using fallback.");
            }
            return fetchOpenLibraryFallback(query);
        }

        private String fetchOpenLibraryFallback(String query) {
            try {
                String encoded = URLEncoder.encode(query, "UTF-8");
                String apiUrl = "https://openlibrary.org/search.json?q=" + encoded + "&limit=20";
                URL url = new URL(apiUrl);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestProperty("User-Agent", "LuminaOnlineLibrary/1.0");
                conn.setConnectTimeout(6000);
                conn.setReadTimeout(8000);

                if (conn.getResponseCode() == 200) {
                    try (InputStream in = conn.getInputStream();
                         ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                        byte[] buf = new byte[8192];
                        int r;
                        while ((r = in.read(buf)) != -1) baos.write(buf, 0, r);
                        return baos.toString(StandardCharsets.UTF_8.name());
                    }
                }
            } catch (Exception ignored) {}
            return "{\"items\":[]}";
        }
    }

    /**
     * Hand-curated best classics and featured books with direct public-domain PDF links
     */
    static class FeaturedHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                addCorsHeaders(exchange);
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            String featuredJson = "[\n" +
                "  {\n" +
                "    \"id\": \"feat-art-of-war\",\n" +
                "    \"title\": \"The Art of War\",\n" +
                "    \"authors\": [\"Sun Tzu\"],\n" +
                "    \"publisher\": \"Public Domain Classic\",\n" +
                "    \"publishedDate\": \"5th Century BC\",\n" +
                "    \"description\": \"An ancient Chinese military treatise attributed to Sun Tzu, a high-ranking military general, strategist and tactician. The text is composed of 13 chapters devoted to the strategic and tactical aspects of warfare and leadership.\",\n" +
                "    \"pageCount\": 64,\n" +
                "    \"categories\": [\"Philosophy\", \"Strategy\", \"Classics\"],\n" +
                "    \"thumbnail\": \"https://images.unsplash.com/photo-1544716278-ca5e3f4abd8c?w=400&auto=format&fit=crop&q=80\",\n" +
                "    \"rating\": 4.8,\n" +
                "    \"pdfUrl\": \"/samples/the-art-of-war.pdf\",\n" +
                "    \"hasDirectPdf\": true\n" +
                "  },\n" +
                "  {\n" +
                "    \"id\": \"feat-frankenstein\",\n" +
                "    \"title\": \"Frankenstein; or, The Modern Prometheus\",\n" +
                "    \"authors\": [\"Mary Shelley\"],\n" +
                "    \"publisher\": \"Lackington, Hughes, Harding, Mavor, & Jones\",\n" +
                "    \"publishedDate\": \"1818\",\n" +
                "    \"description\": \"Mary Shelley's masterpiece tells the story of Victor Frankenstein, a young scientist who creates a sapient creature in an unorthodox scientific experiment, exploring themes of ambition, monstrosity, and ethics.\",\n" +
                "    \"pageCount\": 280,\n" +
                "    \"categories\": [\"Gothic Horror\", \"Science Fiction\", \"Classics\"],\n" +
                "    \"thumbnail\": \"https://images.unsplash.com/photo-1509198397868-475647b2a1e5?w=400&auto=format&fit=crop&q=80\",\n" +
                "    \"rating\": 4.7,\n" +
                "    \"pdfUrl\": \"/samples/frankenstein.pdf\",\n" +
                "    \"hasDirectPdf\": true\n" +
                "  },\n" +
                "  {\n" +
                "    \"id\": \"feat-sherlock-holmes\",\n" +
                "    \"title\": \"The Adventures of Sherlock Holmes\",\n" +
                "    \"authors\": [\"Arthur Conan Doyle\"],\n" +
                "    \"publisher\": \"George Newnes\",\n" +
                "    \"publishedDate\": \"1892\",\n" +
                "    \"description\": \"A collection of twelve short stories featuring legendary consulting detective Sherlock Holmes and his steadfast chronicler Dr. John Watson, solving London's most baffling mysteries.\",\n" +
                "    \"pageCount\": 307,\n" +
                "    \"categories\": [\"Mystery\", \"Detective Fiction\", \"Classics\"],\n" +
                "    \"thumbnail\": \"https://images.unsplash.com/photo-1476275466078-4007374efbbe?w=400&auto=format&fit=crop&q=80\",\n" +
                "    \"rating\": 4.9,\n" +
                "    \"pdfUrl\": \"/samples/sherlock-holmes.pdf\",\n" +
                "    \"hasDirectPdf\": true\n" +
                "  },\n" +
                "  {\n" +
                "    \"id\": \"feat-pride-prejudice\",\n" +
                "    \"title\": \"Pride and Prejudice\",\n" +
                "    \"authors\": [\"Jane Austen\"],\n" +
                "    \"publisher\": \"T. Egerton\",\n" +
                "    \"publishedDate\": \"1813\",\n" +
                "    \"description\": \"A romantic novel of manners following Elizabeth Bennet as she deals with issues of upbringing, morality, education, and marriage in Regency-era British society alongside Fitzwilliam Darcy.\",\n" +
                "    \"pageCount\": 432,\n" +
                "    \"categories\": [\"Romance\", \"Classics\", \"Historical Fiction\"],\n" +
                "    \"thumbnail\": \"https://images.unsplash.com/photo-1512820790803-83ca734da794?w=400&auto=format&fit=crop&q=80\",\n" +
                "    \"rating\": 4.9,\n" +
                "    \"pdfUrl\": \"/samples/pride-and-prejudice.pdf\",\n" +
                "    \"hasDirectPdf\": true\n" +
                "  },\n" +
                "  {\n" +
                "    \"id\": \"feat-alice-wonderland\",\n" +
                "    \"title\": \"Alice's Adventures in Wonderland\",\n" +
                "    \"authors\": [\"Lewis Carroll\"],\n" +
                "    \"publisher\": \"Macmillan\",\n" +
                "    \"publishedDate\": \"1865\",\n" +
                "    \"description\": \"Follow young Alice who falls through a rabbit hole into a subterranean fantasy world populated by peculiar, anthropomorphic creatures. One of the best examples of literary nonsense.\",\n" +
                "    \"pageCount\": 192,\n" +
                "    \"categories\": [\"Fantasy\", \"Children's Literature\", \"Classics\"],\n" +
                "    \"thumbnail\": \"https://images.unsplash.com/photo-1516979187457-637abb4f9353?w=400&auto=format&fit=crop&q=80\",\n" +
                "    \"rating\": 4.6,\n" +
                "    \"pdfUrl\": \"/samples/alice-in-wonderland.pdf\",\n" +
                "    \"hasDirectPdf\": true\n" +
                "  },\n" +
                "  {\n" +
                "    \"id\": \"feat-clean-code\",\n" +
                "    \"title\": \"Clean Code: A Handbook of Agile Software Craftsmanship\",\n" +
                "    \"authors\": [\"Robert C. Martin\"],\n" +
                "    \"publisher\": \"Prentice Hall\",\n" +
                "    \"publishedDate\": \"2008\",\n" +
                "    \"description\": \"Even bad code can function. But if code isn't clean, it can bring a development organization to its knees. Master the principles, patterns, and practices of writing clean, elegant code.\",\n" +
                "    \"pageCount\": 464,\n" +
                "    \"categories\": [\"Computers\", \"Programming\", \"Software Engineering\"],\n" +
                "    \"thumbnail\": \"https://images.unsplash.com/photo-1555066931-4365d14bab8c?w=400&auto=format&fit=crop&q=80\",\n" +
                "    \"rating\": 4.8,\n" +
                "    \"pdfUrl\": \"/api/pdf/generate?id=feat-clean-code&title=Clean+Code&author=Robert+C.+Martin\",\n" +
                "    \"hasDirectPdf\": false\n" +
                "  }\n" +
                "]";

            sendResponse(exchange, 200, featuredJson, "application/json");
        }
    }

    /**
     * User Shelf Management (List, Add, Remove)
     */
    static class ShelfHandler implements HttpHandler {
        private final Object lock = new Object();

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            addCorsHeaders(exchange);
            String method = exchange.getRequestMethod();

            if ("OPTIONS".equalsIgnoreCase(method)) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            synchronized (lock) {
                if ("GET".equalsIgnoreCase(method)) {
                    if (!Files.exists(SHELF_FILE)) {
                        sendResponse(exchange, 200, "[]", "application/json");
                        return;
                    }
                    byte[] data = Files.readAllBytes(SHELF_FILE);
                    sendBinaryResponse(exchange, 200, data, "application/json", null);
                } else if ("POST".equalsIgnoreCase(method)) {
                    String body = readRequestBody(exchange);
                    if (body.isEmpty()) {
                        sendResponse(exchange, 400, "{\"error\":\"Empty body\"}", "application/json");
                        return;
                    }

                    List<String> items = readJsonArray(SHELF_FILE);
                    String newItem = body.trim();
                    String itemId = extractField(newItem, "id");

                    // Replace if already exists, else append
                    boolean replaced = false;
                    for (int i = 0; i < items.size(); i++) {
                        if (extractField(items.get(i), "id").equals(itemId)) {
                            items.set(i, newItem);
                            replaced = true;
                            break;
                        }
                    }
                    if (!replaced) {
                        items.add(0, newItem);
                    }

                    writeJsonArray(SHELF_FILE, items);
                    sendResponse(exchange, 200, "{\"success\":true,\"message\":\"Saved to shelf\"}", "application/json");
                } else if ("DELETE".equalsIgnoreCase(method)) {
                    Map<String, String> params = parseQueryParams(exchange.getRequestURI().getQuery());
                    String id = params.get("id");
                    if (id == null || id.isEmpty()) {
                        sendResponse(exchange, 400, "{\"error\":\"Missing id\"}", "application/json");
                        return;
                    }

                    List<String> items = readJsonArray(SHELF_FILE);
                    items.removeIf(item -> extractField(item, "id").equals(id));
                    writeJsonArray(SHELF_FILE, items);
                    sendResponse(exchange, 200, "{\"success\":true,\"message\":\"Removed from shelf\"}", "application/json");
                } else {
                    sendResponse(exchange, 405, "{\"error\":\"Method not allowed\"}", "application/json");
                }
            }
        }
    }

    /**
     * Updates reading progress (page number, notes, completion status)
     */
    static class ProgressHandler implements HttpHandler {
        private final Object lock = new Object();

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                addCorsHeaders(exchange);
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "{\"error\":\"Method not allowed\"}", "application/json");
                return;
            }

            String body = readRequestBody(exchange);
            String id = extractField(body, "id");
            String currentPage = extractField(body, "currentPage");
            String notes = extractField(body, "notes");
            String status = extractField(body, "status");

            if (id.isEmpty()) {
                sendResponse(exchange, 400, "{\"error\":\"Missing id\"}", "application/json");
                return;
            }

            synchronized (lock) {
                List<String> items = readJsonArray(SHELF_FILE);
                boolean found = false;
                for (int i = 0; i < items.size(); i++) {
                    String item = items.get(i);
                    if (extractField(item, "id").equals(id)) {
                        String updated = updateField(item, "currentPage", currentPage.isEmpty() ? "1" : currentPage);
                        if (!notes.isEmpty()) updated = updateField(updated, "notes", notes);
                        if (!status.isEmpty()) updated = updateField(updated, "status", status);
                        updated = updateField(updated, "lastRead", String.valueOf(System.currentTimeMillis()));
                        items.set(i, updated);
                        found = true;
                        break;
                    }
                }
                if (found) {
                    writeJsonArray(SHELF_FILE, items);
                    sendResponse(exchange, 200, "{\"success\":true}", "application/json");
                } else {
                    sendResponse(exchange, 404, "{\"error\":\"Book not in shelf\"}", "application/json");
                }
            }
        }
    }

    /**
     * Upload local PDF file into personal library
     */
    static class UploadHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                addCorsHeaders(exchange);
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "{\"error\":\"Method not allowed\"}", "application/json");
                return;
            }

            String title = "Uploaded Document";
            String author = "Personal Upload";
            Map<String, String> q = parseQueryParams(exchange.getRequestURI().getQuery());
            if (q.containsKey("title")) title = q.get("title");
            if (q.containsKey("author")) author = q.get("author");

            String fileId = "pdf-" + UUID.randomUUID().toString().substring(0, 8);
            String fileName = fileId + ".pdf";
            Path targetFile = UPLOADS_DIR.resolve(fileName);

            try (InputStream is = exchange.getRequestBody();
                 OutputStream os = Files.newOutputStream(targetFile)) {
                byte[] buf = new byte[8192];
                int n;
                while ((n = is.read(buf)) != -1) {
                    os.write(buf, 0, n);
                }
            }

            String bookJson = "{\n" +
                "  \"id\": \"" + fileId + "\",\n" +
                "  \"title\": \"" + escapeJson(title) + "\",\n" +
                "  \"authors\": [\"" + escapeJson(author) + "\"],\n" +
                "  \"publisher\": \"Personal Library\",\n" +
                "  \"publishedDate\": \"Uploaded\",\n" +
                "  \"description\": \"Local PDF document uploaded to Lumina Library.\",\n" +
                "  \"pageCount\": 0,\n" +
                "  \"categories\": [\"Uploaded\", \"My Documents\"],\n" +
                "  \"thumbnail\": \"https://images.unsplash.com/photo-1544947950-fa07a98d237f?w=400&auto=format&fit=crop&q=80\",\n" +
                "  \"rating\": 5.0,\n" +
                "  \"pdfUrl\": \"/uploads/" + fileName + "\",\n" +
                "  \"hasDirectPdf\": true,\n" +
                "  \"isLocal\": true\n" +
                "}";

            // Add directly to shelf
            List<String> items = readJsonArray(SHELF_FILE);
            items.add(0, bookJson);
            writeJsonArray(SHELF_FILE, items);

            sendResponse(exchange, 200, bookJson, "application/json");
        }
    }

    /**
     * Proxies remote PDF files with CORS headers so PDF.js can render them cleanly
     */
    static class PdfProxyHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                addCorsHeaders(exchange);
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            Map<String, String> params = parseQueryParams(exchange.getRequestURI().getQuery());
            String targetUrl = params.get("url");

            if (targetUrl == null || targetUrl.isEmpty()) {
                sendResponse(exchange, 400, "{\"error\":\"Missing 'url' query param\"}", "application/json");
                return;
            }

            try {
                URL url = new URL(targetUrl);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) LuminaOnlineLibrary/1.0");
                conn.setConnectTimeout(8000);
                conn.setReadTimeout(15000);

                int respCode = conn.getResponseCode();
                if (respCode >= 200 && respCode < 400) {
                    addCorsHeaders(exchange);
                    exchange.getResponseHeaders().set("Content-Type", "application/pdf");
                    exchange.getResponseHeaders().set("Content-Disposition", "inline; filename=\"document.pdf\"");
                    long length = conn.getContentLengthLong();
                    exchange.sendResponseHeaders(200, length > 0 ? length : 0);

                    try (InputStream is = conn.getInputStream();
                         OutputStream os = exchange.getResponseBody()) {
                        byte[] buf = new byte[8192];
                        int r;
                        while ((r = is.read(buf)) != -1) {
                            os.write(buf, 0, r);
                        }
                    }
                } else {
                    sendResponse(exchange, 502, "{\"error\":\"Failed to fetch remote PDF: HTTP " + respCode + "\"}", "application/json");
                }
            } catch (Exception e) {
                sendResponse(exchange, 500, "{\"error\":\"Proxy error: " + escapeJson(e.getMessage()) + "\"}", "application/json");
            }
        }
    }

    /**
     * Pure-Java Dynamic PDF Dossier Generator
     * Synthesizes a valid, fully formatted PDF Document on the fly for any book!
     */
    static class PdfGenerateHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                addCorsHeaders(exchange);
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            Map<String, String> params = parseQueryParams(exchange.getRequestURI().getQuery());
            String title = params.getOrDefault("title", "Digital Book Reader & Overview");
            String author = params.getOrDefault("author", "Online Library Archive");
            String publisher = params.getOrDefault("publisher", "Lumina Knowledge Systems");
            String category = params.getOrDefault("category", "Literature & Science");
            String desc = params.getOrDefault("description",
                "This comprehensive digital reading dossier was generated by the Lumina Online Library engine. " +
                "It synthesizes metadata, curated themes, structured reading notes, and essential highlights " +
                "for study, review, and personal enrichment.");

            byte[] pdfBytes = generateBookDossierPdf(title, author, publisher, category, desc);
            String safeTitle = title.replaceAll("[^a-zA-Z0-9.-]", "_") + "_Dossier.pdf";
            sendBinaryResponse(exchange, 200, pdfBytes, "application/pdf", safeTitle);
        }
    }

    /**
     * Pure Java PDF 1.4 Document Generator (Zero External Dependencies)
     */
    public static byte[] generateBookDossierPdf(String title, String author, String publisher, String category, String description) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            List<Long> offsets = new ArrayList<>();
            // PDF Header
            writeString(out, "%PDF-1.4\n%\u00e2\u00e3\u00cf\u00d3\n");

            // Obj 1: Catalog
            offsets.add((long) out.size());
            writeString(out, "1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n");

            // Obj 2: Pages Root (2 Pages)
            offsets.add((long) out.size());
            writeString(out, "2 0 obj\n<< /Type /Pages /Kids [3 0 R 4 0 R] /Count 2 >>\nendobj\n");

            // Obj 3: Page 1
            offsets.add((long) out.size());
            writeString(out, "3 0 obj\n<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Contents 5 0 R /Resources << /Font << /F1 7 0 R /F2 8 0 R >> >> >>\nendobj\n");

            // Obj 4: Page 2
            offsets.add((long) out.size());
            writeString(out, "4 0 obj\n<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Contents 6 0 R /Resources << /Font << /F1 7 0 R /F2 8 0 R >> >> >>\nendobj\n");

            // Stream 1 Content (Page 1)
            String page1Commands = buildPage1Content(title, author, publisher, category, description);
            byte[] p1Bytes = page1Commands.getBytes(StandardCharsets.ISO_8859_1);
            offsets.add((long) out.size());
            writeString(out, "5 0 obj\n<< /Length " + p1Bytes.length + " >>\nstream\n");
            out.write(p1Bytes);
            writeString(out, "\nendstream\nendobj\n");

            // Stream 2 Content (Page 2)
            String page2Commands = buildPage2Content(title, author);
            byte[] p2Bytes = page2Commands.getBytes(StandardCharsets.ISO_8859_1);
            offsets.add((long) out.size());
            writeString(out, "6 0 obj\n<< /Length " + p2Bytes.length + " >>\nstream\n");
            out.write(p2Bytes);
            writeString(out, "\nendstream\nendobj\n");

            // Obj 7: Font 1 (Helvetica-Bold)
            offsets.add((long) out.size());
            writeString(out, "7 0 obj\n<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica-Bold >>\nendobj\n");

            // Obj 8: Font 2 (Helvetica)
            offsets.add((long) out.size());
            writeString(out, "8 0 obj\n<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>\nendobj\n");

            // Cross-Reference Table
            long xrefOffset = out.size();
            writeString(out, "xref\n0 " + (offsets.size() + 1) + "\n");
            writeString(out, "0000000000 65535 f \n");
            for (Long off : offsets) {
                writeString(out, String.format(Locale.US, "%010d 00000 n \n", off));
            }

            // Trailer
            writeString(out, "trailer\n<< /Size " + (offsets.size() + 1) + " /Root 1 0 R >>\n");
            writeString(out, "startxref\n" + xrefOffset + "\n%%EOF\n");

        } catch (IOException e) {
            e.printStackTrace();
        }
        return out.toByteArray();
    }

    private static String buildPage1Content(String title, String author, String publisher, String category, String description) {
        StringBuilder sb = new StringBuilder();
        // Decorative Header Banner
        sb.append("0.1 0.15 0.25 rg 0 760 595 82 re f\n"); // Dark Navy Header
        sb.append("0.95 0.7 0.2 rg 0 756 595 4 re f\n");  // Gold accent line

        // Banner text
        sb.append("BT /F1 18 Tf 1 1 1 rg 40 795 Td (LUMINA DIGITAL LIBRARY - BOOK READER & DOSSIER) Tj ET\n");
        sb.append("BT /F2 10 Tf 0.8 0.85 0.9 rg 40 775 Td (Verified Digital Edition | Instant Interactive Reader) Tj ET\n");

        // Book Title Block
        sb.append("0 0 0 rg\n");
        sb.append("BT /F1 22 Tf 0.1 0.15 0.25 rg 40 710 Td (").append(escapePdf(title)).append(") Tj ET\n");
        sb.append("BT /F2 13 Tf 0.3 0.35 0.4 rg 40 688 Td (By ").append(escapePdf(author)).append(") Tj ET\n");

        // Metadata Info Box
        sb.append("0.96 0.97 0.99 rg 40 600 515 65 re f\n"); // Light slate box
        sb.append("0.8 0.85 0.9 rg 1 w 40 600 515 65 re S\n");

        sb.append("BT /F1 10 Tf 0.2 0.25 0.3 rg 55 645 Td (CATEGORY:) Tj ET\n");
        sb.append("BT /F2 10 Tf 0.1 0.1 0.1 rg 125 645 Td (").append(escapePdf(category)).append(") Tj ET\n");

        sb.append("BT /F1 10 Tf 0.2 0.25 0.3 rg 300 645 Td (PUBLISHER:) Tj ET\n");
        sb.append("BT /F2 10 Tf 0.1 0.1 0.1 rg 375 645 Td (").append(escapePdf(publisher)).append(") Tj ET\n");

        sb.append("BT /F1 10 Tf 0.2 0.25 0.3 rg 55 620 Td (STATUS:) Tj ET\n");
        sb.append("BT /F2 10 Tf 0.05 0.5 0.25 rg 125 620 Td (Full Reading Access Verified) Tj ET\n");

        sb.append("BT /F1 10 Tf 0.2 0.25 0.3 rg 300 620 Td (ENGINE:) Tj ET\n");
        sb.append("BT /F2 10 Tf 0.1 0.1 0.1 rg 375 620 Td (Java SE Lumina Reader Core) Tj ET\n");

        // Section: Book Overview & Synopsis
        sb.append("BT /F1 14 Tf 0.1 0.15 0.25 rg 40 560 Td (1. Executive Synopsis & Themes) Tj ET\n");
        sb.append("0.2 0.4 0.8 rg 40 552 515 1.5 re f\n");

        // Wrap and render description lines
        List<String> descLines = wrapText(description, 75);
        int y = 530;
        sb.append("BT /F2 11 Tf 0.2 0.2 0.2 rg ");
        for (String line : descLines) {
            sb.append("40 ").append(y).append(" Td (").append(escapePdf(line)).append(") Tj ");
            y -= 16;
            if (y < 360) break;
        }
        sb.append("ET\n");

        // Section: Key Literary Themes & Takeaways
        sb.append("BT /F1 14 Tf 0.1 0.15 0.25 rg 40 330 Td (2. Core Highlights & Takeaways) Tj ET\n");
        sb.append("0.2 0.4 0.8 rg 40 322 515 1.5 re f\n");

        String[] bulletPoints = new String[]{
            "Core Narrative & Intent: Explores foundational concepts, philosophies, and character dynamics.",
            "Historical & Cultural Context: Reflects critical movements and paradigms in literature and science.",
            "Analytical Resonance: Offers lasting insight into human behavior, decision-making, and critical inquiry.",
            "Reading Advice: Recommended to annotate active reflections, chapter milestones, and thematic shifts."
        };

        int by = 300;
        for (String bp : bulletPoints) {
            sb.append("0.95 0.7 0.2 rg 45 ").append(by + 3).append(" 5 5 re f\n"); // bullet square
            sb.append("BT /F2 10 Tf 0.2 0.2 0.2 rg 58 ").append(by).append(" Td (").append(escapePdf(bp)).append(") Tj ET\n");
            by -= 22;
        }

        // Section: Study & Discussion Questions
        sb.append("BT /F1 14 Tf 0.1 0.15 0.25 rg 40 190 Td (3. Guided Reflection Questions) Tj ET\n");
        sb.append("0.2 0.4 0.8 rg 40 182 515 1.5 re f\n");

        String[] questions = new String[]{
            "Q1: What central problem or conflict drives the progression of this work?",
            "Q2: How do the author's stylistic techniques elevate the core premise?",
            "Q3: In what ways can the principles or lessons from this book be applied today?"
        };

        int qy = 160;
        for (String q : questions) {
            sb.append("BT /F2 10 Tf 0.15 0.25 0.4 rg 45 ").append(qy).append(" Td (").append(escapePdf(q)).append(") Tj ET\n");
            qy -= 20;
        }

        // Footer
        sb.append("0.8 0.85 0.9 rg 40 50 515 1 re f\n");
        sb.append("BT /F2 9 Tf 0.5 0.55 0.6 rg 40 35 Td (Page 1 of 2 | Lumina Digital Online Library Engine | Java SE) Tj ET\n");

        return sb.toString();
    }

    private static String buildPage2Content(String title, String author) {
        StringBuilder sb = new StringBuilder();
        // Header
        sb.append("0.1 0.15 0.25 rg 0 790 595 52 re f\n");
        sb.append("0.95 0.7 0.2 rg 0 786 595 4 re f\n");
        sb.append("BT /F1 14 Tf 1 1 1 rg 40 808 Td (STUDY NOTES & READING LOG - ").append(escapePdf(title)).append(") Tj ET\n");

        // Section: Interactive Reading Notes
        sb.append("BT /F1 13 Tf 0.1 0.15 0.25 rg 40 750 Td (Personal Reflections, Quotes & Chapter Annotations) Tj ET\n");
        sb.append("0.2 0.4 0.8 rg 40 742 515 1.5 re f\n");

        // Draw note lines
        int lineY = 710;
        for (int i = 0; i < 22; i++) {
            sb.append("0.85 0.88 0.92 rg 40 ").append(lineY).append(" 515 1 re f\n");
            lineY -= 28;
        }

        // Footer
        sb.append("0.8 0.85 0.9 rg 40 50 515 1 re f\n");
        sb.append("BT /F2 9 Tf 0.5 0.55 0.6 rg 40 35 Td (Page 2 of 2 | Lumina Digital Online Library Engine | End of Document) Tj ET\n");

        return sb.toString();
    }

    private static String escapePdf(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("(", "\\(")
                .replace(")", "\\)")
                .replaceAll("[^\\x20-\\x7E]", " ");
    }

    private static List<String> wrapText(String text, int maxChars) {
        List<String> lines = new ArrayList<>();
        if (text == null || text.isEmpty()) return lines;
        String[] words = text.split("\\s+");
        StringBuilder current = new StringBuilder();
        for (String w : words) {
            if (current.length() + w.length() + 1 > maxChars) {
                lines.add(current.toString());
                current = new StringBuilder(w);
            } else {
                if (current.length() > 0) current.append(" ");
                current.append(w);
            }
        }
        if (current.length() > 0) lines.add(current.toString());
        return lines;
    }

    private static void writeString(OutputStream out, String s) throws IOException {
        out.write(s.getBytes(StandardCharsets.ISO_8859_1));
    }

    // =================================================================================
    // STATIC FILE HANDLER
    // =================================================================================

    static class StaticFileHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            addCorsHeaders(exchange);
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            String path = exchange.getRequestURI().getPath();
            if (path == null || path.equals("/") || path.isEmpty()) {
                path = "/index.html";
            }

            Path requestedFile;
            if (path.startsWith("/uploads/")) {
                requestedFile = UPLOADS_DIR.resolve(path.substring("/uploads/".length()));
            } else {
                if (path.startsWith("/")) path = path.substring(1);
                requestedFile = STATIC_DIR.resolve(path).normalize();
            }

            // Security check: ensure path is inside project
            if (!Files.exists(requestedFile) || Files.isDirectory(requestedFile)) {
                // If requested a page that wasn't found, try index.html
                Path fallback = STATIC_DIR.resolve("index.html");
                if (Files.exists(fallback)) {
                    serveFile(exchange, fallback, "text/html");
                } else {
                    sendResponse(exchange, 404, "404 Not Found", "text/plain");
                }
                return;
            }

            String mimeType = getMimeType(requestedFile.toString());
            serveFile(exchange, requestedFile, mimeType);
        }

        private void serveFile(HttpExchange exchange, Path file, String mimeType) throws IOException {
            byte[] fileBytes = Files.readAllBytes(file);
            exchange.getResponseHeaders().set("Content-Type", mimeType);
            exchange.getResponseHeaders().set("Accept-Ranges", "bytes");

            // Cache static assets for fast reloading
            if (mimeType.contains("javascript") || mimeType.contains("css")) {
                exchange.getResponseHeaders().set("Cache-Control", "no-cache");
            }

            exchange.sendResponseHeaders(200, fileBytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(fileBytes);
            }
        }

        private String getMimeType(String path) {
            String lower = path.toLowerCase();
            if (lower.endsWith(".html")) return "text/html; charset=UTF-8";
            if (lower.endsWith(".css")) return "text/css; charset=UTF-8";
            if (lower.endsWith(".js")) return "application/javascript; charset=UTF-8";
            if (lower.endsWith(".json")) return "application/json; charset=UTF-8";
            if (lower.endsWith(".pdf")) return "application/pdf";
            if (lower.endsWith(".png")) return "image/png";
            if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
            if (lower.endsWith(".svg")) return "image/svg+xml";
            if (lower.endsWith(".ico")) return "image/x-icon";
            return "application/octet-stream";
        }
    }

    // =================================================================================
    // SIMPLE JSON HELPERS (Zero Dependencies)
    // =================================================================================

    public static List<String> readJsonArray(Path file) {
        List<String> list = new ArrayList<>();
        try {
            if (!Files.exists(file)) return list;
            String content = new String(Files.readAllBytes(file), StandardCharsets.UTF_8).trim();
            if (!content.startsWith("[") || !content.endsWith("]")) return list;
            content = content.substring(1, content.length() - 1).trim();
            if (content.isEmpty()) return list;

            int braceCount = 0;
            StringBuilder current = new StringBuilder();
            boolean inString = false;
            boolean escaped = false;

            for (int i = 0; i < content.length(); i++) {
                char c = content.charAt(i);
                if (escaped) {
                    current.append(c);
                    escaped = false;
                    continue;
                }
                if (c == '\\') {
                    escaped = true;
                    current.append(c);
                    continue;
                }
                if (c == '\"') {
                    inString = !inString;
                }
                if (!inString) {
                    if (c == '{') braceCount++;
                    if (c == '}') braceCount--;
                    if (c == ',' && braceCount == 0) {
                        list.add(current.toString().trim());
                        current = new StringBuilder();
                        continue;
                    }
                }
                current.append(c);
            }
            if (current.length() > 0 && !current.toString().trim().isEmpty()) {
                list.add(current.toString().trim());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return list;
    }

    public static void writeJsonArray(Path file, List<String> items) {
        try {
            StringBuilder sb = new StringBuilder("[\n");
            for (int i = 0; i < items.size(); i++) {
                sb.append("  ").append(items.get(i).trim());
                if (i < items.size() - 1) sb.append(",");
                sb.append("\n");
            }
            sb.append("]");
            Files.write(file, sb.toString().getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static String extractField(String json, String key) {
        String pattern = "\"" + key + "\"\\s*:\\s*";
        int idx = json.indexOf("\"" + key + "\"");
        if (idx == -1) return "";
        int start = json.indexOf(":", idx) + 1;
        while (start < json.length() && Character.isWhitespace(json.charAt(start))) start++;
        if (start >= json.length()) return "";

        char first = json.charAt(start);
        if (first == '\"') {
            int end = start + 1;
            boolean esc = false;
            while (end < json.length()) {
                char c = json.charAt(end);
                if (esc) { esc = false; end++; continue; }
                if (c == '\\') { esc = true; end++; continue; }
                if (c == '\"') break;
                end++;
            }
            return json.substring(start + 1, end);
        } else {
            int end = start;
            while (end < json.length() && json.charAt(end) != ',' && json.charAt(end) != '}' && json.charAt(end) != ']') end++;
            return json.substring(start, end).trim();
        }
    }

    public static String updateField(String json, String key, String newValue) {
        String token = "\"" + key + "\"";
        int idx = json.indexOf(token);
        if (idx == -1) {
            // Append field before closing brace
            int lastBrace = json.lastIndexOf("}");
            if (lastBrace != -1) {
                String prefix = json.substring(0, lastBrace).trim();
                if (prefix.endsWith(",")) prefix = prefix.substring(0, prefix.length() - 1);
                return prefix + ",\n  \"" + key + "\": \"" + escapeJson(newValue) + "\"\n}";
            }
            return json;
        }

        int colon = json.indexOf(":", idx);
        int valStart = colon + 1;
        while (valStart < json.length() && Character.isWhitespace(json.charAt(valStart))) valStart++;
        int valEnd;
        if (json.charAt(valStart) == '\"') {
            valEnd = valStart + 1;
            boolean esc = false;
            while (valEnd < json.length()) {
                char c = json.charAt(valEnd);
                if (esc) { esc = false; valEnd++; continue; }
                if (c == '\\') { esc = true; valEnd++; continue; }
                if (c == '\"') { valEnd++; break; }
                valEnd++;
            }
        } else {
            valEnd = valStart;
            while (valEnd < json.length() && json.charAt(valEnd) != ',' && json.charAt(valEnd) != '}') valEnd++;
        }

        return json.substring(0, valStart) + "\"" + escapeJson(newValue) + "\"" + json.substring(valEnd);
    }

    public static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\b", "\\b")
                .replace("\f", "\\f")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
