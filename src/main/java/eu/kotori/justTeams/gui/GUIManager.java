package eu.kotori.justTeams.gui;

import eu.kotori.justTeams.JustTeams;
import eu.kotori.justTeams.util.ItemBuilder;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

public class GUIManager {
   private final JustTeams plugin;
   private File guiConfigFile;
   private FileConfiguration guiConfig;
   private final GUIUpdateThrottle updateThrottle;

   public GUIManager(JustTeams plugin) {
      this.plugin = plugin;
      this.updateThrottle = new GUIUpdateThrottle(plugin);
      this.createGuiConfig();
   }

   public void reload() {
      this.createGuiConfig();
   }

   private void createGuiConfig() {
      File guiFolder = new File(this.plugin.getDataFolder(), "gui");
      this.guiConfigFile = guiFolder;
      if (!guiFolder.exists()) {
         guiFolder.mkdirs();
      }

      YamlConfiguration mergedConfig = new YamlConfiguration();
      for (String fileName : eu.kotori.justTeams.util.GuiConfigManager.GUI_FILES) {
         File file = new File(guiFolder, fileName);
         if (!file.exists()) {
            this.plugin.saveResource("gui/" + fileName, false);
         }
         YamlConfiguration singleConfig = YamlConfiguration.loadConfiguration(file);
         for (String key : singleConfig.getKeys(false)) {
            mergedConfig.set(key, singleConfig.get(key));
         }
      }
      mergedConfig.set("gui-version", 15);
      this.guiConfig = mergedConfig;
   }

   public FileConfiguration getGuiConfig() {
      return this.guiConfig;
   }

   public ConfigurationSection getGUI(String key) {
      return this.guiConfig.getConfigurationSection(key);
   }

   public ConfigurationSection getNoTeamGUI() {
      return this.getGUI("no-team-gui");
   }

   public String getNoTeamGUITitle() {
      return this.getString("no-team-gui.title", "ᴛᴇᴀᴍ ᴍᴇɴᴜ");
   }

   public int getNoTeamGUISize() {
      return this.getInt("no-team-gui.size", 27);
   }

   public ConfigurationSection getCreateTeamButton() {
      return this.getGUI("no-team-gui.items.create-team");
   }

   public ConfigurationSection getLeaderboardsButton() {
      return this.getGUI("no-team-gui.items.leaderboards");
   }

   public ConfigurationSection getNoTeamGUIFillItem() {
      return this.getGUI("no-team-gui.fill-item");
   }

   public ConfigurationSection getTeamGUI() {
      return this.getGUI("team-gui");
   }

   public String getTeamGUITitle() {
      return this.getString("team-gui.title", "ᴛᴇᴀᴍ - <members>/<max_members>");
   }

   public int getTeamGUISize() {
      return this.getInt("team-gui.size", 54);
   }

   public ConfigurationSection getPlayerHeadSection() {
      return this.getGUI("team-gui.items.player-head");
   }

   public String getOnlineNameFormat() {
      return this.getString("team-gui.items.player-head.online-name-format", "<gradient:#4C9DDE:#4C96D2><status_indicator><role_icon><player></gradient>");
   }

   public String getOfflineNameFormat() {
      return this.getString("team-gui.items.player-head.offline-name-format", "<gray><status_indicator><role_icon><player>");
   }

   public List<String> getPlayerHeadLore() {
      return this.getStringList("team-gui.items.player-head.lore");
   }

   public String getCanEditPrompt() {
      return this.getString("team-gui.items.player-head.can-edit-prompt", "<yellow>Click to edit this member.</yellow>");
   }

   public String getCanViewPrompt() {
      return this.getString("team-gui.items.player-head.can-view-prompt", "<yellow>Click to view your information.</yellow>");
   }

   public String getCannotEditPrompt() {
      return this.getString("team-gui.items.player-head.cannot-edit-prompt", "");
   }

   public ConfigurationSection getJoinRequestsButton() {
      return this.getGUI("team-gui.items.join-requests");
   }

