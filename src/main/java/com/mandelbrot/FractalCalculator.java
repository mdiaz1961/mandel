package com.mandelbrot;

/**
 * Contrato para el cálculo de los conjuntos de Mandelbrot y Julia.
 * <p>
 * Las implementaciones pueden usar la CPU ({@link MandelbrotCalculator})
 * o la GPU ({@link OpenCLFractalCalculator}).
 * </p>
 *
 * @author Mandelbrot Explorer
 * @version 1.0
 */
public interface FractalCalculator {

    /** @return número máximo de iteraciones configurado */
    int getMaxIterations();

    /** @return parte real del parámetro {@code c} del conjunto de Julia */
    double getJuliaReal();

    /** @return parte imaginaria del parámetro {@code c} del conjunto de Julia */
    double getJuliaImaginary();

    /**
     * Define el parámetro {@code c} del conjunto de Julia.
     *
     * @param real      parte real de {@code c}
     * @param imaginary parte imaginaria de {@code c}
     */
    void setJuliaConstant(double real, double imaginary);

    /**
     * Calcula las iteraciones de escape para cada píxel de una vista de Mandelbrot.
     *
     * @param width  ancho de la imagen en píxeles
     * @param height alto de la imagen en píxeles
     * @param view   estado de la vista (centro y escala)
     * @return matriz {@code [height][width]} con el número de iteraciones por píxel
     */
    int[][] computeMandelbrot(int width, int height, ViewState view);

    /**
     * Calcula las iteraciones de escape para cada píxel de una vista de Julia.
     *
     * @param width  ancho de la imagen en píxeles
     * @param height alto de la imagen en píxeles
     * @param view   estado de la vista (centro y escala)
     * @return matriz {@code [height][width]} con el número de iteraciones por píxel
     */
    int[][] computeJulia(int width, int height, ViewState view);

    /**
     * Libera los recursos nativos asociados a esta implementación (por ejemplo, el contexto OpenCL).
     * <p>
     * Por defecto no hace nada; las implementaciones que reserven recursos externos
     * deben sobreescribir este método.
     * </p>
     */
    default void dispose() {}

    /**
     * Crea la implementación seleccionada por la propiedad del sistema {@code calculator}.
     * <p>
     * Valores posibles de la propiedad:
     * <ul>
     *   <li>{@code opencl} → {@link OpenCLFractalCalculator} (GPU; cae a CPU si OpenCL no está disponible)</li>
     *   <li>cualquier otro valor o ausencia → {@link MandelbrotCalculator} (CPU)</li>
     * </ul>
     * Ejemplos de arranque:
     * <pre>
     *   java -Dcalculator=opencl -jar mandelbrot-explorer.jar   # GPU
     *   mvn exec:java -Dcalculator.type=opencl                  # GPU vía Maven
     *   mvn exec:java                                           # CPU (por defecto)
     * </pre>
     *
     * @return instancia lista para calcular fractales
     */
    static FractalCalculator create() {
        if ("opencl".equalsIgnoreCase(System.getProperty("calculator"))) {
            return new OpenCLFractalCalculator();
        }
        return new MandelbrotCalculator();
    }
}
