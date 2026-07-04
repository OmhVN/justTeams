package eu.kotori.justTeams.gui;

import eu.kotori.justTeams.JustTeams;
import eu.kotori.justTeams.core.storage.IDataStorage;
import eu.kotori.justTeams.core.team.Team;
import eu.kotori.justTeams.core.util.GuiConfigManager;
import eu.kotori.justTeams.core.util.GuiSlotResolver;
import eu.kotori.justTeams.core.util.ItemBuilder;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

public class WarpsGUI implements IRefreshableGUI, InventoryHolder {
   private final JustTeams plugin;
   private final Player viewer;
   private final Team team;
   private final Inventory inventory;

   public WarpsGUI(JustTeams plugin, Team team, Player viewer) {
      this.plugin = plugin;
      this.viewer = viewer;
      this.team = team;
      GuiConfigManager guiManager = plugin.getGuiConfigManager();
      ConfigurationSection guiConfig = guiManager.getGUI("warps-gui");
      String title = guiConfig != null ? guiConfig.getString("title", "ᴛᴇᴀᴍ ᴡᴀʀᴘs") : "ᴛᴇᴀᴍ ᴡᴀʀᴘs";
      int size = guiConfig != null ? guiConfig.getInt("size", 54) : 54;
      this.inventory = Bukkit.createInventory(this, size, plugin.getMiniMessage().deserialize(title));
      this.initializeItems();
   }

   public void initializeItems() {
      this.inventory.clear();
      GuiConfigManager guiManager = this.plugin.getGuiConfigManager();
      ConfigurationSection guiConfig = guiManager.getGUI("warps-gui");
      if (guiConfig == null) {
         this.plugin.getLogger().warning("warps-gui section not found in gui.yml!");
      } else {
         ConfigurationSection itemsConfig = guiConfig.getConfigurationSection("items");
         if (itemsConfig != null) {
            ItemStack border = (new ItemBuilder(guiManager.getMaterial("warps-gui.fill-item.material", Material.GRAY_STAINED_GLASS_PANE))).withName(guiManager.getString("warps-gui.fill-item.name", " ")).build();

            for(int i = 0; i < 9; ++i) {
               this.inventory.setItem(i, border);
            }

            for(int i = 45; i < 54; ++i) {
               this.inventory.setItem(i, border);
            }

            this.plugin.getTaskRunner().runAsync(() -> {
               List<IDataStorage.TeamWarp> warps = this.plugin.getStorageManager().getStorage().getWarps(this.team.getId());
               this.plugin.getTaskRunner().runOnEntity(this.viewer, () -> {
                  if (warps.isEmpty()) {
                     ConfigurationSection noWarpsConfig = itemsConfig.getConfigurationSection("no-warps");
                     if (noWarpsConfig != null && noWarpsConfig.getBoolean("enabled", true)) {
                        ItemStack noWarps = (new ItemBuilder(Material.matchMaterial(noWarpsConfig.getString("material", "BARRIER")))).withName(noWarpsConfig.getString("name", "<red><bold>No Warps Set</bold></red>")).withLore(noWarpsConfig.getStringList("lore")).build();
                        this.inventory.setItem(noWarpsConfig.getInt("slot", 22), noWarps);
                     }
                  } else {
                     ConfigurationSection warpConfig = itemsConfig.getConfigurationSection("warp-item");
                     List<Integer> warpSlots = GuiSlotResolver.resolve(warpConfig, 9, 45);
                     int slotIndex = 0;

                     for(IDataStorage.TeamWarp warp : warps) {
                        if (slotIndex >= warpSlots.size()) {
                           break;
                        }

                        if (warpConfig != null && warpConfig.getBoolean("enabled", true)) {
                           boolean canDelete = this.team.hasElevatedPermissions(this.viewer.getUniqueId()) || warp.name().equals(this.viewer.getName());
                           String name = warpConfig.getString("name", "<gradient:#4C9D9D:#4C96D2><bold><warp_name></bold></gradient>").replace("<warp_name>", warp.name());
                           List<String> lore = warpConfig.getStringList("lore");

                           for(int i = 0; i < lore.size(); ++i) {
                              String line = (String)lore.get(i);
                              String displayServerName = (String)this.plugin.getStorageManager().getStorage().getServerAlias(warp.serverName()).orElse(warp.serverName());
                              line = line.replace("<server_name>", displayServerName).replace("<server>", displayServerName).replace("<warp_protection_status>", warp.password() != null ? "<red>Password Protected" : "<green>Public").replace("<delete_prompt>", canDelete ? "<red>Right-Click to delete." : "");
                              lore.set(i, line);
                           }

                           ItemStack warpItem = (new ItemBuilder(warp.password() != null ? Material.IRON_BLOCK : Material.GOLD_BLOCK)).withName(name).withLore(lore).withAction("warp_item").withData("warp_name", warp.name()).build();
                           this.inventory.setItem((Integer)warpSlots.get(slotIndex++), warpItem);
                        }
                     }
                  }

                  ConfigurationSection backConfig = itemsConfig.getConfigurationSection("back-button");
                  if (backConfig != null && backConfig.getBoolean("enabled", true)) {
                     ItemStack backButton = (new ItemBuilder(Material.matchMaterial(backConfig.getString("material", "ARROW")))).withName(backConfig.getString("name", "<gray><bold>ʙᴀᴄᴋ</bold></gray>")).withLore(backConfig.getStringList("lore")).withAction("back-button").build();
                     this.inventory.setItem(backConfig.getInt("slot", 49), backButton);
                  }

               });
            });
         }
      }
   }

   public void open() {
      this.viewer.openInventory(this.inventory);
   }

   public void refresh() {
      this.open();
   }

   public Inventory getInventory() {
      return this.inventory;
   }

   public Team getTeam() {
      return this.team;
   }
}
