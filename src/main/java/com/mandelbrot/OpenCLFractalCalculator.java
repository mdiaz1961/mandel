package com.mandelbrot;

import org.jocl.CL;
import org.jocl.CLException;
import org.jocl.Pointer;
import org.jocl.Sizeof;
import org.jocl.cl_command_queue;
import org.jocl.cl_context;
import org.jocl.cl_device_id;
import org.jocl.cl_kernel;
import org.jocl.cl_mem;
import org.jocl.cl_platform_id;
import org.jocl.cl_program;

import java.util.logging.Level;
import java.util.logging.Logger;

import static org.jocl.CL.*;

/**
 * Implementación de {@link FractalCalculator} que ejecuta los cálculos en la GPU
 * mediante OpenCL a través de la librería JOCL.
 *
 * <h3>Dependencia requerida</h3>
 * <p>
 * Añadir al classpath el JAR de JOCL (Maven: {@code org.jocl:jocl:2.0.5}) junto con
 * la librería nativa correspondiente al sistema operativo. También se necesitan los
 * drivers OpenCL del fabricante de la GPU instalados en el sistema.
 * </p>
 *
 * <h3>Fallback automático</h3>
 * <p>
 * Si OpenCL no está disponible o la inicialización falla, cada llamada a
 * {@code compute*} delega silenciosamente en {@link MandelbrotCalculator} (CPU).
 * </p>
 *
 * @author Mandelbrot Explorer
 * @version 1.0
 */
public class OpenCLFractalCalculator implements FractalCalculator {

    private static final Logger LOGGER =
            Logger.getLogger(OpenCLFractalCalculator.class.getName());

    private static final int DEFAULT_MAX_ITERATIONS = 500;

    // Kernel OpenCL con precisión doble (requiere extensión cl_khr_fp64).
    // Dos kernels en la misma fuente: "mandelbrot" y "julia".
    // Kernels 2D: get_global_id(0)=lx (col en region), get_global_id(1)=ly (fila en region).
    // px = xIni + lx,  py = yIni + ly  →  resultado en result[ly * regionW + lx].
    private static final String KERNEL_SOURCE =
        "#pragma OPENCL EXTENSION cl_khr_fp64 : enable\n"
        + "\n"
        + "__kernel void mandelbrot(\n"
        + "    __global int* result,\n"
        + "    const int    xIni,\n"
        + "    const int    yIni,\n"
        + "    const int    regionW,\n"
        + "    const int    width,\n"
        + "    const int    height,\n"
        + "    const double centerX,\n"
        + "    const double centerY,\n"
        + "    const double scale,\n"
        + "    const int    maxIterations)\n"
        + "{\n"
        + "    int lx = get_global_id(0);\n"
        + "    int ly = get_global_id(1);\n"
        + "    int px = xIni + lx;\n"
        + "    int py = yIni + ly;\n"
        + "    double cReal = centerX + (px - width  * 0.5) * scale;\n"
        + "    double cImag = centerY + (py - height * 0.5) * scale;\n"
        + "    double zr = 0.0, zi = 0.0;\n"
        + "    int iter = 0;\n"
        + "    while (iter < maxIterations) {\n"
        + "        double zr2 = zr * zr;\n"
        + "        double zi2 = zi * zi;\n"
        + "        if (zr2 + zi2 > 4.0) break;\n"
        + "        zi = 2.0 * zr * zi + cImag;\n"
        + "        zr = zr2 - zi2 + cReal;\n"
        + "        iter++;\n"
        + "    }\n"
        + "    result[ly * regionW + lx] = iter;\n"
        + "}\n"
        + "\n"
        + "__kernel void julia(\n"
        + "    __global int* result,\n"
        + "    const int    xIni,\n"
        + "    const int    yIni,\n"
        + "    const int    regionW,\n"
        + "    const int    width,\n"
        + "    const int    height,\n"
        + "    const double centerX,\n"
        + "    const double centerY,\n"
        + "    const double scale,\n"
        + "    const double juliaReal,\n"
        + "    const double juliaImag,\n"
        + "    const int    maxIterations)\n"
        + "{\n"
        + "    int lx = get_global_id(0);\n"
        + "    int ly = get_global_id(1);\n"
        + "    int px = xIni + lx;\n"
        + "    int py = yIni + ly;\n"
        + "    double zr = centerX + (px - width  * 0.5) * scale;\n"
        + "    double zi = centerY + (py - height * 0.5) * scale;\n"
        + "    int iter = 0;\n"
        + "    while (iter < maxIterations) {\n"
        + "        double zr2 = zr * zr;\n"
        + "        double zi2 = zi * zi;\n"
        + "        if (zr2 + zi2 > 4.0) break;\n"
        + "        zi = 2.0 * zr * zi + juliaImag;\n"
        + "        zr = zr2 - zi2 + juliaReal;\n"
        + "        iter++;\n"
        + "    }\n"
        + "    result[ly * regionW + lx] = iter;\n"
        + "}\n";

