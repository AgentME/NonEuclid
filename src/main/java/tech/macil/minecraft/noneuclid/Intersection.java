package tech.macil.minecraft.noneuclid;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.*;

public class Intersection {
    public enum Path {
        NorthSouth,
        EastWest
    }

    private final Location center;
    private final int width;
    private final int height;
    private final Material material;
    private final Path defaultPath;
    private final List<Location> northSouthLocations;
    private final List<Location> eastWestLocations;
    private final Set<Location> allLocations = new HashSet<>();
    private final Map<Player, Path> currentPlayerPaths = new HashMap<>();
    private final Set<Player> playersInIntersection = new HashSet<>();
    private final double maxDistanceSquared;

    public Intersection(Location center, int width, int height, Material material, Path defaultPath, int maxDistance) {
        center = calculateAccurateCenter(center, width);
        this.center = center;
        this.width = width;
        this.height = height;
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
        allLocations.addAll(northSouthLocations);
        allLocations.addAll(eastWestLocations);
    }

    static Location calculateAccurateCenter(Location loc, int width) {
        if (width % 2 == 0) {
            return new Location(
                    loc.getWorld(),
                    Math.round(loc.getX()), Math.round(loc.getY()), Math.round(loc.getZ())
            );
        } else {
            return new Location(
                    loc.getWorld(),
                    Math.floor(loc.getX()) + 0.5, Math.round(loc.getY()), Math.floor(loc.getZ()) + 0.5
            );
        }
    }

    Location getCenter() {
        return center;
    }

    public Material getMaterial() {
        return material;
    }

    public Path getDefaultPath() {
        return defaultPath;
    }

    public Set<Location> getAllLocations() {
        return allLocations;
    }

    public List<Location> getLocations(Path path) {
        switch (path) {
            case NorthSouth:
                return northSouthLocations;
            case EastWest:
                return eastWestLocations;
            default:
                throw new RuntimeException("Invalid path value: " + path);
        }
    }

    public List<Location> getOtherLocations(Path path) {
        switch (path) {
            case NorthSouth:
                return eastWestLocations;
            case EastWest:
                return northSouthLocations;
            default:
                throw new RuntimeException("Invalid path value: " + path);
        }
    }

    public Map<Player, Path> getCurrentPlayerPaths() {
        return currentPlayerPaths;
    }

    public Set<Player> getPlayersInIntersection() {
        return playersInIntersection;
    }

    boolean isLocationClose(Location loc) {
        return loc.getWorld() == center.getWorld() &&
                center.distanceSquared(loc) < maxDistanceSquared;
    }

    public boolean isInIntersection(Location loc) {
        return isInAboveOrBelowIntersection(loc, true) &&
                loc.getY() >= center.getY() - 1.5 &&
                loc.getY() <= center.getY() + height - 0.5;
    }

    private boolean isInAboveOrBelowIntersection(Location loc, boolean includeWalls) {
        double maxDistance = (double) width / 2;
        if (includeWalls) {
            maxDistance += 1;
        }
        return Math.abs(loc.getX() - center.getX()) <= maxDistance &&
                Math.abs(loc.getZ() - center.getZ()) <= maxDistance;
    }

    public Path getPathForLocation(Location loc, Path previousPath) {
        if (!isLocationClose(loc)) {
            return null;
        }
        assert loc.getWorld() == center.getWorld();
        if (isInAboveOrBelowIntersection(loc, false)) {
            return previousPath == null ? getDefaultPath() : previousPath;
        }
        boolean a1 = loc.getZ() > loc.getX() + center.getZ() - center.getX();
        boolean a2 = loc.getZ() > -loc.getX() + center.getZ() + center.getX();
        return a1 == a2 ? Path.NorthSouth : Path.EastWest;
    }
}
