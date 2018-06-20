package tech.macil.minecraft.noneuclid;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.junit.Test;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class IntersectionTest {
    private static final World world = mock(World.class);
    private static final World world2 = mock(World.class);

    private static final Location center = new Location(world, 10, 20, 30);
    private static final Intersection intersection = new Intersection(
            center, 5, 10, Material.STONE, Intersection.Path.EastWest, 100
    );

    @Test
    public void isLocationClose() {
        Location close = new Location(world, 25, 55, 35);
        assertTrue(intersection.isLocationClose(close));

        Location far = new Location(world, 105, 105, 35);
        assertFalse(intersection.isLocationClose(far));

        Location wrongWorld = center.clone();
        wrongWorld.setWorld(world2);
        assertFalse(intersection.isLocationClose(wrongWorld));
    }

    @Test
    public void isInIntersection() {
        assertTrue(intersection.isInIntersection(center));

        Location inside = new Location(world, 7, 29, 31);
        assertTrue(intersection.isInIntersection(inside));

        Location close = new Location(world, 25, 55, 35);
        assertFalse(intersection.isInIntersection(close));
    }
}