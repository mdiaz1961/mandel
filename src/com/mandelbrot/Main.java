package com.mandelbrot;

import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;

import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;

/**
 * Punto de entrada de la aplicación del conjunto de Mandelbrot.
 * <p>
 * Crea la ventana principal con el explorador de fractales. La aplicación se abre
 * en pantalla completa; pulsa Escape para salir.
 * </p>
 *
 * @author Mandelbrot Explorer
 * @version 1.0
 */
public class Main {

    /** Título mostrado en la barra de la ventana. */
    private static final String WINDOW_TITLE = "Conjunto de Mandelbrot";

    /**
     * Método principal que inicia la aplicación.
     *
     * @param args argumentos de línea de comandos (no utilizados)
     */
    public static void main(String[] args) {
        SwingUtilities.invokeLater(Main::createAndShowGui);
    }

    /**
     * Construye y muestra la interfaz gráfica en el hilo de eventos de Swing.
     */
    private static void createAndShowGui() {
        JFrame frame = new JFrame(WINDOW_TITLE);
        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        frame.setResizable(true);

        ExplorerPanel explorerPanel = new ExplorerPanel();
        frame.setContentPane(explorerPanel);

        GraphicsDevice screen = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice();

        frame.setExtendedState(JFrame.MAXIMIZED_BOTH);
        frame.setVisible(true);

        explorerPanel.requestFocusInWindow();
        SwingUtilities.invokeLater(explorerPanel::requestInitialRender);
    }
}
