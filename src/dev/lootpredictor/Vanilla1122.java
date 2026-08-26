package dev.lootpredictor;

import java.io.*;
import java.lang.reflect.*;
import java.net.*;
import java.security.MessageDigest;
import java.util.*;

final class Vanilla1122 implements Closeable {
    static final String SERVER_SHA1 = "886945bfb2b978778c3a0288fd7fab09d315b25f";
    static final String SERVER_URL = "https://piston-data.mojang.com/v1/objects/886945bfb2b978778c3a0288fd7fab09d315b25f/server.jar";
    private final URLClassLoader loader;
    private final Class<?> managerClass, resourceClass, contextClass, tableClass, inventoryClass, stackClass, itemClass;
    private final Object manager, context, emptyStack, registry;
    private final Method getTable, fillTable, stackItem, stackCount, stackMeta, stackTag, registryName;

    Vanilla1122(File serverJar) throws Exception {
        verify(serverJar);
        loader = new URLClassLoader(new URL[]{serverJar.toURI().toURL()}, Vanilla1122.class.getClassLoader());
        Thread.currentThread().setContextClassLoader(loader);
        quietBootstrap(loader);
        managerClass = Class.forName("bfv", true, loader);
        resourceClass = Class.forName("nf", true, loader);
        contextClass = Class.forName("bft", true, loader);
        tableClass = Class.forName("bfs", true, loader);
        inventoryClass = Class.forName("tv", true, loader);
        stackClass = Class.forName("aip", true, loader);
        itemClass = Class.forName("ain", true, loader);
        manager = managerClass.getConstructor(File.class).newInstance((File)null);
        context = findConstructor(contextClass, 6).newInstance(0.0f, null, manager, null, null, null);
        emptyStack = stackClass.getField("a").get(null);
        registry = itemClass.getField("g").get(null);
        getTable = managerClass.getMethod("a", resourceClass);
        fillTable = tableClass.getMethod("a", inventoryClass, Random.class, contextClass);
        stackItem = stackClass.getMethod("c");
        stackCount = stackClass.getMethod("E");
        stackMeta = stackClass.getMethod("j");
        stackTag = stackClass.getMethod("p");
        registryName = findRegistryName(registry.getClass());
    }

    List<Stack> fill(String tableName, long seed, int slotCount) throws Exception {
        if (slotCount < 1 || slotCount > 512) throw new IllegalArgumentException("Container slot count must be 1..512");
        final Object[] slots = new Object[slotCount];
        Arrays.fill(slots, emptyStack);
        Object inventory = java.lang.reflect.Proxy.newProxyInstance(loader, new Class<?>[]{inventoryClass}, new InvocationHandler() {
            public Object invoke(Object proxy, Method method, Object[] args) {
                String n = method.getName();
                if (n.equals("w_") && method.getParameterTypes().length == 0) return slots.length;
                if (n.equals("a") && method.getParameterTypes().length == 1 && method.getParameterTypes()[0] == int.class) return slots[(Integer)args[0]];
                if (n.equals("a") && method.getParameterTypes().length == 2 && method.getParameterTypes()[0] == int.class && method.getReturnType() == void.class) { slots[(Integer)args[0]] = args[1]; return null; }
                if (method.getReturnType() == boolean.class) return false;
                if (method.getReturnType() == int.class) return 0;
                return null;
            }
        });
        Object resource = resourceClass.getConstructor(String.class).newInstance(Main.normalizeTable(tableName));
        Object table = getTable.invoke(manager, resource);
        fillTable.invoke(table, inventory, new Random(seed), context);
        List<Stack> result = new ArrayList<Stack>();
        for (int slot = 0; slot < slots.length; slot++) {
            Object value = slots[slot];
            if (value == emptyStack) continue;
            int count = (Integer)stackCount.invoke(value);
            if (count <= 0) continue;
            Object item = stackItem.invoke(value);
            Object name = registryName.invoke(registry, item);
            int meta = (Integer)stackMeta.invoke(value);
            Object tag = stackTag.invoke(value);
            result.add(new Stack(slot, String.valueOf(name), count, meta, tag == null ? null : String.valueOf(tag)));
        }
        return result;
    }