   public ConfigurationSection getJoinRequestsLockedButton() {
      return this.getGUI("team-gui.items.join-requests-locked");
   }

   public ConfigurationSection getWarpsButton() {
      return this.getGUI("team-gui.items.warps");
   }

   public ConfigurationSection getBankButton() {
      return this.getGUI("team-gui.items.bank");
   }

   public ConfigurationSection getBankLockedButton() {
      return this.getGUI("team-gui.items.bank-locked");
   }

   public ConfigurationSection getHomeButton() {
      return this.getGUI("team-gui.items.home");
   }

   public ConfigurationSection getTeamSettingsButton() {
      return this.getGUI("team-gui.items.team-settings");
   }

   public ConfigurationSection getTeamSettingsGUI() {
      return this.getGUI("team-settings-gui");
   }

   public String getTeamSettingsGUITitle() {
      return this.getString("team-settings-gui.title", "ᴛᴇᴀᴍ sᴇᴛᴛɪɴɢs");
   }

   public int getTeamSettingsGUISize() {
      return this.getInt("team-settings-gui.size", 27);
   }

   public ConfigurationSection getMemberEditGUI() {
      return this.getGUI("member-edit-gui");
   }

   public String getMemberEditGUITitle() {
      return this.getString("member-edit-gui.title", "ᴇᴅɪᴛ ᴍᴇᴍʙᴇʀ");
   }

   public int getMemberEditGUISize() {
      return this.getInt("member-edit-gui.size", 27);
   }

   public ConfigurationSection getMemberPermissionsGUI() {
      return this.getGUI("member-permissions-gui");
   }

   public String getMemberPermissionsGUITitle() {
      return this.getString("member-permissions-gui.title", "ᴍᴇᴍʙᴇʀ ᴘᴇʀᴍɪssɪᴏɴs");
   }

   public int getMemberPermissionsGUISize() {
      return this.getInt("member-permissions-gui.size", 27);
   }

   public ConfigurationSection getBankGUI() {
      return this.getGUI("bank-gui");
   }

   public String getBankGUITitle() {
      return this.getString("bank-gui.title", "ᴛᴇᴀᴍ ʙᴀɴᴋ");
   }

   public int getBankGUISize() {
      return this.getInt("bank-gui.size", 27);
   }

   public ConfigurationSection getWarpsGUI() {
      return this.getGUI("warps-gui");
   }

   public String getWarpsGUITitle() {
      return this.getString("warps-gui.title", "ᴛᴇᴀᴍ ᴡᴀʀᴘs");
   }

   public int getWarpsGUISize() {
      return this.getInt("warps-gui.size", 27);
   }

   public ConfigurationSection getLeaderboardGUI() {
      return this.getGUI("leaderboard-gui");
   }

   public String getLeaderboardGUITitle() {
      return this.getString("leaderboard-gui.title", "ᴛᴇᴀᴍ ʟᴇᴀᴅᴇʀʙᴏᴀʀᴅ");
   }

   public int getLeaderboardGUISize() {
      return this.getInt("leaderboard-gui.size", 27);
   }

   public ConfigurationSection getAdminGUI() {
      return this.getGUI("admin-gui");
   }

   public String getAdminGUITitle() {
      return this.getString("admin-gui.title", "ᴀᴅᴍɪɴ ᴘᴀɴᴇʟ");
   }

   public int getAdminGUISize() {
      return this.getInt("admin-gui.size", 27);
   }

   public String getString(String path, String defaultValue) {
      return this.guiConfig.getString(path, defaultValue);
   }

   public int getInt(String path, int defaultValue) {
      return this.guiConfig.getInt(path, defaultValue);
   }

   public boolean getBoolean(String path, boolean defaultValue) {
      return this.guiConfig.getBoolean(path, defaultValue);
   }

   public List<String> getStringList(String path) {
      return this.guiConfig.getStringList(path);
   }

