# Refactoring U11 — Refactorización guiada por pruebas

Post-contenido de la Unidad 11 de Patrones de Diseño de Software.
Un único proyecto Spring Boot (`refactoring-u11`) con un servicio de
inventario que nace con code smells reales. Primero se protege con
pruebas de caracterización y después se refactoriza, comprobando con
las mismas pruebas que el comportamiento no cambia.

## Cómo ejecutar
```
mvn test
```

## Parte 1 — Red de seguridad y refactorización

### Code smells encontrados (código original, primer commit)

| Smell | Dónde | Evidencia medida |
|-------|-------|------------------|
| Long Method (Bloater) | `InventarioService.actualizarInventario()` | 44 líneas (19 a 62), complejidad ciclomática 13, 6 responsabilidades en un solo bloque |
| Switch Statements duplicado (OO Abuser) | `ClasificadorProducto.calcularRecargoEnvio()` y `EtiquetadoProducto.generarEtiqueta()` | El mismo `switch (producto.getTipo())` con las mismas 4 ramas en 2 clases; complejidad 5 en cada una |

**Long Method.** Las seis responsabilidades mezcladas eran: validar la
entrada, buscar el producto, actualizar el stock, calcular la cantidad
de reorden, registrar el movimiento y decidir la alerta. El método
tenía 4 `return` distintos repartidos entre esas responsabilidades y
no tenía ninguna prueba.

**Switch duplicado.** De las 8 líneas de cada bloque `switch`, 4 son
idénticas carácter por carácter (la cabecera, las dos del `default` y
el cierre) y las otras 4 tienen las mismas etiquetas `case` y solo
cambia lo que retornan. El detector de copiado de PMD (CPD, con 25
tokens mínimos) no lo reporta, porque no es código copiado sino la
misma estructura repetida: es un smell que una herramienta de
duplicación textual no ve. Además, la rama `default` no se puede
alcanzar con un enum, así que cada switch tenía una rama que ninguna
prueba podía cubrir (4 de 5). Agregar un tipo de producto obligaba a
recordar modificar las dos clases (Shotgun Surgery).

La complejidad y la cobertura se midieron con JaCoCo sin modificar el
`pom.xml`:

```
mvn org.jacoco:jacoco-maven-plugin:0.8.14:prepare-agent test org.jacoco:jacoco-maven-plugin:0.8.14:report
```

### Pruebas de caracterización (antes de tocar el código)
Se escribieron 20 pruebas y se ejecutaron en verde sobre el código
original antes de modificarlo (commit `84d9f0c`). Instancian las
clases con `new`, sin contexto de Spring.

| Clase de prueba | Pruebas | Qué fija |
|-----------------|---------|----------|
| `InventarioServiceTest` | 12 | Los cinco códigos de retorno (`OK_n`, `ERROR_DATOS_INVALIDOS`, `ERROR_PRODUCTO_NO_EXISTE`, `ERROR_STOCK_INSUFICIENTE`, `OK_BAJO_STOCK_REORDEN_n`) y los casos límite |
| `ClasificadorProductoTest` | 4 | Las cuatro ramas del switch de recargo |
| `EtiquetadoProductoTest` | 4 | Las cuatro ramas del switch de etiqueta |

Además de las pruebas del enunciado se agregaron casos límite, porque
son los que un refactor rompe sin que se note:

- stock que queda exactamente en el mínimo (no hay reorden) y stock
  que queda en cero (hay reorden, no error);
- cantidad cero y `productoId` nulo;
- las ramas ROPA y ALIMENTO de `generarEtiqueta`, que faltaban para
  cubrir las cuatro;
- dos comportamientos del código original que parecen defectos y que
  una prueba de caracterización debe fijar igual: una ENTRADA con
  cantidad negativa reduce el stock sin validar insuficiencia, y un
  tipo de movimiento nulo lanza `NullPointerException`.

Con estas pruebas, JaCoCo reportó 24 de 24 ramas de
`actualizarInventario()` cubiertas antes de refactorizar.

