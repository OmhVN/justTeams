package eu.kotori.justTeams.gui;

import eu.kotori.justTeams.JustTeams;
import eu.kotori.justTeams.team.Team;
import eu.kotori.justTeams.util.GuiConfigManager;
import eu.kotori.justTeams.util.GuiSlotResolver;
import eu.kotori.justTeams.util.ItemBuilder;
import java.util.List;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

public class JoinRequestGUI implements IRefreshableGUI, InventoryHolder {
   private final JustTeams plugin;
   private final Player viewer;
   private final Team team;
   private final Inventory inventory;

   public JoinRequestGUI(JustTeams plugin, Player viewer, Team team) {
      this.plugin = plugin;
      this.viewer = viewer;
      this.team = team;
      GuiConfigManager guiManager = plugin.getGuiConfigManager();
      ConfigurationSection guiConfig = guiManager.getGUI("join-requests-gui");
      String title = guiConfig != null ? guiConfig.getString("title", "ᴊᴏɪɴ ʀᴇǫᴜᴇsᴛs") : "ᴊᴏɪɴ ʀᴇǫᴜᴇsᴛs";
      int size = guiConfig != null ? guiConfig.getInt("size", 54) : 54;
      this.inventory = Bukkit.createInventory(this, size, plugin.getMiniMessage().deserialize(title));
      this.initializeItems();
   }

   public void initializeItems() {
      this.inventory.clear();
      GuiConfigManager guiManager = this.plugin.getGuiConfigManager();
      ConfigurationSection guiConfig = guiManager.getGUI("join-requests-gui");
      if (guiConfig == null) {
         this.plugin.getLogger().warning("join-requests-gui section not found in gui.yml!");
      } else {
         ConfigurationSection itemsConfig = guiConfig.getConfigurationSection("items");
         if (itemsConfig != null) {
            ItemStack border = (new ItemBuilder(guiManager.getMaterial("join-requests-gui.fill-item.material", Material.GRAY_STAINED_GLASS_PANE))).withName(guiManager.getString("join-requests-gui.fill-item.name", " ")).build();

            for(int i = 0; i < 9; ++i) {
               this.inventory.setItem(i, border);
            }

            for(int i = 45; i < 54; ++i) {
               this.inventory.setItem(i, border);
            }

            this.plugin.getTaskRunner().runAsync(() -> {
               List<UUID> requests = this.plugin.getStorageManager().getStorage().getJoinRequests(this.team.getId());
               this.plugin.getTaskRunner().runOnEntity(this.viewer, () -> {
                  if (requests.isEmpty()) {
                     ConfigurationSection noRequestsConfig = itemsConfig.getConfigurationSection("no-requests");
                     if (noRequestsConfig != null && noRequestsConfig.getBoolean("enabled", true)) {
                        ItemStack noRequestsItem = (new ItemBuilder(Material.matchMaterial(noRequestsConfig.getString("material", "BARRIER")))).withName(noRequestsConfig.getString("name", "<red><bold>No Join Requests</bold></red>")).withLore(noRequestsConfig.getStringList("lore")).build();
                        this.inventory.setItem(noRequestsConfig.getInt("slot", 22), noRequestsItem);
                     }
                  } else {
                     ConfigurationSection headConfig = itemsConfig.getConfigurationSection("player-head");
                     List<Integer> requestSlots = GuiSlotResolver.resolve(headConfig, 9, 45);
                     int slotIndex = 0;

                     for(UUID requestUuid : requests) {
                        if (slotIndex >= requestSlots.size()) {
                           break;
                        }

                        OfflinePlayer requester = Bukkit.getOfflinePlayer(requestUuid);
                        if (requester.getName() != null && headConfig != null && headConfig.getBoolean("enabled", true)) {
                           String name = headConfig.getString("name", "<gradient:#4C9D9D:#4C96D2><status_indicator><bold><player_name></bold></gradient>").replace("<player_name>", requester.getName()).replace("<status_indicator>", requester.isOnline() ? "<green>● </green>" : "<red>● </red>");
                           List<String> lore = headConfig.getStringList("lore");
                           ItemStack head = (new ItemBuilder(Material.PLAYER_HEAD)).asPlayerHead(requestUuid).withName(name).withLore(lore).withAction("player-head").withData("player_uuid", requestUuid.toString()).build();
                           this.inventory.setItem((Integer)requestSlots.get(slotIndex++), head);
                        }
                     }
                  }

                  this.setItemFromConfig(itemsConfig, "back-button");
               });
            });
         }
      }
   }

   private void setItemFromConfig(ConfigurationSection itemsSection, String key) {
      ConfigurationSection itemConfig = itemsSection.getConfigurationSection(key);
      if (itemConfig != null && itemConfig.getBoolean("enabled", true)) {
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

   public void open() {
      this.viewer.openInventory(this.inventory);
   }

   public void refresh() {
      this.open();
   }

   public Team getTeam() {
      return this.team;
   }

   public Inventory getInventory() {
      return this.inventory;
   }
}
