package it.onlynelchilling.ultrasell.utils;

import com.cryptomorin.xseries.reflection.minecraft.MinecraftConnection;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;

public final class NMSUtil {

    private static boolean initialized;
    private static Constructor<?> systemChatCtor;
    private static Method fromJsonMethod;
    private static Object registryAccess;

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.builder()
            .character('§')
            .hexColors()
            .useUnusualXRepeatedCharacterHexFormat()
            .build();

    private NMSUtil() {}

    public static void init() {
        try {
            var componentClass = Class.forName("net.minecraft.network.chat.Component");
            var packetClass = Class.forName("net.minecraft.network.protocol.game.ClientboundSystemChatPacket");

            systemChatCtor = packetClass.getConstructor(componentClass, boolean.class);
            registryAccess = resolveRegistryAccess();
            fromJsonMethod = resolveFromJson(componentClass);

            initialized = true;
        } catch (Exception ignored) {
            initialized = false;
        }
    }

    private static Object resolveRegistryAccess() throws Exception {
        var server = Bukkit.getServer();
        var minecraft = server.getClass().getMethod("getServer").invoke(server);

        for (var name : new String[]{"registryAccess", "getRegistryAccess"}) {
            try {
                return minecraft.getClass().getMethod(name).invoke(minecraft);
            } catch (NoSuchMethodException ignored) {}
        }

        throw new RuntimeException("RegistryAccess method not found");
    }

    private static Method resolveFromJson(Class<?> componentClass) throws Exception {
        var serializer = Class.forName("net.minecraft.network.chat.Component$Serializer");

        return Arrays.stream(serializer.getMethods())
                .filter(m -> Modifier.isStatic(m.getModifiers()))
                .filter(m -> m.getParameterCount() == 2)
                .filter(m -> m.getParameterTypes()[0] == String.class)
                .filter(m -> m.getParameterTypes()[1].isInstance(registryAccess))
                .filter(m -> componentClass.isAssignableFrom(m.getReturnType()))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Component.Serializer fromJson not found"));
    }

    public static void sendMessage(Player player, Component component) {
        send(player, component, false);
    }

    public static void sendActionBar(Player player, Component component) {
        send(player, component, true);
    }

    private static void send(Player player, Component component, boolean overlay) {
        if (!initialized) {
            fallback(player, component, overlay);
            return;
        }

        try {
            var json = GsonComponentSerializer.gson().serialize(component);
            var nmsComponent = fromJsonMethod.invoke(null, json, registryAccess);
            var packet = systemChatCtor.newInstance(nmsComponent, overlay);

            MinecraftConnection.sendPacket(player, packet);
        } catch (Exception e) {
            fallback(player, component, overlay);
        }
    }

    @SuppressWarnings("deprecation")
    private static void fallback(Player player, Component component, boolean overlay) {
        var legacy = LEGACY.serialize(component);

        if (overlay) {
            player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(legacy));
        } else {
            player.sendMessage(legacy);
        }
    }
}
