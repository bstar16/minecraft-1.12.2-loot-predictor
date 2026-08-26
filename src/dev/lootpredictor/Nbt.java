package dev.lootpredictor;

import java.io.*;
import java.util.*;
import java.util.zip.GZIPInputStream;

final class Nbt {
    static final class Compound extends LinkedHashMap<String,Object> {
        private static final long serialVersionUID = 1L;
        Compound compound(String key) { Object v = get(key); return v instanceof Compound ? (Compound)v : null; }
        ListTag list(String key) { Object v = get(key); return v instanceof ListTag ? (ListTag)v : null; }
        String string(String key) { Object v = get(key); return v instanceof String ? (String)v : null; }
        Long longNumber(String key) { Object v = get(key); return v instanceof Number ? ((Number)v).longValue() : null; }
        Integer intNumber(String key) { Object v = get(key); return v instanceof Number ? ((Number)v).intValue() : null; }
    }

    static final class ListTag extends ArrayList<Object> {
        private static final long serialVersionUID = 1L;
        final int elementType;
        ListTag(int elementType, int size) { super(size); this.elementType = elementType; }
    }

    static Compound readGzip(File file) throws IOException {
        InputStream in = new GZIPInputStream(new BufferedInputStream(new FileInputStream(file)));
        try { return read(in); } finally { in.close(); }
    }

    static Compound read(InputStream input) throws IOException {
        DataInputStream in = new DataInputStream(new BufferedInputStream(input));
        int type = in.readUnsignedByte();
        if (type != 10) throw new IOException("Root NBT tag is type " + type + ", expected compound");
        in.readUTF();
        return (Compound)readPayload(in, 10, 0);
    }

    private static Object readPayload(DataInput in, int type, int depth) throws IOException {
        if (depth > 512) throw new IOException("NBT nesting is too deep");
        switch (type) {
            case 0: return null;
            case 1: return in.readByte();
            case 2: return in.readShort();
            case 3: return in.readInt();
            case 4: return in.readLong();
            case 5: return in.readFloat();
            case 6: return in.readDouble();
            case 7: { int n = length(in); byte[] a = new byte[n]; in.readFully(a); return a; }
            case 8: return in.readUTF();
            case 9: {
                int child = in.readUnsignedByte(), n = length(in);
                ListTag list = new ListTag(child, n);
                for (int i = 0; i < n; i++) list.add(readPayload(in, child, depth + 1));
                return list;
            }
            case 10: {
                Compound compound = new Compound();
                while (true) {
                    int child = in.readUnsignedByte();
                    if (child == 0) return compound;
                    String name = in.readUTF();
                    compound.put(name, readPayload(in, child, depth + 1));
                }
            }
            case 11: { int n = length(in); int[] a = new int[n]; for (int i = 0; i < n; i++) a[i] = in.readInt(); return a; }
            case 12: { int n = length(in); long[] a = new long[n]; for (int i = 0; i < n; i++) a[i] = in.readLong(); return a; }
            default: throw new IOException("Unknown NBT tag type " + type);
        }
    }

    private static int length(DataInput in) throws IOException {
        int n = in.readInt();
        if (n < 0 || n > 64 * 1024 * 1024) throw new IOException("Invalid NBT array/list length " + n);
        return n;
    }
}
