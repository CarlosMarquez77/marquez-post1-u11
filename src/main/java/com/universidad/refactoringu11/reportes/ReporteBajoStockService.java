package com.universidad.refactoringu11.reportes;

import com.universidad.refactoringu11.inventario.InventarioService;
import com.universidad.refactoringu11.inventario.Producto;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class ReporteBajoStockService {

    private final InventarioService inventarioService;

    public ReporteBajoStockService(InventarioService inventarioService) {
        this.inventarioService = inventarioService;
    }

    public String generarReporte() {
        List<Producto> bajoStock = inventarioService.productosBajoStockMinimo();
        if (bajoStock.isEmpty()) {
            return "Reporte de bajo stock: sin productos por debajo del minimo";
        }
        return bajoStock.stream()
                .map(this::describir)
                .collect(Collectors.joining("\n", "Reporte de bajo stock:\n", ""));
    }

    private String describir(Producto producto) {
        return "- " + producto.getNombre() + ": stock " + producto.getStock()
                + " (minimo " + producto.getStockMinimo() + ")";
    }
}
