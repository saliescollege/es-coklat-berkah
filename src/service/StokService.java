package service;

import dao.BahanBakuDAO;
import model.BahanBaku;
import java.sql.SQLException;
import java.util.List;

public class StokService {
    private final BahanBakuDAO bahanBakuDAO = new BahanBakuDAO();

    // Ambil data stok dengan batas jumlah baris.
    public List<BahanBaku> getStok(String limit) throws SQLException {
        return bahanBakuDAO.getAllBahanBaku(limit);
    }

    public void tampilkanStatusStok() {
        try {
            List<BahanBaku> listBahan = bahanBakuDAO.getAllBahanBaku("all");
            System.out.println("=== LAPORAN STOK PERSAAN GUDANG (REAL-TIME) ===");
            for (BahanBaku b : listBahan) {
                String warning = (b.getStok() <= b.getMinStok()) ? " [ WARNING: STOK MINIMUM! ]" : "";
                System.out.printf("- %s: %.1f %s%s%n", b.getNamaBahan(), b.getStok(), b.getSatuan(), warning);
            }
        } catch (SQLException e) {
            System.err.println("Gagal mengambil data stok: " + e.getMessage());
        }
    }
}