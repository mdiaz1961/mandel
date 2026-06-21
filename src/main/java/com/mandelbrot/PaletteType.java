package com.mandelbrot;

/**
 * Tipos de paleta de colores disponibles para visualizar el conjunto de Mandelbrot.
 *
 * @author Mandelbrot Explorer
 * @version 1.0
 */
public enum PaletteType {

    /** Paleta basica con transiciones discretas. */
    BASICO("Basico"),

    /** Paleta coseno suave con transiciones continuas */
    SMOOTH("Suave"),

    /** Paleta cíclica con 64 colores discretos. */
    COLORS_64("64 colores"),

    /** Paleta cíclica con 256 colores discretos. */
    COLORS_32("32 colores"),

    /** Paleta cíclica con 16 colores discretos. */
    COLORS_16("16 colores"),

    /** Paleta en escala de grises. */
    GRAYSCALE("Tonos de gris");

    private final String displayName;

    /**
     * @param displayName nombre mostrado en el menú contextual
     */
    PaletteType(String displayName) {
        this.displayName = displayName;
    }

    /**
     * @return etiqueta legible para la interfaz de usuario
     */
    public String getDisplayName() {
        return displayName;
    }
}
