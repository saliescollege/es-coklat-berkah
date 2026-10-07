package model;

import java.util.Date;

public class Pembelian {
    private int id;
    private int idBahan;
    private String namaBahan;
    private double jumlah;
    private int totalBiaya;
    private String supplier;
    private Date tanggal;

    public Pembelian(int id, int idBahan, String namaBahan, double jumlah, int totalBiaya, String supplier, Date tanggal) {
        this.id = id;
        this.idBahan = idBahan;
        this.namaBahan = namaBahan;
        this.jumlah = jumlah;
        this.totalBiaya = totalBiaya;
        this.supplier = supplier;
        this.tanggal = tanggal;
    }

    public int getId() {
        return id;
    }

    public int getIdBahan() {
        return idBahan;
    }

    public String getNamaBahan() {
        return namaBahan;
    }

    public double getJumlah() {
        return jumlah;
    }

    public int getTotalBiaya() {
        return totalBiaya;
    }

    public String getSupplier() {
        return supplier;
    }

    public Date getTanggal() {
        return tanggal;
    }
}