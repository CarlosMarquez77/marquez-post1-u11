package com.universidad.refactoringu11.inventario;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class InventarioServiceTest {

    private InventarioService service;
    private Producto libro;

    @BeforeEach
    void setUp() {
        service = new InventarioService();
        libro = new Producto(1L, "Clean Code", TipoProducto.LIBRO, 45.0, 20, 5);
        service.agregarProducto(libro);
    }

    @Test
    void entrada_incrementaStockYRetornaOk() {
        assertEquals("OK_30", service.actualizarInventario(1L, 10, "ENTRADA"));
        assertEquals(30, libro.getStock());
    }

    @Test
    void salida_conStockSuficiente_descuentaYRetornaOk() {
        assertEquals("OK_12", service.actualizarInventario(1L, 8, "SALIDA"));
    }

    @Test
    void salida_conStockInsuficiente_retornaErrorYNoModificaStock() {
        assertEquals("ERROR_STOCK_INSUFICIENTE", service.actualizarInventario(1L, 50, "SALIDA"));
        assertEquals(20, libro.getStock());
    }

    @Test
    void salida_dejaStockBajoElMinimo_activaReordenConCantidadCalculada() {
        // stockMinimo=5, stock inicial=20, salida de 17 -> nuevoStock=3 (<5)
        // cantidadReorden = stockMinimo*3 - nuevoStock = 15 - 3 = 12
        assertEquals("OK_BAJO_STOCK_REORDEN_12", service.actualizarInventario(1L, 17, "SALIDA"));
        assertEquals(3, libro.getStock());
    }

    @Test
    void salida_dejaStockIgualAlMinimo_noActivaReorden() {
        // nuevoStock=5 no es menor que stockMinimo=5
        assertEquals("OK_5", service.actualizarInventario(1L, 15, "SALIDA"));
    }

    @Test
    void salida_dejaStockEnCero_activaReordenSinError() {
        // nuevoStock=0 no es negativo: no hay error, pero si reorden de 15 - 0
        assertEquals("OK_BAJO_STOCK_REORDEN_15", service.actualizarInventario(1L, 20, "SALIDA"));
        assertEquals(0, libro.getStock());
    }

    @Test
    void tipoMovimientoInvalido_retornaError() {
        assertEquals("ERROR_DATOS_INVALIDOS", service.actualizarInventario(1L, 5, "TRANSFERENCIA"));
    }

    @Test
    void cantidadCero_retornaErrorYNoModificaStock() {
        assertEquals("ERROR_DATOS_INVALIDOS", service.actualizarInventario(1L, 0, "ENTRADA"));
        assertEquals(20, libro.getStock());
    }

    @Test
    void productoIdNulo_retornaError() {
        assertEquals("ERROR_DATOS_INVALIDOS", service.actualizarInventario(null, 5, "ENTRADA"));
    }

    @Test
    void tipoMovimientoNulo_lanzaNullPointerException() {
        // Comportamiento actual: el tipo de movimiento se compara sin proteger contra null
        assertThrows(NullPointerException.class,
                () -> service.actualizarInventario(1L, 5, null));
    }

    @Test
    void productoInexistente_retornaError() {
        assertEquals("ERROR_PRODUCTO_NO_EXISTE", service.actualizarInventario(99L, 5, "ENTRADA"));
    }

    @Test
    void entrada_conCantidadNegativa_reduceStockSinValidarInsuficiencia() {
        // Comportamiento actual, no necesariamente el deseado: solo se rechaza cantidad == 0,
        // y el control de stock insuficiente existe unicamente en la rama SALIDA.
        assertEquals("OK_BAJO_STOCK_REORDEN_25", service.actualizarInventario(1L, -30, "ENTRADA"));
        assertEquals(-10, libro.getStock());
    }
}
