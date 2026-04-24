package it.onlynelchilling.ultrasell.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;

public enum ConfigType {

    CONFIG("config"),
    PRICES("prices"),
    MESSAGES("messages"),
    DATABASE("database");

    private static JavaPlugin plugin;

    private final String path;
    private FileConfiguration config;

    ConfigType(String path) {
        this.path = path;
    }

    public String getPath() {
        return path + ".yml";
    }

    public FileConfiguration getConfig() {
        return config;
    }

    public String getString(String key) {
        return config.getString(key);
    }

    public boolean getBoolean(String key) {
        return config.getBoolean(key);
    }

    public int getInt(String key) {
        return config.getInt(key);
    }

    public double getDouble(String key) {
        return config.getDouble(key);
    }

    public List<String> getStringList(String key) {
        return config.getStringList(key);
    }

    public ConfigurationSection getConfigurationSection(String key) {
        return config.getConfigurationSection(key);
    }

    public Set<String> getKeys(boolean deep) {
        return config.getKeys(deep);
    }

    public static void init(JavaPlugin instance) {
        plugin = instance;

        for (ConfigType type : values()) {
            type.load();
            type.update();
        }
    }

    public static void reloadAll() {
        plugin.reloadConfig();

        for (ConfigType type : values()) {
            type.load();
            type.update();
        }
    }

    private void load() {
        if (this == CONFIG) {
            plugin.saveDefaultConfig();
            config = plugin.getConfig();
            return;
        }

        File file = new File(plugin.getDataFolder(), getPath());

        if (!file.exists()) {
            file.getParentFile().mkdirs();
            plugin.saveResource(getPath(), false);
        }

        config = YamlConfiguration.loadConfiguration(file);
    }

    private void update() {
        InputStream resource = plugin.getResource(getPath());
        if (resource == null) return;

        FileConfiguration defaults = YamlConfiguration.loadConfiguration(
                new InputStreamReader(resource, StandardCharsets.UTF_8)
        );

        config.setDefaults(defaults);

        boolean hasMissing = false;

        for (String key : defaults.getKeys(true)) {
            if (defaults.isConfigurationSection(key)) continue;

            if (!config.isSet(key)) {
                config.set(key, defaults.get(key));
                hasMissing = true;
            }
        }

        if (!hasMissing) return;

        try {
            config.save(new File(plugin.getDataFolder(), getPath()));

            if (this == CONFIG) {
                plugin.reloadConfig();
                config = plugin.getConfig();
                config.setDefaults(defaults);
            }
        } catch (IOException ignored) {
        }
    }
}

