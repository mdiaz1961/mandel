package com.mandelbrot;

import java.awt.Color;

/**
 * Genera colores para representar el número de iteraciones del algoritmo de Mandelbrot.
 * <p>
 * Soporta varios modos de paleta: degradado suave coseno, ciclos discretos de
 * 256, 64 o 16 colores, y escala de grises.
 * </p>
 *
 * @author Mandelbrot Explorer
 * @version 1.0
 */
public class ColorPalette {

    /** Color asignado a los puntos que pertenecen al conjunto de Mandelbrot. */
    private static final Color INSIDE_COLOR = new Color(2, 2, 8);

    /** Color interior para la paleta en escala de grises. */
    private static final Color GRAYSCALE_INSIDE_COLOR = Color.BLACK;

    /** Iteraciones de escape por ciclo completo de la paleta suave. */
    private static final double ITERATIONS_PER_CYCLE = 38.0;

    /** Componente base R de la paleta coseno. */
    private static final double BASE_R = 0.03;

    /** Componente base G de la paleta coseno. */
    private static final double BASE_G = 0.04;

    /** Componente base B de la paleta coseno. */
    private static final double BASE_B = 0.12;

    /** Amplitud R de la paleta coseno. */
    private static final double AMP_R = 0.55;

    /** Amplitud G de la paleta coseno. */
    private static final double AMP_G = 0.65;

    /** Amplitud B de la paleta coseno. */
    private static final double AMP_B = 0.90;

    /** Frecuencia R de la paleta coseno. */
    private static final double FREQ_R = 1.0;

    /** Frecuencia G de la paleta coseno. */
    private static final double FREQ_G = 1.05;

    /** Frecuencia B de la paleta coseno. */
    private static final double FREQ_B = 0.95;

    /** Desfase R de la paleta coseno. */
    private static final double PHASE_R = 0.00;

    /** Desfase G de la paleta coseno. */
    private static final double PHASE_G = 0.28;

    /** Desfase B de la paleta coseno. */
    private static final double PHASE_B = 0.52;

    /** Constante 2π para las funciones coseno. */
    private static final double TWO_PI = 2.0 * Math.PI;

    /** Tabla precalculada de 64 colores. */
    private final Color[] spectrum64 = buildSpectrumPalette(64);

    /** Tabla precalculada de 64 colores. */
    private final Color[] spectrumBasic = buildSpectrumBasic();

    /** Tabla precalculada de 256 colores para paletas discretas. */
    private final Color[] spectrum32 = buildSpectrumPalette(32);

    /** Tabla precalculada de 16 colores. */
    private final Color[] spectrum16 = buildSpectrumPalette(16);

    /** Tabla precalculada de 256 tonos de gris. */
    private final Color[] grayscale32 = buildGrayscalePalette(32);

    /** Tipo de paleta activo. */
    private PaletteType paletteType = PaletteType.BASICO;

    /**
     * @return tipo de paleta actualmente seleccionado
     */
    public PaletteType getPaletteType() {
        return paletteType;
    }

    private Color[] buildSpectrumBasic() {
        Color[] set1= {
           new Color((66 << 16)|( 30 << 8)|( 15)),
           new Color((25 << 16)|( 7 << 8)|( 26)),
           new Color((9 << 16)|( 1 << 8)|( 47)),
           new Color((4 << 16)|( 4 << 8)|( 73)),
           new Color((0 << 16)|( 7 << 8)|( 100)),
           new Color((12 << 16)|( 44 << 8)|( 138)),
           new Color((24 << 16)|( 82 << 8)|( 177)),
           new Color((57 << 16)|( 125 << 8)|( 209)),
           new Color((134 << 16)|( 181 << 8)|( 229)),
           new Color((211 << 16)|( 236 << 8)|( 248)),
           new Color((241 << 16)|( 233 << 8)|( 191)),
           new Color((248 << 16)|( 201 << 8)|( 95)),
           new Color((255 << 16)|( 170 << 8)|( 0)),
           new Color((204 << 16)|( 128 << 8)|( 0)),
           new Color((153 << 16)|( 87 << 8)|( 0)),
           new Color((106 << 16)|( 52 << 8)|( 3))
        };
        return set1;
    }