### Refactorización 1 — Long Method
**Técnica elegida: Extract Method** (Fowler), aplicada una
responsabilidad a la vez y ejecutando `mvn test` después de cada
extracción. Las variables temporales `necesitaReorden` y
`cantidadReorden` se reemplazaron por métodos de consulta (Replace
Temp with Query).

| | Antes | Después |
|---|-------|---------|
| Líneas de `actualizarInventario()` | 44 | 8 |
| Complejidad ciclomática de `actualizarInventario()` | 13 | 2 |
| Complejidad máxima de un método de la clase | 13 | 5 (`esMovimientoValido`, una sola expresión booleana) |
| Responsabilidades en el método | 6 | 1 (coordinar) |

```java
public String actualizarInventario(Long productoId, int cantidad, String tipoMovimiento) {
    if (!esMovimientoValido(productoId, cantidad, tipoMovimiento)) {
        return "ERROR_DATOS_INVALIDOS";
    }
    return buscarProducto(productoId)
            .map(producto -> aplicarMovimiento(producto, cantidad, tipoMovimiento))
            .orElse("ERROR_PRODUCTO_NO_EXISTE");
}
```

Cada método extraído tiene una responsabilidad: `esMovimientoValido`,
`buscarProducto`, `aplicarMovimiento`, `calcularNuevoStock`,
`registrarMovimiento`, `resultadoSegunStockMinimo`, `necesitaReorden`
y `calcularCantidadReorden`.

Dos cosas se dejaron igual a propósito, porque cambiarlas ya no sería
refactorizar sino modificar el comportamiento que las pruebas fijan:

- Los códigos de retorno como texto. La guía recomienda excepciones
  en lugar de códigos, pero eso cambia lo que observa quien llama.
- El control de stock insuficiente solo para SALIDA y la comparación
  `tipoMovimiento.equals(...)`, que conserva el
  `NullPointerException` del original.

**Decisión de diseño 1 — El cálculo de reorden queda como método
privado, no como clase `CalculadoraReorden`.**

- *Justificación.* Hoy la regla es una sola fórmula
  (`stockMinimo * 3 - stock`), la usa un único punto del servicio y
  no tiene una razón de cambio distinta a la del resto de la clase:
  ningún requisito pide políticas de reorden por tipo de producto o
  por proveedor. Una clase para una línea sería una Lazy Class, y
  crearla "por si acaso" sería Speculative Generality.
- *Qué sí se hizo.* El 3 quedó como la constante `FACTOR_REORDEN` y
  la regla quedó aislada en `necesitaReorden()` y
  `calcularCantidadReorden()`, de modo que tiene nombre y un solo
  lugar donde cambiar.
- *Qué haría reconsiderarla.* Que aparezca una segunda política de
  reorden. En ese momento el método privado ya extraído se mueve a su
  propia clase con Extract Class sin tocar `actualizarInventario()`.

### Refactorización 2 — Switch Statements duplicado
**Técnica elegida: Replace Conditional with Polymorphism**, usando
métodos abstractos en el enum `TipoProducto` con una implementación
por constante.

| | Antes | Después |
|---|-------|---------|
| `switch` sobre `TipoProducto` en el proyecto | 2 | 0 |
| Complejidad de `calcularRecargoEnvio` / `generarEtiqueta` | 5 / 5 | 1 / 1 |
| Clases a modificar para agregar un tipo | 2 (y recordar las dos) | Ninguna de las dos: basta la constante nueva en `TipoProducto` |
| Ramas imposibles de cubrir (`default`) | 2 | 0 |

`calcularRecargoEnvio(Producto)` y `generarEtiqueta(Producto)`
conservan su firma; ahora solo delegan en el tipo:

```java
public double calcularRecargoEnvio(Producto producto) {
    return producto.getTipo().calcularRecargoEnvio(producto.getPrecio());
}
```

**Decisión de diseño 2 — Enum con métodos abstractos, no interfaz de
estrategia con una clase por tipo.**

