package eu.kotori.justTeams.gui.admin;

import eu.kotori.justTeams.JustTeams;
import eu.kotori.justTeams.core.team.Team;
import eu.kotori.justTeams.core.util.GuiConfigManager;
import eu.kotori.justTeams.core.util.ItemBuilder;
import eu.kotori.justTeams.core.util.TextUtil;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

public class AdminTeamListGUI implements InventoryHolder {
   private final JustTeams plugin;
   private final Player viewer;
   private final Inventory inventory;
   private final List<Team> allTeams;
   private int page;

   public AdminTeamListGUI(JustTeams plugin, Player viewer, List<Team> allTeams, int page) {
      this.plugin = plugin;
      this.viewer = viewer;
      this.allTeams = allTeams;
      this.page = page;
      GuiConfigManager guiManager = plugin.getGuiConfigManager();
      ConfigurationSection guiConfig = guiManager.getGUI("admin-team-list-gui");
      String title = guiConfig != null ? guiConfig.getString("title", "All Teams - Page " + (page + 1)) : "All Teams - Page " + (page + 1);
      int size = guiConfig != null ? guiConfig.getInt("size", 54) : 54;
      MiniMessage miniMessage = MiniMessage.miniMessage();
      Component titleComponent = miniMessage.deserialize(title, new TagResolver[]{Placeholder.unparsed("page", String.valueOf(page + 1)), Placeholder.unparsed("total_pages", String.valueOf((int)Math.ceil((double)allTeams.size() / (double)36.0F)))});
      this.inventory = Bukkit.createInventory(this, size, titleComponent);
      this.initializeItems();
   }

   private void initializeItems() {
      this.inventory.clear();
      ItemStack border = (new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE)).withName(" ").build();

      for(int i = 0; i < 9; ++i) {
         this.inventory.setItem(i, border);
      }

      for(int i = 45; i < 54; ++i) {
         this.inventory.setItem(i, border);
      }

      int maxItemsPerPage = 36;
      int startIndex = this.page * maxItemsPerPage;
      int endIndex = Math.min(startIndex + maxItemsPerPage, this.allTeams.size());
      GuiConfigManager guiManager = this.plugin.getGuiConfigManager();

      for(int i = startIndex; i < endIndex; ++i) {
         Team team = (Team)this.allTeams.get(i);
         String ownerName = Bukkit.getOfflinePlayer(team.getOwnerUuid()).getName();
         String teamNameFormat = guiManager.getPlaceholder("admin.team_name_format", "<gold><bold>%team%</bold></gold>");
         String ownerFormat = guiManager.getPlaceholder("admin.owner_format", "<gray>Owner: <white>%owner%");
         String membersFormat = guiManager.getPlaceholder("admin.members_format", "<gray>Members: <white>%count%");
         String clickFormat = guiManager.getPlaceholder("admin.click_format", "<yellow>Click to manage this team.</yellow>");
         this.inventory.addItem(new ItemStack[]{(new ItemBuilder(Material.PLAYER_HEAD)).asPlayerHead(team.getOwnerUuid()).withName(teamNameFormat.replace("%team%", TextUtil.toMiniMessage(this.plugin.getMiniMessage(), team.getName()))).withLore(ownerFormat.replace("%owner%", ownerName != null ? ownerName : "Unknown"), membersFormat.replace("%count%", String.valueOf(team.getMembers().size())), "", clickFormat).withAction("team-head").build()});
      }

      if (this.page > 0) {
         this.inventory.setItem(45, (new ItemBuilder(Material.ARROW)).withName("<gray>Previous Page").withAction("previous-page").build());
      }

      if (endIndex < this.allTeams.size()) {
         this.inventory.setItem(53, (new ItemBuilder(Material.ARROW)).withName("<gray>Next Page").withAction("next-page").build());
      }

      this.inventory.setItem(49, (new ItemBuilder(Material.BARRIER)).withName("<red>Back to Admin Menu").withAction("back-button").build());
   }

   public void open() {
      this.viewer.openInventory(this.inventory);
   }

   public List<Team> getAllTeams() {
      return this.allTeams;
   }

   public int getPage() {
      return this.page;
   }

   public Inventory getInventory() {
      return this.inventory;
   }
}
