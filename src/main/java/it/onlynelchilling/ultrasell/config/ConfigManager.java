package it.onlynelchilling.ultrasell.config;

import it.onlynelchilling.ultrasell.UltraSell;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.*;

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
    private final Cache<Double, String> smartCache = Caffeine.newBuilder().maximumSize(4096).build();

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

    private boolean autoPickupEnabled;
    private boolean autoPickupNotify;

    private SellWandSettings sellWand;

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
        smartCache.invalidateAll();
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
        metricsEnabled = c.getBoolean("settings.metrics");
        autoSellEnabled = c.getBoolean("auto-sell.enabled");
        autoSellIntervalSeconds = Math.max(1, c.getInt("auto-sell.interval-seconds"));
        autoSellDefault = c.getBoolean("auto-sell.default-enabled");
        autoSellIgnoreHand = c.getBoolean("auto-sell.ignore-hand");
        autoSellNotify = c.getBoolean("auto-sell.notify");
        autoPickupEnabled = c.getBoolean("auto-pickup.enabled");
        autoPickupNotify = c.getBoolean("auto-pickup.notify");
        loadSellWand(c);
    }

    private void loadSellWand(FileConfiguration c) {
        Map<String, SellWandTemplate> templates = new LinkedHashMap<>();

        ConfigurationSection section = c.getConfigurationSection("sell-wand.templates");
        if (section != null) {
            for (String id : section.getKeys(false)) {
                ConfigurationSection t = section.getConfigurationSection(id);
                if (t == null) continue;
                String key = id.toLowerCase(Locale.ROOT);
                templates.put(key, readWandTemplate(key, t));
            }
        }

        if (templates.isEmpty()) {
            ConfigurationSection root = c.getConfigurationSection("sell-wand");
            templates.put(SellWandSettings.DEFAULT_ID, root != null
                    ? readWandTemplate(SellWandSettings.DEFAULT_ID, root)
                    : new SellWandTemplate(SellWandSettings.DEFAULT_ID, Material.BLAZE_ROD,
                            "&6&lSell Wand", List.of(), true, 1.0));
        }

        String defaultId = c.getString("sell-wand.default-template", "");
        defaultId = defaultId.toLowerCase(Locale.ROOT);
        if (!templates.containsKey(defaultId)) {
            defaultId = templates.containsKey(SellWandSettings.DEFAULT_ID)
                    ? SellWandSettings.DEFAULT_ID
                    : templates.keySet().iterator().next();
        }

        sellWand = new SellWandSettings(
                c.getBoolean("sell-wand.enabled", false),
                Math.max(0L, c.getLong("sell-wand.cooldown-ms")),
                Math.max(1, c.getInt("sell-wand.default-uses", 100)),
                Collections.unmodifiableMap(templates),
                defaultId
        );
    }

    private SellWandTemplate readWandTemplate(String id, ConfigurationSection s) {
        Material material = Material.BLAZE_ROD;
        String mat = s.getString("material");
        if (mat != null) {
            try {
                material = Material.valueOf(mat.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Unknown sell-wand material in template '" + id + "': " + mat);
            }
        }
        double multiplier = s.getDouble("multiplier", 1.0);
        return new SellWandTemplate(
                id,
                material,
                s.getString("name", "&6&lSell Wand"),
                List.copyOf(s.getStringList("lore")),
                s.getBoolean("glow", true),
                multiplier > 0 ? multiplier : 1.0
        );
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
        return smartCache.get(p, k ->
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
    public boolean isAutoPickupEnabled() { return autoPickupEnabled; }
    public boolean isAutoPickupNotify() { return autoPickupNotify; }
    public SellWandSettings getSellWand() { return sellWand; }

    public record MultiplierEntry(double multiplier, String permission) {}
    public record DecorationItem(Material material, String name, List<String> lore, List<Integer> slots) {}
    public record SellWandSettings(boolean enabled, long cooldownMillis, int defaultUses,
                                   Map<String, SellWandTemplate> templates, String defaultTemplateId) {
        public static final String DEFAULT_ID = "default";

        public SellWandTemplate defaultTemplate() {
            return templates.get(defaultTemplateId);
        }

        public SellWandTemplate template(String id) {
            if (id == null) return defaultTemplate();
            SellWandTemplate t = templates.get(id.toLowerCase(Locale.ROOT));
            return t != null ? t : defaultTemplate();
        }
    }

    public record SellWandTemplate(String id, Material material, String name, List<String> lore,
                                   boolean glow, double defaultMultiplier) {}

    public record SoundEntry(Sound sound, float volume, float pitch) {
        public void play(org.bukkit.entity.Player player) {
            player.playSound(player.getLocation(), sound, volume, pitch);
        }
    }
}

