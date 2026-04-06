package it.onlynelchilling.ultrasell.config;

import it.onlynelchilling.ultrasell.UltraSell;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.*;

public class ConfigManager {

    private final UltraSell plugin;
    private final ConfigFile configFile;
    private final ConfigFile pricesFile;

    private volatile Map<Material, Double> prices = Map.of();
    private volatile List<MultiplierEntry> multiplierEntries = List.of();
    private volatile boolean multiplierEnabled;

    private String guiTitle;
    private int guiSize;
    private List<DecorationItem> decorations = List.of();

    private DecimalFormat priceFormat;
    private DecimalFormat integerPriceFormat;

    private SoundEntry sellSuccessSound;
    private SoundEntry sellFailSound;

    private boolean worthLoreEnabled;
    private String worthLoreFormat;

    private final Map<String, String> messages = new HashMap<>();

    public ConfigManager(UltraSell plugin) {
        this.plugin = plugin;
        this.configFile = new ConfigFile(plugin, "config");
        this.pricesFile = new ConfigFile(plugin, "prices");
        load();
    }

    public void load() {
        var config = configFile.getConfig();

        loadPrices();
        loadMultipliers(config);
        loadGUISettings(config);
        loadMessages(config);
        loadSettings(config);
    }

    private void loadPrices() {
        var config = pricesFile.getConfig();
        var map = new EnumMap<Material, Double>(Material.class);

        for (String key : config.getKeys(false)) {
            try {
                var material = Material.valueOf(key.toUpperCase());
                var price = config.getDouble(key + ".price-per-unit");

                if (price > 0) {
                    map.put(material, price);
                }
            } catch (IllegalArgumentException ignored) {
                plugin.getLogger().warning("Unknown material in prices.yml: " + key);
            }
        }

        prices = Map.copyOf(map);
    }

    private void loadMultipliers(FileConfiguration config) {
        multiplierEnabled = config.getBoolean("sell-multipliers.enabled");

        var section = config.getConfigurationSection("sell-multipliers.multipliers");
        if (section == null) {
            multiplierEntries = List.of();
            return;
        }

        var entries = new ArrayList<MultiplierEntry>();

        for (String key : section.getKeys(false)) {
            try {
                var multiplier = Double.parseDouble(key);
                var permission = section.getString(key);

                if (permission != null && !permission.isEmpty() && multiplier > 0) {
                    entries.add(new MultiplierEntry(multiplier, permission));
                }
            } catch (NumberFormatException ignored) {
                plugin.getLogger().warning("Invalid multiplier in config: " + key);
            }
        }

        entries.sort(Comparator.comparingDouble(MultiplierEntry::multiplier).reversed());
        multiplierEntries = List.copyOf(entries);
    }

    private void loadGUISettings(FileConfiguration config) {
        guiTitle = config.getString("gui.title");
        guiSize = config.getInt("gui.size");

        if (guiSize % 9 != 0 || guiSize < 9 || guiSize > 54) {
            guiSize = 54;
        }

        loadDecorations(config);
    }

    private void loadDecorations(FileConfiguration config) {
        var section = config.getConfigurationSection("gui.decorations");
        if (section == null) {
            decorations = List.of();
            return;
        }

        var items = new ArrayList<DecorationItem>();

        for (String key : section.getKeys(false)) {
            var itemSection = section.getConfigurationSection(key);
            if (itemSection == null) continue;

            try {
                var materialName = itemSection.getString("material");
                if (materialName == null) continue;

                var material = Material.valueOf(materialName.toUpperCase());
                var name = itemSection.getString("name");
                var lore = itemSection.getStringList("lore");
                var slots = itemSection.getIntegerList("slots");

                if (!slots.isEmpty()) {
                    items.add(new DecorationItem(material, name, lore, List.copyOf(slots)));
                }
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Invalid decoration material in config: " + key);
            }
        }

        decorations = List.copyOf(items);
    }

