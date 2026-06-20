# Conjunto de Mandelbrot - Explorador Java

Aplicación Java con interfaz gráfica (Swing) para visualizar e interactuar con el conjunto de Mandelbrot.

## Requisitos

- Java 17 o superior (probado con Java 21)

## Compilación y ejecución

### Forma rápida (recomendada)

- **Doble clic** en `run.bat` (compila y ejecuta automáticamente)
- O en PowerShell: `.\run.ps1`
- O en Cursor: abrir `Main.java` y pulsar **F5** (configuración en `.vscode/launch.json`)

### Manual

```powershell
javac -d out src/com/mandelbrot/*.java
java -cp out com.mandelbrot.Main
```

## Controles

| Acción | Control |
|--------|---------|
| Zoom in/out | Rueda del ratón |
| Desplazamiento (pan) | Clic izquierdo + arrastrar |
| Centrar y acercar 50% | Doble clic en el gráfico |

## Estructura del proyecto

```
src/com/mandelbrot/
├── Main.java                 # Punto de entrada
├── MandelbrotPanel.java      # Panel gráfico con interacción
├── MandelbrotCalculator.java # Algoritmo de iteraciones
├── ViewState.java            # Estado de vista (zoom/pan)
└── ColorPalette.java         # Coloreado del fractal
```

Cada clase incluye documentación Javadoc en español.
