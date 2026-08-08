package com.stasis.plugin;

import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

public class StasisPlugin extends JavaPlugin {

    private BobberEntityManager bobberManager;

    @Override
    public void onEnable() {
        this.bobberManager = new BobberEntityManager(this);
        this.bobberManager.loadData();

        this.getServer().getPluginManager().registerEvents((Listener) new StasisListener(this), (Plugin) this);
        this.getServer().getScheduler().runTaskTimer((Plugin) this, this.bobberManager::tick, 0L, 2L);

        this.getLogger().info("StasisPlugin " + this.getPluginMeta().getVersion() + " enabled.");
    }

    @Override
    public void onDisable() {
        this.bobberManager.saveData();
        this.getLogger().info("StasisPlugin disabled.");
    }

    public BobberEntityManager getBobberManager() {
        return this.bobberManager;
    }
}
