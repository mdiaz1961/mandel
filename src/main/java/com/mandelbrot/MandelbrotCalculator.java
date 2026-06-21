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
     * Calcula el número de iteraciones para cada píxel de una vista de Mandelbrot.
     *
     * @param width  anchura de la imagen en píxeles
     * @param height altura de la imagen en píxeles
     * @param view   estado de vista con centro y escala
     * @return matriz de iteraciones de tamaño {@code [height][width]}
     */
    public int[][] computeMandelbrot(int width, int height, ViewState view) {
        return computeFractal(width, height, view, false);
    }

    /**
     * Calcula el número de iteraciones para cada píxel de una vista de Julia.
     *
     * @param width  anchura de la imagen en píxeles
     * @param height altura de la imagen en píxeles
     * @param view   estado de vista con centro y escala
     * @return matriz de iteraciones de tamaño {@code [height][width]}
     */
    public int[][] computeJulia(int width, int height, ViewState view) {
        return computeFractal(width, height, view, true);
    }

    private int[][] computeFractal(int width, int height, ViewState view, boolean julia) {
        int[][] iterations = new int[height][width];
        for (int y = 0; y < height; y++) {
            double imaginary = view.pixelToImaginary(y, height);
            for (int x = 0; x < width; x++) {
                double real = view.pixelToReal(x, width);
                iterations[y][x] = julia
                        ? iterate(real, imaginary, juliaReal, juliaImaginary)
                        : iterate(0.0, 0.0, real, imaginary);
            }
        }
        return iterations;
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