- *Justificación.* El conjunto de tipos es cerrado y estable, y
  `TipoProducto` ya era el dato sobre el que se preguntaba. Con
  métodos abstractos en el enum, el compilador obliga a que cada
  constante nueva implemente los dos comportamientos: olvidar uno deja
  de ser un error en ejecución y pasa a ser un error de compilación,
  que era justo el riesgo del switch duplicado.
- *Por qué se descartó la interfaz de estrategia.* Habría significado
  8 clases nuevas (4 tipos por 2 comportamientos) o 4 clases con dos
  métodos, más un mapa de `TipoProducto` a estrategia que puede quedar
  incompleto y fallar en ejecución. Ese costo se justifica cuando las
  estrategias necesitan colaboradores inyectados por Spring o cuando
  los tipos los agrega otro módulo sin recompilar el enum; ninguna de
  las dos cosas pasa hoy.
- *Costo asumido.* El enum ahora conoce la regla de recargo y el
  formato de la etiqueta, dos razones de cambio en un mismo archivo.
  Con dos comportamientos es aceptable; si llegara un tercero o si
  alguno necesitara un servicio externo, se migraría a estrategias.
- `ClasificadorProducto` y `EtiquetadoProducto` quedan como clases de
  una línea que delegan. Se conservan porque el enunciado exige
  mantener sus firmas y las pruebas las usan.

### Resultado de `mvn test` en cada momento

| Momento | Commit | Resultado |
|---------|--------|-----------|
| Código original con pruebas de caracterización | `84d9f0c` | `Tests run: 20, Failures: 0, Errors: 0, Skipped: 0` |
| Después de refactorizar el Long Method | `460e808` | `Tests run: 20, Failures: 0, Errors: 0, Skipped: 0` |
| Después de eliminar el switch duplicado | `2a57f26` | `Tests run: 20, Failures: 0, Errors: 0, Skipped: 0` |

Las pruebas no se modificaron después de escribirse. Se comprueba con:

```
git diff 84d9f0c HEAD --stat -- src/test/java/com/universidad/refactoringu11/inventario
```

que no muestra ningún cambio.

## Parte 2 — Acoplamiento entre paquetes

### Diagnóstico: ciclo entre `inventario` y `reportes`
Al agregar el módulo de reportes, `InventarioService` empezó a
importar `ReporteBajoStockService` para avisarle del bajo stock, y
`ReporteBajoStockService` ya importaba `InventarioService` para
consultar los productos:

```
            importa ReporteBajoStockService
   ┌─────────────────────────────────────────────┐
   │                                             ▼
┌──────────────┐                          ┌──────────────┐
│  inventario  │                          │   reportes   │
└──────────────┘                          └──────────────┘
   ▲                                             │
   └─────────────────────────────────────────────┘
      importa InventarioService y Producto
```

El proyecto compila y las 20 pruebas de la Parte 1 siguen en verde,
así que nada avisa del problema. Lo detecta la prueba de arquitectura
`ArquitecturaPaquetesTest`, que en este punto falla:

```
$ mvn test -Dtest=ArquitecturaPaquetesTest
[ERROR] Tests run: 1, Failures: 1, Errors: 0, Skipped: 0
Architecture Violation [Priority: MEDIUM] - Rule 'slices matching
'com.universidad.refactoringu11.(*)..' should be free of cycles' was violated (1 times):
Cycle detected: Slice inventario ->
                Slice reportes ->
                Slice inventario
  1. Dependencies of Slice inventario
    - Field <...inventario.InventarioService.reporteBajoStockService> has type
      <...reportes.ReporteBajoStockService>
    - Method <...InventarioService.resultadoSegunStockMinimo(Producto)> calls method
      <...reportes.ReporteBajoStockService.generarReporte()>
  2. Dependencies of Slice reportes
    - Constructor <...reportes.ReporteBajoStockService.<init>(InventarioService)> has
      parameter of type <...inventario.InventarioService>
    - Method <...ReporteBajoStockService.generarReporte()> calls method
      <...inventario.InventarioService.productosBajoStockMinimo()>
```

