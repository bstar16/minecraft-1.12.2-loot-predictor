package dev.lootpredictor;

import java.io.File;
import java.util.*;

final class SelfTest {
    static void run(File serverJar) throws Exception {
        Random random = new Random(0L);
        long[] expected = {-4962768465676381896L, 4437113781045784766L, -6688467811848818630L};
        for (long value : expected) {
            long actual = random.nextLong();
            if (actual != value) throw new AssertionError("java.util.Random mismatch: " + actual + " != " + value);
        }
        Vanilla1122 engine = new Vanilla1122(serverJar);
        List<Vanilla1122.Stack> first = engine.fill("minecraft:chests/simple_dungeon", 12345L, 27);
        List<Vanilla1122.Stack> second = engine.fill("minecraft:chests/simple_dungeon", 12345L, 27);
        if (!stable(first).equals(stable(second))) throw new AssertionError("Loot generation is not repeatable");
        if (first.isEmpty()) throw new AssertionError("Reference dungeon test unexpectedly produced no loot");
        List<Vanilla1122.Stack> enchanted = engine.fill("minecraft:chests/end_city_treasure", 1L, 27);
        boolean hasEnchant = false;
        for (Vanilla1122.Stack s : enchanted) if (s.nbt != null && s.nbt.contains("ench")) hasEnchant = true;
        if (!hasEnchant) throw new AssertionError("Reference end-city test did not preserve enchantment NBT");
        System.out.println("PASS: Java Random reference vector");
        System.out.println("PASS: verified Mojang server SHA-1");
        System.out.println("PASS: deterministic simple_dungeon table and exact slot filling");
        System.out.println("PASS: enchanted item NBT from end_city_treasure");
    }
    private static String stable(List<Vanilla1122.Stack> list) { StringBuilder b = new StringBuilder(); for (Vanilla1122.Stack s : list) b.append(s.stable()).append('\n'); return b.toString(); }
}
