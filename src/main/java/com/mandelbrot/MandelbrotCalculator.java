package com.mandelbrot;

/**
 * Calcula las iteraciones de escape para el conjunto de Mandelbrot y de Julia.
 * <p>
 * <b>Mandelbrot:</b> para cada punto {@code c} del plano, itera {@code z_{n+1} = z_n² + c}
 * con {@code z_0 = 0}.
 * </p>
 * <p>
 * <b>Julia:</b> con {@code c} fijo, itera {@code z_{n+1} = z_n² + c} donde {@code z_0}
 * es la coordenada del píxel.
 * </p>
 *
 * @author Mandelbrot Explorer
 * @version 1.0
 */
public class MandelbrotCalculator implements FractalCalculator {

    /** Radio al cual se considera que un punto ha escapado del conjunto. */
    private static final double ESCAPE_RADIUS = 2.0;

    /** Radio de escape al cuadrado, usado para evitar calcular {@code sqrt}. */
    private static final double ESCAPE_RADIUS_SQUARED = ESCAPE_RADIUS * ESCAPE_RADIUS;

    /** Número máximo de iteraciones por defecto. */
    private static final int DEFAULT_MAX_ITERATIONS = 500;

    /** Límite actual de iteraciones para el cálculo. */
    private final int maxIterations;

    /** Parte real del parámetro {@code c} usado en el conjunto de Julia. */
    private volatile double juliaReal;

    /** Parte imaginaria del parámetro {@code c} usado en el conjunto de Julia. */
    private volatile double juliaImaginary;

    /**
     * Crea un calculador con el máximo de iteraciones por defecto.
     */
    public MandelbrotCalculator() {
        this(DEFAULT_MAX_ITERATIONS);
    }

    /**
     * Crea un calculador con un límite personalizado de iteraciones.
     *
     * @param maxIterations número máximo de iteraciones antes de considerar
     *                      que el punto pertenece al conjunto
     */
    public MandelbrotCalculator(int maxIterations) {
        this.maxIterations = maxIterations;
    }

    /**
     * @return el número máximo de iteraciones configurado
     */
    public int getMaxIterations() {
        return maxIterations;
    }

    /**
     * @return parte real del parámetro {@code c} del conjunto de Julia
     */
    public double getJuliaReal() {
        return juliaReal;
    }

    /**
     * @return parte imaginaria del parámetro {@code c} del conjunto de Julia
     */
    public double getJuliaImaginary() {
        return juliaImaginary;
    }

    /**
     * Define el parámetro {@code c} del conjunto de Julia.
     *
     * @param real      parte real de {@code c}
     * @param imaginary parte imaginaria de {@code c}
     */
    public void setJuliaConstant(double real, double imaginary) {
        this.juliaReal = real;
        this.juliaImaginary = imaginary;
    }

    /**
     * Calcula el número de iteraciones para el rectángulo {@code [xIni,xFin) × [yIni,yFin)}
     * en una vista de Mandelbrot.
     *
     * @param xIni   columna inicial (inclusive)
     * @param xFin   columna final   (exclusive)
     * @param yIni   fila inicial    (inclusive)
     * @param yFin   fila final      (exclusive)
     * @param width  anchura total de la imagen
     * @param height altura total de la imagen
     * @param view   estado de vista con centro y escala
     * @return matriz de iteraciones de tamaño {@code [yFin-yIni][xFin-xIni]}
     */
    public int[][] computeMandelbrot(int xIni, int xFin, int yIni, int yFin, int width, int height, ViewState view) {
        int rh = yFin - yIni;
        int rw = xFin - xIni;
        int[][] result = new int[rh][rw];
        for (int y = yIni; y < yFin; y++) {
            double imaginary = view.pixelToImaginary(y, height);
            for (int x = xIni; x < xFin; x++) {
                result[y - yIni][x - xIni] = iterate(0.0, 0.0, view.pixelToReal(x, width), imaginary);
            }
        }
        return result;
    }

    /**
     * Calcula el número de iteraciones para el rectángulo {@code [xIni,xFin) × [yIni,yFin)}
     * en una vista de Julia.
     *
     * @param xIni   columna inicial (inclusive)
     * @param xFin   columna final   (exclusive)
     * @param yIni   fila inicial    (inclusive)
     * @param yFin   fila final      (exclusive)
     * @param width  anchura total de la imagen
     * @param height altura total de la imagen
     * @param view   estado de vista con centro y escala
     * @return matriz de iteraciones de tamaño {@code [yFin-yIni][xFin-xIni]}
     */
    public int[][] computeJulia(int xIni, int xFin, int yIni, int yFin, int width, int height, ViewState view) {
        int rh = yFin - yIni;
        int rw = xFin - xIni;
        int[][] result = new int[rh][rw];
        for (int y = yIni; y < yFin; y++) {
            double imaginary = view.pixelToImaginary(y, height);
            for (int x = xIni; x < xFin; x++) {
                result[y - yIni][x - xIni] = iterate(view.pixelToReal(x, width), imaginary, juliaReal, juliaImaginary);
            }
        }
        return result;
    }

    /**
     * Ejecuta la iteración {@code z_{n+1} = z_n² + c} hasta escape o límite.
     *
     * @param zReal      parte real inicial de {@code z}
     * @param zImaginary parte imaginaria inicial de {@code z}
     * @param cReal      parte real de {@code c}
     * @param cImaginary parte imaginaria de {@code c}
     * @return resultado de escape
     */
    private int iterate(double zReal, double zImaginary, double cReal, double cImaginary) {
        int iteration = 0;

        while (iteration < maxIterations) {
            double zRealSquared = zReal * zReal;
            double zImaginarySquared = zImaginary * zImaginary;

            if (zRealSquared + zImaginarySquared > ESCAPE_RADIUS_SQUARED) {
                return iteration;
            }

            zImaginary = 2.0 * zReal * zImaginary + cImaginary;
            zReal = zRealSquared - zImaginarySquared + cReal;
            iteration++;
        }

        return maxIterations;
    }

   }
