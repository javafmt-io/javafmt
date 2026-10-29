package io.javafmt.cache;

import io.javafmt.JavafmtVersion;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Persistent {@link FormatCache} backed by a single text file at a configured location.
 *
 * <p>File format: one entry per line, {@code <sha256-hex> <space> <absolute-path>}. The hash
 * is over the UTF-8 bytes of the post-format content; the path is the absolute,
 * non-normalized string form of {@link Path#toAbsolutePath()}. A leading {@code # javafmt
 * cache v3 <version> <config-fingerprint>} marker line identifies the file format, the release
 * that wrote it and the configuration it was written under.</p>
 *
 * <p>Loading a missing or malformed file silently yields an empty cache: callers will format
 * every file once and rebuild the cache on save. Saving writes to a sibling temp file and
 * atomically moves it into place so a crashed write never corrupts the existing cache.</p>
 *
 * <p>The in-memory map is a {@link ConcurrentHashMap}, so concurrent reads and writes from a
 * format thread pool are safe. {@link #save()} is not thread-safe with respect to concurrent
 * mutation — call it after all formatting threads have finished.</p>
 */
public final class FileFormatCache implements FormatCache {

    /**
     * Carries the formatter version and a fingerprint of the run's configuration, because a
     * javafmt upgrade and a configuration change both legitimately change what {@code format(x)}
     * produces, which makes every stored hash wrong. A header mismatch discards the whole file
     * rather than trusting entries another version or another configuration wrote.
     */
    private static final String HEADER_PREFIX = "# javafmt cache v3 " + JavafmtVersion.VERSION + " ";

    private static final HexFormat HEX = HexFormat.of();

    private static final ThreadLocal<MessageDigest> DIGEST = ThreadLocal.withInitial(() -> {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    });

    private final Path cacheFile;

    private final String header;

    private final ConcurrentHashMap<String, String> hashByPath;

    private FileFormatCache(final Path cacheFile, final String header, final Map<String, String> initial) {
        this.cacheFile = cacheFile;
        this.header = header;
        this.hashByPath = new ConcurrentHashMap<>(initial);
    }

    /**
     * Loads the cache at {@code cacheFile} for a run configured as {@code key} describes. If the
     * file is missing, unreadable, malformed, or was written by another javafmt version or another
     * configuration, returns an empty cache; the file is written fresh on the next {@link #save()}.
     */
    public static FileFormatCache load(final Path cacheFile, final CacheKey key) {
        Objects.requireNonNull(cacheFile, "cacheFile");
        Objects.requireNonNull(key, "key");
        final var header = HEADER_PREFIX + key.fingerprint();
        if (!Files.isRegularFile(cacheFile)) {
            return new FileFormatCache(cacheFile, header, Map.of());
        }
        try {
            final var lines = Files.readAllLines(cacheFile, StandardCharsets.UTF_8);
            final var parsed = parseLines(lines, header);
            return new FileFormatCache(cacheFile, header, parsed);
        } catch (IOException | RuntimeException e) {
            return new FileFormatCache(cacheFile, header, Map.of());
        }
    }

    private static Map<String, String> parseLines(final List<String> lines, final String header) {
        if (lines.isEmpty() || !header.equals(lines.getFirst())) {
            return Map.of();
        }
        return lines.stream()
                .skip(1)
                .filter(line -> !line.isBlank())
                .map(line -> {
                    final var space = line.indexOf(' ');
                    if (space <= 0 || space == line.length() - 1) {
                        throw new IllegalArgumentException("malformed cache entry");
                    }
                    final var hash = line.substring(0, space);
                    final var path = line.substring(space + 1);
                    if (hash.length() != 64) {
                        throw new IllegalArgumentException("malformed cache entry");
                    }
                    return Map.entry(path, hash);
                })
                .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> b));
    }

    @Override
    public boolean isUpToDate(final Path file, final String currentContent) {
        Objects.requireNonNull(file, "file");
        Objects.requireNonNull(currentContent, "currentContent");
        final var stored = hashByPath.get(key(file));
        if (stored == null) {
            return false;
        }
        return stored.equals(sha256(currentContent));
    }

    @Override
    public void recordFormatted(final Path file, final String postFormatContent) {
        Objects.requireNonNull(file, "file");
        Objects.requireNonNull(postFormatContent, "postFormatContent");
        hashByPath.put(key(file), sha256(postFormatContent));
    }

    @Override
    public void save() {
        final var parent = cacheFile.toAbsolutePath().getParent();
        try {
            if (parent != null) {
                Files.createDirectories(parent);
            }
            final var body = hashByPath.entrySet()
                    .stream()
                    .filter(FileFormatCache::stillExists)
                    .sorted(Map.Entry.comparingByKey())
                    .map(e -> e.getValue() + " " + e.getKey())
                    .collect(Collectors.joining("\n", header + "\n", "\n"));
            final var directory = parent != null ? parent : Path.of(".");
            final var tmp = Files.createTempFile(directory, cacheFile.getFileName().toString() + ".", ".tmp");
            Files.writeString(tmp, body, StandardCharsets.UTF_8);
            try {
                Files.move(tmp, cacheFile, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, cacheFile, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("failed to save javafmt cache to " + cacheFile, e);
        }
    }

    /**
     * Whether {@code entry}'s file still exists. A run only ever touches the files it was
     * given, so nothing else prunes entries for files deleted since the last save — without
     * this filter they would accumulate in the cache file forever.
     */
    private static boolean stillExists(final Map.Entry<String, String> entry) {
        return Files.isRegularFile(Path.of(entry.getKey()));
    }

    private static String key(final Path file) {
        return file.toAbsolutePath().toString();
    }

    private static String sha256(final String content) {
        final var digest = DIGEST.get();
        digest.reset();
        return HEX.formatHex(digest.digest(content.getBytes(StandardCharsets.UTF_8)));
    }
}
