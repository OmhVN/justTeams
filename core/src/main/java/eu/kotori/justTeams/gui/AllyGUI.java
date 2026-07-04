package eu.kotori.justTeams.gui;

import eu.kotori.justTeams.JustTeams;
import eu.kotori.justTeams.core.team.Team;
import eu.kotori.justTeams.core.util.GuiConfigManager;
import eu.kotori.justTeams.core.util.GuiSlotResolver;
import eu.kotori.justTeams.core.util.ItemBuilder;
import eu.kotori.justTeams.core.util.TextUtil;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

public class AllyGUI implements InventoryHolder, IRefreshableGUI {
   private final JustTeams plugin;
   private final Team team;
   private final Player viewer;
   private final Inventory inventory;

   public AllyGUI(JustTeams plugin, Team team, Player viewer) {
      this.plugin = plugin;
      this.team = team;
      this.viewer = viewer;
      GuiConfigManager guiManager = plugin.getGuiConfigManager();
      ConfigurationSection guiConfig = guiManager.getGUI("ally-gui");
      String title = guiConfig != null ? guiConfig.getString("title", "ᴛᴇᴀᴍ ᴀʟʟɪᴇs") : "ᴛᴇᴀᴍ ᴀʟʟɪᴇs";
      int size = guiConfig != null ? guiConfig.getInt("size", 54) : 54;
      this.inventory = Bukkit.createInventory(this, size, plugin.getMiniMessage().deserialize(title));
      this.initializeItems();
   }