   public Material getMaterial(String path, Material defaultValue) {
      String materialName = this.guiConfig.getString(path, defaultValue.name());

      try {
         return Material.valueOf(materialName.toUpperCase());
      } catch (IllegalArgumentException var5) {
         this.plugin.getLogger().log(Level.WARNING, "Invalid material " + materialName + " found in gui.yml at path " + path + ". Using default: " + defaultValue.name());
         return defaultValue;
      }
   }

   public Material getMaterial(String path) {
      return this.getMaterial(path, Material.STONE);
   }

   public boolean hasGUI(String key) {
      return this.guiConfig.contains(key);
   }

   public Set<String> getGUIKeys() {
      return this.guiConfig.getKeys(true);
   }

   public GUIUpdateThrottle getUpdateThrottle() {
      return this.updateThrottle;
   }

   public ConfigurationSection getItemConfig(String guiKey, String itemKey) {
      ConfigurationSection guiSection = this.getGUI(guiKey);
      return guiSection != null ? guiSection.getConfigurationSection("items." + itemKey) : null;
   }

   public int getItemSlot(String guiKey, String itemKey, int defaultValue) {
      ConfigurationSection itemSection = this.getItemConfig(guiKey, itemKey);
      return itemSection != null ? itemSection.getInt("slot", defaultValue) : defaultValue;
   }

   public Material getItemMaterial(String guiKey, String itemKey, Material defaultValue) {
      ConfigurationSection itemSection = this.getItemConfig(guiKey, itemKey);
      return itemSection != null ? this.getMaterial("items." + itemKey + ".material", defaultValue) : defaultValue;
   }

   public String getItemName(String guiKey, String itemKey, String defaultValue) {
      ConfigurationSection itemSection = this.getItemConfig(guiKey, itemKey);
      return itemSection != null ? itemSection.getString("name", defaultValue) : defaultValue;
   }

   public List<String> getItemLore(String guiKey, String itemKey) {
      ConfigurationSection itemSection = this.getItemConfig(guiKey, itemKey);
      return itemSection != null ? itemSection.getStringList("lore") : Collections.emptyList();
   }

   public static void loadDummyItems(Inventory inventory, ConfigurationSection guiConfig) {
      if (guiConfig != null) {
         ConfigurationSection dummyItemsSection = guiConfig.getConfigurationSection("dummy-items");
         if (dummyItemsSection != null) {
            for(String key : dummyItemsSection.getKeys(false)) {
               ConfigurationSection itemConfig = dummyItemsSection.getConfigurationSection(key);
               if (itemConfig != null) {
                  Object slotsObj = itemConfig.get("slot");
                  List<Integer> slots = new ArrayList();
                  if (slotsObj instanceof Integer) {
                     slots.add((Integer)slotsObj);
                  } else if (slotsObj instanceof List) {
                     for(Object slotObj : (List)slotsObj) {
                        if (slotObj instanceof Integer) {
                           slots.add((Integer)slotObj);
                        }
                     }
                  }

                  if (!slots.isEmpty()) {
                     Material material = Material.matchMaterial(itemConfig.getString("material", "STONE"));
                     if (material == null) {
                        material = Material.STONE;
                     }

                     ItemBuilder builder = new ItemBuilder(material);
                     String name = itemConfig.getString("name", "");
                     if (!name.isEmpty()) {
                        builder.withName(name);
                     }

                     List<String> lore = itemConfig.getStringList("lore");
                     if (!lore.isEmpty()) {
                        builder.withLore(lore);
                     }

                     if (itemConfig.contains("custom-model-data")) {
                        builder.withCustomModelData(itemConfig.getInt("custom-model-data"));
                     }

                     if (itemConfig.getBoolean("enchanted", false)) {
                        builder.withEnchantmentGlint();
                     }

                     ItemStack dummyItem = builder.build();

                     for(int slot : slots) {
                        if (slot >= 0 && slot < inventory.getSize()) {
                           inventory.setItem(slot, dummyItem);
                        }
                     }
                  }
               }
            }

         }
      }
   }
}
