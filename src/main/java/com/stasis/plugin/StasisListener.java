package com.stasis.plugin;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerFishEvent;

public class StasisListener implements Listener {

    private final BobberEntityManager manager;

    public StasisListener(StasisPlugin plugin) {
        this.manager = plugin.getBobberManager();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerFish(PlayerFishEvent event) {
        switch (event.getState()) {
            case FISHING -> this.manager.onCast(event.getPlayer(), event.getHook());
            case REEL_IN, CAUGHT_FISH, CAUGHT_ENTITY, IN_GROUND, FAILED_ATTEMPT ->
                    this.manager.onReelIn(event.getPlayer().getUniqueId());
            default -> {
                // no-op for other states (e.g. BITE)
            }
        }
    }
}
