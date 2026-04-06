package it.onlynelchilling.ultrasell.config;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

public class ConfigFile {

    private final Plugin plugin;
    private final String fileName;
    private final File file;
    private YamlConfiguration config;

    public ConfigFile(Plugin plugin, String fileName) {
        this.plugin = plugin;
        this.fileName = fileName + ".yml";
        this.file = new File(plugin.getDataFolder(), this.fileName);

        if (!file.exists()) {
            plugin.saveResource(this.fileName, false);
        }

        config = loadSafely(file);

        if (config.getKeys(false).isEmpty()) {
            backupCorrupt();
            plugin.saveResource(this.fileName, true);
            config = loadSafely(file);
        }

        mergeDefaults();
    }

    public YamlConfiguration getConfig() {
        return config;
    }

    public void reload() {
        config = loadSafely(file);

        if (config.getKeys(false).isEmpty()) {
            backupCorrupt();
            plugin.saveResource(this.fileName, true);
            config = loadSafely(file);
        }

        mergeDefaults();
    }

    private YamlConfiguration loadSafely(File file) {
        var yaml = new YamlConfiguration();
        try {
            var content = Files.readString(file.toPath(), StandardCharsets.UTF_8);
            yaml.loadFromString(content);
        } catch (InvalidConfigurationException e) {
            plugin.getLogger().warning("Invalid config file, regenerating: " + e.getMessage());
        } catch (IOException e) {
            plugin.getLogger().warning("Could not read config file: " + e.getMessage());
        }
        return yaml;
    }

    private YamlConfiguration parseSafely(String content) {
        var yaml = new YamlConfiguration();
        try {
            yaml.loadFromString(content);
            return yaml;
        } catch (InvalidConfigurationException e) {
            return null;
        }
    }

