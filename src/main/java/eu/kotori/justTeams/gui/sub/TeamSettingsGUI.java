package eu.kotori.justTeams.gui.sub;

import eu.kotori.justTeams.JustTeams;
import eu.kotori.justTeams.gui.IRefreshableGUI;
import eu.kotori.justTeams.team.Team;
import eu.kotori.justTeams.util.GuiConfigManager;
import eu.kotori.justTeams.util.ItemBuilder;
import eu.kotori.justTeams.util.TextUtil;
import java.util.List;
import java.util.stream.Collectors;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

public class TeamSettingsGUI implements IRefreshableGUI, InventoryHolder {
   private final JustTeams plugin;
   private final Player viewer;
   private final Team team;
   private final Inventory inventory;

   public TeamSettingsGUI(JustTeams plugin, Player viewer, Team team) {
      this.plugin = plugin;
      this.viewer = viewer;
      this.team = team;
      GuiConfigManager guiManager = plugin.getGuiConfigManager();
      ConfigurationSection guiConfig = guiManager.getGUI("team-settings-gui");
      String title = "ᴛᴇᴀᴍ sᴇᴛᴛɪɴɢs";
      int size = 27;
      if (guiConfig != null) {
         title = guiConfig.getString("title", "ᴛᴇᴀᴍ sᴇᴛᴛɪɴɢs");
         size = guiConfig.getInt("size", 27);
      }

      this.inventory = Bukkit.createInventory(this, size, plugin.getMiniMessage().deserialize(title));
      this.initializeItems();
   }

   public void initializeItems() {
      this.inventory.clear();
      GuiConfigManager guiManager = this.plugin.getGuiConfigManager();
      ConfigurationSection guiConfig = guiManager.getGUI("team-settings-gui");
      if (guiConfig == null) {
         this.plugin.getLogger().warning("team-settings-gui section not found in gui.yml!");
      } else {
         ConfigurationSection itemsSection = guiConfig.getConfigurationSection("items");
         if (itemsSection != null) {
            for(String key : itemsSection.getKeys(false)) {
               ConfigurationSection itemConfig = itemsSection.getConfigurationSection(key);
               if (itemConfig != null && (!key.equals("change-tag") || this.plugin.getConfigManager().isTeamTagEnabled()) && (!key.equals("change-description") || this.plugin.getConfigManager().isTeamDescriptionEnabled()) && (!key.equals("toggle-public") || this.plugin.getConfigManager().isTeamPublicToggleEnabled())) {
                  int slot = itemConfig.getInt("slot", -1);
                  if (slot != -1) {
                     Material material = Material.matchMaterial(itemConfig.getString("material", "STONE"));
                     String name = this.replacePlaceholders(itemConfig.getString("name", ""));
                     List<String> lore = (List)itemConfig.getStringList("lore").stream().map(this::replacePlaceholders).collect(Collectors.toList());
                     this.inventory.setItem(slot, (new ItemBuilder(material)).withName(name).withLore(lore).withAction(key).build());
                  }
               }
            }

            ConfigurationSection fillItemSection = guiConfig.getConfigurationSection("fill-item");
            if (fillItemSection != null) {
               ItemStack fillItem = (new ItemBuilder(Material.matchMaterial(fillItemSection.getString("material", "GRAY_STAINED_GLASS_PANE")))).withName(fillItemSection.getString("name", " ")).build();

               for(int i = 0; i < this.inventory.getSize(); ++i) {
                  if (this.inventory.getItem(i) == null) {
                     this.inventory.setItem(i, fillItem);
                  }
               }
            }

         }
      }
   }

   private String replacePlaceholders(String text) {
      if (text != null && this.team != null) {
         String statusPublic = this.plugin.getGuiConfigManager().getString("team-settings-gui.items.toggle-public.status-public", "<green>Public");
         String statusPrivate = this.plugin.getGuiConfigManager().getString("team-settings-gui.items.toggle-public.status-private", "<red>Private");
         String statusLabel = this.team.isPublic() ? statusPublic : statusPrivate;
         return text.replace("<team_tag>", TextUtil.toMiniMessage(this.plugin.getMiniMessage(), this.team.getTag())).replace("<team_name>", TextUtil.toMiniMessage(this.plugin.getMiniMessage(), this.team.getName())).replace("<team_description>", this.team.getDescription() != null ? this.team.getDescription() : "").replace("<status_text>", statusLabel).replace("<status>", statusLabel).replace("<public_status>", statusLabel);
      } else {
         return text;
      }
   }

   public void open() {
      this.viewer.openInventory(this.inventory);
   }

   public void refresh() {
      this.initializeItems();
   }

   public Team getTeam() {
      return this.team;
   }

   public Inventory getInventory() {
      return this.inventory;
   }
}
