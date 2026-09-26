package com.fairy294.playtimeguard;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

public final class PlaytimeTracker {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private final Map<UUID, Entry> entries = new HashMap<>();
    private Path dataPath;

    // 重置时刻以前仍属于前一个统计日，星期限制也按这个统计日判断。
    public static LocalDate currentDate() {
        return LocalDateTime.now().minusHours(GuardConfig.RESET_HOUR.get()).toLocalDate();
    }

    public void load(MinecraftServer server) {
        entries.clear();
        dataPath = server.getWorldPath(LevelResource.ROOT).resolve(GuardConfig.DATA_FILE_NAME.get());
        if (!Files.exists(dataPath)) {
            return;
        }
        try (Reader reader = Files.newBufferedReader(dataPath, StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            for (Map.Entry<String, JsonElement> item : root.entrySet()) {
                try {
                    UUID id = UUID.fromString(item.getKey());
                    JsonObject value = item.getValue().getAsJsonObject();
                    LocalDate date = LocalDate.parse(value.get("date").getAsString());
                    long millis = Math.max(0, value.get("millis").getAsLong());
                    entries.put(id, new Entry(date, millis));
                } catch (RuntimeException exception) {
                    PlaytimeGuard.LOGGER.warn("跳过无效的游玩时长记录：{}", item.getKey(), exception);
                }
            }
        } catch (IOException | RuntimeException exception) {
            PlaytimeGuard.LOGGER.error("读取游玩时长文件失败：{}", dataPath, exception);
        }
    }

    public long usedMillis(UUID id) {
        Entry entry = entries.get(id);
        LocalDate today = currentDate();
        if (entry == null || !entry.date.equals(today)) {
            entries.put(id, new Entry(today, 0));
            return 0;
        }
        return entry.millis;
    }

    public void addTicks(UUID id, long ticks) {
        if (ticks <= 0) {
            return;
        }
        long previous = usedMillis(id);
        entries.put(id, new Entry(currentDate(), previous + ticks * 50));
    }

    // 先写临时文件，再替换正式文件，避免写入中断产生半份 JSON。
    public void save() {
        if (dataPath == null) {
            return;
        }
        JsonObject root = new JsonObject();
        for (Map.Entry<UUID, Entry> item : entries.entrySet()) {
            JsonObject value = new JsonObject();
            value.addProperty("date", item.getValue().date.toString());
            value.addProperty("millis", item.getValue().millis);
            root.add(item.getKey().toString(), value);
        }
        Path tempPath = dataPath.resolveSibling(dataPath.getFileName() + ".tmp");
        try {
            Files.createDirectories(dataPath.getParent());
            try (Writer writer = Files.newBufferedWriter(tempPath, StandardCharsets.UTF_8)) {
                GSON.toJson(root, writer);
            }
            try {
                Files.move(tempPath, dataPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(tempPath, dataPath, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            PlaytimeGuard.LOGGER.error("保存游玩时长文件失败：{}", dataPath, exception);
        }
    }

    private record Entry(LocalDate date, long millis) {}
}
