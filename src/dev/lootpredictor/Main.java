package dev.lootpredictor;

import java.io.File;
import java.util.List;

public final class Main {
    private static final String VERSION = "1.0.0";

    public static void main(String[] args) {
        try {
            run(args);
        } catch (IllegalArgumentException e) {
            System.err.println("Error: " + e.getMessage());
            System.exit(2);
        } catch (Exception e) {
            System.err.println("Unexpected error: " + e.getMessage());
            e.printStackTrace(System.err);
            System.exit(1);
        }
    }

    private static void run(String[] args) throws Exception {
        if (args.length == 0 || "help".equals(args[0]) || "--help".equals(args[0]) || "-h".equals(args[0])) {
            printHelp();
            return;
        }

        Args a = new Args(args);
        String command = a.command();
        if ("version".equals(command)) {
            System.out.println("Minecraft Loot Predictor " + VERSION + " (Minecraft Java 1.12.2 only)");
        } else if ("setup".equals(command)) {
            File jar = Vanilla1122.setup(a.file("--server-jar", null));
            System.out.println("Verified vanilla engine: " + jar.getAbsolutePath());
        } else if ("predict".equals(command)) {
            predict(a);
        } else if ("scan".equals(command)) {
            scan(a);
        } else if ("replay-spawn".equals(command)) {
            replay(a);
        } else if ("explain".equals(command)) {
            explain(a);
        } else if ("java-random".equals(command)) {
            javaRandom(a);
        } else if ("self-test".equals(command)) {
            SelfTest.run(Vanilla1122.resolveOrSetup(a.file("--server-jar", null)));
        } else {
            throw new IllegalArgumentException("Unknown command '" + command + "'. Run with help.");
        }
    }

    private static void predict(Args a) throws Exception {
        String table = normalizeTable(a.required("--table"));
        if (!isSupportedTable(table)) throw new IllegalArgumentException("Unsupported vanilla 1.12.2 table: " + table);
        long lootSeed = a.longValue("--loot-seed");
        int slots = a.intValue("--slots", defaultSlots(table));
        File jar = Vanilla1122.resolveOrSetup(a.file("--server-jar", null));
        if (lootSeed == 0L) {
            System.out.println("LootTableSeed is 0. Minecraft uses new Random() in this special case, so the result is not deterministic.");
            return;
        }
        Vanilla1122 engine = new Vanilla1122(jar);
        printLoot(table, lootSeed, slots, engine.fill(table, lootSeed, slots));
    }

    private static void scan(Args a) throws Exception {
        File save = a.file("--save", null);
        if (save == null) throw new IllegalArgumentException("Missing --save <world folder>.");
        File jar = Vanilla1122.resolveOrSetup(a.file("--server-jar", null));
        SaveScanner scanner = new SaveScanner(save);
        SaveScanner.Result result = scanner.scan();
        System.out.println("World: " + save.getAbsolutePath());
        System.out.println("World seed: " + (result.worldSeed == null ? "not found" : result.worldSeed));
        System.out.println("Unopened vanilla loot containers: " + result.containers.size());
        if (result.containers.isEmpty()) {
            System.out.println("No saved unopened loot-table containers were found. Chunks must have been generated and saved, and the chest must not have been opened.");
            printWarnings(result.warnings);
            return;
        }
        Vanilla1122 engine = new Vanilla1122(jar);
        for (SaveScanner.Container c : result.containers) {
            System.out.println();
            System.out.println(c.locationLine());
            System.out.println("  table=" + c.lootTable + "  LootTableSeed=" + c.lootSeed);
            if (c.lootSeed == 0L) {
                System.out.println("  unpredictable: seed 0 makes Minecraft use a fresh time-dependent Random");
            } else if (!isSupportedTable(c.lootTable)) {
                System.out.println("  not evaluated: this is not one of the supported vanilla 1.12.2 chest tables");
            } else if (!c.occupiedSlots.isEmpty()) {
                System.out.println("  not evaluated: container already has occupied slots as well as a LootTable tag");
            } else {
                List<Vanilla1122.Stack> loot = engine.fill(c.lootTable, c.lootSeed, c.slots);
                printStacks(loot, "  ");
            }
        }
        printWarnings(result.warnings);
    }

