package it.onlynelchilling.ultrasell.config;

import it.onlynelchilling.ultrasell.UltraSell;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ConfigManager {

    private final UltraSell plugin;

    private volatile Map<Material, Double> prices = Map.of();
    private volatile List<MultiplierEntry> multiplierEntries = List.of();
    private volatile boolean multiplierEnabled;

    private String guiTitle;
    private int guiSize;
    private List<DecorationItem> decorations = List.of();

    private DecimalFormat priceFormat;
    private DecimalFormat integerPriceFormat;
    private final Map<Double, String> smartCache = new ConcurrentHashMap<>();

    private SoundEntry sellSuccessSound;
    private SoundEntry sellFailSound;

    private boolean worthLoreEnabled;
    private String worthLoreFormat;
    private boolean metricsEnabled;

    private boolean autoSellEnabled;
    private int autoSellIntervalSeconds;
    private boolean autoSellDefault;
    private boolean autoSellIgnoreHand;
    private boolean autoSellNotify;

    public ConfigManager(UltraSell plugin) {
        this.plugin = plugin;
        load();
    }

    public void load() {
        FileConfiguration c = ConfigType.CONFIG.getConfig();
        loadPrices();
        loadMultipliers(c);
        loadGUISettings(c);
        loadSettings(c);
        smartCache.clear();
    }

    private void loadPrices() {
        FileConfiguration c = ConfigType.PRICES.getConfig();
        EnumMap<Material, Double> map = new EnumMap<>(Material.class);
        for (String key : c.getKeys(false)) {
            try {
                Material m = Material.valueOf(key.toUpperCase(Locale.ROOT));
                double p = c.getDouble(key + ".price-per-unit");
                if (p > 0) map.put(m, p);
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Unknown material: " + key);
            }
        }
        prices = Map.copyOf(map);
    }

    private void loadMultipliers(FileConfiguration c) {
        multiplierEnabled = c.getBoolean("sell-multipliers.enabled");
        ConfigurationSection s = c.getConfigurationSection("sell-multipliers.multipliers");
        if (s == null) { multiplierEntries = List.of(); return; }
        List<MultiplierEntry> list = new ArrayList<>();
        for (String key : s.getKeys(false)) {
            try {
                double mul = Double.parseDouble(key);
                String perm = s.getString(key);
                if (perm != null && !perm.isEmpty() && mul > 0)
                    list.add(new MultiplierEntry(mul, perm));
            } catch (NumberFormatException ignored) {}
        }
        list.sort(Comparator.comparingDouble(MultiplierEntry::multiplier).reversed());
        multiplierEntries = List.copyOf(list);
    }

    private void loadGUISettings(FileConfiguration c) {
        guiTitle = c.getString("gui.title");
        guiSize = c.getInt("gui.size");
        if (guiSize % 9 != 0 || guiSize < 9 || guiSize > 54) guiSize = 54;
        loadDecorations(c);
    }

    private void loadDecorations(FileConfiguration c) {
        ConfigurationSection s = c.getConfigurationSection("gui.decorations");
        if (s == null) { decorations = List.of(); return; }
        List<DecorationItem> items = new ArrayList<>();
        for (String key : s.getKeys(false)) {
            ConfigurationSection it = s.getConfigurationSection(key);
            if (it == null) continue;
            try {
                String mat = it.getString("material");
                if (mat == null) continue;
                Material m = Material.valueOf(mat.toUpperCase(Locale.ROOT));
                List<Integer> slots = it.getIntegerList("slots");
                if (!slots.isEmpty())
                    items.add(new DecorationItem(m, it.getString("name"), it.getStringList("lore"), List.copyOf(slots)));
            } catch (IllegalArgumentException ignored) {}
        }
        decorations = List.copyOf(items);
    }


    private void loadSettings(FileConfiguration c) {
        try {
            DecimalFormatSymbols sym = new DecimalFormatSymbols(Locale.forLanguageTag(c.getString("settings.price-locale")));
            priceFormat = new DecimalFormat(c.getString("settings.price-format"), sym);
            integerPriceFormat = new DecimalFormat("#,##0", sym);
        } catch (Exception e) {
            DecimalFormatSymbols d = new DecimalFormatSymbols(Locale.US);
            priceFormat = new DecimalFormat("#,##0.00", d);
            integerPriceFormat = new DecimalFormat("#,##0", d);
        }
        sellSuccessSound = loadSound(c, "sounds.sell-success", Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.2f);
        sellFailSound = loadSound(c, "sounds.sell-fail", Sound.ENTITY_VILLAGER_NO, 0.7f, 0.8f);
        worthLoreEnabled = c.getBoolean("worth-lore.enabled");
        worthLoreFormat = c.getString("worth-lore.format");
        metricsEnabled = c.getBoolean("settings.metrics", true);
        autoSellEnabled = c.getBoolean("auto-sell.enabled", false);
        autoSellIntervalSeconds = Math.max(1, c.getInt("auto-sell.interval-seconds", 10));
        autoSellDefault = c.getBoolean("auto-sell.default-enabled", false);
        autoSellIgnoreHand = c.getBoolean("auto-sell.ignore-hand", true);
        autoSellNotify = c.getBoolean("auto-sell.notify", true);
    }

    private SoundEntry loadSound(FileConfiguration c, String path, Sound def, float dv, float dp) {
        String n = c.getString(path + ".sound");
        if (n == null || n.isBlank() || n.equalsIgnoreCase("NONE")) return null;
        try {
            return new SoundEntry(Sound.valueOf(n.toUpperCase(Locale.ROOT)),
                    (float) c.getDouble(path + ".volume"), (float) c.getDouble(path + ".pitch"));
        } catch (IllegalArgumentException e) {
            return new SoundEntry(def, dv, dp);
        }
    }

    public String formatPrice(double p) { return priceFormat.format(p); }

    public String formatPriceSmart(double p) {
        return smartCache.computeIfAbsent(p, k ->
                k == (long) (double) k ? integerPriceFormat.format((long) (double) k) : priceFormat.format(k));
    }

    public void reload() {
        ConfigType.reloadAll();
        load();
    }


    public Map<Material, Double> getPrices() { return prices; }
    public List<MultiplierEntry> getMultiplierEntries() { return multiplierEntries; }
    public boolean isMultiplierEnabled() { return multiplierEnabled; }
    public String getGuiTitle() { return guiTitle; }
    public int getGuiSize() { return guiSize; }
    public List<DecorationItem> getDecorations() { return decorations; }
    public SoundEntry getSellSuccessSound() { return sellSuccessSound; }
    public SoundEntry getSellFailSound() { return sellFailSound; }
    public boolean isWorthLoreEnabled() { return worthLoreEnabled; }
    public String getWorthLoreFormat() { return worthLoreFormat; }
    public boolean isMetricsEnabled() { return metricsEnabled; }

    public boolean isAutoSellEnabled() { return autoSellEnabled; }
    public int getAutoSellIntervalSeconds() { return autoSellIntervalSeconds; }
    public boolean isAutoSellDefault() { return autoSellDefault; }
    public boolean isAutoSellIgnoreHand() { return autoSellIgnoreHand; }
    public boolean isAutoSellNotify() { return autoSellNotify; }

    public record MultiplierEntry(double multiplier, String permission) {}
    public record DecorationItem(Material material, String name, List<String> lore, List<Integer> slots) {}
    public record SoundEntry(Sound sound, float volume, float pitch) {}
}

