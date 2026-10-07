package service;

import config.DatabaseConfig;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public class PosService {

    public boolean prosesTransaksiPenjualan(String channel, double totalHarga, String metodePembayaran, int jumlahGelas, double diskon) {
        String sql = "INSERT INTO transaksi (channel, total_harga, metode_pembayaran, jumlah_gelas, diskon, tanggal) VALUES (?, ?, ?, ?, ?, NOW())";

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, channel);
            stmt.setDouble(2, totalHarga);
            stmt.setString(3, metodePembayaran);
            stmt.setInt(4, jumlahGelas);
            stmt.setDouble(5, diskon);

            int rowsInserted = stmt.executeUpdate();
            return rowsInserted > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }
}