Hay una segunda evidencia que las pruebas tampoco muestran, porque
ninguna levanta el contexto de Spring: con este ciclo la aplicación no
arranca.

```
$ mvn spring-boot:run
APPLICATION FAILED TO START
The dependencies of some of the beans in the application context form a cycle:
┌─────┐
|  inventarioService
↑     ↓
|  reporteBajoStockService
└─────┘
```

### Solución: invertir la dependencia
**Técnica: Extract Interface + inversión de dependencias (DIP).** Se
extrajo la interfaz `NotificadorBajoStock` dentro del paquete
`inventario`. `InventarioService` dejó de importar
`ReporteBajoStockService` y solo conoce esa interfaz;
`ReporteBajoStockService` la implementa desde `reportes`.

```
┌───────────────────────────────┐        ┌──────────────────────────┐
│          inventario           │        │         reportes         │
│                               │        │                          │
│  InventarioService ──usa──┐   │        │                          │
│                           ▼   │        │                          │
│   «interface»                 │◀───────│  ReporteBajoStockService │
│   NotificadorBajoStock        │implementa  (usa InventarioService │
│                               │        │   y Producto)            │
└───────────────────────────────┘        └──────────────────────────┘
        una sola dirección: reportes ──▶ inventario
```

```
$ mvn test -Dtest=ArquitecturaPaquetesTest
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS

$ grep -rn "refactoringu11.reportes" src/main/java/com/universidad/refactoringu11/inventario
(sin resultados)
```

| Paquete | Dependencias antes | Inestabilidad antes | Dependencias después | Inestabilidad después |
|---------|--------------------|---------------------|----------------------|-----------------------|
| `inventario` | entra 1, sale 1 | 0,5 | entra 1, sale 0 | 0 (estable) |
| `reportes` | entra 1, sale 1 | 0,5 | entra 0, sale 1 | 1 (inestable) |

Después del cambio la dependencia va del paquete inestable al
estable, y el estable es el que contiene la abstracción.

**Decisión de diseño 3 — `NotificadorBajoStock` vive en `inventario`,
no en `reportes`.**

- *Cuál es el paquete de mayor nivel.* `inventario` es el dominio:
  tiene las reglas de stock, de mínimo y de reorden, y funciona sin
  que exista ningún reporte. `reportes` es un consumidor: solo toma
  datos del inventario y les da formato de texto. Por la regla de la
  guía (sección 4.3), "los paquetes de infraestructura dependen de
  dominio; dominio nunca depende de infraestructura", la flecha debe
  ir de `reportes` hacia `inventario`.
- *Por qué la ubicación decide la dirección.* Lo que invierte la
  dependencia no es crear la interfaz, sino quién es su dueño. Con la
  interfaz en `inventario`, el servicio la usa sin salir de su
  paquete y es `reportes` quien importa para implementarla.
- *Qué pasaría en `reportes`.* `InventarioService` tendría que
  importar `reportes.NotificadorBajoStock`, y como la interfaz
  necesita `Producto`, `reportes` seguiría importando `inventario`:
  el ciclo quedaría igual, solo que pasando por una interfaz.