    private static Constructor<?> findConstructor(Class<?> type, int parameters) {
        for (Constructor<?> c : type.getConstructors()) if (c.getParameterTypes().length == parameters) return c;
        throw new IllegalStateException("Expected constructor not found in " + type.getName());
    }

    private static Method findRegistryName(Class<?> type) {
        for (Method m : type.getMethods()) {
            if (m.getName().equals("b") && m.getParameterTypes().length == 1 && m.getParameterTypes()[0] == Object.class) return m;
        }
        throw new IllegalStateException("Minecraft item registry method not found");
    }

    private static void quietBootstrap(ClassLoader loader) throws Exception {
        PrintStream oldOut = System.out, oldErr = System.err;
        PrintStream sink = new PrintStream(new OutputStream() { public void write(int b) { } });
        try {
            URL quietLog = Vanilla1122.class.getResource("/log4j2-lootpredictor.xml");
            if (quietLog != null) System.setProperty("log4j.configurationFile", quietLog.toExternalForm());
            System.setOut(sink);
            System.setErr(sink);
            Class.forName("ni", true, loader).getMethod("c").invoke(null);
        } finally {
            System.setOut(oldOut);
            System.setErr(oldErr);
            sink.close();
        }
    }

    static File resolveOrSetup(File explicit) throws Exception {
        if (explicit != null) { verify(explicit); return explicit.getCanonicalFile(); }
        File bundled = defaultJar();
        if (bundled.isFile()) { verify(bundled); return bundled.getCanonicalFile(); }
        return setup(null);
    }

    static File setup(File supplied) throws Exception {
        if (supplied != null) { verify(supplied); return supplied.getCanonicalFile(); }
        File target = defaultJar();
        File parent = target.getParentFile();
        if (!parent.isDirectory() && !parent.mkdirs()) throw new IOException("Could not create " + parent);
        File temp = new File(parent, target.getName() + ".download");
        System.out.println("Downloading Mojang's official Minecraft 1.12.2 server jar...");
        URLConnection connection = new URL(SERVER_URL).openConnection();
        connection.setConnectTimeout(20000);
        connection.setReadTimeout(60000);
        InputStream in = connection.getInputStream();
        OutputStream out = new FileOutputStream(temp);
        try { copy(in, out); } finally { try { in.close(); } finally { out.close(); } }
        verify(temp);
        if (target.exists() && !target.delete()) throw new IOException("Could not replace " + target);
        if (!temp.renameTo(target)) throw new IOException("Could not move verified download to " + target);
        return target.getCanonicalFile();
    }

    private static File defaultJar() throws Exception {
        File here = new File(Main.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        File base = here.isFile() ? here.getParentFile() : here;
        return new File(new File(base, "runtime"), "minecraft_server.1.12.2.jar");
    }

    static void verify(File file) throws Exception {
        if (file == null || !file.isFile()) throw new IllegalArgumentException("Minecraft 1.12.2 server jar not found: " + file);
        MessageDigest digest = MessageDigest.getInstance("SHA-1");
        InputStream in = new FileInputStream(file);
        try {
            byte[] buffer = new byte[65536]; int n;
            while ((n = in.read(buffer)) >= 0) if (n > 0) digest.update(buffer, 0, n);
        } finally { in.close(); }
        StringBuilder hex = new StringBuilder();
        for (byte b : digest.digest()) hex.append(String.format("%02x", b & 255));
        if (!SERVER_SHA1.equals(hex.toString())) throw new IllegalArgumentException("Jar SHA-1 is " + hex + ", expected Mojang 1.12.2 server " + SERVER_SHA1);
    }

    private static void copy(InputStream in, OutputStream out) throws IOException {
        byte[] buffer = new byte[65536]; int n;
        while ((n = in.read(buffer)) >= 0) if (n > 0) out.write(buffer, 0, n);
    }

    public void close() throws IOException { loader.close(); }

    static final class Stack {
        final int slot, count, meta;
        final String id, nbt;
        Stack(int slot, String id, int count, int meta, String nbt) { this.slot = slot; this.id = id; this.count = count; this.meta = meta; this.nbt = nbt; }
        String stable() { return slot + ":" + id + ":" + count + ":" + meta + ":" + nbt; }
    }
}
