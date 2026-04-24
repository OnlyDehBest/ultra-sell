package it.onlynelchilling.ultrasell;

import co.aikar.commands.BukkitCommandManager;
import it.onlynelchilling.ultrasell.autosell.AutoSellTask;
import it.onlynelchilling.ultrasell.cache.PlayerCache;
import it.onlynelchilling.ultrasell.commands.SellCommand;
import it.onlynelchilling.ultrasell.config.ConfigManager;
import it.onlynelchilling.ultrasell.config.ConfigType;
import it.onlynelchilling.ultrasell.database.DatabaseManager;
import it.onlynelchilling.ultrasell.gui.SellGUISystem;
import it.onlynelchilling.ultrasell.hooks.PacketEventsHook;
import it.onlynelchilling.ultrasell.hooks.VaultHook;
import it.onlynelchilling.ultrasell.listeners.PlayerJoinListener;
import it.onlynelchilling.ultrasell.listeners.SellGUIListener;
import it.onlynelchilling.ultrasell.message.MessageManager;
import it.onlynelchilling.ultrasell.utils.MessageUtils;
import it.onlynelchilling.ultrasell.utils.NMSUtil;
import org.bstats.bukkit.Metrics;
import org.bstats.charts.MultiLineChart;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;

public final class UltraSell extends JavaPlugin {

    private static final int BSTATS_PLUGIN_ID = 30869;

    private ConfigManager configManager;
    private MessageManager messageManager;
    private DatabaseManager databaseManager;
    private PlayerCache playerCache;
    private VaultHook vaultHook;
    private SellGUISystem sellGUISystem;
    private MessageUtils messageUtils;
    private Runnable worthLoreRebuilder;
    private AutoSellTask autoSellTask;

    @Override
    public void onEnable() {
        ConfigType.init(this);
        configManager = new ConfigManager(this);
        messageManager = new MessageManager(this);
        messageUtils = new MessageUtils(this);

        NMSUtil.init();

        vaultHook = new VaultHook(this);
        if (!vaultHook.setup()) {
            getLogger().severe("Vault not found! Disabling plugin.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        databaseManager = new DatabaseManager(this);
        if (!databaseManager.connect()) {
            getLogger().severe("Database connection failed! Disabling plugin.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        playerCache = new PlayerCache(this);
        Bukkit.getOnlinePlayers().forEach(p -> playerCache.loadAsync(p));

        sellGUISystem = new SellGUISystem(this);

        new BukkitCommandManager(this).registerCommand(new SellCommand(this));

        getServer().getPluginManager().registerEvents(new SellGUIListener(this), this);
        getServer().getPluginManager().registerEvents(new PlayerJoinListener(this), this);

        if (configManager.isWorthLoreEnabled()
                && getServer().getPluginManager().getPlugin("packetevents") != null) {
            try {
                worthLoreRebuilder = PacketEventsHook.register(this);
            } catch (Throwable e) {
                getLogger().severe("PacketEvents hook failed: " + e.getMessage());
            }
        }

        if (configManager.isMetricsEnabled()) Bukkit.getScheduler().runTaskAsynchronously(this, this::bstats);

        autoSellTask = new AutoSellTask(this);
        autoSellTask.start();
    }

    @Override
    public void onDisable() {
        if (autoSellTask != null) autoSellTask.stop();
        if (playerCache != null) playerCache.flushAll();
        if (databaseManager != null) databaseManager.close();
    }

    public void rebuildWorthLoreCache() {
        if (worthLoreRebuilder != null) worthLoreRebuilder.run();
    }

    private void bstats() {
        Metrics metrics = new Metrics(this, BSTATS_PLUGIN_ID);
        metrics.addCustomChart(new MultiLineChart("players_and_servers", () -> {
            Map<String, Integer> valueMap = new HashMap<>();
            valueMap.put("servers", 1);
            valueMap.put("players", Bukkit.getOnlinePlayers().size());
            return valueMap;
        }));
    }

    public ConfigManager getConfigManager() { return configManager; }
    public MessageManager getMessageManager() { return messageManager; }
    public DatabaseManager getDatabaseManager() { return databaseManager; }
    public PlayerCache getPlayerCache() { return playerCache; }
    public VaultHook getVaultHook() { return vaultHook; }
    public SellGUISystem getSellGUISystem() { return sellGUISystem; }
    public MessageUtils getMessageUtils() { return messageUtils; }
    public AutoSellTask getAutoSellTask() { return autoSellTask; }
}

