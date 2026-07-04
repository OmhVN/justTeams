package eu.kotori.justTeams.gui.admin;

import eu.kotori.justTeams.JustTeams;
import eu.kotori.justTeams.core.storage.IDataStorage;
import eu.kotori.justTeams.core.team.Team;
import eu.kotori.justTeams.core.util.ItemBuilder;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

public class AdminWarpsGUI implements InventoryHolder {
   private final JustTeams plugin;
   private final Player viewer;
   private final Team team;
   private final int memberPage;
   private final Inventory inventory;

   public AdminWarpsGUI(JustTeams plugin, Player viewer, Team team, int memberPage) {
      this.plugin = plugin;
      this.viewer = viewer;
      this.team = team;
      this.memberPage = memberPage;
      this.inventory = Bukkit.createInventory(this, 54, plugin.getMiniMessage().deserialize("<dark_gray>Warps: <gray>" + team.getName()));
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

      this.inventory.setItem(49, (new ItemBuilder(Material.ARROW)).withName("<gray><bold>ʙᴀᴄᴋ</bold></gray>").withAction("back-button").build());
      this.plugin.getTaskRunner().runAsync(() -> {
         List<IDataStorage.TeamWarp> warps = this.plugin.getStorageManager().getStorage().getTeamWarps(this.team.getId());
         this.plugin.getTaskRunner().runOnEntity(this.viewer, () -> {
            if (warps.isEmpty()) {
               this.inventory.setItem(22, (new ItemBuilder(Material.BARRIER)).withName("<red><bold>No Warps Set</bold></red>").withLore(List.of("<gray>This team has not set any warps.")).build());
            } else {
               int slot = 9;

               for(IDataStorage.TeamWarp warp : warps) {
                  if (slot >= 45) {
                     break;
                  }

                  String server = (String)this.plugin.getStorageManager().getStorage().getServerAlias(warp.serverName()).orElse(warp.serverName());
                  boolean locked = warp.password() != null && !warp.password().isEmpty();
                  ItemBuilder var10000 = new ItemBuilder(locked ? Material.IRON_BLOCK : Material.GOLD_BLOCK);
                  String var10001 = warp.name();
                  var10000 = var10000.withName("<gradient:#4C9D9D:#4C96D2><bold>" + var10001 + "</bold></gradient>");
                  var10001 = this.formatLocation(warp.location());
                  ItemStack item = var10000.withLore(List.of("<gray>Location: <white>" + var10001, "<gray>Server: <yellow>" + server, "<gray>Access: " + (locked ? "<red>Password protected" : "<green>Public"))).build();
                  this.inventory.setItem(slot++, item);
               }

            }
         });
      });
   }

   private String formatLocation(String serialized) {
      if (serialized != null && !serialized.isEmpty()) {
         String[] parts = serialized.split(",");
         if (parts.length >= 4) {
            try {
               return parts[0] + " " + Math.round(Double.parseDouble(parts[1])) + ", " + Math.round(Double.parseDouble(parts[2])) + ", " + Math.round(Double.parseDouble(parts[3]));
            } catch (NumberFormatException var4) {
            }
         }

         return serialized;
      } else {
         return "unknown";
      }
   }

   public void open() {
      this.viewer.openInventory(this.inventory);
   }

   public Team getTargetTeam() {
      return this.team;
   }

   public int getMemberPage() {
      return this.memberPage;
   }

   public @NotNull Inventory getInventory() {
      return this.inventory;
   }
}
