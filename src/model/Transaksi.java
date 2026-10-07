package model;

import java.util.Date;

public class Transaksi {
    private int id;
    private String channel;
    private double totalHarga;
    private String metodePembayaran;
    private Date tanggal;

    public Transaksi(int id, String channel, double totalHarga, String metodePembayaran, Date tanggal) {
        this.id = id;
        this.channel = channel;
        this.totalHarga = totalHarga;
        this.metodePembayaran = metodePembayaran;
        this.tanggal = tanggal;
    }

    public int getId() {
        return id;
    }

    public String getChannel() {
        return channel;
    }

    public double getTotalHarga() {
        return totalHarga;
    }

    public String getMetodePembayaran() {
        return metodePembayaran;
    }

    public Date getTanggal() {
        return tanggal;
    }
}