package tech.macil.minecraft.noneuclid;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Setup {
    public enum Path {
        NorthSouth,
        EastWest
    }

    private final Location center;
    private final int width;
    private final Material material;
    private final Path defaultPath;
    private final List<Location> northSouthLocations;
    private final List<Location> eastWestLocations;
    private final Map<Player, Path> currentPlayerPaths = new HashMap<>();
    private final double maxDistanceSquared;

    public Setup(Location center, int width, int height, Material material, Path defaultPath, int maxDistance) {
        this.center = center;
        this.width = width;
        this.material = material;
        this.defaultPath = defaultPath;
        this.maxDistanceSquared = Math.pow(maxDistance, 2);

        northSouthLocations = new ArrayList<>();
        for (double x : new double[]{center.getX() - (double) width / 2 - 1, center.getX() + (double) width / 2}) {
            for (double y = center.getY(); y < center.getY() + height; y++) {
                for (double z = center.getZ() - (double) width / 2; z < center.getZ() + (double) width / 2; z++) {
                    northSouthLocations.add(new Location(
                            center.getWorld(), Location.locToBlock(x),
                            Location.locToBlock(y), Location.locToBlock(z)
                    ));
                }
            }
        }
        eastWestLocations = new ArrayList<>();
        for (double z : new double[]{center.getZ() - (double) width / 2 - 1, center.getZ() + (double) width / 2}) {
            for (double y = center.getY(); y < center.getY() + height; y++) {
                for (double x = center.getX() - (double) width / 2; x < center.getX() + (double) width / 2; x++) {
                    eastWestLocations.add(new Location(
                            center.getWorld(), Location.locToBlock(x),
                            Location.locToBlock(y), Location.locToBlock(z)
                    ));
                }
            }
        }
    }

    public Location getCenter() {
        return center;
    }

    public Material getMaterial() {
        return material;
    }

    public Path getDefaultPath() {
        return defaultPath;
    }

    public List<Location> getNorthSouthLocations() {
        return northSouthLocations;
    }

    public List<Location> getEastWestLocations() {
        return eastWestLocations;
    }

    public Map<Player, Path> getCurrentPlayerPaths() {
        return currentPlayerPaths;
    }

    public boolean isLocationClose(Location loc) {
        return loc.getWorld() == center.getWorld() &&
                center.distanceSquared(loc) < maxDistanceSquared;
    }

    public Path getPathForLocation(Location loc) {
        assert loc.getWorld() == center.getWorld();
        if (
                Math.abs(loc.getX() - center.getX()) <= width &&
                        Math.abs(loc.getZ() - center.getZ()) <= width
                ) {
            return null;
        }
        boolean a1 = loc.getZ() > loc.getX() + center.getZ() - center.getX();
        boolean a2 = loc.getZ() > -loc.getX() + center.getZ() + center.getX();
        return a1 == a2 ? Path.NorthSouth : Path.EastWest;
    }
}