    private void loadMessages(FileConfiguration config) {
        messages.clear();

        var section = config.getConfigurationSection("messages");
        if (section == null) return;

        for (String key : section.getKeys(true)) {
            if (section.isString(key)) {
                messages.put(key, section.getString(key));
            }
        }
    }

    private void loadSettings(FileConfiguration config) {
        var pattern = config.getString("settings.price-format");
        var localeTag = config.getString("settings.price-locale");

        try {
            var locale = Locale.forLanguageTag(localeTag);
            var symbols = new DecimalFormatSymbols(locale);

            priceFormat = new DecimalFormat(pattern, symbols);
            integerPriceFormat = new DecimalFormat("#,##0", symbols);
        } catch (Exception e) {
            plugin.getLogger().warning("Invalid price format in config, using default: " + e.getMessage());
            var defaultSymbols = new DecimalFormatSymbols(Locale.US);
            priceFormat = new DecimalFormat("#,##0.00", defaultSymbols);
            integerPriceFormat = new DecimalFormat("#,##0", defaultSymbols);
        }

        sellSuccessSound = loadSound(config, "sounds.sell-success", Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.2f);
        sellFailSound = loadSound(config, "sounds.sell-fail", Sound.ENTITY_VILLAGER_NO, 0.7f, 0.8f);

        worthLoreEnabled = config.getBoolean("worth-lore.enabled");
        worthLoreFormat = config.getString("worth-lore.format");
    }

    private SoundEntry loadSound(FileConfiguration config, String path, Sound defaultSound, float defaultVolume, float defaultPitch) {
        var soundName = config.getString(path + ".sound");

        if (soundName == null || soundName.isBlank() || soundName.equalsIgnoreCase("NONE")) {
            return null;
        }

        try {
            var sound = Sound.valueOf(soundName.toUpperCase());
            var volume = (float) config.getDouble(path + ".volume");
            var pitch = (float) config.getDouble(path + ".pitch");

            return new SoundEntry(sound, volume, pitch);
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Unknown sound in config: " + soundName + ", using default.");
            return new SoundEntry(defaultSound, defaultVolume, defaultPitch);
        }
    }

    public String formatPrice(double price) {
        return priceFormat.format(price);
    }

    public String formatPriceSmart(double price) {
        if (price == (long) price) {
            return integerPriceFormat.format((long) price);
        }
        return priceFormat.format(price);
    }

    public void reload() {
        configFile.reload();
        pricesFile.reload();
        load();
    }

    public String getMessage(String key) {
        return messages.getOrDefault(key, "");
    }

    public String getMessage(String key, Object... replacements) {
        var msg = getMessage(key);

        for (int i = 0; i < replacements.length - 1; i += 2) {
            msg = msg.replace(String.valueOf(replacements[i]), String.valueOf(replacements[i + 1]));
        }

        return msg;
    }

    public Map<Material, Double> getPrices() {
        return prices;
    }

    public List<MultiplierEntry> getMultiplierEntries() {
        return multiplierEntries;
    }

    public boolean isMultiplierEnabled() {
        return multiplierEnabled;
    }

    public String getGuiTitle() {
        return guiTitle;
    }

    public int getGuiSize() {
        return guiSize;
    }

    public List<DecorationItem> getDecorations() {
        return decorations;
    }

    public SoundEntry getSellSuccessSound() {
        return sellSuccessSound;
    }

    public SoundEntry getSellFailSound() {
        return sellFailSound;
    }

    public boolean isWorthLoreEnabled() {
        return worthLoreEnabled;
    }

    public String getWorthLoreFormat() {
        return worthLoreFormat;
    }

    public record MultiplierEntry(double multiplier, String permission) {}

    public record DecorationItem(Material material, String name, List<String> lore, List<Integer> slots) {}

    public record SoundEntry(Sound sound, float volume, float pitch) {}
}
