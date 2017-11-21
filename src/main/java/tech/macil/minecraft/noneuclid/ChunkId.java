package tech.macil.minecraft.noneuclid;

import com.google.common.base.Objects;
import org.bukkit.Location;
import org.bukkit.World;

public final class ChunkId {
    private final World world;
    private final int x;
    private final int z;

    public ChunkId(World world, int x, int z) {
        this.world = world;
        this.x = x;
        this.z = z;
    }

    public ChunkId(Location location) {
        this(location.getWorld(), location.getBlockX() >> 4, location.getBlockZ() >> 4);
    }

    public World getWorld() {
        return world;
    }

    public int getX() {
        return x;
    }

    public int getZ() {
        return z;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        } else if (!(obj instanceof ChunkId)) {
            return false;
        } else {
            ChunkId other = (ChunkId) obj;
            return this.world == other.world && this.x == other.x && this.z == other.z;
        }
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(world, x, z);
    }

    @Override
    public String toString() {
        return "ChunkId [world=" + world.getName() + ", x=" + x + ", z=" + z + "]";
    }
}
