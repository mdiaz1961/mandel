package com.mandelbrot;

import java.awt.Color;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.util.Locale;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.event.MouseWheelEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;

import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

/**
 * Panel de visualización de un fractal (Mandelbrot o Julia) con zoom, pan y hint de coordenadas.
 *
 * @author Mandelbrot Explorer
 * @version 1.0
 */
public class FractalViewPanel extends JPanel {

    /** Factor de zoom por paso de la rueda del ratón. */
    private static final double ZOOM_FACTOR = 0.85;

    /** Factor de zoom al hacer doble clic (0.5 = acercar un 50%). */
    private static final double DOUBLE_CLICK_ZOOM_FACTOR = 0.25;

    /** Desplazamiento del hint respecto al cursor en píxeles. */
    private static final int COORDINATE_HINT_OFFSET = 16;

    /** Margen interior del panel para mantener el hint visible. */
    private static final int COORDINATE_HINT_MARGIN = 8;

    /** Tipo de fractal que renderiza este panel. */
    private final FractalType fractalType;

    /** Estado actual de la vista (centro y escala). */
    private final ViewState viewState = new ViewState();

    /** Motor de cálculo de iteraciones (implementación seleccionada por la propiedad {@code calculator}). */
    private final FractalCalculator calculator = FractalCalculator.create();

    /** Paleta de colores compartida. */
    private final ColorPalette colorPalette;

    /** Par (imagen, viewState) que representa el último render estable. */
    private static final class RenderBase {
        final BufferedImage image;
        final ViewState view;
        RenderBase(BufferedImage img, ViewState v) { image = img; view = v; }
    }

    /** Imagen en memoria donde se dibuja el fractal. */
    private volatile BufferedImage fractalImage;

    /** Último render estable; base para el desplazamiento incremental. */
    private volatile RenderBase renderBase;

    /** Generación del task activo; tareas con generación obsoleta no actualizan el estado. */
    private final AtomicLong taskGen = new AtomicLong();