    private void backupCorrupt() {
        if (!file.exists()) return;
        try {
            var backupFile = new File(file.getParentFile(), fileName + ".broken");
            Files.copy(file.toPath(), backupFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            plugin.getLogger().warning("Corrupt config backed up to " + backupFile.getName());
        } catch (IOException ignored) {}
    }

    private void mergeDefaults() {
        var defaultStream = plugin.getResource(fileName);
        if (defaultStream == null) return;

        var defaultLines = readLines(defaultStream);
        if (defaultLines == null) return;

        var defaults = YamlConfiguration.loadConfiguration(
                new StringReader(String.join("\n", defaultLines)));

        if (!hasMissingKeys(defaults)) return;

        byte[] backup;
        List<String> userLines;
        try {
            backup = Files.readAllBytes(file.toPath());
            userLines = new ArrayList<>(Files.readAllLines(file.toPath(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            return;
        }

        var missingRoots = findMissingRoots(defaults);

        for (var missing : missingRoots) {
            var block = extractDefaultBlock(defaultLines, missing);
            if (block.isEmpty()) continue;

            int insertAt = findInsertionPoint(userLines, defaultLines, missing);
            userLines.addAll(insertAt, block);
        }

        writeMerged(userLines, backup);
    }

    private int findInsertionPoint(List<String> userLines, List<String> defaultLines, String key) {
        int defLine = findKeyLine(defaultLines, key);
        if (defLine < 0) return userLines.size();

        int defIndent = countIndent(defaultLines.get(defLine));
        int afterBlock = skipValueBlock(defaultLines, defLine) + 1;

        for (int i = afterBlock; i < defaultLines.size(); i++) {
            var line = defaultLines.get(i);
            if (line.isBlank() || line.stripLeading().startsWith("#")) continue;

            int indent = countIndent(line);
            if (indent < defIndent) break;
            if (indent > defIndent) continue;

            var siblingPath = resolvePathAtLine(defaultLines, i);
            if (siblingPath != null && config.contains(siblingPath)) {
                int userLine = findKeyLine(userLines, siblingPath);
                if (userLine >= 0) return userLine;
            }
        }

        var parentKey = key.contains(".")
                ? key.substring(0, key.lastIndexOf('.'))
                : null;

        if (parentKey != null) {
            return findSectionEnd(userLines, parentKey);
        }

        return userLines.size();
    }

    private boolean hasMissingKeys(YamlConfiguration defaults) {
        for (var key : defaults.getKeys(true)) {
            if (!config.contains(key)) return true;
        }
        return false;
    }

    private List<String> findMissingRoots(YamlConfiguration defaults) {
        var roots = new ArrayList<String>();

        for (var key : defaults.getKeys(true)) {
            if (config.contains(key)) continue;

            var ancestorMissing = false;
            var check = key;

            while (check.contains(".")) {
                check = check.substring(0, check.lastIndexOf('.'));
                if (!config.contains(check)) {
                    ancestorMissing = true;
                    break;
                }
            }

            if (!ancestorMissing) {
                roots.add(key);
            }
        }

        return roots;
    }

    private List<String> extractDefaultBlock(List<String> defaultLines, String key) {
        int keyLine = findKeyLine(defaultLines, key);
        if (keyLine < 0) return List.of();

        int start = keyLine;
        while (start > 0 && defaultLines.get(start - 1).stripLeading().startsWith("#")) {
            start--;
        }
        while (start > 0 && defaultLines.get(start - 1).isBlank()) {
            start--;
        }

        int end = skipValueBlock(defaultLines, keyLine);

        var block = new ArrayList<String>();
        for (int i = start; i <= end; i++) {
            block.add(defaultLines.get(i));
        }
        return block;
    }

    private int findKeyLine(List<String> lines, String key) {
        for (int i = 0; i < lines.size(); i++) {
            if (key.equals(resolvePathAtLine(lines, i))) return i;
        }
        return -1;
    }

    private int findSectionEnd(List<String> lines, String sectionKey) {
        int keyLine = findKeyLine(lines, sectionKey);
        if (keyLine < 0) return lines.size();
        return skipValueBlock(lines, keyLine) + 1;
    }

    private void writeMerged(List<String> result, byte[] backup) {
        var merged = String.join("\n", result);

        var test = parseSafely(merged);
        if (test == null || test.getKeys(false).isEmpty()) {
            plugin.getLogger().warning("Merged config was invalid, keeping original.");
            return;
        }

        try {
            Files.writeString(file.toPath(), merged, StandardCharsets.UTF_8);
            config = test;
        } catch (IOException e) {
            plugin.getLogger().warning("Could not write merged config: " + e.getMessage());
            try {
                Files.write(file.toPath(), backup);
                config = loadSafely(file);
            } catch (IOException ignored) {}
        }
    }

    private List<String> readLines(InputStream stream) {
        try (var reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            var lines = new ArrayList<String>();
            String line;
            while ((line = reader.readLine()) != null) {
                lines.add(line);
            }
            return lines;
        } catch (IOException e) {
            return null;
        }
    }

    private String resolvePathAtLine(List<String> lines, int lineIdx) {
        var line = lines.get(lineIdx);
        if (line.isBlank() || line.stripLeading().startsWith("#")) return null;

        var trimmed = line.stripLeading();
        int colonIdx = trimmed.indexOf(':');
        if (colonIdx <= 0) return null;

        if (colonIdx + 1 < trimmed.length()) {
            char afterColon = trimmed.charAt(colonIdx + 1);
            if (afterColon != ' ' && afterColon != '\r') return null;
        }

        var key = stripQuotes(trimmed.substring(0, colonIdx).strip());
        if (key.startsWith("-")) return null;

        int myIndent = countIndent(line);
        if (myIndent == 0) return key;

        Deque<String> parts = new ArrayDeque<>();
        parts.addFirst(key);

        int expectedIndent = myIndent - 2;
        for (int i = lineIdx - 1; i >= 0 && expectedIndent >= 0; i--) {
            var pLine = lines.get(i);
            if (pLine.isBlank() || pLine.stripLeading().startsWith("#")) continue;

            if (countIndent(pLine) == expectedIndent) {
                var pTrimmed = pLine.stripLeading();
                int pColon = pTrimmed.indexOf(':');
                if (pColon <= 0) return null;

                var pKey = stripQuotes(pTrimmed.substring(0, pColon).strip());
                if (pKey.startsWith("-")) return null;

                parts.addFirst(pKey);
                expectedIndent -= 2;
            }
        }

        return String.join(".", parts);
    }

    private int skipValueBlock(List<String> lines, int startIdx) {
        int indent = countIndent(lines.get(startIdx));
        int i = startIdx + 1;

        while (i < lines.size()) {
            var line = lines.get(i);

            if (line.isBlank() || line.stripLeading().startsWith("#")) {
                int peek = i;
                while (peek < lines.size()
                        && (lines.get(peek).isBlank() || lines.get(peek).stripLeading().startsWith("#"))) {
                    peek++;
                }
                if (peek >= lines.size() || countIndent(lines.get(peek)) <= indent) {
                    return i - 1;
                }
                i++;
                continue;
            }

            if (countIndent(line) <= indent) return i - 1;
            i++;
        }

        return i - 1;
    }

    private String stripQuotes(String str) {
        if ((str.startsWith("'") && str.endsWith("'"))
                || (str.startsWith("\"") && str.endsWith("\""))) {
            return str.substring(1, str.length() - 1);
        }
        return str;
    }

    private int countIndent(String line) {
        int count = 0;
        for (int i = 0; i < line.length(); i++) {
            if (line.charAt(i) == ' ') count++;
            else break;
        }
        return count;
    }
}
