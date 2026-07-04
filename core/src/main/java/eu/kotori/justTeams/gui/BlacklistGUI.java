package eu.kotori.justTeams.gui;

import eu.kotori.justTeams.JustTeams;
import eu.kotori.justTeams.core.team.BlacklistedPlayer;
import eu.kotori.justTeams.core.team.Team;
import eu.kotori.justTeams.core.util.GuiConfigManager;
import eu.kotori.justTeams.core.util.GuiSlotResolver;
import eu.kotori.justTeams.core.util.ItemBuilder;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.logging.Logger;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;

public class BlacklistGUI implements InventoryHolder, IRefreshableGUI {
   private final JustTeams plugin;
   private final Team team;
   private final Player viewer;
   private Inventory inventory;

   public BlacklistGUI(JustTeams plugin, Team team, Player viewer) {
      this.plugin = plugin;
      this.team = team;
      this.viewer = viewer;
      GuiConfigManager guiManager = plugin.getGuiConfigManager();
      ConfigurationSection guiConfig = guiManager.getGUI("blacklist-gui");
      String title = guiConfig != null ? guiConfig.getString("title", "ᴛᴇᴀᴍ ʙʟᴀᴄᴋʟɪsᴛ") : "ᴛᴇᴀᴍ ʙʟᴀᴄᴋʟɪsᴛ";
      int size = guiConfig != null ? guiConfig.getInt("size", 54) : 54;
      this.inventory = Bukkit.createInventory(this, size, Component.text(title));
      this.initializeItems();
   }

   public void initializeItems() {
      this.inventory.clear();
      ConfigurationSection itemsSection = this.plugin.getGuiConfigManager().getGUI("blacklist-gui").getConfigurationSection("items");
      ConfigurationSection fillConfig = this.plugin.getGuiConfigManager().getGUI("blacklist-gui").getConfigurationSection("fill-item");
      if (fillConfig != null) {
         ItemStack fillItem = (new ItemBuilder(Material.matchMaterial(fillConfig.getString("material", "GRAY_STAINED_GLASS_PANE")))).withName(fillConfig.getString("name", " ")).build();

         for(int i = 0; i < this.inventory.getSize(); ++i) {
            this.inventory.setItem(i, fillItem);
         }
      }

      if (itemsSection != null) {
         this.setItemFromConfig(itemsSection, "blacklist-header");
         this.setItemFromConfig(itemsSection, "back-button");
      }

      this.loadBlacklistedPlayers();
   }

   private void setItemFromConfig(ConfigurationSection itemsSection, String key) {
      ConfigurationSection itemConfig = itemsSection.getConfigurationSection(key);
      if (itemConfig != null) {
         if (itemConfig.getBoolean("enabled", true)) {
            int slot = itemConfig.getInt("slot", -1);
            if (slot != -1) {
               Material material = Material.matchMaterial(itemConfig.getString("material", "STONE"));
               String name = itemConfig.getString("name", "");
               List<String> lore = itemConfig.getStringList("lore");
               String action = itemConfig.getString("action", key);
               this.inventory.setItem(slot, (new ItemBuilder(material)).withName(name).withLore(lore).withAction(action).build());
            }
         }
      }
   }

   private void loadBlacklistedPlayers() {
      this.plugin.getTaskRunner().runAsync(() -> {
         try {
            List<BlacklistedPlayer> blacklist = this.plugin.getStorageManager().getStorage().getTeamBlacklist(this.team.getId());
            ConfigurationSection itemsSection = this.plugin.getGuiConfigManager().getGUI("blacklist-gui").getConfigurationSection("items");
            if (blacklist.isEmpty()) {
               this.plugin.getTaskRunner().runOnEntity(this.viewer, () -> {
                  if (itemsSection != null && itemsSection.contains("no-blacklisted")) {
                     this.setItemFromConfig(itemsSection, "no-blacklisted");
                  }

               });
               return;
            }

            ConfigurationSection blacklistItemSection = this.plugin.getGuiConfigManager().getGUI("blacklist-gui").getConfigurationSection("items.player-head");
            List<Integer> blacklistSlots = GuiSlotResolver.resolve(blacklistItemSection, 9, 45);
            int slotIndex = 0;

            for(BlacklistedPlayer blacklistedPlayer : blacklist) {
               if (slotIndex >= blacklistSlots.size()) {
                  break;
               }

               int currentSlot = (Integer)blacklistSlots.get(slotIndex++);
               this.plugin.getTaskRunner().runOnEntity(this.viewer, () -> this.inventory.setItem(currentSlot, this.createBlacklistedPlayerItem(blacklistedPlayer)));
            }
         } catch (Exception e) {
            this.plugin.getLogger().severe("Error loading team blacklist: " + e.getMessage());
            this.plugin.getTaskRunner().runOnEntity(this.viewer, () -> {
               ConfigurationSection itemsSection = this.plugin.getGuiConfigManager().getGUI("blacklist-gui").getConfigurationSection("items");
               if (itemsSection != null && itemsSection.contains("error-loading")) {
                  this.setItemFromConfig(itemsSection, "error-loading");
               }

            });
         }

      });
   }

