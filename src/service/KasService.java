package service;

import config.DatabaseConfig;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class KasService {

    public void cetakLaporanKasHarian(double saldoAwal, int totalTransaksi) {
        System.out.println("========== LAPORAN KAS HARIAN ==========");
        System.out.println("Saldo Awal     : Rp " + saldoAwal);
        System.out.println("Total Transaksi: " + totalTransaksi);

        String sql = "SELECT COALESCE(SUM(total_harga), 0) AS total_pemasukan FROM transaksi WHERE DATE(tanggal) = CURDATE()";

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            if (rs.next()) {
                double totalPemasukan = rs.getDouble("total_pemasukan");
                double saldoAkhir = saldoAwal + totalPemasukan;

                System.out.println("Total Pemasukan: Rp " + totalPemasukan);
                System.out.println("Saldo Akhir    : Rp " + saldoAkhir);
            }
        } catch (SQLException e) {
            System.err.println("Gagal mencetak laporan kas harian: " + e.getMessage());
            e.printStackTrace();
        }
        System.out.println("========================================");
    }
}