   public void initializeItems() {
      this.inventory.clear();
      GuiConfigManager guiManager = this.plugin.getGuiConfigManager();
      ConfigurationSection guiConfig = guiManager.getGUI("ally-gui");
      if (guiConfig == null) {
         this.plugin.getLogger().warning("ally-gui section not found in gui.yml!");
      } else {
         ConfigurationSection itemsConfig = guiConfig.getConfigurationSection("items");
         if (itemsConfig != null) {
            ItemStack border = (new ItemBuilder(guiManager.getMaterial("ally-gui.fill-item.material", Material.GRAY_STAINED_GLASS_PANE))).withName(guiManager.getString("ally-gui.fill-item.name", " ")).build();

            for(int i = 0; i < 9; ++i) {
               this.inventory.setItem(i, border);
            }

            for(int i = 45; i < 54; ++i) {
               this.inventory.setItem(i, border);
            }

            this.plugin.getTaskRunner().runAsync(() -> {
               List<Integer> allies = new ArrayList(this.team.getAllies());
               List<Integer> receivedRequests = new ArrayList(this.team.getReceivedAllyRequests());
               List<Team> resolvedAllies = new ArrayList();

               for(Integer allyTeamId : allies) {
                  Optional<Team> var10000 = this.plugin.getTeamManager().getTeamById(allyTeamId);
                  Objects.requireNonNull(resolvedAllies);
                  var10000.ifPresent(resolvedAllies::add);
               }

               List<Team> resolvedRequests = new ArrayList();

               for(Integer senderTeamId : receivedRequests) {
                  Optional<Team> var10 = this.plugin.getTeamManager().getTeamById(senderTeamId);
                  Objects.requireNonNull(resolvedRequests);
                  var10.ifPresent(resolvedRequests::add);
               }

               this.plugin.getTaskRunner().runOnEntity(this.viewer, () -> {
                  if (resolvedAllies.isEmpty() && resolvedRequests.isEmpty()) {
                     ConfigurationSection noAlliesConfig = itemsConfig.getConfigurationSection("no-allies");
                     if (noAlliesConfig != null && noAlliesConfig.getBoolean("enabled", true)) {
                        ItemStack noAlliesItem = (new ItemBuilder(Material.matchMaterial(noAlliesConfig.getString("material", "BARRIER")))).withName(noAlliesConfig.getString("name", "<gray><bold>No Allies</bold></gray>")).withLore(noAlliesConfig.getStringList("lore")).build();
                        this.inventory.setItem(noAlliesConfig.getInt("slot", 22), noAlliesItem);
                     }
                  } else {
                     List<Integer> allySlots = GuiSlotResolver.resolve(itemsConfig.getConfigurationSection("ally-team"), 9, 45);
                     int slotIndex = 0;

                     for(Team allyTeam : resolvedAllies) {
                        if (slotIndex >= allySlots.size()) {
                           break;
                        }

                        this.inventory.setItem((Integer)allySlots.get(slotIndex++), this.createAllyItem(allyTeam, itemsConfig));
                     }

                     for(Team senderTeam : resolvedRequests) {
                        if (slotIndex >= allySlots.size()) {
                           break;
                        }

                        this.inventory.setItem((Integer)allySlots.get(slotIndex++), this.createAllyRequestItem(senderTeam, itemsConfig));
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

   private ItemStack createAllyItem(Team allyTeam, ConfigurationSection itemsConfig) {
      ConfigurationSection itemConfig = itemsConfig.getConfigurationSection("ally-team");
      if (itemConfig != null && itemConfig.getBoolean("enabled", true)) {
         List<String> lore = new ArrayList();

         for(String line : itemConfig.getStringList("lore")) {
            lore.add(line.replace("<team>", TextUtil.toMiniMessage(this.plugin.getMiniMessage(), allyTeam.getName())).replace("<tag>", TextUtil.toMiniMessage(this.plugin.getMiniMessage(), allyTeam.getTag())).replace("<members>", String.valueOf(allyTeam.getMembers().size())));
         }

         Material material = Material.matchMaterial(itemConfig.getString("material", "PLAYER_HEAD"));
         if (material == null) {
            material = Material.PLAYER_HEAD;
         }

         return (new ItemBuilder(material)).withName(itemConfig.getString("name", "<green><bold><team></bold>").replace("<team>", TextUtil.toMiniMessage(this.plugin.getMiniMessage(), allyTeam.getName()))).withLore(lore).withAction("remove-ally:" + allyTeam.getId()).build();
      } else {
         return (new ItemBuilder(Material.PLAYER_HEAD)).withName("<green><bold>" + allyTeam.getName() + "</bold>").withAction("remove-ally:" + allyTeam.getId()).build();
      }
   }

   private ItemStack createAllyRequestItem(Team senderTeam, ConfigurationSection itemsConfig) {
      ConfigurationSection itemConfig = itemsConfig.getConfigurationSection("ally-request");
      if (itemConfig != null && itemConfig.getBoolean("enabled", true)) {
         List<String> lore = new ArrayList();

         for(String line : itemConfig.getStringList("lore")) {
            lore.add(line.replace("<team>", TextUtil.toMiniMessage(this.plugin.getMiniMessage(), senderTeam.getName())).replace("<tag>", TextUtil.toMiniMessage(this.plugin.getMiniMessage(), senderTeam.getTag())).replace("<members>", String.valueOf(senderTeam.getMembers().size())));
         }

         Material material = Material.matchMaterial(itemConfig.getString("material", "PAPER"));
         if (material == null) {
            material = Material.PAPER;
         }

         return (new ItemBuilder(material)).withName(itemConfig.getString("name", "<yellow><bold>Request from <team></bold>").replace("<team>", TextUtil.toMiniMessage(this.plugin.getMiniMessage(), senderTeam.getName()))).withLore(lore).withAction("ally-request:" + senderTeam.getId()).build();
      } else {
         return (new ItemBuilder(Material.PAPER)).withName("<yellow><bold>Request from " + TextUtil.toMiniMessage(this.plugin.getMiniMessage(), senderTeam.getName()) + "</bold>").withAction("ally-request:" + senderTeam.getId()).build();
      }
   }

   public void open() {
      this.viewer.openInventory(this.inventory);
   }

   public void refresh() {
      this.initializeItems();
   }

   public @NotNull Inventory getInventory() {
      return this.inventory;
   }

   public Team getTeam() {
      return this.team;
   }

   public Player getViewer() {
      return this.viewer;
   }
}