    private static void replay(Args a) throws Exception {
        long seed = a.longValue("--world-seed");
        File output = a.file("--output", null);
        if (output == null) throw new IllegalArgumentException("Missing --output <new folder>.");
        if (!a.flag("--accept-eula")) {
            throw new IllegalArgumentException("Vanilla replay runs Mojang's server. Read https://aka.ms/MinecraftEULA and rerun with --accept-eula if you accept it.");
        }
        File jar = Vanilla1122.resolveOrSetup(a.file("--server-jar", null));
        File save = ReplayWorld.generateSpawn(jar, seed, output);
        System.out.println("Vanilla replay completed. Scanning saved spawn chunks...");
        SaveScanner.Result result = new SaveScanner(save).scan();
        System.out.println("Generated world seed: " + result.worldSeed);
        System.out.println("Predictable unopened containers saved near spawn: " + result.containers.size());
        if (!result.containers.isEmpty()) {
            Vanilla1122 engine = new Vanilla1122(jar);
            for (SaveScanner.Container c : result.containers) {
                System.out.println();
                System.out.println(c.locationLine());
                System.out.println("  table=" + c.lootTable + "  LootTableSeed=" + c.lootSeed);
                if (c.lootSeed != 0L && isSupportedTable(c.lootTable) && c.occupiedSlots.isEmpty()) printStacks(engine.fill(c.lootTable, c.lootSeed, c.slots), "  ");
            }
        }
        printWarnings(result.warnings);
    }

    private static void explain(Args a) {
        String seed = a.optional("--world-seed", "<any signed 64-bit seed>");
        System.out.println("Minecraft Java 1.12.2 predictability for world seed " + seed);
        System.out.println();
        System.out.println("Exact from LootTableSeed or an unopened saved container:");
        System.out.println("  simple dungeons; abandoned mineshafts; desert and jungle temples;");
        System.out.println("  stronghold corridor/crossing/library; village blacksmiths;");
        System.out.println("  nether fortresses; end cities (plus igloos and woodland mansions).");
        System.out.println();
        System.out.println("Not a direct worldSeed -> loot formula:");
        System.out.println("  During chunk population, generation code calls Random.nextLong() and stores the result as LootTableSeed.");
        System.out.println("  The RNG state at that call depends on terrain, biomes, decorator order, structure pieces, neighboring chunks,");
        System.out.println("  and generation history. A seed plus chest coordinates is therefore insufficient by itself.");
        System.out.println();
        System.out.println("Exact world-seed route provided by this tool:");
        System.out.println("  replay-spawn runs the verified vanilla 1.12.2 server generator in a new folder, stops it after spawn chunks");
        System.out.println("  are saved, then reads each real LootTableSeed and evaluates it with the original vanilla loot engine.");
        System.out.println("  It covers saved spawn chunks only; it does not analytically locate arbitrary distant structures.");
    }

    private static void javaRandom(Args a) {
        long seed = a.longValue("--seed");
        int count = a.intValue("--count", 5);
        if (count < 1 || count > 1000) throw new IllegalArgumentException("--count must be 1..1000");
        java.util.Random random = new java.util.Random(seed);
        System.out.println("java.util.Random seed=" + seed + " (same 48-bit LCG used by Minecraft 1.12.2)");
        for (int i = 0; i < count; i++) System.out.println(i + ": nextLong=" + random.nextLong());
    }

    static void printLoot(String table, long seed, int slots, List<Vanilla1122.Stack> loot) {
        System.out.println("Table: " + table);
        System.out.println("LootTableSeed: " + seed);
        System.out.println("Container slots: " + slots);
        printStacks(loot, "");
    }

    static void printStacks(List<Vanilla1122.Stack> loot, String prefix) {
        if (loot.isEmpty()) {
            System.out.println(prefix + "(empty)");
            return;
        }
        for (Vanilla1122.Stack s : loot) {
            StringBuilder line = new StringBuilder(prefix).append("slot ").append(s.slot).append(": ").append(s.id).append(" x").append(s.count);
            if (s.meta != 0) line.append(" data=").append(s.meta);
            if (s.nbt != null && !"null".equals(s.nbt)) line.append(" nbt=").append(s.nbt);
            System.out.println(line);
        }
    }

