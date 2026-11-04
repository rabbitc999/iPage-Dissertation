package com.rabbitc.ims.ai;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.rabbitc.ims.web.ApiException;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/** Stores each user's conversations independently and replaces archives atomically. */
public final class AiConversations {
    public record Entry(String id, String title, String updatedAt) {}

    private final Path root;

    AiConversations(Path root) {
        this.root = root;
    }

    private Path directory(String userId) {
        return root.resolve(UUID.fromString(userId).toString());
    }

    private Path path(String userId, String id) {
        if (id == null || !id.matches("[a-f0-9-]{36}")) throw ApiException.notFound("对话不存在");
        return directory(userId).resolve(id + ".json");
    }

    private JsonObject read(Path path) {
        if (!Files.exists(path)) throw ApiException.notFound("对话不存在");
        try {
            return JsonParser.parseString(Files.readString(path)).getAsJsonObject();
        } catch (IOException e) {
            throw new UncheckedIOException("读取对话失败", e);
        }
    }

    private void write(Path path, JsonObject conversation) {
        try {
            Files.createDirectories(path.getParent());
            Path temp = Files.createTempFile(path.getParent(), "conversation-", ".tmp");
            try {
                Files.writeString(temp, conversation.toString());
                Files.move(temp, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } finally {
                Files.deleteIfExists(temp);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("保存对话失败", e);
        }
    }

    private Entry entry(JsonObject value) {
        return new Entry(value.get("id").getAsString(), value.get("title").getAsString(),
                value.get("updatedAt").getAsString());
    }

    synchronized List<Entry> list(String userId) {
        Path dir = directory(userId);
        if (!Files.isDirectory(dir)) return List.of();
        var entries = new ArrayList<Entry>();
        try (var paths = Files.list(dir)) {
            paths.filter(path -> path.getFileName().toString().endsWith(".json"))
                    .forEach(path -> entries.add(entry(read(path))));
        } catch (IOException e) {
            throw new UncheckedIOException("读取对话列表失败", e);
        }
        entries.sort(Comparator.comparing((Entry item) -> Instant.parse(item.updatedAt())).reversed()
                .thenComparing(Entry::id));
        return entries;
    }

    synchronized Entry create(String userId) {
        String id = UUID.randomUUID().toString();
        JsonObject value = new JsonObject();
        value.addProperty("id", id);
        value.addProperty("title", "新对话");
        value.addProperty("updatedAt", Instant.now().toString());
        write(path(userId, id), value);
        return entry(value);
    }

    synchronized JsonObject archive(String userId, String id) {
        return read(path(userId, id)).getAsJsonObject("archive");
    }

    synchronized JsonArray messages(String userId, String id) {
        JsonObject value = read(path(userId, id));
        return value.has("messages") ? value.getAsJsonArray("messages") : new JsonArray();
    }

    synchronized void save(String userId, String id, JsonObject archive, JsonArray messages, String firstMessage) {
        Path path = path(userId, id);
        JsonObject value = read(path);
        if (!value.has("archive") && !value.has("renamed")) {
            String title = firstMessage == null ? "" : firstMessage.strip().replaceAll("\\s+", " ");
            if (title.isEmpty()) title = "附件对话";
            value.addProperty("title", title.substring(0, title.offsetByCodePoints(0,
                    Math.min(32, title.codePointCount(0, title.length())))));
        }
        value.add("archive", archive);
        value.add("messages", messages);
        value.addProperty("updatedAt", Instant.now().toString());
        write(path, value);
    }

    synchronized Entry rename(String userId, String id, String title) {
        if (title == null || title.isBlank() || title.strip().length() > 120) {
            throw ApiException.badRequest("请输入 1 到 120 字的对话名称");
        }
        Path path = path(userId, id);
        JsonObject value = read(path);
        value.addProperty("title", title.strip());
        value.addProperty("renamed", true);
        write(path, value);
        return entry(value);
    }

    synchronized void delete(String userId, String id) {
        try {
            if (!Files.deleteIfExists(path(userId, id))) throw ApiException.notFound("对话不存在");
        } catch (IOException e) {
            throw new UncheckedIOException("删除对话失败", e);
        }
    }
}
