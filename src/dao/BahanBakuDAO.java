package dao;

import config.DatabaseConfig;
import model.BahanBaku;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public class BahanBakuDAO {

    // Ambil seluruh data bahan baku.
    public List<BahanBaku> getAllBahanBaku() throws SQLException {
        return getAllBahanBaku("all");
    }

    // Ambil data bahan baku dengan batas jumlah baris.
    public List<BahanBaku> getAllBahanBaku(String limit) throws SQLException {
        List<BahanBaku> list = new ArrayList<>();
        String limitSql = " LIMIT 50";
        if ("100".equals(limit)) {
            limitSql = " LIMIT 100";
        } else if ("all".equals(limit)) {
            limitSql = "";
        }

        String sql = "SELECT * FROM bahan_baku ORDER BY id DESC" + limitSql;
        try (Connection conn = DatabaseConfig.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                list.add(new BahanBaku(
                    rs.getInt("id"),
                    rs.getString("nama_bahan"),
                    rs.getDouble("stok"),
                    rs.getString("satuan"),
                    rs.getDouble("min_stok")
                ));
            }
        }
        return list;
    }

    // Tambahkan atau kurangi stok berdasarkan perubahan jumlah.
    public void updateStok(int idBahan, double deltaStok) throws SQLException {
        String sql = "UPDATE bahan_baku SET stok = stok + ? WHERE id = ?";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setDouble(1, deltaStok);
            pstmt.setInt(2, idBahan);
            pstmt.executeUpdate();
        }
    }
}