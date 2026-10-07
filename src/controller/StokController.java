package controller;

import service.StokService;
import model.BahanBaku;
import java.util.List;

public class StokController {
    private final StokService service = new StokService();

    public List<BahanBaku> fetchStok(String limit) throws Exception {
        return service.getStok(limit);
    }
}