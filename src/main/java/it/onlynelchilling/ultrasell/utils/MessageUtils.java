package it.onlynelchilling.ultrasell.utils;

import it.onlynelchilling.ultrasell.UltraSell;
import it.onlynelchilling.ultrasell.config.ConfigManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.regex.Pattern;

public final class MessageUtils {

    private static final Pattern HEX_PATTERN = Pattern.compile("&#([A-Fa-f0-9]{6})");

    private static final Map<Character, String> COLOR_MAP = Map.ofEntries(
            Map.entry('0', "<black>"), Map.entry('1', "<dark_blue>"),
            Map.entry('2', "<dark_green>"), Map.entry('3', "<dark_aqua>"),
            Map.entry('4', "<dark_red>"), Map.entry('5', "<dark_purple>"),
            Map.entry('6', "<gold>"), Map.entry('7', "<gray>"),
            Map.entry('8', "<dark_gray>"), Map.entry('9', "<blue>"),
            Map.entry('a', "<green>"), Map.entry('b', "<aqua>"),
            Map.entry('c', "<red>"), Map.entry('d', "<light_purple>"),
            Map.entry('e', "<yellow>"), Map.entry('f', "<white>")
    );

    private static final Map<Character, String> DECORATION_OPEN_MAP = Map.of(
            'l', "<bold>",
            'o', "<italic>",
            'n', "<underlined>",
            'm', "<strikethrough>",
            'k', "<obfuscated>"
    );

    private static final Map<Character, String> DECORATION_CLOSE_MAP = Map.of(
            'l', "</bold>",
            'o', "</italic>",
            'n', "</underlined>",
            'm', "</strikethrough>",
            'k', "</obfuscated>"
    );

    private static final LegacyComponentSerializer LEGACY_SERIALIZER = LegacyComponentSerializer.builder()
            .character('§')
            .hexColors()
            .useUnusualXRepeatedCharacterHexFormat()
            .build();

    private final UltraSell plugin;
    private final MiniMessage miniMessage;

    public MessageUtils(UltraSell plugin) {
        this.plugin = plugin;
        this.miniMessage = MiniMessage.miniMessage();
    }

    public Component deserialize(String text) {
        if (text == null || text.isEmpty()) return Component.empty();

        return parse(text);
    }

    private Component parse(String text) {
        String normalized = text.replace('§', '&');

        normalized = HEX_PATTERN.matcher(normalized).replaceAll("<color:#$1>");

        normalized = convertLegacyCodes(normalized);

        return miniMessage.deserialize(normalized);
    }

    private String convertLegacyCodes(String text) {
        StringBuilder sb = new StringBuilder(text.length());
        LinkedHashSet<Character> openDecorations = new LinkedHashSet<>();

        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);

            if (ch == '&' && i + 1 < text.length()) {
                char code = Character.toLowerCase(text.charAt(i + 1));

                String colorTag = COLOR_MAP.get(code);
                if (colorTag != null) {
                    closeAllDecorations(sb, openDecorations);
                    sb.append(colorTag);
                    i++;
                    continue;
                }

                String decoTag = DECORATION_OPEN_MAP.get(code);
                if (decoTag != null) {
                    sb.append(decoTag);
                    openDecorations.add(code);
                    i++;
                    continue;
                }

                if (code == 'r') {
                    closeAllDecorations(sb, openDecorations);
                    sb.append("<reset>");
                    i++;
                    continue;
                }
            }

            sb.append(ch);
        }

        return sb.toString();
    }

    private void closeAllDecorations(StringBuilder sb, LinkedHashSet<Character> openDecorations) {
        ArrayList<Character> list = new ArrayList<>(openDecorations);
        Collections.reverse(list);
        for (char code : list) {
            String closeTag = DECORATION_CLOSE_MAP.get(code);
            if (closeTag != null) {
                sb.append(closeTag);
            }
        }
        openDecorations.clear();
    }

    public String toLegacy(String text) {
        Component wrapped = Component.empty()
                .decoration(TextDecoration.BOLD, false)
                .decoration(TextDecoration.ITALIC, false)
                .append(deserialize(text));
        return LEGACY_SERIALIZER.serialize(wrapped);
    }

    public void send(Player player, String path, Object... replacements) {
        ConfigManager cfg = plugin.getConfigManager();
        Component prefix = deserialize(cfg.getMessage("prefix"));
        String raw = cfg.getMessage(path, replacements);
        Component message = deserialize(raw);

        Component combined = Component.empty()
                .decoration(TextDecoration.BOLD, false)
                .decoration(TextDecoration.ITALIC, false)
                .append(prefix)
                .append(message);

        NMSUtil.sendMessage(player, combined);
    }

    public void sendActionBar(Player player, String path, Object... replacements) {
        String raw = plugin.getConfigManager().getMessage(path, replacements);
        Component component = Component.empty()
                .decoration(TextDecoration.BOLD, false)
                .decoration(TextDecoration.ITALIC, false)
                .append(deserialize(raw));

        NMSUtil.sendActionBar(player, component);
    }

}
