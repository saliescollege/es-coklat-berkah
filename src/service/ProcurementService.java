package service;

import config.DatabaseConfig;
import dao.BahanBakuDAO;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public class ProcurementService {
    private final BahanBakuDAO bahanBakuDAO = new BahanBakuDAO();

    public void catatPembelianBahan(int idBahan, double jumlahBeli, double totalBiaya, String namaSupplier) {
        String sql = "INSERT INTO pembelian (id_bahan, jumlah, total_biaya, supplier, tanggal) VALUES (?, ?, ?, ?, NOW())";

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            conn.setAutoCommit(false);

            // Simpan catatan pembelian.
            pstmt.setInt(1, idBahan);
            pstmt.setDouble(2, jumlahBeli);
            pstmt.setDouble(3, totalBiaya);
            pstmt.setString(4, namaSupplier);
            pstmt.executeUpdate();

            // Perbarui stok bahan baku.
            bahanBakuDAO.updateStok(idBahan, jumlahBeli);

            conn.commit();
            System.out.println("  Pembelian bahan berhasil dicatat dan stok gudang telah bertambah!");

        } catch (SQLException e) {
            System.err.println("  Pengadaan Gagal: " + e.getMessage());
        }
    }
}