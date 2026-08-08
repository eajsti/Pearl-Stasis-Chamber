package com.stasis.plugin;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.FishHook;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Tracks in-flight fishing hooks per player and, once a hook comes to rest on a
 * pressure plate (on a non-waterlogged fence), swaps it for an invisible armor
 * stand that anchors the player in place ("stasis"). Holder positions persist
 * to holders.yml so they survive restarts.
 */
public class BobberEntityManager {

    private final Map<UUID, ArmorStand> stands = new HashMap<>();
    private final Map<UUID, FishHook> hooks = new HashMap<>();
    private final StasisPlugin plugin;
    private boolean savePending = false;

    public BobberEntityManager(StasisPlugin plugin) {
        this.plugin = plugin;
    }

    public void onCast(Player player, FishHook hook) {
        UUID playerUUID = player.getUniqueId();
        ArmorStand existing = this.stands.remove(playerUUID);
        if (existing != null) {
            removeStandEntity(existing);
        }
        this.hooks.put(playerUUID, hook);
        this.scheduleSave();
    }

    public void onReelIn(UUID playerUUID) {
        this.hooks.remove(playerUUID);
        ArmorStand stand = this.stands.remove(playerUUID);
        if (stand != null) {
            removeStandEntity(stand);
            this.scheduleSave();
        }
    }

    public void tick() {
        Iterator<Map.Entry<UUID, FishHook>> iter = this.hooks.entrySet().iterator();
        while (iter.hasNext()) {
            Map.Entry<UUID, FishHook> entry = iter.next();
            UUID playerUUID = entry.getKey();
            FishHook hook = entry.getValue();

            if (hook.isDead()) {
                iter.remove();
                continue;
            }
            if (this.stands.containsKey(playerUUID) || hook.getVelocity().lengthSquared() >= 0.003) {
                continue;
            }

            Location hookLoc = hook.getLocation();
            Block blockAtHook = hookLoc.getBlock();
            Block blockBelowHook = blockAtHook.getRelative(BlockFace.DOWN);

            Block plateBlock = null;
            if (Tag.PRESSURE_PLATES.isTagged(blockAtHook.getType())) {
                plateBlock = blockAtHook;
            } else if (Tag.PRESSURE_PLATES.isTagged(blockBelowHook.getType())) {
                plateBlock = blockBelowHook;
            }

            if (plateBlock == null) {
                iter.remove();
                continue;
            }

            Block blockBelowPlate = plateBlock.getRelative(BlockFace.DOWN);
            if (Tag.FENCES.isTagged(blockBelowPlate.getType())
                    && blockBelowPlate.getBlockData() instanceof Waterlogged wl
                    && wl.isWaterlogged()) {
                continue;
            }

            ArmorStand stand = this.spawnHolder(hookLoc);
            this.stands.put(playerUUID, stand);
            iter.remove();
            this.scheduleSave();
        }

        Iterator<Map.Entry<UUID, ArmorStand>> standIter = this.stands.entrySet().iterator();
        while (standIter.hasNext()) {
            Map.Entry<UUID, ArmorStand> entry = standIter.next();
            UUID playerUUID = entry.getKey();
            Player p = Bukkit.getPlayer(playerUUID);
            if (p == null || !p.isDead()) {
                continue;
            }
            removeStandEntity(entry.getValue());
            standIter.remove();
            this.scheduleSave();
        }
    }

    /** Removes the armor stand from the world, loading its chunk first if needed. */
    private void removeStandEntity(ArmorStand stand) {
        Location loc = stand.getLocation();
        if (!loc.getChunk().isLoaded()) {
            loc.getChunk().load();
        }
        Entity actualStand = loc.getWorld().getEntity(stand.getUniqueId());
        if (actualStand != null) {
            actualStand.remove();
        } else if (!stand.isDead()) {
            stand.remove();
        }
    }

    private ArmorStand spawnHolder(Location loc) {
        return loc.getWorld().spawn(loc, ArmorStand.class, stand -> {
            stand.setVisible(false);
            stand.setGravity(false);
            stand.setPersistent(true);
            stand.setInvulnerable(true);
            stand.setCollidable(false);
            stand.setSilent(true);
            stand.setSmall(true);
            stand.addScoreboardTag("bobber_holder");
        });
    }

    private void scheduleSave() {
        if (this.savePending) {
            return;
        }
        this.savePending = true;
        YamlConfiguration config = this.buildSnapshot();
        this.plugin.getServer().getScheduler().runTaskAsynchronously(this.plugin, () -> {
            File file = new File(this.plugin.getDataFolder(), "holders.yml");
            this.plugin.getDataFolder().mkdirs();
            try {
                config.save(file);
            } catch (IOException e) {
                this.plugin.getLogger().warning("[Bobber] Could not save holders.yml: " + e.getMessage());
            } finally {
                this.savePending = false;
            }
        });
    }

    private YamlConfiguration buildSnapshot() {
        YamlConfiguration config = new YamlConfiguration();
        for (Map.Entry<UUID, ArmorStand> entry : this.stands.entrySet()) {
            String path = "holders." + entry.getKey();
            ArmorStand stand = entry.getValue();
            Location loc = stand.getLocation();
            config.set(path + ".standUUID", stand.getUniqueId().toString());
            config.set(path + ".world", loc.getWorld().getName());
            config.set(path + ".x", loc.getX());
            config.set(path + ".y", loc.getY());
            config.set(path + ".z", loc.getZ());
        }
        return config;
    }

    public void saveData() {
        File file = new File(this.plugin.getDataFolder(), "holders.yml");
        this.plugin.getDataFolder().mkdirs();
        try {
            this.buildSnapshot().save(file);
        } catch (IOException e) {
            this.plugin.getLogger().warning("[Bobber] Could not save holders.yml: " + e.getMessage());
        }
    }

    public void loadData() {
        File file = new File(this.plugin.getDataFolder(), "holders.yml");
        if (!file.exists()) {
            return;
        }
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection holdersSection = config.getConfigurationSection("holders");
        if (holdersSection == null) {
            return;
        }

        boolean hadStale = false;
        for (String playerStr : holdersSection.getKeys(false)) {
            try {
                UUID playerUUID = UUID.fromString(playerStr);
                String worldName = holdersSection.getString(playerStr + ".world");
                String standStr = holdersSection.getString(playerStr + ".standUUID");
                double x = holdersSection.getDouble(playerStr + ".x");
                double z = holdersSection.getDouble(playerStr + ".z");

                World world = this.plugin.getServer().getWorld(worldName);
                if (world == null) {
                    hadStale = true;
                    continue;
                }

                Chunk chunk = world.getChunkAt((int) x >> 4, (int) z >> 4);
                if (!chunk.isLoaded()) {
                    chunk.load();
                }

                Entity entity = world.getEntity(UUID.fromString(standStr));
                if (entity instanceof ArmorStand stand) {
                    this.stands.put(playerUUID, stand);
                } else {
                    hadStale = true;
                }
            } catch (IllegalArgumentException ignored) {
                hadStale = true;
            }
        }

        if (hadStale) {
            this.saveData();
        }
    }

    public void clearAll() {
        this.stands.clear();
        this.hooks.clear();
    }
}