    private static void printWarnings(List<String> warnings) {
        if (!warnings.isEmpty()) {
            System.out.println();
            System.out.println("Warnings:");
            for (String warning : warnings) System.out.println("  - " + warning);
        }
    }

    static String normalizeTable(String value) {
        String s = value.toLowerCase(java.util.Locale.ROOT).replace('\\', '/');
        if (s.startsWith("minecraft:")) return s;
        if (s.startsWith("chests/")) return "minecraft:" + s;
        return "minecraft:chests/" + s;
    }

    static int defaultSlots(String table) {
        return table.endsWith("jungle_temple_dispenser") ? 9 : 27;
    }

    static boolean isSupportedTable(String table) {
        String t = normalizeTable(table);
        return t.equals("minecraft:chests/simple_dungeon") || t.equals("minecraft:chests/abandoned_mineshaft")
            || t.equals("minecraft:chests/desert_pyramid") || t.equals("minecraft:chests/jungle_temple")
            || t.equals("minecraft:chests/jungle_temple_dispenser") || t.equals("minecraft:chests/stronghold_corridor")
            || t.equals("minecraft:chests/stronghold_crossing") || t.equals("minecraft:chests/stronghold_library")
            || t.equals("minecraft:chests/village_blacksmith") || t.equals("minecraft:chests/nether_bridge")
            || t.equals("minecraft:chests/end_city_treasure") || t.equals("minecraft:chests/igloo_chest")
            || t.equals("minecraft:chests/woodland_mansion") || t.equals("minecraft:chests/spawn_bonus_chest");
    }

    private static void printHelp() {
        System.out.println("Minecraft Loot Predictor " + VERSION + " - exact vanilla Java 1.12.2 loot");
        System.out.println();
        System.out.println("Commands:");
        System.out.println("  setup [--server-jar PATH]");
        System.out.println("  predict --table NAME --loot-seed N [--slots 27] [--server-jar PATH]");
        System.out.println("  scan --save PATH [--server-jar PATH]");
        System.out.println("  replay-spawn --world-seed N --output NEW_FOLDER --accept-eula [--server-jar PATH]");
        System.out.println("  explain [--world-seed N]");
        System.out.println("  java-random --seed N [--count 5]");
        System.out.println("  self-test [--server-jar PATH]");
        System.out.println();
        System.out.println("Table names include simple_dungeon, abandoned_mineshaft, desert_pyramid, jungle_temple,");
        System.out.println("stronghold_corridor, stronghold_crossing, stronghold_library, village_blacksmith,");
        System.out.println("nether_bridge, and end_city_treasure.");
    }

    static final class Args {
        private final String[] values;
        Args(String[] values) { this.values = values; }
        String command() { return values[0].toLowerCase(java.util.Locale.ROOT); }
        boolean flag(String name) { for (int i = 1; i < values.length; i++) if (name.equals(values[i])) return true; return false; }
        String optional(String name, String fallback) {
            for (int i = 1; i < values.length; i++) if (name.equals(values[i])) {
                if (i + 1 >= values.length) throw new IllegalArgumentException("Missing value after " + name);
                return values[i + 1];
            }
            return fallback;
        }
        String required(String name) { String v = optional(name, null); if (v == null) throw new IllegalArgumentException("Missing " + name); return v; }
        long longValue(String name) { try { return Long.parseLong(required(name)); } catch (NumberFormatException e) { throw new IllegalArgumentException(name + " must be a signed 64-bit integer"); } }
        int intValue(String name, int fallback) { String v = optional(name, null); if (v == null) return fallback; try { return Integer.parseInt(v); } catch (NumberFormatException e) { throw new IllegalArgumentException(name + " must be an integer"); } }
        File file(String name, File fallback) { String v = optional(name, null); return v == null ? fallback : new File(v); }
    }
}
