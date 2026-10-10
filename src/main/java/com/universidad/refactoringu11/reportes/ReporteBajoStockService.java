package com.universidad.refactoringu11.reportes;

import com.universidad.refactoringu11.inventario.InventarioService;
import com.universidad.refactoringu11.inventario.NotificadorBajoStock;
import com.universidad.refactoringu11.inventario.Producto;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ReporteBajoStockService implements NotificadorBajoStock {

    private final InventarioService inventarioService;
    private final Map<Long, Integer> reordenPorProducto = new LinkedHashMap<>();

    public ReporteBajoStockService(@Lazy InventarioService inventarioService) {
        this.inventarioService = inventarioService;
    }

    @Override
    public void notificar(Producto producto, int cantidadReorden) {
        reordenPorProducto.put(producto.getId(), cantidadReorden);
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
        String linea = "- " + producto.getNombre() + ": stock " + producto.getStock()
                + " (minimo " + producto.getStockMinimo() + ")";
        Integer cantidadReorden = reordenPorProducto.get(producto.getId());
        return cantidadReorden == null ? linea : linea + ", reordenar " + cantidadReorden;
    }
}
