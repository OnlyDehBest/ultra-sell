package it.onlynelchilling.ultrasell;

import co.aikar.commands.BukkitCommandManager;
import it.onlynelchilling.ultrasell.commands.SellCommand;
import it.onlynelchilling.ultrasell.config.ConfigManager;
import it.onlynelchilling.ultrasell.gui.SellGUISystem;
import it.onlynelchilling.ultrasell.hooks.PacketEventsHook;
import it.onlynelchilling.ultrasell.hooks.VaultHook;
import it.onlynelchilling.ultrasell.listeners.SellGUIListener;
import it.onlynelchilling.ultrasell.utils.MessageUtils;
import it.onlynelchilling.ultrasell.utils.NMSUtil;
import org.bukkit.plugin.java.JavaPlugin;

public final class UltraSell extends JavaPlugin {

    private ConfigManager configManager;
    private VaultHook vaultHook;
    private SellGUISystem sellGUISystem;
    private MessageUtils messageUtils;
    private Runnable worthLoreRebuilder;

    @Override
    public void onEnable() {
        configManager = new ConfigManager(this);
        messageUtils = new MessageUtils(this);

        NMSUtil.init();

        vaultHook = new VaultHook(this);
        if (!vaultHook.setup()) {
            getLogger().severe("Vault not found! Disabling plugin.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        sellGUISystem = new SellGUISystem(this);

        var commandManager = new BukkitCommandManager(this);
        commandManager.registerCommand(new SellCommand(this));

        getServer().getPluginManager().registerEvents(new SellGUIListener(this), this);

        if (configManager.isWorthLoreEnabled()
                && getServer().getPluginManager().getPlugin("packetevents") != null) {
            try {
                worthLoreRebuilder = PacketEventsHook.register(this);
            } catch (Throwable e) {
                getLogger().severe("Failed to hook into PacketEvents for worth lore. Disabling worth lore feature.");
                e.printStackTrace();
            }
        }

        getLogger().info("UltraSell successfully enabled!");
    }

    @Override
    public void onDisable() {
        getLogger().info("UltraSell disabled!.");
    }

    public void rebuildWorthLoreCache() {
        if (worthLoreRebuilder != null) {
            worthLoreRebuilder.run();
        }
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public VaultHook getVaultHook() {
        return vaultHook;
    }

    public SellGUISystem getSellGUISystem() {
        return sellGUISystem;
    }

    public MessageUtils getMessageUtils() {
        return messageUtils;
    }
}
