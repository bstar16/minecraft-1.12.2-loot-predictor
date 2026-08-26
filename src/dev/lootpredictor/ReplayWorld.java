package dev.lootpredictor;

import java.io.*;
import java.util.concurrent.*;

final class ReplayWorld {
    static File generateSpawn(File serverJar, long seed, File output) throws Exception {
        if (output.exists()) {
            String[] children = output.list();
            if (!output.isDirectory() || (children != null && children.length != 0)) throw new IllegalArgumentException("--output must be a new or empty folder: " + output);
        } else if (!output.mkdirs()) throw new IOException("Could not create " + output);
        write(new File(output, "eula.txt"), "# Accepted explicitly through Minecraft Loot Predictor\neula=true\n");
        write(new File(output, "server.properties"),
            "level-name=world\nlevel-seed=" + seed + "\nlevel-type=DEFAULT\ngenerate-structures=true\n" +
            "online-mode=false\nserver-port=0\nmax-tick-time=-1\nview-distance=10\nallow-nether=true\nspawn-protection=0\n");
        String javaExe = new File(new File(System.getProperty("java.home"), "bin"), isWindows() ? "java.exe" : "java").getAbsolutePath();
        ProcessBuilder builder = new ProcessBuilder(javaExe, "-Xms512M", "-Xmx1G", "-jar", serverJar.getAbsolutePath(), "nogui");
        builder.directory(output);
        builder.redirectErrorStream(true);
        Process process = builder.start();
        BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), "UTF-8"));
        BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(process.getOutputStream(), "UTF-8"));
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<Boolean> ready = executor.submit(new Callable<Boolean>() {
            public Boolean call() throws Exception {
                String line;
                while ((line = reader.readLine()) != null) {
                    System.out.println("[vanilla] " + line);
                    if (line.contains("Done (") || line.contains("Done (")) return true;
                    if (line.contains("FAILED TO BIND TO PORT") || line.contains("You need to agree to the EULA")) return false;
                }
                return false;
            }
        });
        boolean started;
        try { started = ready.get(180, TimeUnit.SECONDS); }
        catch (TimeoutException e) { process.destroy(); throw new IOException("Vanilla server did not finish spawn generation within 180 seconds"); }
        if (!started) {
            process.destroy();
            throw new IOException("Vanilla server exited before spawn generation completed; see log above and " + new File(output, "logs/latest.log"));
        }
        writer.write("stop\n"); writer.flush();
        if (!process.waitFor(90, TimeUnit.SECONDS)) { process.destroy(); throw new IOException("Vanilla server did not stop cleanly within 90 seconds"); }
        executor.shutdownNow();
        if (process.exitValue() != 0) throw new IOException("Vanilla server exited with code " + process.exitValue());
        File world = new File(output, "world");
        if (!new File(world, "level.dat").isFile()) throw new IOException("Generated world level.dat was not saved");
        return world;
    }

    private static void write(File file, String text) throws IOException {
        Writer writer = new OutputStreamWriter(new FileOutputStream(file), "UTF-8");
        try { writer.write(text); } finally { writer.close(); }
    }
    private static boolean isWindows() { return System.getProperty("os.name", "").toLowerCase().contains("win"); }
}
