package tech.macil.minecraft.noneuclid;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
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

public class NonEuclidPlugin extends JavaPlugin implements Listener {
    public enum Mode {
        A,
        B
    }

    private World mainWorld;
    private List<Location> SETUP_A;
    private List<Location> SETUP_B;
    private Set<Location> BOTH_SETUPS;
    private Map<Player, Mode> playerModes;
    private static final Mode DEFAULT_MODE = Mode.A;
    private static final double CENTER_X = 29;
    private static final double CENTER_Z = 221;
    private static final double CENTER_FLOOR = 64;
    private static final double HALL_HEIGHT = 3;
    private static final double HALL_WIDTH = 2;
    private static final double FORGET_DISTANCE_SQUARED = Math.pow(100, 2);
    private Location CENTER_LOC;
    private static final Material CONFIG_WALL = Material.STONE;

    @Override
    public void onEnable() {
        mainWorld = getServer().getWorld("world");
        CENTER_LOC = new Location(mainWorld, CENTER_X, CENTER_FLOOR, CENTER_Z);
        playerModes = new HashMap<>();
        SETUP_A = new ArrayList<>();
        for (double x : new double[]{CENTER_X - HALL_WIDTH / 2 - 1, CENTER_X + HALL_WIDTH / 2}) {
            for (double y = CENTER_FLOOR; y < CENTER_FLOOR + HALL_HEIGHT; y++) {
                for (double z = CENTER_Z - HALL_WIDTH / 2; z < CENTER_Z + HALL_WIDTH / 2; z++) {
                    SETUP_A.add(new Location(
                            mainWorld, Location.locToBlock(x),
                            Location.locToBlock(y), Location.locToBlock(z)
                    ));
                }
            }
        }
        SETUP_B = new ArrayList<>();
        for (double z : new double[]{CENTER_Z - HALL_WIDTH / 2 - 1, CENTER_Z + HALL_WIDTH / 2}) {
            for (double y = CENTER_FLOOR; y < CENTER_FLOOR + HALL_HEIGHT; y++) {
                for (double x = CENTER_X - HALL_WIDTH / 2; x < CENTER_X + HALL_WIDTH / 2; x++) {
                    SETUP_B.add(new Location(
                            mainWorld, Location.locToBlock(x),
                            Location.locToBlock(y), Location.locToBlock(z)
                    ));
                }
            }
        }
        BOTH_SETUPS = new HashSet<>();
        BOTH_SETUPS.addAll(SETUP_A);
        BOTH_SETUPS.addAll(SETUP_B);
        for (Player player : getServer().getOnlinePlayers()) {
            setupPlayer(player);
        }
        getServer().getPluginManager().registerEvents(this, this);
    }

    @Override
    public void onDisable() {
        List<Block> blocks = new ArrayList<>(BOTH_SETUPS.size());
        for (Location loc : BOTH_SETUPS) {
            blocks.add(loc.getBlock());
        }
        Location loc = new Location(null, 0, 0, 0);
        for (Player player : getServer().getOnlinePlayers()) {
            if (player.getWorld() != mainWorld) {
                continue;
            }
            for (Block block : blocks) {
                block.getLocation(loc);
                player.sendBlockChange(loc, block.getType(), block.getData());
            }
        }
        playerModes = null;
    }

    private void setupPlayer(Player player) {
        setupPlayer(player, false);
    }

    private void setupPlayer(Player player, boolean forceRender) {
        Location playerLoc = player.getLocation();
        if (!locationIsClose(playerLoc)) {
            playerModes.remove(player);
            return;
        }
        Mode currentMode = playerModes.get(player);
        Mode newMode = getModeForLocation(playerLoc);
        if (newMode == null) {
            if (currentMode == null) {
                newMode = DEFAULT_MODE;
            } else {
                if (!forceRender) {
                    return;
                } else {
                    newMode = currentMode;
                }
            }
        } else if (currentMode == newMode && !forceRender) {
            return;
        }

        List<Block> realBlocks = new ArrayList<>();
        for (Location loc : newMode == Mode.B ? SETUP_A : SETUP_B) {
            realBlocks.add(loc.getBlock());
        }
        Location mloc = new Location(null, 0, 0, 0);
        for (Block block : realBlocks) {
            block.getLocation(mloc);
            player.sendBlockChange(mloc, block.getType(), block.getData());
        }
        for (Location loc : newMode == Mode.A ? SETUP_A : SETUP_B) {
            player.sendBlockChange(loc, CONFIG_WALL, (byte) 0);
        }
        playerModes.put(player, newMode);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onPlayerMove(PlayerMoveEvent event) {
        setupPlayer(event.getPlayer());
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onPlayerLogin(PlayerLoginEvent event) {
        // Render the fake blocks in the next frame because any sent now
        // get overridden by the real chunk.
        Player player = event.getPlayer();
        getServer().getScheduler().scheduleSyncDelayedTask(this, () -> {
            if (player.isOnline()) {
                setupPlayer(player, true);
            }
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        playerModes.remove(event.getPlayer());
    }

    private boolean locationIsClose(Location loc) {
        return loc.getWorld() == mainWorld &&
                CENTER_LOC.distanceSquared(loc) < FORGET_DISTANCE_SQUARED;
    }

    private Mode getModeForLocation(Location loc) {
        assert loc.getWorld() == mainWorld;
        if (
                Math.abs(loc.getX() - CENTER_X) < HALL_WIDTH &&
                        Math.abs(loc.getZ() - CENTER_Z) < HALL_WIDTH
                ) {
            return null;
        }
        boolean a1 = loc.getZ() > loc.getX() + CENTER_Z - CENTER_X;
        boolean a2 = loc.getZ() > -loc.getX() + CENTER_Z + CENTER_X;
        return a1 == a2 ? Mode.A : Mode.B;
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (event.getAction() != Action.LEFT_CLICK_BLOCK) {
            return;
        }
        Location playerLoc = player.getLocation();
        if (!locationIsClose(playerLoc)) {
            return;
        }
        Location clickedLoc = event.getClickedBlock().getLocation();
        if (BOTH_SETUPS.contains(clickedLoc)) {
            getServer().getScheduler().scheduleSyncDelayedTask(this, () -> {
                if (player.isOnline()) {
                    setupPlayer(player, true);
                }
            });
        }
    }
}
