package it.onlynelchilling.ultrasell.message;

import it.onlynelchilling.ultrasell.UltraSell;
import it.onlynelchilling.ultrasell.config.ConfigType;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.HashMap;
import java.util.Map;

public final class MessageManager {

    private final Map<String, String> messages = new HashMap<>();

    public MessageManager(UltraSell plugin) {
        load();
    }

    public void load() {
        messages.clear();
        FileConfiguration c = ConfigType.MESSAGES.getConfig();
        for (String key : c.getKeys(true))
            if (c.isString(key)) messages.put(key, c.getString(key));
    }

    public void reload() {
        load();
    }

    public String get(String key) {
        return messages.getOrDefault(key, "");
    }

    public String get(String key, Object... repl) {
        String m = get(key);
        for (int i = 0; i < repl.length - 1; i += 2)
            m = m.replace(String.valueOf(repl[i]), String.valueOf(repl[i + 1]));
        return m;
    }

    public boolean has(String key) {
        return messages.containsKey(key);
    }
}


