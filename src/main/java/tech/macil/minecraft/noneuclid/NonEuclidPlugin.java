package tech.macil.minecraft.noneuclid;

import com.google.common.collect.Iterables;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

public class NonEuclidPlugin extends JavaPlugin implements Listener {
    public enum Mode {
        A,
        B
    }

    private World mainWorld;
    private List<Location> CONFIG_A;
    private List<Location> CONFIG_B;
    private Map<Player, Mode> playerModes;
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
        CONFIG_A = new ArrayList<>();
        for (double x : new double[]{CENTER_X - HALL_WIDTH/2 - 1, CENTER_X + HALL_WIDTH/2}) {
            for (double y = CENTER_FLOOR; y < CENTER_FLOOR+HALL_HEIGHT; y++) {
                for (double z = CENTER_Z - HALL_WIDTH/2; z < CENTER_Z+HALL_WIDTH/2; z++) {
                    CONFIG_A.add(new Location(
                            mainWorld, Location.locToBlock(x),
                            Location.locToBlock(y), Location.locToBlock(z)
                    ));
                }
            }
        }
        CONFIG_B = new ArrayList<>();
        for (double z : new double[]{CENTER_Z - HALL_WIDTH/2 - 1, CENTER_Z + HALL_WIDTH/2}) {
            for (double y = CENTER_FLOOR; y < CENTER_FLOOR+HALL_HEIGHT; y++) {
                for (double x = CENTER_X - HALL_WIDTH/2; x < CENTER_X+HALL_WIDTH/2; x++) {
                    CONFIG_B.add(new Location(
                            mainWorld, Location.locToBlock(x),
                            Location.locToBlock(y), Location.locToBlock(z)
                    ));
                }
            }
        }
        getServer().getPluginManager().registerEvents(this, this);
    }

    @Override
    public void onDisable() {
        List<Block> blocks = new ArrayList<>();
        for (Location loc : Iterables.concat(CONFIG_A, CONFIG_B)) {
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

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (player.getWorld() != mainWorld) {
            playerModes.remove(player);
            return;
        }
        Location playerLoc = player.getLocation();
        if (CENTER_LOC.distanceSquared(playerLoc) > FORGET_DISTANCE_SQUARED) {
            playerModes.remove(player);
            return;
        }
        Mode newMode = getModeForLocation(playerLoc);
        if (newMode == null) {
            return;
        }
        Mode currentMode = playerModes.get(player);
        if (currentMode == newMode) {
            return;
        }

        List<Block> realBlocks = new ArrayList<>();
        for (Location loc : newMode == Mode.B ? CONFIG_A : CONFIG_B) {
            realBlocks.add(loc.getBlock());
        }
        Location mloc = new Location(null, 0, 0, 0);
        for (Block block : realBlocks) {
            block.getLocation(mloc);
            player.sendBlockChange(mloc, block.getType(), block.getData());
        }
        for (Location loc : newMode == Mode.A ? CONFIG_A : CONFIG_B) {
            player.sendBlockChange(loc, CONFIG_WALL, (byte) 0);
        }
        getLogger().log(Level.INFO, "Set player to new mode: " + newMode);
        playerModes.put(player, newMode);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        playerModes.remove(event.getPlayer());
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
        boolean a2 = loc.getZ() > - loc.getX() + CENTER_Z + CENTER_X;
        return a1 == a2 ? Mode.A : Mode.B;
    }
}
