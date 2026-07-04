package eu.kotori.justTeams.util;

import eu.kotori.justTeams.JustTeams;
import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

public class GuiConfigManager {
   private final JustTeams plugin;
   private File guiConfigFile;
   private volatile FileConfiguration guiConfig;
   private File placeholdersConfigFile;
   private volatile FileConfiguration placeholdersConfig;
   private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("<placeholder:([^>]+)>");

   public GuiConfigManager(JustTeams plugin) {
      this.plugin = plugin;
      this.reload();
   }

   public static final List<String> GUI_FILES = List.of(
      "no-team-gui.yml", "team-gui.yml", "team-settings-gui.yml", "blacklist-gui.yml",
      "join-requests-gui.yml", "invites-gui.yml", "warps-gui.yml", "member-edit-gui.yml",
      "member-permissions-edit-menu.yml", "bank-gui.yml", "confirm-gui.yml",
      "leaderboard-category-gui.yml", "leaderboard-view-gui.yml", "admin-team-list-gui.yml",
      "admin-team-manage-gui.yml", "admin-gui.yml", "admin-member-edit-gui.yml",
      "ally-gui.yml", "upgrades-gui.yml"
   );

   public synchronized void reload() {
      try {
         File guiFolder = new File(this.plugin.getDataFolder(), "gui");
         if (!guiFolder.exists()) {
            guiFolder.mkdirs();
         }

         YamlConfiguration mergedConfig = new YamlConfiguration();
         for (String fileName : GUI_FILES) {
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

         this.placeholdersConfigFile = new File(this.plugin.getDataFolder(), "placeholders.yml");
         if (!this.placeholdersConfigFile.exists()) {
            this.plugin.saveResource("placeholders.yml", false);
         }

         this.placeholdersConfig = YamlConfiguration.loadConfiguration(this.placeholdersConfigFile);
         this.plugin.getLogger().info("GUI and placeholders configuration reloaded successfully!");
      } catch (Exception e) {
         this.plugin.getLogger().severe("Failed to reload GUI configuration: " + e.getMessage());
      }
   }

   private void updateGuiConfig(int currentVersion) {
      this.plugin.getLogger().info("Updating GUI configuration from version " + currentVersion + " to 12...");
      if (currentVersion < 12) {
         if (!this.guiConfig.contains("blacklist-gui.items.no-blacklisted")) {
            this.guiConfig.set("blacklist-gui.items.no-blacklisted.slot", 22);
            this.guiConfig.set("blacklist-gui.items.no-blacklisted.enabled", true);
            this.guiConfig.set("blacklist-gui.items.no-blacklisted.material", "BOOK");
            this.guiConfig.set("blacklist-gui.items.no-blacklisted.name", "<gray><bold>No Blacklisted Players</bold></gray>");
            this.guiConfig.set("blacklist-gui.items.no-blacklisted.lore", List.of("<gray>No players are currently blacklisted.</gray>", "<gray>Use /team blacklist <player> to add someone.</gray>"));
            this.guiConfig.set("blacklist-gui.items.no-blacklisted.action", "no-blacklisted");
         }

         if (!this.guiConfig.contains("blacklist-gui.items.error-loading")) {
            this.guiConfig.set("blacklist-gui.items.error-loading.slot", 22);
            this.guiConfig.set("blacklist-gui.items.error-loading.enabled", true);
            this.guiConfig.set("blacklist-gui.items.error-loading.material", "BARRIER");
            this.guiConfig.set("blacklist-gui.items.error-loading.name", "<red><bold>Error Loading Blacklist</bold></red>");
            this.guiConfig.set("blacklist-gui.items.error-loading.lore", List.of("<red>Could not load blacklisted players.</red>"));
            this.guiConfig.set("blacklist-gui.items.error-loading.action", "error");
         }

         this.guiConfig.set("gui-version", 12);

         try {
            this.guiConfig.save(this.guiConfigFile);
            this.plugin.getLogger().info("GUI configuration updated successfully!");
         } catch (Exception e) {
            this.plugin.getLogger().severe("Failed to save updated GUI configuration: " + e.getMessage());
         }
      }

   }

   public ConfigurationSection getGUI(String key) {
      return this.guiConfig.getConfigurationSection(key);
   }

   public String getString(String path, String def) {
      String value = this.guiConfig.getString(path, def);
      return this.replacePlaceholders(value);
   }

   public List<String> getStringList(String path) {
      if (!this.guiConfig.isSet(path)) {
         return Collections.emptyList();
      } else {
         List<String> list = this.guiConfig.getStringList(path);
         return (List)list.stream().map(this::replacePlaceholders).collect(Collectors.toList());
      }
   }

   public Material getMaterial(String path, Material def) {
      String materialName = this.guiConfig.getString(path, def.name());

      try {
         return Material.valueOf(materialName.toUpperCase());
      } catch (IllegalArgumentException var5) {
         this.plugin.getLogger().log(Level.WARNING, "Invalid material " + materialName + " found in gui.yml at path " + path + ". Using default: " + def.name());
         return def;
      }
   }

   public String getPlaceholder(String path, String def) {
      if (this.placeholdersConfig == null) {
         this.plugin.getLogger().warning("Placeholders config not loaded, using default: " + def);
         return def;
      } else {
         return this.placeholdersConfig.getString(path, def);
      }
   }

   public String getPlaceholder(String path) {
      if (this.placeholdersConfig == null) {
         this.plugin.getLogger().warning("Placeholders config not loaded, using empty string");
         return "";
      } else {
         return this.placeholdersConfig.getString(path, "");
      }
   }

   public List<String> getPlaceholderList(String path) {
      if (this.placeholdersConfig == null) {
         this.plugin.getLogger().warning("Placeholders config not loaded, returning empty list");
         return Collections.emptyList();
      } else if (!this.placeholdersConfig.isSet(path)) {
         return Collections.emptyList();
      } else {
         List<String> list = this.placeholdersConfig.getStringList(path);
         return (List)list.stream().map(this::replacePlaceholders).collect(Collectors.toList());
      }
   }

   public ConfigurationSection getPlaceholderSection(String path) {
      if (this.placeholdersConfig == null) {
         this.plugin.getLogger().warning("Placeholders config not loaded, returning null for section: " + path);
         return null;
      } else {
         return this.placeholdersConfig.getConfigurationSection(path);
      }
   }

   public String getRoleIcon(String role) {
      return this.getPlaceholder("roles." + role.toLowerCase() + ".icon", "");
   }

   public String getRoleName(String role) {
      return this.getPlaceholder("roles." + role.toLowerCase() + ".name", role);
   }

   public String getRoleColor(String role) {
      return this.getPlaceholder("roles." + role.toLowerCase() + ".color", "#FFFFFF");
   }

   public String getStatusIcon(boolean isOnline) {
      String status = isOnline ? "online" : "offline";
      return this.getPlaceholder("status." + status + ".icon", isOnline ? "●" : "●");
   }

   public String getStatusColor(boolean isOnline) {
      String status = isOnline ? "online" : "offline";
      return this.getPlaceholder("status." + status + ".color", isOnline ? "#00FF00" : "#FF0000");
   }

   public String getSortName(String sortType) {
      return this.getPlaceholder("sort." + sortType.toLowerCase() + ".name", sortType);
   }

   public String getSortIcon(String sortType) {
      return this.getPlaceholder("sort." + sortType.toLowerCase() + ".icon", "");
   }

   public String getSortSelectedPrefix() {
      return this.getPlaceholder("sort.selected_prefix", "<green>▪ <white>");
   }

   public String getSortUnselectedPrefix() {
      return this.getPlaceholder("sort.unselected_prefix", "<gray>▪ <white>");
   }

   public String getColor(String colorKey) {
      return this.getPlaceholder("colors." + colorKey, "#FFFFFF");
   }

   public String getPermissionIcon(String permissionKey) {
      return this.getPlaceholder("permissions." + permissionKey + "_icon", "\ud83d\udeab");
   }

   public String getErrorIcon(String errorKey) {
      return this.getPlaceholder("errors." + errorKey + "_icon", "❌");
   }

   public String getSuccessIcon(String successKey) {
      return this.getPlaceholder("success." + successKey + "_icon", "✅");
   }

   public String getTeamDisplayFormat() {
      return this.getPlaceholder("team_display.format", "<team_color><team_icon><team_tag></team_color>");
   }

   public String getTeamDisplayIcon() {
      return this.getPlaceholder("team_display.team_icon", "⚔ ");
   }

   public String getTeamDisplayColor() {
      return this.getPlaceholder("team_display.team_color", "#4C9DDE");
   }

   public String getTeamDisplayNoTeam() {
      return this.getPlaceholder("team_display.no_team", "<gray>No Team</gray>");
   }

   public boolean getTeamDisplayShowIcon() {
      return this.getPlaceholder("team_display.show_icon", "true").equals("true");
   }

   public boolean getTeamDisplayShowTag() {
      return this.getPlaceholder("team_display.show_tag", "true").equals("true");
   }

   public boolean getTeamDisplayShowName() {
      return this.getPlaceholder("team_display.show_name", "false").equals("true");
   }

   private String replacePlaceholders(String text) {
      if (text != null && text.contains("<placeholder:")) {
         Matcher matcher = PLACEHOLDER_PATTERN.matcher(text);

         StringBuffer result;
         String replacement;
         for(result = new StringBuffer(); matcher.find(); matcher.appendReplacement(result, Matcher.quoteReplacement(replacement))) {
            String key = matcher.group(1);
            replacement = this.getPlaceholder(key, matcher.group(0));
            if (this.plugin.getConfigManager().isDebugEnabled()) {
               Logger var10000 = this.plugin.getLogger();
               String var10001 = matcher.group(0);
               var10000.info("Replacing placeholder: " + var10001 + " -> " + replacement);
            }
         }

         matcher.appendTail(result);
         return result.toString();
      } else {
         return text;
      }
   }

   public void testPlaceholders() {
      try {
         this.plugin.getLogger().info("Testing placeholder system...");
         String roleIcon = this.getRoleIcon("owner");
         this.plugin.getLogger().info("Owner role icon: " + roleIcon);
         String sortName = this.getSortName("join_date");
         this.plugin.getLogger().info("Join date sort name: " + sortName);
         String statusIcon = this.getStatusIcon(true);
         this.plugin.getLogger().info("Online status icon: " + statusIcon);
         String testText = "Test <placeholder:roles.owner.icon> and <placeholder:sort.join_date.name>";
         String replaced = this.replacePlaceholders(testText);
         this.plugin.getLogger().info("Placeholder replacement test: " + testText + " -> " + replaced);
         this.plugin.getLogger().info("Placeholder system test completed!");
      } catch (Exception e) {
         this.plugin.getLogger().severe("Error during placeholder system test: " + e.getMessage());
         this.plugin.getLogger().severe("Failed to reload GUI config: " + e.getMessage());
      }

   }
}
