package com.name.mysticmonstrosityfixes;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.nio.file.*;
import java.util.Objects;

public final class RegistrySizingCache {
    private static final Path DIR  = Paths.get(".cache","everycompat");
    private static final Path FILE = DIR.resolve("items-expected.json");
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    public static int loadExpected() {
        try {
            if (Files.exists(FILE)) {
                var json = Files.readString(FILE);
                return Math.max(0, Integer.parseInt(json.trim()));
            }
        } catch (Exception ignored) {}
        return 0;
    }

    public static void saveExpected(int n) {
        try {
            Files.createDirectories(DIR);
            Files.writeString(FILE, Objects.toString(n), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (Exception ignored) {}
    }
}
