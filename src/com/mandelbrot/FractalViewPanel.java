package com.mandelbrot;

import java.awt.Color;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.util.Locale;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.event.MouseWheelEvent;
import java.awt.image.BufferedImage;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;

import javax.swing.JPanel;
import javax.swing.SwingUtilities;

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

    /** Motor de cálculo de iteraciones. */
    private final MandelbrotCalculator calculator = new MandelbrotCalculator();

    /** Paleta de colores compartida. */
    private final ColorPalette colorPalette;

    /** Imagen en memoria donde se dibuja el fractal. */
    private BufferedImage fractalImage;

    /** Pool de hilos para renderizado en segundo plano. */
    private final ExecutorService renderExecutor =
            Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());

    /** Tarea de renderizado en curso, si existe. */
    private Future<?> currentRenderTask;

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
        if (width <= 0 || height <= 0) {
            return;
        }
        if (fractalImage == null
                || fractalImage.getWidth() != width
                || fractalImage.getHeight() != height) {
            fractalImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
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
     * Solicita un nuevo renderizado del fractal.
     */
    public void requestRender() {
        if (fractalImage == null || getWidth() <= 0 || getHeight() <= 0) {
            return;
        }

        if (currentRenderTask != null && !currentRenderTask.isDone()) {
            currentRenderTask.cancel(true);
        }

        ViewState snapshot = viewState.copy();
        int width = getWidth();
        int height = getHeight();
        BufferedImage targetImage = fractalImage;
        double juliaReal = calculator.getJuliaReal();
        double juliaImaginary = calculator.getJuliaImaginary();

        currentRenderTask = renderExecutor.submit(() -> {
            renderFractal(targetImage, snapshot, width, height, juliaReal, juliaImaginary);
            if (!Thread.currentThread().isInterrupted()) {
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
                    requestRender();
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
        requestRender();
    }

    @Override
    public void setBounds(int x, int y, int width, int height) {
        super.setBounds(x, y, width, height);
        if (width > 0 && height > 0) {
            fractalImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            requestRender();
        }
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        Graphics2D g2d = (Graphics2D) graphics;

        if (fractalImage != null) {
            g2d.drawImage(fractalImage, 0, 0, null);
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

    private void renderFractal(
            BufferedImage image,
            ViewState view,
            int width,
            int height,
            double juliaReal,
            double juliaImaginary
    ) {
        int maxIter = calculator.getMaxIterations();
        MandelbrotCalculator localCalculator = new MandelbrotCalculator(maxIter);
        localCalculator.setJuliaConstant(juliaReal, juliaImaginary);

        IntStream.range(0, height).parallel().forEach(y -> {
            if (Thread.currentThread().isInterrupted()) {
                return;
            }
            double imaginary = view.pixelToImaginary(y, height);
            for (int x = 0; x < width; x++) {
                double real = view.pixelToReal(x, width);
                int iterations = fractalType == FractalType.MANDELBROT
                    ? localCalculator.computeMandelbrot(real, imaginary)
                    : localCalculator.computeJulia(real, imaginary);
                Color color = colorPalette.getColor(iterations, maxIter);
                image.setRGB(x, y, color.getRGB());
            }
        });
    }

    public void animateToInitialView() {
        animateTo(-0.5,0,ViewState.INITIAL_SCALE,100);
    }

    public void animate(int caso) {
        viewState.set(-0.5,0,ViewState.INITIAL_SCALE);
        switch (caso) {
            case 0:
                animateTo(-0.743643,0.131825,0.000000003,100);
                break;
            case 1:
                animateTo(-0.17033739669900003,-1.0650603561,9.900000000000033E-11,100);
                break;
            case 2:
                animateTo(0.42883621390396764,-0.23134282255078162,3.949805525581182E-12,100);
                break;
            case 3:
                animateTo(-1.943035808586426,9.194024224049161E-16,7.746945919291003E-17,100);
                break;
            case 4:
                animateTo(-0.761575333854055,-0.08475973655846687,9.20864502069162E-16,100);
                break;
        }  
    }

    private void animateTo(double centerX, double centerY, double scale,int frames) {
        double dx = (centerX - viewState.getCenterX()) / frames;
        double dy = (centerY - viewState.getCenterY()) / frames;
        double ds = (scale - viewState.getScale()) / frames;
        for (int i = 0; i < frames; i++) {
            viewState.move(dx,dy,ds);
            requestRender();
            
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }

            this.paintImmediately(0, 0, getWidth(), getHeight());
        }

    }
}
