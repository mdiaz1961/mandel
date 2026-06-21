package com.mandelbrot;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Point;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.ItemEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;

import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.JSplitPane;
import javax.swing.SwingUtilities;

/**
 * Contenedor principal que divide la pantalla entre el conjunto de Mandelbrot
 * y un panel opcional de Julia a la derecha.
 * <p>
 * Al activar Julia, el parámetro {@code c} se inicializa con las coordenadas
 * complejas bajo el cursor del panel de Mandelbrot. Mientras Julia esté visible,
 * {@code c} se actualiza al mover el ratón sobre Mandelbrot con Control presionado.
 * </p>
 *
 * @author Mandelbrot Explorer
 * @version 1.0
 */
public class ExplorerPanel extends JPanel {

    /** Paleta de colores compartida entre ambos paneles. */
    private final ColorPalette colorPalette = new ColorPalette();

    /** Panel del conjunto de Mandelbrot (izquierda). */
    private final FractalViewPanel mandelbrotPanel = new FractalViewPanel(FractalType.MANDELBROT, colorPalette);

    /** Panel del conjunto de Julia (derecha). */
    private final FractalViewPanel juliaPanel = new FractalViewPanel(FractalType.JULIA, colorPalette);

    /** Contenedor central que alterna entre vista simple y vista dividida. */
    private final JPanel contentPanel = new JPanel(new BorderLayout());

    /** Divisor horizontal entre Mandelbrot y Julia; solo se usa cuando Julia está activo. */
    private JSplitPane splitPane;

    /** Indica si el panel de Julia está visible. */
    private boolean juliaVisible = false;

    /** Opción del menú para activar o desactivar Julia. */
    private JCheckBoxMenuItem juliaMenuItem;

    /** Último parámetro {@code c} enviado al panel de Julia. */
    private double lastJuliaReal = Double.NaN;

    /** Última parte imaginaria de {@code c} enviada al panel de Julia. */
    private double lastJuliaImaginary = Double.NaN;

