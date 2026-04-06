package it.onlynelchilling.ultrasell.hooks;

import com.github.retrooper.packetevents.PacketEvents;
import it.onlynelchilling.ultrasell.UltraSell;
import it.onlynelchilling.ultrasell.listeners.packet.WorthLoreListener;

public final class PacketEventsHook {

    private PacketEventsHook() {}

    public static Runnable register(UltraSell plugin) {
        var listener = new WorthLoreListener(plugin);
        PacketEvents.getAPI().getEventManager().registerListener(listener);
        return listener::rebuildPriceCache;
    }
}

