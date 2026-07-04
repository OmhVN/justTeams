package eu.kotori.justTeams.gui;

import eu.kotori.justTeams.JustTeams;
import eu.kotori.justTeams.util.GuiConfigManager;
import eu.kotori.justTeams.util.ItemBuilder;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

public class NoTeamGUI implements IRefreshableGUI, InventoryHolder {
   private final JustTeams plugin;
   private final Player viewer;
   private final Inventory inventory;

   public NoTeamGUI(JustTeams plugin, Player viewer) {
      this.plugin = plugin;
      this.viewer = viewer;
      GuiConfigManager guiManager = plugin.getGuiConfigManager();
      ConfigurationSection guiConfig = guiManager.getGUI("no-team-gui");
      String title = guiConfig.getString("title", "ᴛᴇᴀᴍ ᴍᴇɴᴜ");
      int size = guiConfig.getInt("size", 27);
      this.inventory = Bukkit.createInventory(this, size, plugin.getMiniMessage().deserialize(title));
      this.initializeItems(guiConfig);
   }

   private void initializeItems(ConfigurationSection guiConfig) {
      this.inventory.clear();
      ConfigurationSection itemsSection = guiConfig.getConfigurationSection("items");
      if (itemsSection != null) {
         this.setItemFromConfig(itemsSection, "create-team");
         this.setItemFromConfig(itemsSection, "leaderboards");
      }

      ConfigurationSection fillConfig = guiConfig.getConfigurationSection("fill-item");
      if (fillConfig != null) {
         ItemStack fillItem = (new ItemBuilder(Material.matchMaterial(fillConfig.getString("material", "GRAY_STAINED_GLASS_PANE")))).withName(fillConfig.getString("name", " ")).build();

         for(int i = 0; i < this.inventory.getSize(); ++i) {
            if (this.inventory.getItem(i) == null) {
               this.inventory.setItem(i, fillItem);
            }
         }
      }

      this.loadCustomDummyItems(guiConfig);
   }

   private void setItemFromConfig(ConfigurationSection itemsSection, String key) {
      ConfigurationSection itemConfig = itemsSection.getConfigurationSection(key);
      if (itemConfig != null) {
         if (itemConfig.getBoolean("enabled", true)) {
            int slot = itemConfig.getInt("slot");
            Material material = Material.matchMaterial(itemConfig.getString("material", "STONE"));
            String name = itemConfig.getString("name", "");
            List<String> lore = itemConfig.getStringList("lore");
            this.inventory.setItem(slot, (new ItemBuilder(material)).withName(name).withLore(lore).withAction(key).build());
         }
      }
   }

   private void loadCustomDummyItems(ConfigurationSection guiConfig) {
      GUIManager.loadDummyItems(this.inventory, guiConfig);
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
}