    /**
     * Construye el explorador con Mandelbrot a pantalla completa y Julia oculto.
     */
    public ExplorerPanel() {
        setLayout(new BorderLayout());
        contentPanel.add(mandelbrotPanel, BorderLayout.CENTER);
        add(contentPanel, BorderLayout.CENTER);

        JPopupMenu popupMenu = createPopupMenu();
        setComponentPopupMenu(popupMenu);
        mandelbrotPanel.setComponentPopupMenu(popupMenu);
        juliaPanel.setComponentPopupMenu(popupMenu);
        mandelbrotPanel.setComplexCoordinateListener(this::onMandelbrotMouseCoordinate);
        registerKeyListeners();

        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent event) {
                refreshVisiblePanels();
            }
        });
    }

    /**
     * Actualiza el tamaño y renderizado de los paneles visibles.
     */
    private void refreshVisiblePanels() {
        if (juliaVisible) {
            mandelbrotPanel.refreshImageSize();
            juliaPanel.refreshImageSize();
        } else {
            mandelbrotPanel.refreshImageSize();
        }
    }

    /**
     * Actualiza el conjunto de Julia cuando el cursor se mueve sobre Mandelbrot.
     *
     * @param real            parte real bajo el cursor
     * @param imaginary       parte imaginaria bajo el cursor
     * @param controlPressed  indica si Control está presionado
     */
    private void onMandelbrotMouseCoordinate(double real, double imaginary, boolean controlPressed) {
        if (!juliaVisible || !controlPressed) {
            return;
        }
        if (real == lastJuliaReal && imaginary == lastJuliaImaginary) {
            return;
        }
        lastJuliaReal = real;
        lastJuliaImaginary = imaginary;
        juliaPanel.updateJuliaConstant(real, imaginary);
    }

    /**
     * Registra el listener de teclado para salir con Escape.
     */
    private void registerKeyListeners() {
        setFocusable(true);
        addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent event) {
                if (event.getKeyCode() == KeyEvent.VK_ESCAPE) {
                    System.exit(0);
                }
            }
        });
    }

    /**
     * Crea el menú contextual con opciones de Julia y paleta de colores.
     *
     * @return menú emergente
     */
    private JPopupMenu createPopupMenu() {
        JPopupMenu popupMenu = new JPopupMenu("Opciones");

        popupMenu.add(createMenuLabel("Fractal"));
        juliaMenuItem = new JCheckBoxMenuItem("Mostrar Julia", false);
        juliaMenuItem.addItemListener(event -> {
            boolean seleccionado = (event.getStateChange() == ItemEvent.SELECTED);
            setJuliaVisible(seleccionado);
        });
        popupMenu.add(juliaMenuItem);

        for (int i=0;i<5;i++) {
            JMenuItem item = new JMenuItem("Animar a fractal " + (i+1));
            final int j=i;
            item.addActionListener(event -> {
                mandelbrotPanel.animate(j);
            });
            popupMenu.add(item);
        }

        JMenuItem backMenuItem = new JMenuItem("Regresar a vista inicial");
        backMenuItem.addActionListener(event -> {
            mandelbrotPanel.animateToInitialView();
        });
        popupMenu.add(backMenuItem);
        popupMenu.addSeparator();

        ButtonGroup paletteGroup = new ButtonGroup();
        popupMenu.add(createMenuLabel("Paleta de colores"));
        for (PaletteType type : PaletteType.values()) {
            JRadioButtonMenuItem item = new JRadioButtonMenuItem(type.getDisplayName());
            item.setSelected(type == colorPalette.getPaletteType());
            item.addActionListener(event -> {
                colorPalette.setPaletteType(type);
                mandelbrotPanel.requestRender();
                if (juliaVisible) {
                    juliaPanel.requestRender();
                }
            });
            paletteGroup.add(item);
            popupMenu.add(item);
        }

        return popupMenu;
    }

    private JMenuItem createMenuLabel(String text) {
        JMenuItem label = new JMenuItem(text);
        label.setEnabled(false);
        return label;
    }

    /**
     * Muestra u oculta el panel de Julia a la derecha.
     *
     * @param visible {@code true} para dividir la pantalla y mostrar Julia
     */
    public void setJuliaVisible(boolean visible) {
        if (visible == juliaVisible) {
            return;
        }

        juliaVisible = visible;

        if (!visible) {
            lastJuliaReal = Double.NaN;
            lastJuliaImaginary = Double.NaN;
        } else {
            initializeJuliaFromMandelbrotMouse();
        }

        applyJuliaVisibility(visible);
    }

    /**
     * Aplica el estado visual del panel de Julia.
     *
     * @param visible {@code true} para mostrar Julia a la derecha
     */
    private void applyJuliaVisibility(boolean visible) {
        contentPanel.removeAll();

        if (visible) {
            mandelbrotPanel.setMinimumSize(new Dimension(120, 0));
            juliaPanel.setMinimumSize(new Dimension(120, 0));

            splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, mandelbrotPanel, juliaPanel);
            splitPane.setResizeWeight(0.5);
            splitPane.setDividerSize(10);
            splitPane.setContinuousLayout(true);
            splitPane.setOneTouchExpandable(false);
            splitPane.setBackground(new Color(40, 40, 40));
            splitPane.setBorder(BorderFactory.createMatteBorder(0, 1, 0, 1, new Color(160, 160, 160)));
            splitPane.setComponentPopupMenu(createPopupMenu());
            registerSplitPaneListener(splitPane);
            contentPanel.add(splitPane, BorderLayout.CENTER);
            contentPanel.revalidate();
            contentPanel.repaint();

            SwingUtilities.invokeLater(() -> {
                splitPane.setDividerLocation(0.5);
                refreshVisiblePanels();
            });
        } else {
            splitPane = null;
            contentPanel.add(mandelbrotPanel, BorderLayout.CENTER);
            contentPanel.revalidate();
            contentPanel.repaint();
            SwingUtilities.invokeLater(mandelbrotPanel::requestRender);
        }
    }

    /**
     * Registra un listener para actualizar las imágenes al mover el divisor (slider).
     *
     * @param pane divisor entre Mandelbrot y Julia
     */
    private void registerSplitPaneListener(JSplitPane pane) {
        PropertyChangeListener dividerListener = new PropertyChangeListener() {
            @Override
            public void propertyChange(PropertyChangeEvent event) {
                if (JSplitPane.DIVIDER_LOCATION_PROPERTY.equals(event.getPropertyName())) {
                    refreshVisiblePanels();
                }
            }
        };
        pane.addPropertyChangeListener(dividerListener);
    }

    /**
     * Solicita el renderizado inicial de Mandelbrot tras mostrar la ventana.
     */
    public void requestInitialRender() {
        mandelbrotPanel.requestRender();
    }

    /**
     * Inicializa el conjunto de Julia con las coordenadas bajo el cursor de Mandelbrot.
     * Si el cursor no está sobre el panel, usa el centro de la vista.
     */
    private void initializeJuliaFromMandelbrotMouse() {
        Point mouse = mandelbrotPanel.getMousePosition();
        double cReal;
        double cImaginary;

        if (mouse != null) {
            cReal = mandelbrotPanel.getComplexReal(mouse.x);
            cImaginary = mandelbrotPanel.getComplexImaginary(mouse.y);
        } else {
            int width = Math.max(mandelbrotPanel.getWidth(), 1);
            int height = Math.max(mandelbrotPanel.getHeight(), 1);
            cReal = mandelbrotPanel.getComplexReal(width / 2);
            cImaginary = mandelbrotPanel.getComplexImaginary(height / 2);
        }

        juliaPanel.setJuliaConstant(cReal, cImaginary);
    }
}
