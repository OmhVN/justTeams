package eu.kotori.justTeams.gui;

import eu.kotori.justTeams.JustTeams;
import eu.kotori.justTeams.core.storage.IDataStorage;
import eu.kotori.justTeams.core.team.Team;
import eu.kotori.justTeams.core.util.GuiConfigManager;
import eu.kotori.justTeams.core.util.GuiSlotResolver;
import eu.kotori.justTeams.core.util.ItemBuilder;
import eu.kotori.justTeams.core.util.TextUtil;
import java.util.List;
import java.util.stream.Collectors;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

public class InvitesGUI implements IRefreshableGUI, InventoryHolder {
   private final JustTeams plugin;
   private final Player viewer;
   private final Inventory inventory;

   public InvitesGUI(JustTeams plugin, Player viewer) {
      this.plugin = plugin;
      this.viewer = viewer;
      GuiConfigManager guiManager = plugin.getGuiConfigManager();
      ConfigurationSection guiConfig = guiManager.getGUI("invites-gui");
      String title = guiConfig != null ? guiConfig.getString("title", "ᴘᴇɴᴅɪɴɢ ɪɴᴠɪᴛᴇs") : "ᴘᴇɴᴅɪɴɢ ɪɴᴠɪᴛᴇs";
      int size = guiConfig != null ? guiConfig.getInt("size", 54) : 54;
      this.inventory = Bukkit.createInventory(this, size, plugin.getMiniMessage().deserialize(title));
      this.initializeItems();
   }

   public void initializeItems() {
      this.inventory.clear();
      GuiConfigManager guiManager = this.plugin.getGuiConfigManager();
      ConfigurationSection guiConfig = guiManager.getGUI("invites-gui");
      if (guiConfig == null) {
         this.plugin.getLogger().warning("invites-gui section not found in gui.yml!");
      } else {
         ConfigurationSection itemsConfig = guiConfig.getConfigurationSection("items");
         if (itemsConfig != null) {
            ItemStack border = (new ItemBuilder(guiManager.getMaterial("invites-gui.fill-item.material", Material.GRAY_STAINED_GLASS_PANE))).withName(guiManager.getString("invites-gui.fill-item.name", " ")).build();

            for(int i = 0; i < 9; ++i) {
               this.inventory.setItem(i, border);
            }

            for(int i = 45; i < 54; ++i) {
               this.inventory.setItem(i, border);
            }

            this.plugin.getTaskRunner().runAsync(() -> {
               List<IDataStorage.TeamInvite> inviteDetails = this.plugin.getStorageManager().getStorage().getPlayerInvitesWithDetails(this.viewer.getUniqueId());
               this.plugin.getTaskRunner().runOnEntity(this.viewer, () -> {
                  if (inviteDetails.isEmpty()) {
                     ConfigurationSection noInvitesConfig = itemsConfig.getConfigurationSection("no-invites");
                     if (noInvitesConfig != null) {
                        ItemStack noInvitesItem = (new ItemBuilder(Material.matchMaterial(noInvitesConfig.getString("material", "BARRIER")))).withName(noInvitesConfig.getString("name", "<red><bold>No Pending Invites</bold></red>")).withLore(noInvitesConfig.getStringList("lore")).build();
                        this.inventory.setItem(noInvitesConfig.getInt("slot", 22), noInvitesItem);
                     }
                  } else {
                     ConfigurationSection teamIconConfig = itemsConfig.getConfigurationSection("team-icon");
                     List<Integer> inviteSlots = GuiSlotResolver.resolve(teamIconConfig, 9, 45);
                     int slotIndex = 0;

                     for(IDataStorage.TeamInvite invite : inviteDetails) {
                        if (slotIndex >= inviteSlots.size()) {
                           break;
                        }

                        Team team = this.plugin.getTeamManager().getTeamByName(invite.teamName());
                        if (team != null && teamIconConfig != null) {
                           String name = teamIconConfig.getString("name", "<gradient:#4C9D9D:#4C96D2><bold><team_name></bold></gradient>").replace("<team_name>", TextUtil.toMiniMessage(this.plugin.getMiniMessage(), invite.teamName())).replace("<team_tag>", TextUtil.toMiniMessage(this.plugin.getMiniMessage(), team.getTag()));
                           List<String> loreList = teamIconConfig.getStringList("lore");
                           loreList = (List)loreList.stream().map((line) -> {
                              Player owner = Bukkit.getPlayer(team.getOwnerUuid());
                              int maxSize = owner != null ? this.plugin.getConfigManager().getMaxTeamSize(owner) : this.plugin.getConfigManager().getMaxTeamSize();
                              return line.replace("<team_name>", TextUtil.toMiniMessage(this.plugin.getMiniMessage(), invite.teamName())).replace("<team_tag>", TextUtil.toMiniMessage(this.plugin.getMiniMessage(), team.getTag())).replace("<inviter>", invite.inviterName()).replace("<member_count>", String.valueOf(team.getMembers().size())).replace("<max_members>", String.valueOf(maxSize)).replace("<description>", team.getDescription() != null ? team.getDescription() : "No description");
                           }).collect(Collectors.toList());
                           ItemStack teamIcon = (new ItemBuilder(Material.matchMaterial(teamIconConfig.getString("material", "DIAMOND")))).withName(name).withLore(loreList).withAction("team-icon").withData("team_id", String.valueOf(invite.teamId())).withData("team_name", invite.teamName()).build();
                           this.inventory.setItem((Integer)inviteSlots.get(slotIndex++), teamIcon);
                        }
                     }
                  }

               });
            });
            ConfigurationSection backConfig = itemsConfig.getConfigurationSection("back-button");
            if (backConfig != null) {
               ItemStack backButton = (new ItemBuilder(Material.matchMaterial(backConfig.getString("material", "ARROW")))).withName(backConfig.getString("name", "<gray><bold>ʙᴀᴄᴋ</bold></gray>")).withLore(backConfig.getStringList("lore")).withAction("back-button").build();
               this.inventory.setItem(backConfig.getInt("slot", 49), backButton);
            }

            ConfigurationSection closeConfig = itemsConfig.getConfigurationSection("close-button");
            if (closeConfig != null) {
               ItemStack closeButton = (new ItemBuilder(Material.matchMaterial(closeConfig.getString("material", "BARRIER")))).withName(closeConfig.getString("name", "<red><bold>ᴄʟᴏsᴇ</bold></red>")).withLore(closeConfig.getStringList("lore")).withAction("close-button").build();
               this.inventory.setItem(closeConfig.getInt("slot", 53), closeButton);
            }

         }
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

   public Player getViewer() {
      return this.viewer;
   }
}
