package tech.macil.minecraft.noneuclid;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.BlockPosition;
import com.comphenix.protocol.wrappers.WrappedBlockData;
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

import static com.comphenix.protocol.PacketType.Play.Server.BLOCK_CHANGE;

public class NonEuclidPlugin extends JavaPlugin implements Listener {
    private List<Setup> setups;
    private Set<Location> allSetupBlockLocations;
    private ProtocolManager protocolManager;

    // turn this on when using player.sendBlockChange here
    private boolean disablePacketRewriting;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        disablePacketRewriting = false;
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
                allSetupBlockLocations.addAll(setup.getLocations(Setup.Path.NorthSouth));
                allSetupBlockLocations.addAll(setup.getLocations(Setup.Path.EastWest));
            } catch (Exception e) {
                getLogger().log(Level.SEVERE, "Error parsing config locations." + entry.getKey(), e);
            }
        }

        if (setups.size() == 0) {
            // Don't register event listeners if nothing is configured to be used.
            return;
        }

        protocolManager = ProtocolLibrary.getProtocolManager();
        for (Player player : getServer().getOnlinePlayers()) {
            renderForPlayer(player);
        }
        getServer().getPluginManager().registerEvents(this, this);
        protocolManager.addPacketListener(new PacketAdapter(this,
                ListenerPriority.HIGHEST,
                BLOCK_CHANGE) {
            @Override
            public void onPacketSending(PacketEvent event) {
                if (disablePacketRewriting) {
                    return;
                }
                PacketContainer packet = event.getPacket();
                Player player = event.getPlayer();
                PacketType type = event.getPacketType();
                if (type == BLOCK_CHANGE) {
                    BlockPosition position = packet.getBlockPositionModifier().read(0);
                    Location loc = position.toLocation(player.getWorld());
                    if (allSetupBlockLocations.contains(loc)) {
                        WrappedBlockData blockData = packet.getBlockData().read(0);
                        for (Setup setup : setups) {
                            if (blockData.getType() == setup.getMaterial()) {
                                continue;
                            }
                            Map<Player, Setup.Path> currentPlayerPaths = setup.getCurrentPlayerPaths();
                            Setup.Path path = currentPlayerPaths.get(player);
                            if (path == null) {
                                continue;
                            }
                            if (setup.getLocations(path).contains(loc)) {
                                packet.getBlockData().write(0, WrappedBlockData.createData(setup.getMaterial()));
                                break;
                            }
                            if (setup.getOtherLocations(path).contains(loc)) {
                                // Don't keep checking other setups. We know it's this
                                // one, and we know we don't need to do anything.
                                break;
                            }
                        }
                    }
                }
            }
        });
    }

    @Override
    public void onDisable() {
        disablePacketRewriting = true;
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

            currentPlayerPaths.put(player, newPath);
            disablePacketRewriting = true;
            List<Block> realBlocks = new ArrayList<>();
            for (Location loc : setup.getOtherLocations(newPath)) {
                realBlocks.add(loc.getBlock());
            }
            Location tLoc = new Location(null, 0, 0, 0);
            for (Block block : realBlocks) {
                block.getLocation(tLoc);
                player.sendBlockChange(tLoc, block.getType(), block.getData());
            }
            for (Location loc : setup.getLocations(newPath)) {
                player.sendBlockChange(loc, setup.getMaterial(), (byte) 0);
            }
            disablePacketRewriting = false;
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
