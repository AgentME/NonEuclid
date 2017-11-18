package tech.macil.minecraft.noneuclid;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.MemorySection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;
import java.util.logging.Level;

public class NonEuclidPlugin extends JavaPlugin implements Listener {
    private List<Setup> setups;
    private Set<Location> allSetupBlockLocations;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        setups = new ArrayList<>();
        allSetupBlockLocations = new HashSet<>();
        int defaultMaxDistance = getConfig().getInt("max_distance");
        Map<String, Object> configLocations = getConfig().getConfigurationSection("locations").getValues(false);
        for (Map.Entry<String, Object> entry : configLocations.entrySet()) {
            Object locationConfig = entry.getValue();
            if (!(locationConfig instanceof MemorySection)) {
                getLogger().log(Level.SEVERE, "Error parsing config: locations." + entry.getKey() + " expected to be object");
                continue;
            }
            MemorySection locationSection = (MemorySection) locationConfig;
            try {
                if (locationSection.getBoolean("disabled", false)) {
                    continue;
                }

                World world = getServer().getWorld(locationSection.getString("world"));
                long x = locationSection.getLong("x");
                long y = locationSection.getLong("y");
                long z = locationSection.getLong("z");
                int width = locationSection.getInt("wall_width", 2);
                int height = locationSection.getInt("wall_height", 3);
                Material material = Material.valueOf(locationSection.getString("material", "STONE"));
                Setup.Path defaultPath = Setup.Path.valueOf(locationSection.getString("default_path", "NorthSouth"));
                int maxDistance = locationSection.getInt("max_distance", defaultMaxDistance);

                Location loc = new Location(world, x, y, z);
                Setup setup = new Setup(loc, width, height, material, defaultPath, maxDistance);
                setups.add(setup);
                allSetupBlockLocations.addAll(setup.getNorthSouthLocations());
                allSetupBlockLocations.addAll(setup.getEastWestLocations());
            } catch (Exception e) {
                getLogger().log(Level.SEVERE, "Error parsing config locations." + entry.getKey(), e);
            }
        }

        if (setups.size() == 0) {
            // Don't register event listeners if nothing is configured to be used.
            return;
        }

        for (Player player : getServer().getOnlinePlayers()) {
            renderForPlayer(player);
        }
        getServer().getPluginManager().registerEvents(this, this);
    }

    @Override
    public void onDisable() {
        List<Block> blocks = new ArrayList<>(allSetupBlockLocations.size());
        for (Location loc : allSetupBlockLocations) {
            blocks.add(loc.getBlock());
        }
        final Location tLoc = new Location(null, 0, 0, 0);
        for (Player player : getServer().getOnlinePlayers()) {
            for (Block block : blocks) {
                block.getLocation(tLoc);
                if (player.getWorld() != tLoc.getWorld()) {
                    continue;
                }
                player.sendBlockChange(tLoc, block.getType(), block.getData());
            }
        }
        setups = null;
        allSetupBlockLocations = null;
    }

    private void renderForPlayer(Player player) {
        renderForPlayer(player, false);
    }

    private void renderForPlayer(Player player, boolean forceRender) {
        Location playerLoc = player.getLocation();
        for (Setup setup : setups) {
            Map<Player, Setup.Path> currentPlayerPaths = setup.getCurrentPlayerPaths();
            if (!setup.isLocationClose(playerLoc)) {
                currentPlayerPaths.remove(player);
                continue;
            }
            Setup.Path currentPath = currentPlayerPaths.get(player);
            Setup.Path newPath = setup.getPathForLocation(playerLoc);
            if (newPath == null) {
                if (currentPath == null) {
                    newPath = setup.getDefaultPath();
                } else {
                    if (!forceRender) {
                        continue;
                    } else {
                        newPath = currentPath;
                    }
                }
            } else if (currentPath == newPath && !forceRender) {
                continue;
            }

            List<Block> realBlocks = new ArrayList<>();
            for (Location loc : newPath == Setup.Path.EastWest ? setup.getNorthSouthLocations() : setup.getEastWestLocations()) {
                realBlocks.add(loc.getBlock());
            }
            Location tLoc = new Location(null, 0, 0, 0);
            for (Block block : realBlocks) {
                block.getLocation(tLoc);
                player.sendBlockChange(tLoc, block.getType(), block.getData());
            }
            for (Location loc : newPath == Setup.Path.NorthSouth ? setup.getNorthSouthLocations() : setup.getEastWestLocations()) {
                player.sendBlockChange(loc, setup.getMaterial(), (byte) 0);
            }
            currentPlayerPaths.put(player, newPath);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onPlayerMove(PlayerMoveEvent event) {
        renderForPlayer(event.getPlayer());
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onPlayerLogin(PlayerLoginEvent event) {
        // Render the fake blocks in the next frame because any sent now
        // get overridden by the real chunk.
        Player player = event.getPlayer();
        getServer().getScheduler().scheduleSyncDelayedTask(this, () -> {
            if (player.isOnline()) {
                renderForPlayer(player, true);
            }
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        for (Setup setup : setups) {
            setup.getCurrentPlayerPaths().remove(event.getPlayer());
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.LEFT_CLICK_BLOCK) {
            return;
        }
        Location clickedLoc = event.getClickedBlock().getLocation();
        if (allSetupBlockLocations.contains(clickedLoc)) {
            Player player = event.getPlayer();
            getServer().getScheduler().scheduleSyncDelayedTask(this, () -> {
                if (player.isOnline()) {
                    renderForPlayer(player, true);
                }
            });
        }
    }
}
