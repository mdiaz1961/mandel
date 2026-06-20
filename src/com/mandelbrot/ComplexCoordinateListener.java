package com.mandelbrot;

/**
 * Escucha cambios en la coordenada compleja bajo el cursor de un panel fractal.
 *
 * @author Mandelbrot Explorer
 * @version 1.0
 */
@FunctionalInterface
public interface ComplexCoordinateListener {

    /**
     * Notifica la coordenada compleja correspondiente a un píxel del panel.
     *
     * @param real            parte real
     * @param imaginary       parte imaginaria
     * @param controlPressed  {@code true} si la tecla Control está presionada
     */
    void onComplexCoordinate(double real, double imaginary, boolean controlPressed);
}
