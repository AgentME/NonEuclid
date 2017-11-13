package tech.macil.minecraft.codefoo;

import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Level;

public class CodeFooPlugin extends JavaPlugin {
    @Override
    public void onEnable() {
        getLogger().log(Level.INFO, "yo yo yo what's up");
    }
}
