package com.mandelbrot;

/**
 * Representa el estado de la vista sobre el plano complejo.
 * <p>
 * Mantiene el centro de la vista (coordenadas reales e imaginarias) y el
 * factor de escala que relaciona píxeles del panel con unidades del plano
 * complejo. Permite aplicar operaciones de zoom y desplazamiento (pan).
 * </p>
 *
 * @author Mandelbrot Explorer
 * @version 1.0
 */
public class ViewState {

    /**
     * Escala de referencia de la vista inicial (1x de zoom).
     * Corresponde a una ventana de ~600 px mostrando 3 unidades del plano complejo.
     */
    public static final double INITIAL_SCALE = 3.0 / 600.0;

    /** Centro horizontal de la vista en el plano complejo (parte real). */
    private double centerX;

    /** Centro vertical de la vista en el plano complejo (parte imaginaria). */
    private double centerY;

    /**
     * Escala: unidades del plano complejo por píxel.
     * Valores menores implican mayor zoom (más detalle).
     */
    private double scale;

    /**
     * Crea un estado de vista con valores por defecto centrados en el origen
     * del conjunto de Mandelbrot.
     */
    public ViewState() {
        this(-0.5, 0.0, INITIAL_SCALE);
    }

    /**
     * Crea un estado de vista con parámetros personalizados.
     *
     * @param centerX centro horizontal en el plano complejo
     * @param centerY centro vertical en el plano complejo
     * @param scale   unidades complejas por píxel
     */
    public ViewState(double centerX, double centerY, double scale) {
        this.centerX = centerX;
        this.centerY = centerY;
        this.scale = scale;
    }

    public void set(double centerX, double centerY, double scale) {
        this.centerX = centerX;
        this.centerY = centerY;
        this.scale = scale;
    }
    /**
     * @return coordenada real del centro de la vista
     */
    public double getCenterX() {
        return centerX;
    }

    /**
     * @return coordenada imaginaria del centro de la vista
     */
    public double getCenterY() {
        return centerY;
    }

    /**
     * @return escala actual (unidades complejas por píxel)
     */
    public double getScale() {
        return scale;
    }

    /**
     * Devuelve el factor de ampliación respecto a la vista inicial.
     * Por ejemplo, {@code 2.0} significa que la imagen está al doble de tamaño (2x).
     *
     * @return número de veces que está aumentada la imagen respecto a 1x
     */
    public double getZoomFactor() {
        return INITIAL_SCALE / scale;
    }

    /**
     * Convierte una coordenada horizontal de píxel a su valor real en el plano complejo.
     *
     * @param pixelX coordenada X del píxel (0 = borde izquierdo)
     * @param width  ancho del panel en píxeles
     * @return parte real de la coordenada compleja
     */
    public double pixelToReal(int pixelX, int width) {
        return centerX + (pixelX - width / 2.0) * scale;
    }

    /**
     * Convierte una coordenada vertical de píxel a su valor imaginario en el plano complejo.
     *
     * @param pixelY coordenada Y del píxel (0 = borde superior)
     * @param height alto del panel en píxeles
     * @return parte imaginaria de la coordenada compleja
     */
    public double pixelToImaginary(int pixelY, int height) {
        return centerY + (pixelY - height / 2.0) * scale;
    }

    /**
     * Aplica zoom manteniendo fijo un punto del plano complejo bajo el cursor.
     *
     * @param mouseX      posición X del cursor en píxeles
     * @param mouseY      posición Y del cursor en píxeles
     * @param panelWidth  ancho del panel
     * @param panelHeight alto del panel
     * @param zoomFactor  factor multiplicativo (&lt; 1 acerca, &gt; 1 aleja)
     */
    public void zoomAt(int mouseX, int mouseY, int panelWidth, int panelHeight, double zoomFactor) {
        double worldX = pixelToReal(mouseX, panelWidth);
        double worldY = pixelToImaginary(mouseY, panelHeight);

        scale *= zoomFactor;

        centerX = worldX - (mouseX - panelWidth / 2.0) * scale;
        centerY = worldY - (mouseY - panelHeight / 2.0) * scale;
    }

    /**
     * Desplaza la vista en píxeles de pantalla.
     *
     * @param deltaPixelX desplazamiento horizontal en píxeles
     * @param deltaPixelY desplazamiento vertical en píxeles
     */
    public void pan(int deltaPixelX, int deltaPixelY) {
        centerX -= deltaPixelX * scale;
        centerY -= deltaPixelY * scale;
    }

    /**
     * Centra la vista en el punto del plano complejo bajo el cursor y aplica zoom.
     *
     * @param mouseX      posición X del cursor en píxeles
     * @param mouseY      posición Y del cursor en píxeles
     * @param panelWidth  ancho del panel
     * @param panelHeight alto del panel
     * @param zoomFactor  factor multiplicativo de escala (&lt; 1 acerca, &gt; 1 aleja)
     */
    public void centerAndZoomAt(int mouseX, int mouseY, int panelWidth, int panelHeight, double zoomFactor) {
        centerX = pixelToReal(mouseX, panelWidth);
        centerY = pixelToImaginary(mouseY, panelHeight);
        scale *= zoomFactor;
    }

    /**
     * Restablece la vista a la región estándar del conjunto de Mandelbrot.
     */
    public void reset() {
        resetForFractal(FractalType.MANDELBROT);
    }

    /**
     * Restablece la vista a una región adecuada para el tipo de fractal indicado.
     *
     * @param fractalType tipo de fractal para el cual ajustar centro y escala
     */
    public void resetForFractal(FractalType fractalType) {
        if (fractalType == FractalType.JULIA) {
            centerX = 0.0;
            centerY = 0.0;
        } else {
            centerX = -0.5;
            centerY = 0.0;
        }
        scale = INITIAL_SCALE;
    }

    /**
     * Crea una copia independiente del estado actual.
     *
     * @return nuevo {@link ViewState} con los mismos valores
     */
    public ViewState copy() {
        return new ViewState(centerX, centerY, scale);
    }

    public void move(double dx, double dy, double ds) {
        centerX += dx;
        centerY += dy;
        scale += ds;
    }

}
