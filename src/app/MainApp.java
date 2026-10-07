package app;

import service.KasService;
import service.PosService;
import service.ProcurementService;
import service.StokService;

public class MainApp {
    public static void main(String[] args) {
        System.out.println("=== SISTEM ERP UMKM ES COKLAT BERKAH ===");
        StokService stokService = new StokService();
        PosService posService = new PosService();
        ProcurementService procurementService = new ProcurementService();
        KasService kasService = new KasService();

        stokService.tampilkanStatusStok();
        System.out.println("\n-- Memproses Transaksi Kasir --");
        posService.prosesTransaksiPenjualan("GrabFood", 30000.0, "QRIS", 1, 0.5);

        System.out.println("\n-- Memproses Pembelian Bahan Baku --");
        procurementService.catatPembelianBahan(1, 5.0, 150000.0, "Shopee Supplier Coklat");

        System.out.println();
        stokService.tampilkanStatusStok();

        kasService.cetakLaporanKasHarian(100000.0, 0);
    }
}