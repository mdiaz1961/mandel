package com.mandelbrot;

/**
 * Tipos de fractal que se pueden visualizar en la aplicación.
 *
 * @author Mandelbrot Explorer
 * @version 1.0
 */
public enum FractalType {

    /** Conjunto de Mandelbrot: {@code z₀ = 0}, {@code c} es la coordenada del píxel. */
    MANDELBROT("Mandelbrot"),

    /** Conjunto de Julia: {@code c} es constante, {@code z₀} es la coordenada del píxel. */
    JULIA("Julia");

    private final String displayName;

    /**
     * @param displayName nombre mostrado en el menú contextual
     */
    FractalType(String displayName) {
        this.displayName = displayName;
    }

    /**
     * @return etiqueta legible para la interfaz de usuario
     */
    public String getDisplayName() {
        return displayName;
    }
}
