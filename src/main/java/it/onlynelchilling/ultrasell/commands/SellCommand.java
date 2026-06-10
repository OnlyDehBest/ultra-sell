package it.onlynelchilling.ultrasell.commands;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.annotation.*;
import co.aikar.commands.bukkit.contexts.OnlinePlayer;
import it.onlynelchilling.ultrasell.UltraSell;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

@CommandAlias("sell")
public class SellCommand extends BaseCommand {

    private final UltraSell plugin;

    public SellCommand(UltraSell plugin) {
        this.plugin = plugin;
    }

    @Default
    public void onSell(Player player) {
        plugin.getSellGUISystem().openSellGUI(player);
    }

    @Subcommand("hand")
    public void onSellHand(Player player) {
        plugin.getSellGUISystem().sellHand(player);
    }

    @Subcommand("auto")
    @CommandPermission("ultrasell.auto")
    public void onAuto(Player player) {
        if (!plugin.getConfigManager().isAutoSellEnabled()) {
            plugin.getMessageUtils().send(player, "auto-sell-disabled-global");
            return;
        }
        var stats = plugin.getPlayerCache().get(player).stats();
        boolean now = !stats.autoSell();
        stats.setAutoSell(now);
        plugin.getMessageUtils().send(player, now ? "auto-sell-enabled" : "auto-sell-disabled");
    }

    @Subcommand("pickup")
    @CommandPermission("ultrasell.pickup")
    public void onPickup(Player player) {
        if (!plugin.getConfigManager().isAutoPickupEnabled()) {
            plugin.getMessageUtils().send(player, "auto-pickup-disabled-global");
            return;
        }
        var stats = plugin.getPlayerCache().get(player).stats();
        boolean now = !stats.autoPickup();
        stats.setAutoPickup(now);
        plugin.getMessageUtils().send(player, now ? "auto-pickup-enabled" : "auto-pickup-disabled");
    }

    @Subcommand("wand")
    @CommandPermission("ultrasell.wand.give")
    @CommandCompletion("@players")
    @Syntax("<player> [uses]")
    public void onWand(CommandSender sender, OnlinePlayer target, @Optional Integer uses) {
        var cfg = plugin.getConfigManager().getSellWand();
        Player receiver = target.getPlayer();

        if (!cfg.enabled()) {
            if (sender instanceof Player player) plugin.getMessageUtils().send(player, "wand-disabled-global");
            return;
        }

        int wandUses = uses != null ? uses : cfg.defaultUses();
        if (wandUses == 0 || wandUses < -1) wandUses = cfg.defaultUses();

        ItemStack wand = plugin.getSellWandManager().createWand(wandUses);
        receiver.getInventory().addItem(wand).values()
                .forEach(left -> receiver.getWorld().dropItem(receiver.getLocation(), left));

        String usesText = plugin.getSellWandManager().formatUses(wandUses);
        plugin.getMessageUtils().send(receiver, "wand-received", "{uses}", usesText);
        if (sender instanceof Player player && player != receiver) {
            plugin.getMessageUtils().send(player, "wand-given",
                    "{player}", receiver.getName(), "{uses}", usesText);
        }
    }

    @Subcommand("reload")
    @CommandPermission("ultrasell.reload")
    public void onReload(CommandSender sender) {
        plugin.getConfigManager().reload();
        plugin.getMessageManager().reload();
        plugin.getSellGUISystem().rebuildGUICache();
        plugin.getSellWandManager().rebuildCache();
        plugin.rebuildWorthLoreCache();
        plugin.getAutoSellTask().start();

        if (sender instanceof Player player) {
            plugin.getMessageUtils().send(player, "reload-success");
        }
    }
}
