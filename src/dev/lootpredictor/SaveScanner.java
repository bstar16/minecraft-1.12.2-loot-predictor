package dev.lootpredictor;

import java.io.*;
import java.util.*;
import java.util.zip.*;

final class SaveScanner {
    private final File save;
    SaveScanner(File save) { this.save = save; }

    Result scan() throws IOException {
        if (!save.isDirectory()) throw new IllegalArgumentException("Save folder does not exist: " + save);
        Result result = new Result();
        File level = new File(save, "level.dat");
        if (level.isFile()) {
            try {
                Nbt.Compound root = Nbt.readGzip(level);
                Nbt.Compound data = root.compound("Data");
                if (data != null) result.worldSeed = data.longNumber("RandomSeed");
            } catch (Exception e) { result.warnings.add("Could not read level.dat: " + e.getMessage()); }
        } else result.warnings.add("level.dat was not found");

        scanDimension(new File(save, "region"), "overworld", result);
        scanDimension(new File(new File(save, "DIM-1"), "region"), "nether", result);
        scanDimension(new File(new File(save, "DIM1"), "region"), "end", result);
        Collections.sort(result.containers, new Comparator<Container>() {
            public int compare(Container a, Container b) {
                int d = a.dimension.compareTo(b.dimension); if (d != 0) return d;
                d = Integer.compare(a.chunkX, b.chunkX); if (d != 0) return d;
                d = Integer.compare(a.chunkZ, b.chunkZ); if (d != 0) return d;
                return a.position.compareTo(b.position);
            }
        });
        return result;
    }

    private void scanDimension(File regionDir, String dimension, Result result) {
        if (!regionDir.isDirectory()) return;
        File[] files = regionDir.listFiles(new FilenameFilter() { public boolean accept(File dir, String name) { return name.endsWith(".mca"); } });
        if (files == null) return;
        Arrays.sort(files);
        for (File file : files) {
            try { scanRegion(file, dimension, result); }
            catch (Exception e) { result.warnings.add(file.getName() + ": " + e.getMessage()); }
        }
    }

    private void scanRegion(File file, String dimension, Result result) throws IOException {
        RandomAccessFile region = new RandomAccessFile(file, "r");
        try {
            if (region.length() < 8192) throw new IOException("region file is shorter than its header");
            for (int index = 0; index < 1024; index++) {
                region.seek(index * 4L);
                int location = region.readInt();
                int sector = location >>> 8, sectorCount = location & 255;
                if (sector == 0 || sectorCount == 0) continue;
                long offset = sector * 4096L;
                if (offset + 5 > region.length()) { result.warnings.add(file.getName() + " chunk index " + index + " points outside file"); continue; }
                try {
                    region.seek(offset);
                    int length = region.readInt();
                    int compression = region.readUnsignedByte();
                    if (length <= 1 || length > sectorCount * 4096 - 4) throw new IOException("invalid chunk length " + length);
                    byte[] compressed = new byte[length - 1];
                    region.readFully(compressed);
                    InputStream raw = new ByteArrayInputStream(compressed);
                    InputStream decoded;
                    if (compression == 1) decoded = new GZIPInputStream(raw);
                    else if (compression == 2) decoded = new InflaterInputStream(raw);
                    else throw new IOException("unsupported compression type " + compression);
                    Nbt.Compound root = Nbt.read(decoded);
                    inspectChunk(root, dimension, file.getName(), result);
                } catch (Exception e) {
                    result.warnings.add(file.getName() + " chunk index " + index + ": " + e.getMessage());
                }
            }
        } finally { region.close(); }
    }

    private void inspectChunk(Nbt.Compound root, String dimension, String regionName, Result result) {
        Nbt.Compound level = root.compound("Level");
        if (level == null) level = root;
        Integer chunkX = level.intNumber("xPos"), chunkZ = level.intNumber("zPos");
        inspectList(level.list("TileEntities"), dimension, regionName, chunkX, chunkZ, result);
        inspectList(level.list("Entities"), dimension, regionName, chunkX, chunkZ, result);
    }

