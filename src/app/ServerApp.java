package app;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;
import config.DatabaseConfig;

import java.io.ByteArrayOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ServerApp {

    public static void main(String[] args) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(8081), 0);

        // Daftarkan handler untuk halaman dan API.
        server.createContext("/", new StaticFileHandler());
        server.createContext("/api/login", new LoginHandler());
        server.createContext("/api/user/profile", new ProfileHandler());
        server.createContext("/api/pos", new PosHandler());
        server.createContext("/api/stok", new StokHandler());
        server.createContext("/api/procurement/photo", new ProcurementPhotoHandler());
        server.createContext("/api/procurement", new ProcurementHandler());
        server.createContext("/api/kas", new KasHandler());

        server.setExecutor(null);
        System.out.println("🚀 Server ERP Web Es Coklat Berkah berjalan di: http://localhost:8081");
        server.start();
    }

    // Layani berkas statis untuk antarmuka web.
    static class StaticFileHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getPath();
            if (path.equals("/")) path = "/index.html";

            File file = new File("view" + path);
            if (!file.exists()) file = new File("src/view" + path);

            if (file.exists()) {
                byte[] bytes = Files.readAllBytes(file.toPath());
                exchange.getResponseHeaders().set("Content-Type", getMimeType(path));
                exchange.sendResponseHeaders(200, bytes.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(bytes);
                }
            } else {
                byte[] response = "404 Not Found".getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(404, response.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(response);
                }
            }
        }

        private String getMimeType(String path) {
            if (path.endsWith(".html")) return "text/html";
            if (path.endsWith(".css")) return "text/css";
            if (path.endsWith(".js")) return "application/javascript";
            if (path.endsWith(".svg")) return "image/svg+xml";
            if (path.endsWith(".ico")) return "image/x-icon";
            if (path.endsWith(".png")) return "image/png";
            return "text/plain";
        }
    }

    // Tangani autentikasi pengguna.
    static class LoginHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                String body = readBody(exchange);
                String[] parts = body.split(",");
                String user = parts.length > 0 ? parts[0].trim() : "";
                String pass = parts.length > 1 ? parts[1].trim() : "";

                try (Connection conn = DatabaseConfig.getConnection();
                     PreparedStatement pstmt = conn.prepareStatement("SELECT * FROM users WHERE username = ? AND password = ?")) {
                    pstmt.setString(1, user);
                    pstmt.setString(2, pass);
                    ResultSet rs = pstmt.executeQuery();

                    if (rs.next()) {
                        String json = String.format("{\"success\":true,\"nama\":\"%s\",\"username\":\"%s\"}", 
                            rs.getString("nama_lengkap"), rs.getString("username"));
                        sendJsonResponse(exchange, 200, json);
                    } else {
                        sendJsonResponse(exchange, 401, "{\"success\":false,\"message\":\"Username atau Password salah!\"}");
                    }
                } catch (Exception e) {
                    sendJsonResponse(exchange, 500, "{\"success\":false,\"message\":\"" + e.getMessage() + "\"}");
                }
            }
        }
    }

    // Tangani perubahan profil pengguna.
    static class ProfileHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("PUT".equalsIgnoreCase(exchange.getRequestMethod())) {
                String body = readBody(exchange);
                String[] parts = body.split(",");
                String usernameLama = parts[0].trim();
                String passLama = parts[1].trim();
                String namaBaru = parts[2].trim();
                String passBaru = parts[3].trim();

                try (Connection conn = DatabaseConfig.getConnection()) {
                    // Verifikasi kata sandi saat ini sebelum menyimpan perubahan.
                    PreparedStatement checkStmt = conn.prepareStatement("SELECT id FROM users WHERE username = ? AND password = ?");
                    checkStmt.setString(1, usernameLama);
                    checkStmt.setString(2, passLama);
                    ResultSet rs = checkStmt.executeQuery();

                    if (rs.next()) {
                        PreparedStatement updateStmt = conn.prepareStatement(
                            "UPDATE users SET nama_lengkap = ?, password = ? WHERE username = ?");
                        updateStmt.setString(1, namaBaru);
                        updateStmt.setString(2, passBaru.isEmpty() ? passLama : passBaru);
                        updateStmt.setString(3, usernameLama);
                        updateStmt.executeUpdate();

                        sendJsonResponse(exchange, 200, "{\"success\":true,\"message\":\"Profil berhasil diperbarui!\"}");
                    } else {
                        sendJsonResponse(exchange, 400, "{\"success\":false,\"message\":\"Password lama tidak sesuai!\"}");
                    }
                } catch (Exception e) {
                    sendJsonResponse(exchange, 500, "{\"success\":false,\"message\":\"" + e.getMessage() + "\"}");
                }
            }
        }
    }

    // Tangani operasi data stok gudang.
    static class StokHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String method = exchange.getRequestMethod();

            try (Connection conn = DatabaseConfig.getConnection()) {
                if ("GET".equalsIgnoreCase(method)) {
                    Statement stmt = conn.createStatement();
                    ResultSet rs = stmt.executeQuery("SELECT * FROM bahan_baku ORDER BY id ASC");
                    StringBuilder sb = new StringBuilder("[");
                    boolean first = true;
                    while (rs.next()) {
                        if (!first) sb.append(",");
                        sb.append(String.format("{\"id\":%d,\"nama\":\"%s\",\"stok\":%.1f,\"satuan\":\"%s\",\"minStok\":%.1f}",
                                rs.getInt("id"), rs.getString("nama_bahan"), rs.getDouble("stok"),
                                rs.getString("satuan"), rs.getDouble("min_stok")));
                        first = false;
                    }
                    sb.append("]");
                    sendJsonResponse(exchange, 200, sb.toString());
                } else if ("POST".equalsIgnoreCase(method)) {
                    String body = readBody(exchange);
                    String[] p = body.split(",");
                    String nama = p[0].trim();
                    double stok = Double.parseDouble(p[1].trim());
                    String satuan = p[2].trim();
                    double min = Double.parseDouble(p[3].trim());

                    // Tolak nama bahan baku yang sudah terdaftar.
                    PreparedStatement checkStmt = conn.prepareStatement("SELECT id FROM bahan_baku WHERE LOWER(nama_bahan) = LOWER(?)");
                    checkStmt.setString(1, nama);
                    if (checkStmt.executeQuery().next()) {
                        sendJsonResponse(exchange, 400, "{\"error\":\"Nama bahan baku sudah ada dalam sistem!\"}");
                        return;
                    }

                    // Gunakan ID terkecil yang belum dipakai.
                    int targetId = 1;
                    Statement idStmt = conn.createStatement();
                    ResultSet rsId = idStmt.executeQuery("SELECT id FROM bahan_baku ORDER BY id ASC");
                    while (rsId.next()) {
                        if (rsId.getInt("id") == targetId) {
                            targetId++;
                        } else if (rsId.getInt("id") > targetId) {
                            break;
                        }
                    }

                    PreparedStatement insertStmt = conn.prepareStatement(
                        "INSERT INTO bahan_baku (id, nama_bahan, stok, satuan, min_stok) VALUES (?, ?, ?, ?, ?)");
                    insertStmt.setInt(1, targetId);
                    insertStmt.setString(2, nama);
                    insertStmt.setDouble(3, stok);
                    insertStmt.setString(4, satuan);
                    insertStmt.setDouble(5, min);
                    insertStmt.executeUpdate();

                    sendJsonResponse(exchange, 200, "{\"success\":true}");
                } else if ("PUT".equalsIgnoreCase(method)) {
                    String body = readBody(exchange);
                    String[] p = body.split(",");
                    int id = Integer.parseInt(p[0].trim());
                    String nama = p[1].trim();
                    double stok = Double.parseDouble(p[2].trim());
                    String satuan = p[3].trim();
                    double min = Double.parseDouble(p[4].trim());

                    PreparedStatement updateStmt = conn.prepareStatement(
                        "UPDATE bahan_baku SET nama_bahan = ?, stok = ?, satuan = ?, min_stok = ? WHERE id = ?");
                    updateStmt.setString(1, nama);
                    updateStmt.setDouble(2, stok);
                    updateStmt.setString(3, satuan);
                    updateStmt.setDouble(4, min);
                    updateStmt.setInt(5, id);
                    updateStmt.executeUpdate();

                    sendJsonResponse(exchange, 200, "{\"success\":true}");
                } else if ("DELETE".equalsIgnoreCase(method)) {
                    String query = exchange.getRequestURI().getQuery();
                    int id = Integer.parseInt(query.split("=")[1]);

                    PreparedStatement delStmt = conn.prepareStatement("DELETE FROM bahan_baku WHERE id = ?");
                    delStmt.setInt(1, id);
                    try {
                        if (delStmt.executeUpdate() == 0) {
                            sendJsonResponse(exchange, 404, "{\"error\":\"Bahan baku tidak ditemukan.\"}");
                            return;
                        }
                    } catch (SQLIntegrityConstraintViolationException e) {
                        sendJsonResponse(exchange, 409, "{\"error\":\"Bahan baku masih digunakan dalam riwayat pembelian dan tidak dapat dihapus.\"}");
                        return;
                    }

                    sendJsonResponse(exchange, 200, "{\"success\":true}");
                }
            } catch (Exception e) {
                sendJsonResponse(exchange, 500, "{\"error\":\"" + e.getMessage() + "\"}");
            }
        }
    }

    // Tangani operasi transaksi penjualan.
    static class PosHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String method = exchange.getRequestMethod();
            try (Connection conn = DatabaseConfig.getConnection()) {
                if ("GET".equalsIgnoreCase(method)) {
                    Statement stmt = conn.createStatement();
                    ResultSet rs = stmt.executeQuery("SELECT * FROM transaksi ORDER BY id DESC");
                    StringBuilder sb = new StringBuilder("[");
                    boolean first = true;
                    while (rs.next()) {
                        if (!first) sb.append(",");
                        sb.append(String.format("{\"id\":%d,\"channel\":\"%s\",\"pembeli\":\"%s\",\"gelas\":%d,\"total\":%d,\"metode\":\"%s\",\"tanggal\":\"%s\"}",
                                rs.getInt("id"), rs.getString("channel"),
                                rs.getString("nama_pembeli") == null ? "" : rs.getString("nama_pembeli"),
                                rs.getInt("jumlah_gelas"), rs.getInt("total_harga"),
                                rs.getString("metode_pembayaran"), rs.getString("tanggal")));
                        first = false;
                    }
                    sb.append("]");
                    sendJsonResponse(exchange, 200, sb.toString());
                } else if ("POST".equalsIgnoreCase(method)) {
                    String body = readBody(exchange);
                    String[] p = body.split(",");
                    String ch = p[0].trim();
                    String pem = p[1].trim();
                    int gel = Integer.parseInt(p[2].trim());
                    int tot = Integer.parseInt(p[3].trim()) * 1000;
                    String met = p[4].trim();

                    PreparedStatement pstmt = conn.prepareStatement(
                        "INSERT INTO transaksi (channel, nama_pembeli, jumlah_gelas, total_harga, metode_pembayaran, tanggal) VALUES (?, ?, ?, ?, ?, NOW())");
                    pstmt.setString(1, ch);
                    pstmt.setString(2, pem.isEmpty() ? null : pem);
                    pstmt.setInt(3, gel);
                    pstmt.setInt(4, tot);
                    pstmt.setString(5, met);
                    pstmt.executeUpdate();

                    sendJsonResponse(exchange, 200, "{\"success\":true}");
                } else if ("PUT".equalsIgnoreCase(method)) {
                    String body = readBody(exchange);
                    String[] p = body.split(",");
                    int id = Integer.parseInt(p[0].trim());
                    String ch = p[1].trim();
                    String pem = p[2].trim();
                    int gel = Integer.parseInt(p[3].trim());
                    int tot = Integer.parseInt(p[4].trim());
                    String met = p[5].trim();

                    PreparedStatement pstmt = conn.prepareStatement(
                        "UPDATE transaksi SET channel = ?, nama_pembeli = ?, jumlah_gelas = ?, total_harga = ?, metode_pembayaran = ? WHERE id = ?");
                    pstmt.setString(1, ch);
                    pstmt.setString(2, pem.isEmpty() ? null : pem);
                    pstmt.setInt(3, gel);
                    pstmt.setInt(4, tot);
                    pstmt.setString(5, met);
                    pstmt.setInt(6, id);
                    pstmt.executeUpdate();

                    sendJsonResponse(exchange, 200, "{\"success\":true}");
                } else if ("DELETE".equalsIgnoreCase(method)) {
                    String query = exchange.getRequestURI().getQuery();
                    int id = Integer.parseInt(query.split("=")[1]);

                    PreparedStatement pstmt = conn.prepareStatement("DELETE FROM transaksi WHERE id = ?");
                    pstmt.setInt(1, id);
                    if (pstmt.executeUpdate() == 0) {
                        sendJsonResponse(exchange, 404, "{\"error\":\"Transaksi tidak ditemukan.\"}");
                        return;
                    }

                    sendJsonResponse(exchange, 200, "{\"success\":true}");
                }
            } catch (Exception e) {
                sendJsonResponse(exchange, 500, "{\"error\":\"" + e.getMessage() + "\"}");
            }
        }
    }

    // Tangani operasi pembelian bahan baku.
    static class ProcurementHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String method = exchange.getRequestMethod();
            try (Connection conn = DatabaseConfig.getConnection()) {
                if ("GET".equalsIgnoreCase(method)) {
                    ensureProcurementPhotoTable(conn);
                    Statement stmt = conn.createStatement();
                    ResultSet rs = stmt.executeQuery("SELECT p.*, b.nama_bahan, EXISTS (SELECT 1 FROM pembelian_foto pf WHERE pf.id_pembelian = p.id) AS has_foto FROM pembelian p JOIN bahan_baku b ON p.id_bahan = b.id ORDER BY p.id DESC");
                    StringBuilder sb = new StringBuilder("[");
                    boolean first = true;
                    while (rs.next()) {
                        if (!first) sb.append(",");
                        sb.append(String.format("{\"id\":%d,\"idBahan\":%d,\"id_bahan\":%d,\"namaBahan\":\"%s\",\"jumlah\":%.1f,\"total\":%d,\"supplier\":\"%s\",\"tanggal\":\"%s\",\"hasFoto\":%b}",
                            rs.getInt("id"), rs.getInt("id_bahan"), rs.getInt("id_bahan"), rs.getString("nama_bahan"),
                                rs.getDouble("jumlah"), rs.getInt("total_biaya"),
                                rs.getString("supplier"), rs.getString("tanggal"), rs.getBoolean("has_foto")));
                        first = false;
                    }
                    sb.append("]");
                    sendJsonResponse(exchange, 200, sb.toString());
                } else if ("POST".equalsIgnoreCase(method)) {
                    String body = readBody(exchange);
                    String[] p = body.split(",");
                    if (p.length < 5) {
                        throw new IllegalArgumentException("Format pembelian tidak valid.");
                    }

                    int idBahan = Integer.parseInt(p[0].trim());
                    double jumlah = Double.parseDouble(p[1].trim());
                    int total = Integer.parseInt(p[2].trim()) * 1000;
                    String supplier = p[3].trim();
                    boolean tambahKeStok = Boolean.parseBoolean(p[4].trim());

                    conn.setAutoCommit(false);
                    int idPembelian;
                    try {
                        try (PreparedStatement pstmt = conn.prepareStatement(
                                "INSERT INTO pembelian (id_bahan, jumlah, total_biaya, supplier, tanggal) VALUES (?, ?, ?, ?, NOW())",
                                Statement.RETURN_GENERATED_KEYS)) {
                            pstmt.setInt(1, idBahan);
                            pstmt.setDouble(2, jumlah);
                            pstmt.setInt(3, total);
                            pstmt.setString(4, supplier);
                            pstmt.executeUpdate();
                            try (ResultSet generatedKeys = pstmt.getGeneratedKeys()) {
                                if (!generatedKeys.next()) {
                                    throw new SQLException("ID pembelian tidak berhasil dibuat.");
                                }
                                idPembelian = generatedKeys.getInt(1);
                            }
                        }

                        if (tambahKeStok) {
                            try (PreparedStatement updateStok = conn.prepareStatement(
                                    "UPDATE bahan_baku SET stok = stok + ? WHERE id = ?")) {
                                updateStok.setDouble(1, jumlah);
                                updateStok.setInt(2, idBahan);
                                if (updateStok.executeUpdate() == 0) {
                                    throw new SQLException("Bahan baku tidak ditemukan; pembelian tidak disimpan.");
                                }
                            }
                        } else {
                            try (PreparedStatement checkBahan = conn.prepareStatement(
                                    "SELECT id FROM bahan_baku WHERE id = ?")) {
                                checkBahan.setInt(1, idBahan);
                                try (ResultSet rsBahan = checkBahan.executeQuery()) {
                                    if (!rsBahan.next()) {
                                        throw new SQLException("Bahan baku tidak ditemukan; pembelian tidak disimpan.");
                                    }
                                }
                            }
                        }

                        conn.commit();
                    } catch (SQLException e) {
                        conn.rollback();
                        throw e;
                    }

                    sendJsonResponse(exchange, 200, "{\"success\":true,\"id\":" + idPembelian + "}");
                } else if ("PUT".equalsIgnoreCase(method)) {
                    String body = readBody(exchange);
                    String[] p = body.split(",");
                    int id = Integer.parseInt(p[0].trim());
                    int idBahan = Integer.parseInt(p[1].trim());
                    double jumlah = Double.parseDouble(p[2].trim());
                    int total = Integer.parseInt(p[3].trim());
                    String supplier = p[4].trim();

                    PreparedStatement pstmt = conn.prepareStatement(
                        "UPDATE pembelian SET id_bahan = ?, jumlah = ?, total_biaya = ?, supplier = ? WHERE id = ?");
                    pstmt.setInt(1, idBahan);
                    pstmt.setDouble(2, jumlah);
                    pstmt.setInt(3, total);
                    pstmt.setString(4, supplier);
                    pstmt.setInt(5, id);
                    pstmt.executeUpdate();

                    sendJsonResponse(exchange, 200, "{\"success\":true}");
                } else if ("DELETE".equalsIgnoreCase(method)) {
                    String query = exchange.getRequestURI().getQuery();
                    int id = Integer.parseInt(query.split("=")[1]);

                    ensureProcurementPhotoTable(conn);
                    conn.setAutoCommit(false);
                    try {
                        try (PreparedStatement deletePhoto = conn.prepareStatement(
                                "DELETE FROM pembelian_foto WHERE id_pembelian = ?")) {
                            deletePhoto.setInt(1, id);
                            deletePhoto.executeUpdate();
                        }

                        try (PreparedStatement pstmt = conn.prepareStatement(
                                "DELETE FROM pembelian WHERE id = ?")) {
                            pstmt.setInt(1, id);
                            if (pstmt.executeUpdate() == 0) {
                                conn.rollback();
                                sendJsonResponse(exchange, 404, "{\"error\":\"Pembelian tidak ditemukan.\"}");
                                return;
                            }
                        }
                        conn.commit();
                    } catch (SQLException e) {
                        conn.rollback();
                        throw e;
                    }

                    sendJsonResponse(exchange, 200, "{\"success\":true}");
                }
            } catch (Exception e) {
                sendJsonResponse(exchange, 500, "{\"error\":\"" + e.getMessage() + "\"}");
            }
        }
    }

    // Tangani penyimpanan dan pengambilan foto pembelian.
    static class ProcurementPhotoHandler implements HttpHandler {
        private static final int MAX_PHOTO_BYTES = 5 * 1024 * 1024;

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"/api/procurement/photo".equals(exchange.getRequestURI().getPath())) {
                sendJsonResponse(exchange, 404, "{\"error\":\"Rute foto tidak ditemukan.\"}");
                return;
            }

            int idPembelian;
            try {
                String query = exchange.getRequestURI().getQuery();
                if (query == null || !query.matches("id=\\d+")) {
                    sendJsonResponse(exchange, 400, "{\"error\":\"ID pembelian tidak valid.\"}");
                    return;
                }
                idPembelian = Integer.parseInt(query.substring(3));
            } catch (NumberFormatException e) {
                sendJsonResponse(exchange, 400, "{\"error\":\"ID pembelian tidak valid.\"}");
                return;
            }

            String method = exchange.getRequestMethod();
            try (Connection conn = DatabaseConfig.getConnection()) {
                ensureProcurementPhotoTable(conn);
                try (PreparedStatement purchase = conn.prepareStatement(
                        "SELECT id FROM pembelian WHERE id = ?")) {
                    purchase.setInt(1, idPembelian);
                    try (ResultSet rs = purchase.executeQuery()) {
                        if (!rs.next()) {
                            sendJsonResponse(exchange, 404, "{\"error\":\"Pembelian tidak ditemukan.\"}");
                            return;
                        }
                    }
                }

                if ("GET".equalsIgnoreCase(method)) {
                    try (PreparedStatement pstmt = conn.prepareStatement(
                            "SELECT content_type, image_data FROM pembelian_foto WHERE id_pembelian = ?")) {
                        pstmt.setInt(1, idPembelian);
                        try (ResultSet rs = pstmt.executeQuery()) {
                            if (!rs.next()) {
                                sendJsonResponse(exchange, 404, "{\"error\":\"Foto pembelian belum tersedia.\"}");
                                return;
                            }

                            byte[] image = rs.getBytes("image_data");
                            exchange.getResponseHeaders().set("Content-Type", rs.getString("content_type"));
                            exchange.getResponseHeaders().set("Cache-Control", "no-store");
                            exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
                            exchange.sendResponseHeaders(200, image.length);
                            try (OutputStream output = exchange.getResponseBody()) {
                                output.write(image);
                            }
                        }
                    }
                } else if ("POST".equalsIgnoreCase(method)) {
                    String contentType = exchange.getRequestHeaders().getFirst("Content-Type");
                    if (!isSupportedImageType(contentType)) {
                        sendJsonResponse(exchange, 415, "{\"error\":\"Gunakan foto JPEG, PNG, atau WebP.\"}");
                        return;
                    }

                    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                    byte[] chunk = new byte[8192];
                    int totalBytes = 0;
                    int bytesRead;
                    try (InputStream input = exchange.getRequestBody()) {
                        while ((bytesRead = input.read(chunk)) != -1) {
                            totalBytes += bytesRead;
                            if (totalBytes > MAX_PHOTO_BYTES) {
                                sendJsonResponse(exchange, 413, "{\"error\":\"Ukuran foto maksimal 5 MB.\"}");
                                return;
                            }
                            buffer.write(chunk, 0, bytesRead);
                        }
                    }

                    if (totalBytes == 0) {
                        sendJsonResponse(exchange, 400, "{\"error\":\"File foto kosong.\"}");
                        return;
                    }

                    try (PreparedStatement pstmt = conn.prepareStatement(
                            "INSERT INTO pembelian_foto (id_pembelian, content_type, image_data) VALUES (?, ?, ?) "
                                    + "ON DUPLICATE KEY UPDATE content_type = VALUES(content_type), image_data = VALUES(image_data)")) {
                        pstmt.setInt(1, idPembelian);
                        pstmt.setString(2, contentType);
                        pstmt.setBytes(3, buffer.toByteArray());
                        pstmt.executeUpdate();
                    }
                    sendJsonResponse(exchange, 200, "{\"success\":true}");
                } else if ("DELETE".equalsIgnoreCase(method)) {
                    try (PreparedStatement pstmt = conn.prepareStatement(
                            "DELETE FROM pembelian_foto WHERE id_pembelian = ?")) {
                        pstmt.setInt(1, idPembelian);
                        if (pstmt.executeUpdate() == 0) {
                            sendJsonResponse(exchange, 404, "{\"error\":\"Foto pembelian belum tersedia.\"}");
                            return;
                        }
                    }
                    sendJsonResponse(exchange, 200, "{\"success\":true}");
                } else {
                    sendJsonResponse(exchange, 405, "{\"error\":\"Metode tidak didukung.\"}");
                }
            } catch (Exception e) {
                sendJsonResponse(exchange, 500, "{\"error\":\"" + e.getMessage() + "\"}");
            }
        }

        private boolean isSupportedImageType(String contentType) {
            return "image/jpeg".equalsIgnoreCase(contentType)
                    || "image/png".equalsIgnoreCase(contentType)
                    || "image/webp".equalsIgnoreCase(contentType);
        }
    }

    private static void ensureProcurementPhotoTable(Connection conn) throws SQLException {
        try (Statement statement = conn.createStatement()) {
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS pembelian_foto ("
                    + "id_pembelian INT NOT NULL PRIMARY KEY, "
                    + "content_type VARCHAR(32) NOT NULL, "
                    + "image_data MEDIUMBLOB NOT NULL, "
                    + "updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP"
                    + ")");
        }
    }

    // Hitung ringkasan arus kas.
    static class KasHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            Map<String, String> filters = parseQueryParameters(exchange.getRequestURI().getRawQuery());
            String date = filters.get("tanggal");
            String month = filters.get("bulan");
            String year = filters.get("tahun");
            String where = "";
            List<String> values = new ArrayList<>();

            if (date != null && !date.isBlank()) {
                try {
                    LocalDate.parse(date);
                } catch (DateTimeParseException e) {
                    sendJsonResponse(exchange, 400, "{\"error\":\"Format tanggal tidak valid.\"}");
                    return;
                }
                where = " WHERE DATE(tanggal) = ?";
                values.add(date);
            } else if (month != null && !month.isBlank()) {
                if (!month.matches("0[1-9]|1[0-2]")) {
                    sendJsonResponse(exchange, 400, "{\"error\":\"Bulan tidak valid.\"}");
                    return;
                }
                where = " WHERE MONTH(tanggal) = ?";
                values.add(month);
                if (year != null && !year.isBlank()) {
                    if (!year.matches("\\d{4}")) {
                        sendJsonResponse(exchange, 400, "{\"error\":\"Tahun tidak valid.\"}");
                        return;
                    }
                    where += " AND YEAR(tanggal) = ?";
                    values.add(year);
                }
            } else if (year != null && !year.isBlank()) {
                if (!year.matches("\\d{4}")) {
                    sendJsonResponse(exchange, 400, "{\"error\":\"Tahun tidak valid.\"}");
                    return;
                }
                where = " WHERE YEAR(tanggal) = ?";
                values.add(year);
            }

            try (Connection conn = DatabaseConfig.getConnection()) {
                int mas;
                try (PreparedStatement statement = conn.prepareStatement(
                        "SELECT COALESCE(SUM(total_harga), 0) AS mas FROM transaksi" + where)) {
                    setFilterValues(statement, values);
                    try (ResultSet result = statement.executeQuery()) {
                        mas = result.next() ? result.getInt("mas") : 0;
                    }
                }

                int kel;
                try (PreparedStatement statement = conn.prepareStatement(
                        "SELECT COALESCE(SUM(total_biaya), 0) AS kel FROM pembelian" + where)) {
                    setFilterValues(statement, values);
                    try (ResultSet result = statement.executeQuery()) {
                        kel = result.next() ? result.getInt("kel") : 0;
                    }
                }

                String years = getAvailableYears(conn);
                String json = String.format(
                        "{\"totalPemasukan\":%d,\"totalPengeluaran\":%d,\"saldoNeto\":%d,\"tahunTersedia\":%s}",
                        mas, kel, mas - kel, years);
                sendJsonResponse(exchange, 200, json);
            } catch (Exception e) {
                sendJsonResponse(exchange, 500, "{\"error\":\"" + e.getMessage() + "\"}");
            }
        }

        private Map<String, String> parseQueryParameters(String query) {
            Map<String, String> parameters = new HashMap<>();
            if (query == null || query.isBlank()) return parameters;

            for (String item : query.split("&")) {
                String[] pair = item.split("=", 2);
                String key = URLDecoder.decode(pair[0], StandardCharsets.UTF_8);
                String value = pair.length > 1 ? URLDecoder.decode(pair[1], StandardCharsets.UTF_8) : "";
                parameters.put(key, value);
            }
            return parameters;
        }

        private void setFilterValues(PreparedStatement statement, List<String> values) throws SQLException {
            for (int index = 0; index < values.size(); index++) {
                statement.setString(index + 1, values.get(index));
            }
        }

        private String getAvailableYears(Connection conn) throws SQLException {
            StringBuilder years = new StringBuilder("[");
            boolean first = true;
            String sql = "SELECT tahun FROM ("
                    + "SELECT YEAR(tanggal) AS tahun FROM transaksi UNION "
                    + "SELECT YEAR(tanggal) AS tahun FROM pembelian"
                    + ") AS tahun_data WHERE tahun IS NOT NULL ORDER BY tahun DESC";
            try (Statement statement = conn.createStatement();
                 ResultSet result = statement.executeQuery(sql)) {
                while (result.next()) {
                    if (!first) years.append(',');
                    years.append(result.getInt("tahun"));
                    first = false;
                }
            }
            return years.append(']').toString();
        }
    }

    private static String readBody(HttpExchange exchange) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(exchange.getRequestBody(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
        }
        return sb.toString();
    }

    private static void sendJsonResponse(HttpExchange exchange, int statusCode, String response) throws IOException {
        byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }
}