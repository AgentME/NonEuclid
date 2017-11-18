package tech.macil.minecraft.noneuclid;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

public class NonEuclidPlugin extends JavaPlugin implements Listener {
    private Inventory menuInventory;

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);

        ItemStack lapis = new ItemStack(Material.LAPIS_BLOCK, 1);
        {
            ItemMeta meta = lapis.getItemMeta();
            meta.setDisplayName(ChatColor.YELLOW + "Boop");
            lapis.setItemMeta(meta);
        }

        ItemStack redstone = new ItemStack(Material.REDSTONE_BLOCK, 1);
        {
            ItemMeta meta = redstone.getItemMeta();
            meta.setDisplayName(ChatColor.YELLOW + "More boop");
            redstone.setItemMeta(meta);
        }

        menuInventory = getServer().createInventory(null, 36, "Foo!");
        menuInventory.setItem(0, lapis);
        menuInventory.setItem(1, redstone);
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        Block block = event.getClickedBlock();
        if (player.isSneaking() ||
                block == null ||
                block.getType() != Material.IRON_BLOCK ||
                event.getAction() != Action.RIGHT_CLICK_BLOCK ||
                event.getHand() != EquipmentSlot.HAND
                ) {
            return;
        }
        event.setCancelled(true);
        event.getPlayer().openInventory(menuInventory);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!menuInventory.equals(event.getInventory())) {
            return;
        }
        event.setCancelled(true);

        int slot = event.getRawSlot();
        if (slot == 0) {
            getServer().getScheduler().scheduleSyncDelayedTask(this, () -> {
                event.getWhoClicked().closeInventory();
                event.getWhoClicked().sendMessage("You got booped!");
            });
        } else if (slot == 1) {
            getServer().getScheduler().scheduleSyncDelayedTask(this, () -> {
                event.getWhoClicked().closeInventory();
                event.getWhoClicked().sendMessage("You got more booped!");
            });
        }
    }
}