    /** Pool de hilos para renderizado en segundo plano. */
    private final ExecutorService renderExecutor =
            Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());

    /** Tarea de renderizado en curso, si existe. */
    private Future<?> currentRenderTask;

    /** Estado de la vista correspondiente a la última imagen renderizada. */
    private volatile ViewState lastRenderedViewState = viewState.copy();

    /** Temporizador para agrupar eventos de la rueda y evitar renders repetidos. */
    private final Timer renderDebounceTimer;


    /** Posición del ratón al iniciar un arrastre para pan. */
    private Point lastDragPoint;

    /** Última posición del cursor sobre el panel; {@code null} si está fuera. */
    private Point mousePosition;

    /** Listener opcional para notificar coordenadas complejas bajo el cursor. */
    private ComplexCoordinateListener complexCoordinateListener;

    /**
     * Crea un panel para visualizar el fractal indicado.
     *
     * @param fractalType  tipo de fractal (Mandelbrot o Julia)
     * @param colorPalette paleta de colores compartida
     */
    public FractalViewPanel(FractalType fractalType, ColorPalette colorPalette) {
        this.fractalType = fractalType;
        this.colorPalette = colorPalette;
        setBackground(Color.BLACK);
        setFocusable(true);
        // timer: espera 150 ms tras el último evento de rueda antes de lanzar render
        renderDebounceTimer = new Timer(150, e -> requestRender());
        renderDebounceTimer.setRepeats(false);

        registerMouseListeners();
        registerResizeListener();
        if (fractalType == FractalType.JULIA) {
            viewState.resetForFractal(FractalType.JULIA);
        }
    }

    /**
     * Solicita renderizado cuando el panel obtiene un tamaño válido.
     */
    private void registerResizeListener() {
        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent event) {
                ensureImageSize();
            }
        });
    }

    /**
     * Ajusta la imagen interna al tamaño actual del panel y renderiza si cambió.
     */
    public void refreshImageSize() {
        ensureImageSize();
    }

    /**
     * Crea o redimensiona la imagen interna si el tamaño del panel cambió.
     */
    private void ensureImageSize() {
        int width = getWidth();
        int height = getHeight();
        if (width <= 0 || height <= 0) return;
        BufferedImage img = fractalImage;
        if (img == null || img.getWidth() != width || img.getHeight() != height) {
            requestRender();
        }
    }

    /**
     * @return tipo de fractal de este panel
     */
    public FractalType getFractalType() {
        return fractalType;
    }

    /**
     * @return última posición del cursor sobre el panel, o {@code null} si está fuera
     */
    public Point getMousePosition() {
        return mousePosition;
    }

    /**
     * Obtiene la parte real de la coordenada compleja en un píxel del panel.
     *
     * @param pixelX coordenada X del píxel
     * @return parte real
     */
    public double getComplexReal(int pixelX) {
        return viewState.pixelToReal(pixelX, getWidth());
    }

    /**
     * Obtiene la parte imaginaria de la coordenada compleja en un píxel del panel.
     *
     * @param pixelY coordenada Y del píxel
     * @return parte imaginaria
     */
    public double getComplexImaginary(int pixelY) {
        return viewState.pixelToImaginary(pixelY, getHeight());
    }

    /**
     * Registra un listener que recibe la coordenada compleja bajo el cursor al mover el ratón.
     *
     * @param listener callback invocado en cada movimiento del ratón
     */
    public void setComplexCoordinateListener(ComplexCoordinateListener listener) {
        this.complexCoordinateListener = listener;
    }

    /**
     * Define el parámetro {@code c} del conjunto de Julia, restablece la vista y renderiza.
     *
     * @param real      parte real de {@code c}
     * @param imaginary parte imaginaria de {@code c}
     */
    public void setJuliaConstant(double real, double imaginary) {
        calculator.setJuliaConstant(real, imaginary);
        viewState.resetForFractal(FractalType.JULIA);
        requestRender();
    }

    /**
     * Actualiza el parámetro {@code c} del conjunto de Julia sin cambiar la vista actual.
     *
     * @param real      parte real de {@code c}
     * @param imaginary parte imaginaria de {@code c}
     */
    public void updateJuliaConstant(double real, double imaginary) {
        calculator.setJuliaConstant(real, imaginary);
        repaint();
        requestRender();
    }

    /**
     * Restablece la vista a los valores iniciales del fractal de este panel.
     */
    public void resetView() {
        viewState.resetForFractal(fractalType);
        requestRender();
    }

    /**
     * Solicita un nuevo renderizado completo del fractal.
     * Crea una imagen nueva y la reemplaza atómicamente al terminar.
     */
    public void requestRender() {
        if (getWidth() <= 0 || getHeight() <= 0) return;
        if (currentRenderTask != null && !currentRenderTask.isDone()) {
            currentRenderTask.cancel(true);
        }
        long gen = taskGen.incrementAndGet();
        ViewState view = viewState.copy();
        int width  = getWidth();
        int height = getHeight();
        BufferedImage target = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        double jr = calculator.getJuliaReal();
        double ji = calculator.getJuliaImaginary();

        currentRenderTask = renderExecutor.submit(() -> {
            calculator.setJuliaConstant(jr, ji);
            fillRegion(target, 0, width, 0, height, width, height, view);
            if (!Thread.currentThread().isInterrupted() && taskGen.get() == gen) {
                renderBase = new RenderBase(target, view);
                lastRenderedViewState = view;
                fractalImage = target;
                SwingUtilities.invokeLater(this::repaint);
            }
        });
    }

    private void registerMouseListeners() {
        addMouseWheelListener(this::handleZoom);

        addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent event) {
                if (event.getButton() == MouseEvent.BUTTON1) {
                    lastDragPoint = event.getPoint();
                }
            }

            @Override
            public void mouseReleased(MouseEvent event) {
                if (event.getButton() == MouseEvent.BUTTON1) {
                    lastDragPoint = null;
                }
            }

            @Override
            public void mouseClicked(MouseEvent event) {
                if (event.getClickCount() == 2) {
                    viewState.centerAndZoomAt(event.getX(), event.getY(), getWidth(), getHeight(), DOUBLE_CLICK_ZOOM_FACTOR);
                    requestRender();
                }
            }

            @Override
            public void mouseExited(MouseEvent event) {
                mousePosition = null;
                repaint();
            }
        });

        addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseMoved(MouseEvent event) {
                updateMouseTracking(event);
            }

            @Override
            public void mouseDragged(MouseEvent event) {
                updateMouseTracking(event);
                if (lastDragPoint != null) {
                    int deltaX = event.getX() - lastDragPoint.x;
                    int deltaY = event.getY() - lastDragPoint.y;
                    viewState.pan(deltaX, deltaY);
                    lastDragPoint = event.getPoint();
                    repaint();           // respuesta visual inmediata vía transformación afín
                    requestPanRender();  // calcula solo las franjas expuestas
                }
            }
        });
    }

    /**
     * Actualiza la posición del cursor, repinta el hint y notifica la coordenada compleja.
     *
     * @param event evento de ratón con la posición y teclas modificadoras
     */
    private void updateMouseTracking(MouseEvent event) {
        mousePosition = event.getPoint();
        repaint();
        notifyComplexCoordinate(event);
    }

    /**
     * Notifica al listener la coordenada compleja bajo el cursor, si está registrado.
     *
     * @param event evento de ratón con la posición y teclas modificadoras
     */
    private void notifyComplexCoordinate(MouseEvent event) {
        if (complexCoordinateListener == null || getWidth() <= 0 || getHeight() <= 0) {
            return;
        }
        Point point = event.getPoint();
        double real = viewState.pixelToReal(point.x, getWidth());
        double imaginary = viewState.pixelToImaginary(point.y, getHeight());
        complexCoordinateListener.onComplexCoordinate(real, imaginary, event.isControlDown());
    }

    private void handleZoom(MouseWheelEvent event) {
        double factor = event.getWheelRotation() < 0 ? ZOOM_FACTOR : 1.0 / ZOOM_FACTOR;
        viewState.zoomAt(event.getX(), event.getY(), getWidth(), getHeight(), factor);
        // Repaint inmediato para mostrar un escalado visual rápido de la imagen
        // renderizada previamente y diferir el render pesado hasta que el usuario
        // deje de girar la rueda (debounce).
        repaint();
        if (renderDebounceTimer.isRunning()) {
            renderDebounceTimer.restart();
        } else {
            renderDebounceTimer.start();
        }
    }

    @Override
    public void setBounds(int x, int y, int width, int height) {
        super.setBounds(x, y, width, height);
        if (width > 0 && height > 0) {
            requestRender();
        }
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        Graphics2D g2d = (Graphics2D) graphics;

        if (fractalImage != null) {
            // Si la última imagen renderizada corresponde a un estado distinto
            // al actual, aplicamos una transformación afín para escalar y desplazar
            // la imagen mostrada de forma interactiva mientras el usuario mueve el ratón.
            ViewState last = lastRenderedViewState;
            boolean viewChanged = last != null && last.getScale() > 0 && (
                    last.getScale() != viewState.getScale()
                            || last.getCenterX() != viewState.getCenterX()
                            || last.getCenterY() != viewState.getCenterY());
            if (viewChanged) {
                int w = getWidth();
                int h = getHeight();
                double scaleRatio = last.getScale() / viewState.getScale();

                double dxWorld = last.getCenterX() - viewState.getCenterX();
                double dyWorld = last.getCenterY() - viewState.getCenterY();
                double dxPixels = dxWorld / viewState.getScale();
                double dyPixels = dyWorld / viewState.getScale();

                AffineTransform at = new AffineTransform();
                // trasladar al centro del panel
                at.translate(w / 2.0, h / 2.0);
                // aplicar desplazamiento del centro en pixeles
                at.translate(dxPixels, dyPixels);
                // escalar respecto al centro
                at.scale(scaleRatio, scaleRatio);
                // trasladar para dibujar la imagen (la imagen tiene origen en 0,0)
                at.translate(-w / 2.0, -h / 2.0);

                Graphics2D g2 = (Graphics2D) g2d.create();
                g2.drawImage(fractalImage, at, null);
                g2.dispose();
            } else {
                g2d.drawImage(fractalImage, 0, 0, null);
            }
        }

        drawPanelTitle(g2d);
        drawCoordinateHint(g2d);
    }

    private void drawPanelTitle(Graphics2D g2d) {
        g2d.setColor(new Color(255, 255, 255, 200));
        String title = fractalType.getDisplayName();
        if (fractalType == FractalType.JULIA) {
            double imag = calculator.getJuliaImaginary();
            String imagSign = imag >= 0 ? "+" : "-";
            title += String.format(Locale.US, "  c = %s %s %si",
                    formatCoordinate(calculator.getJuliaReal()),
                    imagSign,
                    formatCoordinate(Math.abs(imag)));
        }
        g2d.drawString(title, 10, 20);
    }

    private void drawCoordinateHint(Graphics2D g2d) {
        if (mousePosition == null) {
            return;
        }

        int width = getWidth();
        int height = getHeight();
        if (width <= 0 || height <= 0) {
            return;
        }

        double real = viewState.pixelToReal(mousePosition.x, width);
        double imaginary = viewState.pixelToImaginary(mousePosition.y, height);

        String realText = "Re: " + formatCoordinate(real);
        String imagText = "Im: " + formatCoordinate(imaginary);
        String zoomText = "Zoom: " + formatZoom(viewState.getZoomFactor());

        g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        FontMetrics metrics = g2d.getFontMetrics();
        int boxWidth = Math.max(
                Math.max(metrics.stringWidth(realText), metrics.stringWidth(imagText)),
                metrics.stringWidth(zoomText)
        ) + 12;
        int lineHeight = metrics.getHeight();
        int boxHeight = lineHeight * 3 + 8;

        int hintX = mousePosition.x + COORDINATE_HINT_OFFSET;
        int hintY = mousePosition.y + COORDINATE_HINT_OFFSET;
        if (hintX + boxWidth > width - COORDINATE_HINT_MARGIN) {
            hintX = mousePosition.x - boxWidth - COORDINATE_HINT_OFFSET;
        }
        if (hintY + boxHeight > height - COORDINATE_HINT_MARGIN) {
            hintY = mousePosition.y - boxHeight - COORDINATE_HINT_OFFSET;
        }
        hintX = Math.max(COORDINATE_HINT_MARGIN, hintX);
        hintY = Math.max(COORDINATE_HINT_MARGIN, hintY);

        g2d.setColor(new Color(0, 0, 0, 190));
        g2d.fillRoundRect(hintX, hintY, boxWidth, boxHeight, 8, 8);
        g2d.setColor(new Color(255, 255, 255, 230));
        g2d.drawRoundRect(hintX, hintY, boxWidth, boxHeight, 8, 8);

        int textX = hintX + 6;
        int textY = hintY + metrics.getAscent() + 4;
        g2d.drawString(realText, textX, textY);
        g2d.drawString(imagText, textX, textY + lineHeight);
        g2d.drawString(zoomText, textX, textY + lineHeight * 2);
    }

    private String formatZoom(double zoom) {
        if (zoom >= 1_000_000 || (zoom > 0 && zoom < 0.0001)) {
            return String.format(Locale.US, "%.4e", zoom) + "x";
        }
        if (zoom >= 100) {
            return String.format(Locale.US, "%.1f", zoom) + "x";
        }
        return String.format(Locale.US, "%.2f", zoom) + "x";
    }

    private String formatCoordinate(double value) {
        int decimals = Math.max(4, (int) Math.ceil(-Math.log10(viewState.getScale())) + 2);
        decimals = Math.min(decimals, 14);
        return String.format(Locale.US, "%." + decimals + "f", value);
    }

    /**
     * Calcula y pinta el rectángulo {@code [xIni,xFin) × [yIni,yFin)} de {@code image}.
     * Invoca al calculator con la región exacta y escribe los colores resultantes.
     */
    private void fillRegion(BufferedImage image,
                            int xIni, int xFin, int yIni, int yFin,
                            int width, int height, ViewState view) {
        int maxIter = calculator.getMaxIterations();
        int[][] data = fractalType == FractalType.MANDELBROT
                ? calculator.computeMandelbrot(xIni, xFin, yIni, yFin, width, height, view)
                : calculator.computeJulia(xIni, xFin, yIni, yFin, width, height, view);
        int rh = yFin - yIni;
        int rw = xFin - xIni;
        for (int ry = 0; ry < rh; ry++) {
            for (int rx = 0; rx < rw; rx++) {
                image.setRGB(xIni + rx, yIni + ry,
                        colorPalette.getColor(data[ry][rx], maxIter).getRGB());
            }
        }
    }

    /**
     * Solicita un render incremental de pan: desplaza la imagen base y recalcula
     * solo las franjas expuestas. El resultado reemplaza {@code fractalImage}.
     */
    private void requestPanRender() {
        if (getWidth() <= 0 || getHeight() <= 0) return;
        RenderBase base = renderBase;
        if (base == null) { requestRender(); return; }
        if (currentRenderTask != null && !currentRenderTask.isDone()) {
            currentRenderTask.cancel(true);
        }
        long gen = taskGen.incrementAndGet();
        ViewState view = viewState.copy();
        int width  = getWidth();
        int height = getHeight();
        BufferedImage target = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        double jr = calculator.getJuliaReal();
        double ji = calculator.getJuliaImaginary();
        // Desplazamiento en píxeles desde la base hasta la vista actual
        int panX = (int) Math.round((base.view.getCenterX() - view.getCenterX()) / view.getScale());
        int panY = (int) Math.round((base.view.getCenterY() - view.getCenterY()) / view.getScale());

        currentRenderTask = renderExecutor.submit(() -> {
            calculator.setJuliaConstant(jr, ji);
            // 1. Copiar imagen base desplazada
            Graphics2D g2d = target.createGraphics();
            g2d.drawImage(base.image, panX, panY, null);
            g2d.dispose();
            if (Thread.currentThread().isInterrupted()) return;

            int absX = Math.abs(panX);
            int absY = Math.abs(panY);

            if (absX >= width || absY >= height) {
                // El desplazamiento cubre toda la pantalla: render completo
                fillRegion(target, 0, width, 0, height, width, height, view);
            } else {
                // Franja horizontal (ancho completo)
                if (absY > 0) {
                    int yIni = panY > 0 ? 0 : height - absY;
                    int yFin = panY > 0 ? absY : height;
                    fillRegion(target, 0, width, yIni, yFin, width, height, view);
                }
                // Franja vertical (filas no cubiertas por la franja horizontal)
                if (!Thread.currentThread().isInterrupted() && absX > 0) {
                    int xIni  = panX > 0 ? 0 : width - absX;
                    int xFin  = panX > 0 ? absX : width;
                    int vYIni = panY > 0 ? absY : 0;
                    int vYFin = panY < 0 ? height - absY : height;
                    if (vYIni < vYFin) {
                        fillRegion(target, xIni, xFin, vYIni, vYFin, width, height, view);
                    }
                }
            }
            if (!Thread.currentThread().isInterrupted() && taskGen.get() == gen) {
                renderBase = new RenderBase(target, view);
                lastRenderedViewState = view;
                fractalImage = target;
                SwingUtilities.invokeLater(this::repaint);
            }
        });
    }

    public void animateToInitialView() {
        animateTo(-0.5,0,ViewState.INITIAL_SCALE,200,false);
    }

    public void animate(int caso) {
        viewState.set(-0.5,0,ViewState.INITIAL_SCALE);
        switch (caso) {
            case 0:
                animateTo(-0.743643,0.131825,0.000000003,200);
                break;
            case 1:
                animateTo(-0.17033739669900003,-1.0650603561,9.900000000000033E-11,200);
                break;
            case 2:
                animateTo(0.42883621390396764,-0.23134282255078162,3.949805525581182E-12,200);
                break;
            case 3:
                animateTo(-1.943035808586426,9.194024224049161E-16,7.746945919291003E-17,200);
                break;
            case 4:
                animateTo(-0.761575333854055,-0.08475973655846687,9.20864502069162E-16,200);
                break;
        }  
    }

    /**
     * Anima suavemente la vista desde la posición actual hasta
     * ({@code targetCX}, {@code targetCY}, {@code targetScale}).
     *
     * <p>La duración aproximada es {@code frames × 16 ms} (~60 fps).
     * La escala se interpola en espacio logarítmico para que el zoom sea
     * perceptualmente uniforme. La posición usa easing smooth-step para
     * arrancar y frenar suavemente.</p>
     *
     * @param targetCX    parte real del destino
     * @param targetCY    parte imaginaria del destino
     * @param targetScale escala destino (unidades complejas / píxel)
     * @param frames      número de frames (~16 ms cada uno)
     */
    /** Sobrecarga con orden por defecto: pan primero, luego zoom. */
    private void animateTo(double targetCX, double targetCY, double targetScale, int frames) {
        animateTo(targetCX, targetCY, targetScale, frames, true);
    }

    /**
     * Anima suavemente la vista desde la posición actual hasta el destino en dos fases.
     *
     * <p>Cada frame se calcula completamente y se pinta antes de pasar al siguiente (~20 fps).
     * La escala se interpola en espacio logarítmico y la posición usa smooth-step.</p>
     *
     * @param targetCX    parte real del destino
     * @param targetCY    parte imaginaria del destino
     * @param targetScale escala destino (unidades complejas / píxel)
     * @param frames      número total de frames a mostrar
     * @param panFirst    {@code true} → mover x,y primero y luego escalar;
     *                    {@code false} → escalar primero y luego mover x,y
     */
    private void animateTo(double targetCX, double targetCY, double targetScale, int frames, boolean panFirst) {
        if (currentRenderTask != null && !currentRenderTask.isDone()) {
            currentRenderTask.cancel(true);
        }
        renderDebounceTimer.stop();

        final double startCX    = viewState.getCenterX();
        final double startCY    = viewState.getCenterY();
        final double startScale = viewState.getScale();
        final double logStart   = Math.log(Math.max(startScale,   1e-300));
        final double logEnd     = Math.log(Math.max(targetScale,  1e-300));
        final double jr         = calculator.getJuliaReal();
        final double ji         = calculator.getJuliaImaginary();
        final long   frameMs    = 1000L / 20;  // 50 ms → 20 fps objetivo

        // Fase 1: pan (1/10 de los frames), Fase 2: zoom (9/10)
        final int panFrames  = Math.max(1, frames / 10);
        final int zoomFrames = frames - panFrames;

        currentRenderTask = renderExecutor.submit(() -> {
            calculator.setJuliaConstant(jr, ji);

            if (panFirst) {
                // ── Fase 1: mover solo x,y con escala inicial ─────────────────
                for (int i = 1; i <= panFrames; i++) {
                    if (Thread.currentThread().isInterrupted()) return;
                    long frameStart = System.currentTimeMillis();
                    double et = smoothStep((double) i / panFrames);
                    double cx = startCX + (targetCX - startCX) * et;
                    double cy = startCY + (targetCY - startCY) * et;
                    if (!paintFrame(cx, cy, startScale)) return;
                    throttle(frameStart, frameMs);
                }
                // ── Fase 2: zoom en el destino x,y ───────────────────────────
                for (int i = 1; i <= zoomFrames; i++) {
                    if (Thread.currentThread().isInterrupted()) return;
                    long frameStart = System.currentTimeMillis();
                    double et = smoothStep((double) i / zoomFrames);
                    double sc = Math.exp(logStart + (logEnd - logStart) * et);
                    if (!paintFrame(targetCX, targetCY, sc)) return;
                    throttle(frameStart, frameMs);
                }
            } else {
                // ── Fase 1: zoom en la posición inicial ───────────────────────
                for (int i = 1; i <= zoomFrames; i++) {
                    if (Thread.currentThread().isInterrupted()) return;
                    long frameStart = System.currentTimeMillis();
                    double et = smoothStep((double) i / zoomFrames);
                    double sc = Math.exp(logStart + (logEnd - logStart) * et);
                    if (!paintFrame(startCX, startCY, sc)) return;
                    throttle(frameStart, frameMs);
                }
                // ── Fase 2: mover x,y con escala destino ─────────────────────
                for (int i = 1; i <= panFrames; i++) {
                    if (Thread.currentThread().isInterrupted()) return;
                    long frameStart = System.currentTimeMillis();
                    double et = smoothStep((double) i / panFrames);
                    double cx = startCX + (targetCX - startCX) * et;
                    double cy = startCY + (targetCY - startCY) * et;
                    if (!paintFrame(cx, cy, targetScale)) return;
                    throttle(frameStart, frameMs);
                }
            }

            // ── Render final de alta calidad ───────────────────────────────────
            SwingUtilities.invokeLater(() -> {
                viewState.set(targetCX, targetCY, targetScale);
                requestRender();
            });
        });
    }

    /** Calcula y pinta un frame; retorna {@code false} si se interrumpió. */
    private boolean paintFrame(double cx, double cy, double sc) {
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return true;

        ViewState frameView = new ViewState(cx, cy, sc);
        BufferedImage frame = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        fillRegion(frame, 0, w, 0, h, w, h, frameView);

        if (Thread.currentThread().isInterrupted()) return false;

        try {
            SwingUtilities.invokeAndWait(() -> {
                viewState.set(cx, cy, sc);
                renderBase            = new RenderBase(frame, frameView);
                lastRenderedViewState = frameView;
                fractalImage          = frame;
                paintImmediately(0, 0, getWidth(), getHeight());
            });
        } catch (Exception e) {
            Thread.currentThread().interrupt();
            return false;
        }
        return true;
    }

    /** Easing cúbico: f(t) = 3t² − 2t³ (arranca y frena suave). */
    private static double smoothStep(double t) { return t * t * (3.0 - 2.0 * t); }

    /** Duerme el tiempo restante para mantener la cadencia de frames. */
    private void throttle(long frameStartMs, long targetFrameMs) {
        long sleep = targetFrameMs - (System.currentTimeMillis() - frameStartMs);
        if (sleep > 0) {
            try { Thread.sleep(sleep); }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }
    }
}
