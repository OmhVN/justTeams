package eu.kotori.justTeams.gui;

import eu.kotori.justTeams.JustTeams;
import eu.kotori.justTeams.core.config.MessageManager;
import eu.kotori.justTeams.core.util.GuiConfigManager;
import eu.kotori.justTeams.core.util.ItemBuilder;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

public class LeaderboardCategoryGUI implements InventoryHolder {
   private final JustTeams plugin;
   private final Player viewer;
   private final Inventory inventory;

   public LeaderboardCategoryGUI(JustTeams plugin, Player viewer) {
      this.plugin = plugin;
      this.viewer = viewer;
      GuiConfigManager guiManager = plugin.getGuiConfigManager();
      ConfigurationSection guiConfig = guiManager.getGUI("leaderboard-category-gui");
      MessageManager messageManager = plugin.getMessageManager();
      String title = messageManager.hasMessage("leaderboard_category_title") ? messageManager.getRawMessage("leaderboard_category_title") : guiConfig.getString("title", "ᴛᴇᴀᴍ ʟᴇᴀᴅᴇʀʙᴏᴀʀᴅ");
      int size = guiConfig.getInt("size", 27);
      this.inventory = Bukkit.createInventory(this, size, plugin.getMiniMessage().deserialize(title));
      this.initializeItems();
   }

   private void initializeItems() {
      this.inventory.clear();
      GuiConfigManager guiManager = this.plugin.getGuiConfigManager();
      ConfigurationSection guiConfig = guiManager.getGUI("leaderboard-category-gui");
      ConfigurationSection itemsConfig = guiConfig.getConfigurationSection("items");
      if (itemsConfig != null) {
         this.setItemFromConfig(itemsConfig, "top-kills");
         this.setItemFromConfig(itemsConfig, "top-balance");
         this.setItemFromConfig(itemsConfig, "top-members");
         this.setItemFromConfig(itemsConfig, "back-button");
         ConfigurationSection fillConfig = guiConfig.getConfigurationSection("fill-item");
         if (fillConfig != null) {
            ItemStack fillItem = (new ItemBuilder(Material.matchMaterial(fillConfig.getString("material", "GRAY_STAINED_GLASS_PANE")))).withName(fillConfig.getString("name", " ")).build();

            for(int i = 0; i < this.inventory.getSize(); ++i) {
               if (this.inventory.getItem(i) == null) {
                  this.inventory.setItem(i, fillItem);
               }
            }
         }

      }
   }

   private void setItemFromConfig(ConfigurationSection itemsSection, String key) {
      ConfigurationSection itemConfig = itemsSection.getConfigurationSection(key);
      if (itemConfig != null) {
         if (itemConfig.getBoolean("enabled", true)) {
            int slot = itemConfig.getInt("slot", -1);
            if (slot != -1) {
               Material material = Material.matchMaterial(itemConfig.getString("material", "STONE"));
               MessageManager messageManager = this.plugin.getMessageManager();
               String messageKeyPrefix = this.getMessageKeyPrefix(key);
               String name;
               List<String> lore;
               if (messageKeyPrefix != null && messageManager.hasMessage(messageKeyPrefix + "_name")) {
                  name = messageManager.getRawMessage(messageKeyPrefix + "_name");
                  lore = messageManager.getRawMessageList(messageKeyPrefix + "_lore");
                  if (lore.isEmpty()) {
                     lore = itemConfig.getStringList("lore");
                  }
               } else {
                  name = itemConfig.getString("name", "");
                  lore = itemConfig.getStringList("lore");
               }

               this.inventory.setItem(slot, (new ItemBuilder(material)).withName(name).withLore(lore).withAction(key).build());
            }
         }
      }
   }

   private String getMessageKeyPrefix(String key) {
      String var10000;
      switch (key) {
         case "top-kills" -> var10000 = "leaderboard_category_kills";
         case "top-balance" -> var10000 = "leaderboard_category_balance";
         case "top-members" -> var10000 = "leaderboard_category_members";
         case "back-button" -> var10000 = "leaderboard_category_back";
         default -> var10000 = null;
      }

      return var10000;
   }

   public void open() {
      this.viewer.openInventory(this.inventory);
   }

   public Inventory getInventory() {
      return this.inventory;
   }
}
