package eu.kotori.justTeams.gui;

import eu.kotori.justTeams.JustTeams;
import eu.kotori.justTeams.core.team.Team;
import eu.kotori.justTeams.core.util.GuiConfigManager;
import eu.kotori.justTeams.core.util.ItemBuilder;
import eu.kotori.justTeams.core.util.TextUtil;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

public class LeaderboardViewGUI implements InventoryHolder {
   private final Player viewer;
   private final Inventory inventory;
   private final JustTeams plugin;

   public LeaderboardViewGUI(JustTeams plugin, Player viewer, String title, Map<Integer, Team> topTeams, LeaderboardType type) {
      this.plugin = plugin;
      this.viewer = viewer;
      this.inventory = Bukkit.createInventory(this, 54, plugin.getMiniMessage().deserialize(title));
      this.initializeItems(topTeams, type);
   }

   private void initializeItems(Map<Integer, Team> topTeams, LeaderboardType type) {
      this.inventory.clear();
      GuiConfigManager guiManager = this.plugin.getGuiConfigManager();
      ConfigurationSection guiConfig = guiManager.getGUI("leaderboard-view-gui");
      ConfigurationSection itemsConfig = guiConfig.getConfigurationSection("items");
      ItemStack border = (new ItemBuilder(guiManager.getMaterial("leaderboard-view-gui.fill-item.material", Material.GRAY_STAINED_GLASS_PANE))).withName(guiManager.getString("leaderboard-view-gui.fill-item.name", " ")).build();

      for(int i = 0; i < 9; ++i) {
         this.inventory.setItem(i, border);
      }

      for(int i = 45; i < 54; ++i) {
         this.inventory.setItem(i, border);
      }

      int[] slots = new int[]{10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34, 37, 38, 39, 40, 41, 42, 43};
      int slotIndex = 0;

      for(Map.Entry<Integer, Team> entry : topTeams.entrySet()) {
         if (slotIndex >= slots.length) {
            break;
         }

         int rank = (Integer)entry.getKey();
         Team team = (Team)entry.getValue();
         String name = itemsConfig.getString("team-head.name", "<gradient:#4C9DDE:#4C96D2><bold>#<rank> <team_name></bold></gradient>").replace("<rank>", String.valueOf(rank)).replace("<team_name>", TextUtil.toMiniMessage(this.plugin.getMiniMessage(), team.getName()));
         List<String> lore = new ArrayList();

         for(String line : itemsConfig.getStringList("team-head.lore")) {
            String statisticName = "";
            String statisticValue = "";
            switch (type.ordinal()) {
               case 0:
                  statisticName = "Kills";
                  statisticValue = String.valueOf(team.getKills());
                  break;
               case 1:
                  statisticName = "Balance";
                  DecimalFormat formatter = new DecimalFormat(this.plugin.getConfigManager().getCurrencyFormat());
                  statisticValue = formatter.format(team.getBalance());
                  break;
               case 2:
                  statisticName = "Members";
                  statisticValue = String.valueOf(team.getMembers().size());
            }

            lore.add(line.replace("<team_tag>", TextUtil.toMiniMessage(this.plugin.getMiniMessage(), team.getTag())).replace("<statistic_name>", statisticName).replace("<statistic_value>", statisticValue));
         }

         this.inventory.setItem(slots[slotIndex++], (new ItemBuilder(Material.PLAYER_HEAD)).asPlayerHead(team.getOwnerUuid()).withName(name).withLore(lore).build());
      }

      ConfigurationSection backButtonConfig = itemsConfig.getConfigurationSection("back-button");
      if (backButtonConfig != null) {
         this.inventory.setItem(backButtonConfig.getInt("slot", 49), (new ItemBuilder(guiManager.getMaterial("leaderboard-view-gui.items.back-button.material", Material.ARROW))).withName(backButtonConfig.getString("name", "<gray><bold>ʙᴀᴄᴋ</bold></gray>")).withLore(backButtonConfig.getStringList("lore")).withAction("back-button").build());
      }

   }

   public void open() {
      this.viewer.openInventory(this.inventory);
   }

   public Inventory getInventory() {
      return this.inventory;
   }

   public static enum LeaderboardType {
      KILLS,
      BALANCE,
      MEMBERS;

      
      private static LeaderboardType[] $values() {
         return new LeaderboardType[]{KILLS, BALANCE, MEMBERS};
      }
   }
}
