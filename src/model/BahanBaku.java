package model;

public class BahanBaku {
    private int id;
    private String namaBahan;
    private double stok;
    private String satuan;
    private double minStok;

    public BahanBaku(int id, String namaBahan, double stok, String satuan, double minStok) {
        this.id = id;
        this.namaBahan = namaBahan;
        this.stok = stok;
        this.satuan = satuan;
        this.minStok = minStok;
    }

    public int getId() {
        return id;
    }

    public String getNamaBahan() {
        return namaBahan;
    }

    public void setNamaBahan(String namaBahan) {
        this.namaBahan = namaBahan;
    }

    public double getStok() {
        return stok;
    }

    public void setStok(double stok) {
        this.stok = stok;
    }

    public String getSatuan() {
        return satuan;
    }

    public void setSatuan(String satuan) {
        this.satuan = satuan;
    }

    public double getMinStok() {
        return minStok;
    }

    public void setMinStok(double minStok) {
        this.minStok = minStok;
    }
}