    private void inspectList(Nbt.ListTag list, String dimension, String regionName, Integer chunkX, Integer chunkZ, Result result) {
        if (list == null) return;
        for (Object value : list) if (value instanceof Nbt.Compound) inspectCompound((Nbt.Compound)value, dimension, regionName, chunkX, chunkZ, result);
    }

    private void inspectCompound(Nbt.Compound c, String dimension, String regionName, Integer chunkX, Integer chunkZ, Result result) {
        String table = c.string("LootTable");
        Long seed = c.longNumber("LootTableSeed");
        if (table != null) {
            if (seed == null) seed = 0L;
            String id = c.string("id");
            String position = position(c);
            int slots = slotsFor(id, table);
            Set<Integer> occupied = occupiedSlots(c.list("Items"));
            result.containers.add(new Container(dimension, regionName, chunkX == null ? 0 : chunkX, chunkZ == null ? 0 : chunkZ,
                id == null ? "unknown" : id, position, Main.normalizeTable(table), seed, slots, occupied));
        }
        for (Object child : c.values()) {
            if (child instanceof Nbt.Compound) inspectCompound((Nbt.Compound)child, dimension, regionName, chunkX, chunkZ, result);
            else if (child instanceof Nbt.ListTag) inspectNestedList((Nbt.ListTag)child, dimension, regionName, chunkX, chunkZ, result);
        }
    }

    private void inspectNestedList(Nbt.ListTag list, String dimension, String regionName, Integer chunkX, Integer chunkZ, Result result) {
        for (Object child : list) {
            if (child instanceof Nbt.Compound) inspectCompound((Nbt.Compound)child, dimension, regionName, chunkX, chunkZ, result);
            else if (child instanceof Nbt.ListTag) inspectNestedList((Nbt.ListTag)child, dimension, regionName, chunkX, chunkZ, result);
        }
    }

    private static Set<Integer> occupiedSlots(Nbt.ListTag items) {
        Set<Integer> result = new HashSet<Integer>();
        if (items != null) for (Object item : items) if (item instanceof Nbt.Compound) {
            Integer slot = ((Nbt.Compound)item).intNumber("Slot");
            if (slot != null) result.add(slot & 255);
        }
        return result;
    }

    private static int slotsFor(String id, String table) {
        String lower = (id == null ? "" : id).toLowerCase(Locale.ROOT);
        if (lower.contains("dispenser") || table.endsWith("jungle_temple_dispenser")) return 9;
        return 27;
    }

    private static String position(Nbt.Compound c) {
        Integer x = c.intNumber("x"), y = c.intNumber("y"), z = c.intNumber("z");
        if (x != null && y != null && z != null) return x + "," + y + "," + z;
        Nbt.ListTag pos = c.list("Pos");
        if (pos != null && pos.size() >= 3 && pos.get(0) instanceof Number && pos.get(1) instanceof Number && pos.get(2) instanceof Number) {
            return String.format(Locale.ROOT, "%.2f,%.2f,%.2f", ((Number)pos.get(0)).doubleValue(), ((Number)pos.get(1)).doubleValue(), ((Number)pos.get(2)).doubleValue());
        }
        return "unknown";
    }

    static final class Result {
        Long worldSeed;
        final List<Container> containers = new ArrayList<Container>();
        final List<String> warnings = new ArrayList<String>();
    }

    static final class Container {
        final String dimension, regionFile, id, position, lootTable;
        final int chunkX, chunkZ, slots;
        final long lootSeed;
        final Set<Integer> occupiedSlots;
        Container(String dimension, String regionFile, int chunkX, int chunkZ, String id, String position, String lootTable, long lootSeed, int slots, Set<Integer> occupiedSlots) {
            this.dimension = dimension; this.regionFile = regionFile; this.chunkX = chunkX; this.chunkZ = chunkZ; this.id = id;
            this.position = position; this.lootTable = lootTable; this.lootSeed = lootSeed; this.slots = slots; this.occupiedSlots = occupiedSlots;
        }
        String locationLine() { return dimension + " " + id + " at " + position + " (chunk " + chunkX + "," + chunkZ + "; " + regionFile + ")"; }
    }
}