    /**
     * Cambia el tipo de paleta activo.
     *
     * @param paletteType nuevo tipo de paleta
     */
    public void setPaletteType(PaletteType paletteType) {
        this.paletteType = paletteType;
    }

    /**
     * Obtiene el color correspondiente a un conteo de iteraciones.
     *
     * @param iterations    número de iteraciones antes de escapar, o máximo si no escapa
     * @param maxIterations límite máximo de iteraciones del cálculo
     * @return color {@link Color} para el píxel
     */
    public Color getColor(int iterations, int maxIterations) {
        boolean escaped = iterations < maxIterations;
        if (!escaped) {
            return paletteType == PaletteType.GRAYSCALE ? GRAYSCALE_INSIDE_COLOR : INSIDE_COLOR;
        }

        double smoothIteration = iterations; // fallback: no suavizado disponible, usamos el entero
        return switch (paletteType) {
            case SMOOTH -> cosinePalette(smoothIteration / ITERATIONS_PER_CYCLE);
            case BASICO -> discreteColor(spectrumBasic, smoothIteration);
            case COLORS_64 -> discreteColor(spectrum64, smoothIteration);
            case COLORS_32 -> discreteColor(spectrum32, smoothIteration);
            case COLORS_16 -> discreteColor(spectrum16, smoothIteration);
            case GRAYSCALE -> discreteColor(grayscale32, smoothIteration);
        };
    }

    /**
     * Construye una tabla de colores discretos usando la paleta coseno.
     *
     * @param size número de colores en la tabla
     * @return arreglo de colores precalculados
     */
    private Color[] buildSpectrumPalette(int size) {
        Color[] palette = new Color[size];
        for (int i = 0; i < size; i++) {
            palette[i] = cosinePalette((double) i / size);
        }
        return palette;
    }

    /**
     * Construye una tabla de tonos de gris de oscuro a claro.
     *
     * @param size número de niveles de gris
     * @return arreglo de grises precalculados
     */
    private Color[] buildGrayscalePalette(int size) {
        Color[] palette = new Color[size];
        for (int i = 0; i < size; i++) {
            int gray = (int) (255.0 * i / (size - 1));
            palette[i] = new Color(gray, gray, gray);
        }
        return palette;
    }

    /**
     * Selecciona un color discreto de una tabla según las iteraciones de escape.
     *
     * @param palette         tabla de colores precalculada
     * @param smoothIteration iteraciones suavizadas del punto
     * @return color de la tabla correspondiente al índice calculado
     */
    private Color discreteColor(Color[] palette, double smoothIteration) {
        int index = Math.floorMod((int) smoothIteration, palette.length);
        return palette[index];
    }

    /**
     * Aplica la paleta coseno: {@code base + amplitude * cos(2π * (frequency * t + phase))}.
     *
     * @param t índice de color normalizado
     * @return color resultante
     */
    private Color cosinePalette(double t) {
        double r = BASE_R + AMP_R * Math.cos(TWO_PI * (FREQ_R * t + PHASE_R));
        double g = BASE_G + AMP_G * Math.cos(TWO_PI * (FREQ_G * t + PHASE_G));
        double b = BASE_B + AMP_B * Math.cos(TWO_PI * (FREQ_B * t + PHASE_B));

        r = Math.pow(clamp01(r), 0.92);
        g = Math.pow(clamp01(g), 0.92);
        b = Math.pow(clamp01(b), 0.88);

        return new Color(toByte(r), toByte(g), toByte(b));
    }

    /**
     * Limita un valor al rango [0, 1].
     *
     * @param value valor a limitar
     * @return valor entre 0 y 1 inclusive
     */
    private double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    /**
     * Convierte un componente normalizado [0, 1] a entero [0, 255].
     *
     * @param component valor entre 0 y 1
     * @return componente de color en escala 0-255
     */
    private int toByte(double component) {
        return (int) (clamp01(component) * 255);
    }
}