- La interfaz expresa lo que el inventario necesita decir ("este
  producto quedó bajo el mínimo"), no lo que el reporte sabe hacer.
  Por eso mañana puede implementarla un envío de correo sin tocar
  `inventario`.

**Decisión de diseño 4 — `notificar(Producto, int cantidadReorden)`
por evento, no `notificar(List<Producto>)` por lote.**

- *Ventaja en este diseño.* El bajo stock se detecta dentro de
  `actualizarInventario()`, que procesa un movimiento de un producto.
  En ese momento ya se tienen el producto y la cantidad de reorden
  calculada, así que avisar ahí no exige guardar nada ni decidir
  cuándo se cierra un lote. El inventario no acumula estado para
  otros y quien recibe el aviso decide qué hacer: en este caso
  `ReporteBajoStockService` lo guarda y lo muestra en el siguiente
  `generarReporte()`.
- *Qué se perdería con el lote.* `InventarioService` necesitaría una
  lista de pendientes y una regla de cuándo enviarla, que es una
  responsabilidad nueva dentro del servicio que se acaba de limpiar.
  Además, una lista de `Producto` no lleva la cantidad de reorden de
  cada uno; habría que crear otro tipo para transportarla.
- *Cuándo preferiría el lote.* En una carga masiva, por ejemplo un
  inventario físico que actualiza cientos de productos, o si cada
  aviso tuviera un costo externo (un correo o una llamada a otra
  API): ahí conviene un solo aviso consolidado al final del proceso.

### Un segundo ciclo que la prueba de arquitectura no ve
Después de invertir la dependencia, `ArquitecturaPaquetesTest` pasó,
pero `mvn spring-boot:run` siguió fallando con el mismo mensaje. El
ciclo de paquetes (lo que el compilador ve) estaba resuelto, pero
quedaba un ciclo de objetos en ejecución: Spring necesita un
`NotificadorBajoStock` para terminar de armar `InventarioService`, y
el único que existe es `ReporteBajoStockService`, que pide un
`InventarioService` en su constructor.

Se resolvió con `@Lazy` en el parámetro del constructor de
`ReporteBajoStockService`: Spring le entrega un proxy y busca el
servicio real en la primera llamada. Se puso en `reportes` y no en
`inventario` para que el dominio no cargue con un detalle del
contenedor. `IntegracionInventarioReportesTest` (`@SpringBootTest`)
comprueba que el contexto arranca y que una salida bajo el mínimo
termina en el reporte:

```
Reporte de bajo stock:
- Teclado: stock 3 (minimo 5), reordenar 12
```

ArchUnit analiza clases compiladas, así que no puede ver ese segundo
ciclo. Para cubrirlo hace falta una prueba que levante el contexto.

### Las pruebas de la Parte 1 siguen en verde sin modificarse

```
$ mvn test
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0 -- in com.universidad.refactoringu11.ArquitecturaPaquetesTest
[INFO] Tests run: 12, Failures: 0, Errors: 0, Skipped: 0 -- in com.universidad.refactoringu11.inventario.InventarioServiceTest
[INFO] Tests run: 4, Failures: 0, Errors: 0, Skipped: 0 -- in com.universidad.refactoringu11.inventario.ClasificadorProductoTest
[INFO] Tests run: 4, Failures: 0, Errors: 0, Skipped: 0 -- in com.universidad.refactoringu11.inventario.EtiquetadoProductoTest
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0 -- in com.universidad.refactoringu11.IntegracionInventarioReportesTest
[INFO] Tests run: 22, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS

$ git diff 84d9f0c HEAD --stat -- src/test/java/com/universidad/refactoringu11/inventario
(sin cambios)
```

Las 20 pruebas de caracterización son las mismas que se escribieron
antes del primer refactor. La reestructuración de paquetes no cambió
ningún resultado.

## Herramientas utilizadas
- Java 17, Spring Boot 3.3, Apache Maven
- JUnit 5, ArchUnit 1.3.0, JaCoCo y PMD CPD (medición)
- Git, GitHub

## Conclusiones
Lo que más me sirvió de este laboratorio fue escribir las pruebas
antes de tocar el código: al preparar los casos límite aparecieron
dos comportamientos que yo habría "arreglado" sin darme cuenta
durante el refactor, y eso ya no habría sido refactorizar. También vi
que las herramientas ven cosas distintas: el detector de duplicados
no encontró el switch repetido, las pruebas de negocio no notaron el
ciclo entre paquetes, y la prueba de arquitectura no notó el ciclo
que impedía arrancar la aplicación. Cada problema necesitó su propia
evidencia. Sobre las técnicas, Extract Method y el polimorfismo en el
enum resolvieron los smells sin agregar clases que hoy no hacen
falta, y la inversión de dependencias dejó claro que lo importante no
es crear una interfaz sino en qué paquete queda.
