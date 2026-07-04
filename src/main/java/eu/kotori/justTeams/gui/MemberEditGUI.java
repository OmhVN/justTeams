package eu.kotori.justTeams.gui;

import eu.kotori.justTeams.JustTeams;
import eu.kotori.justTeams.team.Team;
import eu.kotori.justTeams.team.TeamPlayer;
import eu.kotori.justTeams.team.TeamRole;
import eu.kotori.justTeams.util.GuiConfigManager;
import eu.kotori.justTeams.util.ItemBuilder;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

public class MemberEditGUI implements IRefreshableGUI, InventoryHolder {
   private final JustTeams plugin;
   private final Team team;
   private final Player viewer;
   private final UUID targetUuid;
   private final Inventory inventory;

   public MemberEditGUI(JustTeams plugin, Team team, Player viewer, UUID targetUuid) {
      this.plugin = plugin;
      this.team = team;
      this.viewer = viewer;
      this.targetUuid = targetUuid;
      GuiConfigManager guiManager = plugin.getGuiConfigManager();
      ConfigurationSection guiConfig = guiManager.getGUI("member-edit-gui");
      OfflinePlayer target = Bukkit.getOfflinePlayer(targetUuid);
      String title = guiConfig.getString("title", "Edit: <player_name>").replace("<player_name>", target.getName() != null ? target.getName() : "Unknown");
      int size = guiConfig.getInt("size", 54);
      this.inventory = Bukkit.createInventory(this, size, plugin.getMiniMessage().deserialize(title));
      this.initializeItems();
   }

