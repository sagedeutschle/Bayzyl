package com.bayzyl.persistence;

import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.AbstractConstruct;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.error.YAMLException;
import org.yaml.snakeyaml.nodes.Node;
import org.yaml.snakeyaml.representer.Representer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Strict YAML codec for {@code crash-recovery.yml}. Documents are plain YAML data only (maps, lists, strings,
 * numbers, booleans). A versioned document must carry {@link #FORMAT_VERSION}; an unversioned document is the
 * legacy 0.1 layout and is accepted so it can be migrated once. Payload-level validation lives in
 * {@link RecoverySnapshot#fromDocument(Map)}.
 */
public final class CrashRecoverySnapshotCodec implements AtomicYamlStore.SnapshotCodec<Map<String, Object>> {
    public static final int FORMAT_VERSION = 1;
    static final String FORMAT_KEY = "formatVersion";
    private static final List<String> SECTIONS = List.of("meta", "sessions", "clipboards", "nudge");
    private static final int CODE_POINT_LIMIT = 256 * 1024 * 1024;

    /** Stands in for a legacy value whose YAML tag named a Java type. It is never constructed, only rejected. */
    public static final Object UNSUPPORTED_LEGACY_VALUE = new Object() {
        @Override
        public String toString() {
            return "<unsupported legacy value>";
        }
    };

    @Override
    public byte[] encode(Map<String, Object> document) throws IOException {
        requirePlain(document, "document");
        return newYaml().dump(document).getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public Map<String, Object> decodeStrict(byte[] encoded) throws IOException {
        Object loaded;
        try {
            loaded = newYaml().load(new String(encoded, StandardCharsets.UTF_8));
        } catch (YAMLException exception) {
            throw new IOException("malformed crash-recovery YAML", exception);
        }
        if (!(loaded instanceof Map<?, ?> raw)) {
            throw new IOException("crash-recovery document must be a mapping");
        }
        Map<String, Object> document = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : raw.entrySet()) {
            if (!(entry.getKey() instanceof String key)) {
                throw new IOException("crash-recovery document keys must be strings");
            }
            document.put(key, entry.getValue());
        }
        Object version = document.get(FORMAT_KEY);
        if (version != null) {
            if (!(version instanceof Integer number) || number != FORMAT_VERSION) {
                throw new IOException("unsupported crash-recovery format version: " + version);
            }
            if (containsUnsupported(document)) {
                throw new IOException("versioned crash-recovery document contains a Java type tag");
            }
        }
        for (String section : SECTIONS) {
            Object value = document.get(section);
            if (value != null && !(value instanceof Map)) {
                throw new IOException("crash-recovery section '" + section + "' must be a mapping");
            }
        }
        return document;
    }

    private static Yaml newYaml() {
        LoaderOptions loaderOptions = new LoaderOptions();
        loaderOptions.setAllowDuplicateKeys(false);
        loaderOptions.setMaxAliasesForCollections(50);
        loaderOptions.setCodePointLimit(CODE_POINT_LIMIT);
        // Global (Java type) tags may be composed only because LegacyTolerantConstructor turns every one of them
        // into an inert marker; no class is ever instantiated from a tag.
        loaderOptions.setTagInspector(tag -> true);
        DumperOptions dumperOptions = new DumperOptions();
        dumperOptions.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        dumperOptions.setIndent(2);
        dumperOptions.setSplitLines(false);
        return new Yaml(new LegacyTolerantConstructor(loaderOptions), new Representer(dumperOptions),
                dumperOptions, loaderOptions);
    }

    private static void requirePlain(Object value, String path) throws IOException {
        if (value instanceof String || value instanceof Integer || value instanceof Long
                || value instanceof Double || value instanceof Boolean) {
            return;
        }
        if (value instanceof List<?> list) {
            for (int i = 0; i < list.size(); i++) {
                requirePlain(list.get(i), path + "[" + i + "]");
            }
            return;
        }
        if (value instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!(entry.getKey() instanceof String key)) {
                    throw new IOException(path + " has a non-string key");
                }
                requirePlain(entry.getValue(), path + "." + key);
            }
            return;
        }
        throw new IOException(path + " is not plain YAML data: " + (value == null ? "null" : value.getClass().getName()));
    }

    private static boolean containsUnsupported(Object value) {
        if (value == UNSUPPORTED_LEGACY_VALUE) {
            return true;
        }
        if (value instanceof Map<?, ?> map) {
            for (Object nested : map.values()) {
                if (containsUnsupported(nested)) {
                    return true;
                }
            }
        } else if (value instanceof List<?> list) {
            for (Object nested : list) {
                if (containsUnsupported(nested)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Safe construction, except that unknown (Java type) tags become a marker instead of failing the file. */
    private static final class LegacyTolerantConstructor extends SafeConstructor {
        LegacyTolerantConstructor(LoaderOptions options) {
            super(options);
            this.yamlConstructors.put(null, new AbstractConstruct() {
                @Override
                public Object construct(Node node) {
                    return UNSUPPORTED_LEGACY_VALUE;
                }
            });
        }
    }
}
