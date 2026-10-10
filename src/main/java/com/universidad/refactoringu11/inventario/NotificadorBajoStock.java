package com.universidad.refactoringu11.inventario;

/**
 * Punto de salida del inventario para avisar que un producto quedo por debajo
 * de su stock minimo. La interfaz pertenece a este paquete para que inventario
 * no dependa de quien reacciona al aviso.
 */
public interface NotificadorBajoStock {

    void notificar(Producto producto, int cantidadReorden);
}