   public void initializeItems() {
      this.inventory.clear();
      TeamPlayer targetMember = this.getTargetMember();
      if (targetMember != null) {
         GuiConfigManager guiManager = this.plugin.getGuiConfigManager();
         ConfigurationSection guiConfig = guiManager.getGUI("member-edit-gui");
         ConfigurationSection itemsSection = guiConfig.getConfigurationSection("items");
         if (itemsSection != null) {
            this.setItemFromConfig("player-info-head", itemsSection);
            boolean isSelfView = this.targetUuid.equals(this.viewer.getUniqueId());
            if (!isSelfView) {
               if (targetMember.getRole() == TeamRole.MEMBER) {
                  this.setItemFromConfig("promote-button", itemsSection);
               } else if (targetMember.getRole() == TeamRole.CO_OWNER) {
                  this.setItemFromConfig("demote-button", itemsSection);
               }

               this.setItemFromConfig("kick-button", itemsSection);
               if (this.team.isOwner(this.viewer.getUniqueId())) {
                  this.setItemFromConfig("transfer-button", itemsSection);
               }
            }

            if (isSelfView) {
               if (this.plugin.getConfigManager().isTeamBankEnabled()) {
                  this.setItemFromConfig("withdraw-permission-view", itemsSection);
               }

               if (this.plugin.getConfigManager().isTeamEnderchestEnabled()) {
                  this.setItemFromConfig("enderchest-permission-view", itemsSection);
               }

               if (this.plugin.getConfigManager().isTeamHomeSetEnabled()) {
                  this.setItemFromConfig("sethome-permission-view", itemsSection);
               }

               if (this.plugin.getConfigManager().isTeamHomeTeleportEnabled()) {
                  this.setItemFromConfig("usehome-permission-view", itemsSection);
               }
            } else {
               if (this.plugin.getConfigManager().isTeamBankEnabled()) {
                  this.setItemFromConfig("withdraw-permission", itemsSection);
               }

               if (this.plugin.getConfigManager().isTeamEnderchestEnabled()) {
                  this.setItemFromConfig("enderchest-permission", itemsSection);
               }

               if (this.plugin.getConfigManager().isTeamHomeSetEnabled()) {
                  this.setItemFromConfig("sethome-permission", itemsSection);
               }

               if (this.plugin.getConfigManager().isTeamHomeTeleportEnabled()) {
                  this.setItemFromConfig("usehome-permission", itemsSection);
               }

               if (targetMember.getRole() == TeamRole.CO_OWNER && this.team.isOwner(this.viewer.getUniqueId())) {
                  this.setItemFromConfig("toggle-promote-co-owner", itemsSection);
               }
            }

            this.setItemFromConfig("back-button", itemsSection);
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
   }

   private void setItemFromConfig(String key, ConfigurationSection parentSection) {
      ConfigurationSection itemConfig = parentSection.getConfigurationSection(key);
      if (itemConfig != null) {
         if (itemConfig.getBoolean("enabled", true)) {
            Material material = Material.matchMaterial(itemConfig.getString("material", "STONE"));
            String name = this.replacePlaceholders(itemConfig.getString("name", ""));
            List<String> lore = (List)itemConfig.getStringList("lore").stream().map(this::replacePlaceholders).collect(Collectors.toList());
            int slot = itemConfig.getInt("slot");
            ItemBuilder builder = (new ItemBuilder(material)).withName(name).withLore(lore).withAction(key);
            if (key.equals("player-info-head")) {
               builder.asPlayerHead(this.targetUuid);
            }

            this.inventory.setItem(slot, builder.build());
         }
      }
   }

   private String replacePlaceholders(String text) {
      TeamPlayer targetMember = this.getTargetMember();
      if (targetMember == null) {
         return text;
      } else {
         OfflinePlayer targetPlayer = Bukkit.getOfflinePlayer(this.targetUuid);
         String joinDate = this.formatJoinDate(targetMember.getJoinDate());
         String platformIndicator = this.getPlatformIndicator();
         String playerName = targetPlayer.getName() != null ? targetPlayer.getName() : "Unknown";
         return text.replace("<player_name>", playerName).replace("<target_name>", playerName).replace("<role_icon>", this.getRoleIcon(targetMember.getRole())).replace("<role>", this.getRoleName(targetMember.getRole())).replace("<joindate>", joinDate).replace("<withdraw_status>", this.getStatus(targetMember.canWithdraw())).replace("<enderchest_status>", this.getStatus(targetMember.canUseEnderChest())).replace("<set_home_status>", this.getStatus(targetMember.canSetHome())).replace("<use_home_status>", this.getStatus(targetMember.canUseHome())).replace("<promote_co_owner_status>", this.getStatus(targetMember.canPromoteToCoOwner())).replace("<platform_indicator>", platformIndicator);
      }
   }

   private String getRoleIcon(TeamRole role) {
      return this.plugin.getGuiConfigManager().getRoleIcon(role.name());
   }

   private String getPlatformIndicator() {
      boolean isBedrockPlayer = this.plugin.getBedrockSupport() != null && this.plugin.getBedrockSupport().isBedrockPlayer(this.targetUuid);
      if (this.plugin.getGuiConfigManager().getPlaceholder("platform.show_in_gui", "true").equals("true")) {
         if (isBedrockPlayer && this.plugin.getGuiConfigManager().getPlaceholder("platform.bedrock.enabled", "true").equals("true")) {
            return this.plugin.getGuiConfigManager().getPlaceholder("platform.bedrock.format", " <#00D4FF>[BE]</#00D4FF>");
         }

         if (!isBedrockPlayer && this.plugin.getGuiConfigManager().getPlaceholder("platform.java.enabled", "true").equals("true")) {
            return this.plugin.getGuiConfigManager().getPlaceholder("platform.java.format", " <#00FF00>[JE]</#00FF00>");
         }
      }

      return "";
   }

   private String getStatus(boolean hasPerm) {
      String key = hasPerm ? "status_enabled" : "status_disabled";
      String value = this.plugin.getMessageManager().getRawMessage(key);
      if (value != null && !value.isEmpty()) {
         return value;
      } else {
         return hasPerm ? "<green>ENABLED" : "<red>DISABLED";
      }
   }

   private String getRoleName(TeamRole role) {
      return this.plugin.getGuiConfigManager().getRoleName(role.name());
   }

   private String formatJoinDate(Instant joinDate) {
      try {
         if (joinDate != null) {
            String dateFormat = this.plugin.getGuiConfigManager().getPlaceholder("date_time.join_date_format", "dd MMM yyyy");
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern(dateFormat).withZone(ZoneOffset.UTC);
            return formatter.format(joinDate);
         } else {
            return this.plugin.getGuiConfigManager().getPlaceholder("date_time.unknown_date", "Unknown");
         }
      } catch (Exception e) {
         this.plugin.getLogger().warning("Error formatting join date: " + e.getMessage());
         return this.plugin.getGuiConfigManager().getPlaceholder("date_time.unknown_date", "Unknown");
      }
   }

   public void open() {
      this.viewer.openInventory(this.inventory);
   }

   public void refresh() {
      this.open();
   }

   public UUID getTargetUuid() {
      return this.targetUuid;
   }

   public TeamPlayer getTargetMember() {
      return this.team.getMember(this.targetUuid);
   }

   public Team getTeam() {
      return this.team;
   }

   public Inventory getInventory() {
      return this.inventory;
   }
}