   private ItemStack createBlacklistedPlayerItem(BlacklistedPlayer blacklistedPlayer) {
      OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(blacklistedPlayer.getPlayerUuid());
      OfflinePlayer blacklistedBy = Bukkit.getOfflinePlayer(blacklistedPlayer.getBlacklistedByUuid());
      String timeAgo = this.formatTimeAgo(blacklistedPlayer.getBlacklistedAt());
      String actionKey = "remove-blacklist:" + blacklistedPlayer.getPlayerUuid().toString();
      ConfigurationSection itemsSection = this.plugin.getGuiConfigManager().getGUI("blacklist-gui").getConfigurationSection("items");
      ConfigurationSection itemConfig = itemsSection != null ? itemsSection.getConfigurationSection("player-head") : null;
      Material material = Material.PLAYER_HEAD;
      String name = "<red><bold>" + blacklistedPlayer.getPlayerName() + "</bold></red>";
      List<String> lore = List.of();
      if (itemConfig != null) {
         material = Material.matchMaterial(itemConfig.getString("material", "PLAYER_HEAD"));
         name = itemConfig.getString("name", name).replace("<player_name>", blacklistedPlayer.getPlayerName());
         lore = itemConfig.getStringList("lore").stream().map((line) -> line.replace("<blacklister>", blacklistedBy.getName() != null ? blacklistedBy.getName() : "Unknown").replace("<date>", timeAgo).replace("<reason>", blacklistedPlayer.getReason())).toList();
      }

      Logger var10000 = this.plugin.getLogger();
      String var10001 = blacklistedPlayer.getPlayerName();
      var10000.info("Creating blacklist item for " + var10001 + " with action: " + actionKey);
      ItemStack itemStack = new ItemStack(material);
      if (material == Material.PLAYER_HEAD) {
         ItemMeta var13 = itemStack.getItemMeta();
         if (var13 instanceof SkullMeta) {
            SkullMeta skullMeta = (SkullMeta)var13;
            skullMeta.setOwningPlayer(offlinePlayer);
            itemStack.setItemMeta(skullMeta);
         }
      }

      itemStack = (new ItemBuilder(itemStack)).withName(name).withLore(lore).withAction(actionKey).build();
      if (itemStack.getItemMeta() != null) {
         String actualAction = (String)itemStack.getItemMeta().getPersistentDataContainer().get(JustTeams.getActionKey(), PersistentDataType.STRING);
         if (!actionKey.equals(actualAction)) {
            ItemMeta meta = itemStack.getItemMeta();
            if (meta != null) {
               meta.getPersistentDataContainer().set(JustTeams.getActionKey(), PersistentDataType.STRING, actionKey);
               itemStack.setItemMeta(meta);
            }
         }
      }

      return itemStack;
   }

   private String formatTimeAgo(Instant blacklistedAt) {
      Duration duration = Duration.between(blacklistedAt, Instant.now());
      if (duration.toDays() > 0L) {
         long var4 = duration.toDays();
         return var4 + " day" + (duration.toDays() == 1L ? "" : "s") + " ago";
      } else if (duration.toHours() > 0L) {
         long var3 = duration.toHours();
         return var3 + " hour" + (duration.toHours() == 1L ? "" : "s") + " ago";
      } else if (duration.toMinutes() > 0L) {
         long var10000 = duration.toMinutes();
         return var10000 + " minute" + (duration.toMinutes() == 1L ? "" : "s") + " ago";
      } else {
         return "Just now";
      }
   }

   public void open() {
      this.viewer.openInventory(this.inventory);
   }

   public Inventory getInventory() {
      return this.inventory;
   }

   public Team getTeam() {
      return this.team;
   }

   public void refresh() {
      if (this.plugin.getConfigManager().isDebugEnabled()) {
         this.plugin.getLogger().info("Refreshing blacklist GUI for team " + this.team.getName());
      }

      if (this.viewer != null && this.viewer.isOnline()) {
         this.plugin.getGuiManager().getUpdateThrottle().scheduleUpdate(this.viewer.getUniqueId(), () -> this.plugin.getTaskRunner().runOnEntity(this.viewer, () -> {
               try {
                  this.initializeItems();
                  if (this.plugin.getConfigManager().isDebugEnabled()) {
                     this.plugin.getLogger().info("Blacklist GUI refresh completed for team " + this.team.getName());
                  }
               } catch (Exception e) {
                  Logger var10000 = this.plugin.getLogger();
                  String var10001 = this.team.getName();
                  var10000.severe("Error refreshing blacklist GUI for team " + var10001 + ": " + e.getMessage());
               }

            }));
      }

   }
}
