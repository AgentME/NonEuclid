package tech.macil.minecraft.noneuclid;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.BlockPosition;
import com.comphenix.protocol.wrappers.MultiBlockChangeInfo;
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
import static com.comphenix.protocol.PacketType.Play.Server.MULTI_BLOCK_CHANGE;

public class NonEuclidPlugin extends JavaPlugin implements Listener {
    private boolean useInvisibility;
    private List<Intersection> intersections;
    private Set<Location> allIntersectionBlockLocations;
    private ProtocolManager protocolManager;

    // turn this on when using player.sendBlockChange here
    private boolean disablePacketRewriting;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        disablePacketRewriting = false;
        intersections = new ArrayList<>();
        allIntersectionBlockLocations = new HashSet<>();
        useInvisibility = getConfig().getBoolean("use_invisibility", true);

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
                double x = locationSection.getDouble("x");
                double y = locationSection.getDouble("y");
                double z = locationSection.getDouble("z");
                int width = locationSection.getInt("wall_width", 2);
                int height = locationSection.getInt("wall_height", 3);
                Material material = Material.valueOf(locationSection.getString("material", "STONE"));
                Intersection.Path defaultPath = Intersection.Path.valueOf(locationSection.getString("default_path", "NorthSouth"));
                int maxDistance = locationSection.getInt("max_distance", defaultMaxDistance);