    private final int maxIterations;
    private volatile double juliaReal;
    private volatile double juliaImaginary;

    private cl_context      context;
    private cl_command_queue commandQueue;
    private cl_program      program;
    private cl_kernel       mandelbrotKernel;
    private cl_kernel       juliaKernel;
    private boolean         initialized;

    /** Crea un calculador GPU con el máximo de iteraciones por defecto. */
    public OpenCLFractalCalculator() {
        this(DEFAULT_MAX_ITERATIONS);
    }

    /**
     * Crea un calculador GPU con un límite personalizado de iteraciones.
     *
     * @param maxIterations número máximo de iteraciones
     */
    public OpenCLFractalCalculator(int maxIterations) {
        this.maxIterations = maxIterations;
        initOpenCL();
    }

    // -------------------------------------------------------------------------
    // Inicialización OpenCL
    // -------------------------------------------------------------------------

    private void initOpenCL() {
        try {
            CL.setExceptionsEnabled(true);

            int[] numPlatforms = new int[1];
            clGetPlatformIDs(0, null, numPlatforms);
            if (numPlatforms[0] == 0) {
                LOGGER.warning("OpenCL: no se encontraron plataformas.");
                return;
            }

            cl_platform_id[] platforms = new cl_platform_id[numPlatforms[0]];
            clGetPlatformIDs(platforms.length, platforms, null);
            cl_platform_id platform = platforms[0];

            cl_device_id device = findDevice(platform, CL_DEVICE_TYPE_GPU);
            if (device == null) {
                device = findDevice(platform, CL_DEVICE_TYPE_CPU);
            }
            if (device == null) {
                LOGGER.warning("OpenCL: no se encontró ningún dispositivo.");
                return;
            }

            context      = clCreateContext(null, 1, new cl_device_id[]{device}, null, null, null);
            commandQueue = clCreateCommandQueueWithProperties(context, device, null, null);

            program = clCreateProgramWithSource(context, 1, new String[]{KERNEL_SOURCE}, null, null);
            clBuildProgram(program, 0, null, null, null, null);

            mandelbrotKernel = clCreateKernel(program, "mandelbrot", null);
            juliaKernel      = clCreateKernel(program, "julia",      null);

            initialized = true;
            LOGGER.info("OpenCL iniciado en: " + deviceName(device));

        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "OpenCL: error de inicialización — usando fallback CPU. " + e.getMessage(), e);
            initialized = false;
        }
    }

    private cl_device_id findDevice(cl_platform_id platform, long deviceType) {
        try {
            int[] count = new int[1];
            clGetDeviceIDs(platform, deviceType, 0, null, count);
            if (count[0] == 0) return null;
            cl_device_id[] devices = new cl_device_id[count[0]];
            clGetDeviceIDs(platform, deviceType, count[0], devices, null);
            return devices[0];
        } catch (CLException e) {
            return null;
        }
    }

    private String deviceName(cl_device_id device) {
        long[] size = new long[1];
        clGetDeviceInfo(device, CL_DEVICE_NAME, 0, null, size);
        byte[] bytes = new byte[(int) size[0]];
        clGetDeviceInfo(device, CL_DEVICE_NAME, bytes.length, Pointer.to(bytes), null);
        return new String(bytes, 0, bytes.length - 1).trim();
    }

    // -------------------------------------------------------------------------
    // FractalCalculator
    // -------------------------------------------------------------------------

    @Override
    public int getMaxIterations() {
        return maxIterations;
    }

    @Override
    public double getJuliaReal() {
        return juliaReal;
    }

    @Override
    public double getJuliaImaginary() {
        return juliaImaginary;
    }

    @Override
    public void setJuliaConstant(double real, double imaginary) {
        this.juliaReal      = real;
        this.juliaImaginary = imaginary;
    }

    @Override
    public synchronized int[][] computeMandelbrot(int xIni, int xFin, int yIni, int yFin,
                                                  int width, int height, ViewState view) {
        if (!initialized) {
            return cpuFallback().computeMandelbrot(xIni, xFin, yIni, yFin, width, height, view);
        }
        int rw   = xFin - xIni;
        int rh   = yFin - yIni;
        int size = rw * rh;
        int[] flat = new int[size];
        cl_mem resultBuffer = clCreateBuffer(
                context, CL_MEM_WRITE_ONLY, (long) size * Sizeof.cl_int, null, null);
        try {
            clSetKernelArg(mandelbrotKernel, 0, Sizeof.cl_mem,    Pointer.to(resultBuffer));
            clSetKernelArg(mandelbrotKernel, 1, Sizeof.cl_int,    Pointer.to(new int[]{xIni}));
            clSetKernelArg(mandelbrotKernel, 2, Sizeof.cl_int,    Pointer.to(new int[]{yIni}));
            clSetKernelArg(mandelbrotKernel, 3, Sizeof.cl_int,    Pointer.to(new int[]{rw}));
            clSetKernelArg(mandelbrotKernel, 4, Sizeof.cl_int,    Pointer.to(new int[]{width}));
            clSetKernelArg(mandelbrotKernel, 5, Sizeof.cl_int,    Pointer.to(new int[]{height}));
            clSetKernelArg(mandelbrotKernel, 6, Sizeof.cl_double, Pointer.to(new double[]{view.getCenterX()}));
            clSetKernelArg(mandelbrotKernel, 7, Sizeof.cl_double, Pointer.to(new double[]{view.getCenterY()}));
            clSetKernelArg(mandelbrotKernel, 8, Sizeof.cl_double, Pointer.to(new double[]{view.getScale()}));
            clSetKernelArg(mandelbrotKernel, 9, Sizeof.cl_int,    Pointer.to(new int[]{maxIterations}));

            clEnqueueNDRangeKernel(commandQueue, mandelbrotKernel, 2,
                    null, new long[]{rw, rh}, null, 0, null, null);
            clEnqueueReadBuffer(commandQueue, resultBuffer, CL_TRUE, 0,
                    (long) size * Sizeof.cl_int, Pointer.to(flat), 0, null, null);
        } finally {
            clReleaseMemObject(resultBuffer);
        }
        return flatToMatrix(flat, rw, rh);
    }

    @Override
    public synchronized int[][] computeJulia(int xIni, int xFin, int yIni, int yFin,
                                             int width, int height, ViewState view) {
        if (!initialized) {
            return cpuFallback().computeJulia(xIni, xFin, yIni, yFin, width, height, view);
        }
        double jr = juliaReal;
        double ji = juliaImaginary;
        int rw   = xFin - xIni;
        int rh   = yFin - yIni;
        int size = rw * rh;
        int[] flat = new int[size];
        cl_mem resultBuffer = clCreateBuffer(
                context, CL_MEM_WRITE_ONLY, (long) size * Sizeof.cl_int, null, null);
        try {
            clSetKernelArg(juliaKernel, 0,  Sizeof.cl_mem,    Pointer.to(resultBuffer));
            clSetKernelArg(juliaKernel, 1,  Sizeof.cl_int,    Pointer.to(new int[]{xIni}));
            clSetKernelArg(juliaKernel, 2,  Sizeof.cl_int,    Pointer.to(new int[]{yIni}));
            clSetKernelArg(juliaKernel, 3,  Sizeof.cl_int,    Pointer.to(new int[]{rw}));
            clSetKernelArg(juliaKernel, 4,  Sizeof.cl_int,    Pointer.to(new int[]{width}));
            clSetKernelArg(juliaKernel, 5,  Sizeof.cl_int,    Pointer.to(new int[]{height}));
            clSetKernelArg(juliaKernel, 6,  Sizeof.cl_double, Pointer.to(new double[]{view.getCenterX()}));
            clSetKernelArg(juliaKernel, 7,  Sizeof.cl_double, Pointer.to(new double[]{view.getCenterY()}));
            clSetKernelArg(juliaKernel, 8,  Sizeof.cl_double, Pointer.to(new double[]{view.getScale()}));
            clSetKernelArg(juliaKernel, 9,  Sizeof.cl_double, Pointer.to(new double[]{jr}));
            clSetKernelArg(juliaKernel, 10, Sizeof.cl_double, Pointer.to(new double[]{ji}));
            clSetKernelArg(juliaKernel, 11, Sizeof.cl_int,    Pointer.to(new int[]{maxIterations}));

            clEnqueueNDRangeKernel(commandQueue, juliaKernel, 2,
                    null, new long[]{rw, rh}, null, 0, null, null);
            clEnqueueReadBuffer(commandQueue, resultBuffer, CL_TRUE, 0,
                    (long) size * Sizeof.cl_int, Pointer.to(flat), 0, null, null);
        } finally {
            clReleaseMemObject(resultBuffer);
        }
        return flatToMatrix(flat, rw, rh);
    }

    /** Libera el contexto OpenCL, la cola y los kernels. */
    @Override
    public synchronized void dispose() {
        if (!initialized) return;
        try {
            clReleaseKernel(mandelbrotKernel);
            clReleaseKernel(juliaKernel);
            clReleaseProgram(program);
            clReleaseCommandQueue(commandQueue);
            clReleaseContext(context);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "OpenCL: error al liberar recursos.", e);
        }
        initialized = false;
    }

    // -------------------------------------------------------------------------
    // Helpers privados
    // -------------------------------------------------------------------------

    private MandelbrotCalculator cpuFallback() {
        MandelbrotCalculator calc = new MandelbrotCalculator(maxIterations);
        calc.setJuliaConstant(juliaReal, juliaImaginary);
        return calc;
    }

    private static int[][] flatToMatrix(int[] flat, int rw, int rh) {
        int[][] matrix = new int[rh][rw];
        for (int ry = 0; ry < rh; ry++) {
            System.arraycopy(flat, ry * rw, matrix[ry], 0, rw);
        }
        return matrix;
    }

}
