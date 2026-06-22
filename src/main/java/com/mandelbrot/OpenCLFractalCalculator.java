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
    private static final String KERNEL_SOURCE =
        "#pragma OPENCL EXTENSION cl_khr_fp64 : enable\n"
        + "\n"
        + "__kernel void mandelbrot(\n"
        + "    __global int* result,\n"
        + "    const int    width,\n"
        + "    const int    height,\n"
        + "    const int    py,\n"
        + "    const double centerX,\n"
        + "    const double centerY,\n"
        + "    const double scale,\n"
        + "    const int    maxIterations)\n"
        + "{\n"
        + "    int px = get_global_id(0);\n"
        + "    if (px >= width) return;\n"
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
        + "    result[px] = iter;\n"
        + "}\n"
        + "\n"
        + "__kernel void julia(\n"
        + "    __global int* result,\n"
        + "    const int    width,\n"
        + "    const int    height,\n"
        + "    const int    py,\n"
        + "    const double centerX,\n"
        + "    const double centerY,\n"
        + "    const double scale,\n"
        + "    const double juliaReal,\n"
        + "    const double juliaImag,\n"
        + "    const int    maxIterations)\n"
        + "{\n"
        + "    int px = get_global_id(0);\n"
        + "    if (px >= width) return;\n"
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
        + "    result[px] = iter;\n"
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
    public synchronized int[] computeMandelbrot(int y, int width, int height, ViewState view) {
        if (!initialized) {
            return cpuFallback().computeMandelbrot(y, width, height, view);
        }
        int[]  row          = new int[width];
        cl_mem resultBuffer = clCreateBuffer(
                context, CL_MEM_WRITE_ONLY, (long) width * Sizeof.cl_int, null, null);
        try {
            clSetKernelArg(mandelbrotKernel, 0, Sizeof.cl_mem,    Pointer.to(resultBuffer));
            clSetKernelArg(mandelbrotKernel, 1, Sizeof.cl_int,    Pointer.to(new int[]{width}));
            clSetKernelArg(mandelbrotKernel, 2, Sizeof.cl_int,    Pointer.to(new int[]{height}));
            clSetKernelArg(mandelbrotKernel, 3, Sizeof.cl_int,    Pointer.to(new int[]{y}));
            clSetKernelArg(mandelbrotKernel, 4, Sizeof.cl_double, Pointer.to(new double[]{view.getCenterX()}));
            clSetKernelArg(mandelbrotKernel, 5, Sizeof.cl_double, Pointer.to(new double[]{view.getCenterY()}));
            clSetKernelArg(mandelbrotKernel, 6, Sizeof.cl_double, Pointer.to(new double[]{view.getScale()}));
            clSetKernelArg(mandelbrotKernel, 7, Sizeof.cl_int,    Pointer.to(new int[]{maxIterations}));

            clEnqueueNDRangeKernel(commandQueue, mandelbrotKernel, 1,
                    null, new long[]{width}, null, 0, null, null);
            clEnqueueReadBuffer(commandQueue, resultBuffer, CL_TRUE, 0,
                    (long) width * Sizeof.cl_int, Pointer.to(row), 0, null, null);
        } finally {
            clReleaseMemObject(resultBuffer);
        }
        return row;
    }

    @Override
    public synchronized int[] computeJulia(int y, int width, int height, ViewState view) {
        if (!initialized) {
            return cpuFallback().computeJulia(y, width, height, view);
        }
        double jr = juliaReal;
        double ji = juliaImaginary;

        int[]  row          = new int[width];
        cl_mem resultBuffer = clCreateBuffer(
                context, CL_MEM_WRITE_ONLY, (long) width * Sizeof.cl_int, null, null);
        try {
            clSetKernelArg(juliaKernel, 0, Sizeof.cl_mem,    Pointer.to(resultBuffer));
            clSetKernelArg(juliaKernel, 1, Sizeof.cl_int,    Pointer.to(new int[]{width}));
            clSetKernelArg(juliaKernel, 2, Sizeof.cl_int,    Pointer.to(new int[]{height}));
            clSetKernelArg(juliaKernel, 3, Sizeof.cl_int,    Pointer.to(new int[]{y}));
            clSetKernelArg(juliaKernel, 4, Sizeof.cl_double, Pointer.to(new double[]{view.getCenterX()}));
            clSetKernelArg(juliaKernel, 5, Sizeof.cl_double, Pointer.to(new double[]{view.getCenterY()}));
            clSetKernelArg(juliaKernel, 6, Sizeof.cl_double, Pointer.to(new double[]{view.getScale()}));
            clSetKernelArg(juliaKernel, 7, Sizeof.cl_double, Pointer.to(new double[]{jr}));
            clSetKernelArg(juliaKernel, 8, Sizeof.cl_double, Pointer.to(new double[]{ji}));
            clSetKernelArg(juliaKernel, 9, Sizeof.cl_int,    Pointer.to(new int[]{maxIterations}));

            clEnqueueNDRangeKernel(commandQueue, juliaKernel, 1,
                    null, new long[]{width}, null, 0, null, null);
            clEnqueueReadBuffer(commandQueue, resultBuffer, CL_TRUE, 0,
                    (long) width * Sizeof.cl_int, Pointer.to(row), 0, null, null);
        } finally {
            clReleaseMemObject(resultBuffer);
        }
        return row;
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

}