                Location loc = new Location(world, x, y, z);
                Intersection intersection = new Intersection(loc, width, height, material, defaultPath, maxDistance);
                intersections.add(intersection);
                allIntersectionBlockLocations.addAll(intersection.getLocations(Intersection.Path.NorthSouth));
                allIntersectionBlockLocations.addAll(intersection.getLocations(Intersection.Path.EastWest));
            } catch (Exception e) {
                getLogger().log(Level.SEVERE, "Error parsing config locations." + entry.getKey(), e);
            }
        }

        if (intersections.size() == 0) {
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
                BLOCK_CHANGE, MULTI_BLOCK_CHANGE) {
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
                    if (allIntersectionBlockLocations.contains(loc)) {
                        event.setCancelled(true);
                    }
                } else if (type == MULTI_BLOCK_CHANGE) {
                    MultiBlockChangeInfo[] changes = packet.getMultiBlockChangeInfoArrays().read(0);
                    Location loc = new Location(player.getWorld(), 0, 0, 0);
                    for (int i = 0; i < changes.length; i++) {
                        loc.setX(changes[i].getAbsoluteX());
                        loc.setY(changes[i].getY());
                        loc.setZ(changes[i].getAbsoluteZ());
                        if (allIntersectionBlockLocations.contains(loc)) {
                            List<MultiBlockChangeInfo> newChanges = new ArrayList<>(changes.length);
                            for (int j = 0; j < i; j++) {
                                newChanges.add(changes[j]);
                            }
                            i++; // skip current value
                            for (; i < changes.length; i++) {
                                loc.setX(changes[i].getAbsoluteX());
                                loc.setY(changes[i].getY());
                                loc.setZ(changes[i].getAbsoluteZ());
                                if (!allIntersectionBlockLocations.contains(loc)) {
                                    newChanges.add(changes[i]);
                                }
                            }
                            if (newChanges.size() == 0) {
                                event.setCancelled(true);
                            } else {
                                MultiBlockChangeInfo[] newChangesArray = newChanges.toArray(new MultiBlockChangeInfo[newChanges.size()]);
                                packet.getMultiBlockChangeInfoArrays().write(0, newChangesArray);
                            }
                            break;
                        }
                    }
                }
            }
        });
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onDisable() {
        disablePacketRewriting = true;
        List<Block> blocks = new ArrayList<>(allIntersectionBlockLocations.size());
        for (Location loc : allIntersectionBlockLocations) {
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
        if (useInvisibility) {
            for (Intersection intersection : intersections) {
                Map<Player, Intersection.Path> currentPlayerPaths = intersection.getCurrentPlayerPaths();

                for (Player intersectionPlayer : intersection.getPlayersInIntersection()) {
                    Intersection.Path intersectionPlayerPath = currentPlayerPaths.get(intersectionPlayer);
                    assert intersectionPlayerPath != null;

                    for (Map.Entry<Player, Intersection.Path> entry : currentPlayerPaths.entrySet()) {
                        Player pathPlayer = entry.getKey();
                        if (pathPlayer == intersectionPlayer) {
                            continue;
                        }
                        Intersection.Path pathPlayerPath = entry.getValue();
                        if (intersectionPlayerPath != pathPlayerPath) {
                            pathPlayer.showPlayer(intersectionPlayer);
                        }
                    }
                }
            }
        }
        intersections = null;
        allIntersectionBlockLocations = null;
    }

    private void renderForPlayer(Player player) {
        renderForPlayer(player, false);
    }

    @SuppressWarnings("deprecation")
    private void renderForPlayer(Player player, boolean forceRender) {
        Location playerLoc = player.getLocation();
        for (Intersection intersection : intersections) {
            Map<Player, Intersection.Path> currentPlayerPaths = intersection.getCurrentPlayerPaths();
            Set<Player> playersInIntersection = intersection.getPlayersInIntersection();

            Intersection.Path oldPath = currentPlayerPaths.get(player);
            Intersection.Path newPath = intersection.getPathForLocation(playerLoc, oldPath);

            if (oldPath != newPath) {
                if (newPath == null) {
                    currentPlayerPaths.remove(player);
                } else {
                    currentPlayerPaths.put(player, newPath);
                }
            }

            if (newPath != null && (oldPath != newPath || forceRender)) {
                disablePacketRewriting = true;
                List<Block> realBlocks = new ArrayList<>();
                for (Location loc : intersection.getOtherLocations(newPath)) {
                    realBlocks.add(loc.getBlock());
                }
                Location tLoc = new Location(null, 0, 0, 0);
                for (Block block : realBlocks) {
                    block.getLocation(tLoc);
                    player.sendBlockChange(tLoc, block.getType(), block.getData());
                }
                for (Location loc : intersection.getLocations(newPath)) {
                    player.sendBlockChange(loc, intersection.getMaterial(), (byte) 0);
                }
                disablePacketRewriting = false;
            }

            boolean newInIntersection = newPath != null && intersection.isInIntersection(playerLoc, true);
            boolean exitedIntersection = playersInIntersection.contains(player) && (
                    oldPath != newPath || !newInIntersection
            );
            if (exitedIntersection) {
                playersInIntersection.remove(player);
                assert oldPath != null;
            }
            boolean enteredIntersection = newInIntersection && !playersInIntersection.contains(player);
            if (enteredIntersection) {
                playersInIntersection.add(player);
            }

            if (useInvisibility) {
                if (exitedIntersection) {
                    // Player should be revealed to everyone on the opposite path.
                    for (Map.Entry<Player, Intersection.Path> entry : currentPlayerPaths.entrySet()) {
                        Player pathPlayer = entry.getKey();
                        if (player == pathPlayer) {
                            continue;
                        }
                        Intersection.Path pathPlayerPath = entry.getValue();
                        if (oldPath != pathPlayerPath) {
                            pathPlayer.showPlayer(player);
                        }
                    }
                }

                if (oldPath != newPath) {
                    for (Player intersectionPlayer : playersInIntersection) {
                        if (player == intersectionPlayer) {
                            continue;
                        }
                        Intersection.Path intersectionPlayerPath = currentPlayerPaths.get(intersectionPlayer);
                        assert intersectionPlayerPath != null;

                        // No one in the intersection who had been on the opposing path should be
                        // hidden from player now.
                        if (oldPath != null && oldPath != intersectionPlayerPath) {
                            player.showPlayer(intersectionPlayer);
                        }

                        // Everyone in the intersection who is on the opposing path should be
                        // hidden from player now.
                        if (newPath != null && newPath != intersectionPlayerPath) {
                            player.hidePlayer(intersectionPlayer);
                        }
                    }
                }

                if (enteredIntersection) {
                    // Player should be hidden to everyone on the opposite path.
                    for (Map.Entry<Player, Intersection.Path> entry : currentPlayerPaths.entrySet()) {
                        Player pathPlayer = entry.getKey();
                        if (player == pathPlayer) {
                            continue;
                        }
                        Intersection.Path pathPlayerPath = entry.getValue();
                        if (newPath != pathPlayerPath) {
                            pathPlayer.hidePlayer(player);
                        }
                    }
                }
            }
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
        Player player = event.getPlayer();
        for (Intersection intersection : intersections) {
            intersection.getCurrentPlayerPaths().remove(player);
            intersection.getPlayersInIntersection().remove(player);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.LEFT_CLICK_BLOCK) {
            return;
        }
        Location clickedLoc = event.getClickedBlock().getLocation();
        if (allIntersectionBlockLocations.contains(clickedLoc)) {
            Player player = event.getPlayer();
            getServer().getScheduler().scheduleSyncDelayedTask(this, () -> {
                if (player.isOnline()) {
                    renderForPlayer(player, true);
                }
            });
        }
    }
}
