package eu.kotori.justTeams.gui;

import eu.kotori.justTeams.JustTeams;
import eu.kotori.justTeams.team.Team;
import eu.kotori.justTeams.team.TeamManager.EnderChestPageMetadata;
import eu.kotori.justTeams.util.ItemBuilder;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import java.util.ArrayList;
import java.util.List;

public class EnderChestSelectorGUI implements InventoryHolder {
    private final JustTeams plugin;
    private final Player player;
    private final Team team;
    private final int selectorPage;
    private final Inventory inventory;

    public EnderChestSelectorGUI(JustTeams plugin, Player player, Team team) {
        this(plugin, player, team, 1);
    }

    public EnderChestSelectorGUI(JustTeams plugin, Player player, Team team, int selectorPage) {
        this.plugin = plugin;
        this.player = player;
        this.team = team;
        this.selectorPage = selectorPage;
        this.inventory = Bukkit.createInventory(this, 54, Component.text("ʀươɴɢ ĐỘɪ (ᴛʀᴀɴɢ " + selectorPage + ")"));
        this.initializeItems();
    }

    private void initializeItems() {
        this.inventory.clear();
        
        int unlockedCount = this.plugin.getTeamManager().getUnlockedEnderChestPages(team);
        List<EnderChestPageMetadata> pagesMetadata = this.plugin.getTeamManager().loadEnderChestPages(team);
        
        
        int startIndex = (selectorPage - 1) * 45;
        for (int slot = 0; slot < 45; slot++) {
            int chestPage = startIndex + slot + 1;
            if (chestPage > 99) break;
            
            if (chestPage <= unlockedCount) {
                EnderChestPageMetadata meta = (chestPage - 1 < pagesMetadata.size()) ? pagesMetadata.get(chestPage - 1) : null;
                String minRole = (meta != null) ? meta.minRole : "MEMBER";
                boolean locked = (meta != null) && meta.locked;
                
                List<String> lore = new ArrayList<>();
                lore.add("<gray>Quyền tối thiểu: <yellow>" + minRole + "</yellow></gray>");
                lore.add("<gray>Trạng thái khóa: <yellow>" + (locked ? "Đang khóa" : "Mở khóa") + "</yellow></gray>");
                lore.add("");
                lore.add("<green>▶ Nhấp chuột trái để mở rương.</green>");
                lore.add("<aqua>▶ Nhấp chuột phải để chỉnh sửa.</aqua>");
                
                ItemStack chestItem = new ItemBuilder(Material.CHEST)
                    .withName("<gradient:#4C9DDE:#4C96D2>ᴋʜᴏ " + chestPage + "</gradient>")
                    .withLore(lore)
                    .withAction("open-page-" + chestPage)
                    .build();
                this.inventory.setItem(slot, chestItem);
            }
        }
        
        
        if (selectorPage > 1) {
            ItemStack prevButton = new ItemBuilder(Material.ARROW)
                .withName("<yellow>ᴛʀᴀɴɢ ᴛʀướᴄ</yellow>")
                .withAction("selector-prev-page")
                .build();
            this.inventory.setItem(45, prevButton);
        }
        
        int nextToUnlock = unlockedCount + 1;
        if (unlockedCount < 99) {
            double cost = this.plugin.getTeamManager().getUnlockPageCost(nextToUnlock);
            List<String> lore = new ArrayList<>();
            lore.add("<gray>Mua trang tiếp theo: <gold>" + String.format("%,.0f", cost) + " xu</gold></gray>");
            lore.add("");
            lore.add("<yellow>Nhấp để mở khóa trang " + nextToUnlock + "!</yellow>");
            
            ItemStack buyItem = new ItemBuilder(Material.BUNDLE)
                .withName("<gradient:#55FF55:#00AA00>ɴÂɴɢ ᴄẤᴘ ᴋʜᴏ</gradient>")
                .withLore(lore)
                .withAction("buy-page-" + nextToUnlock)
                .build();
            this.inventory.setItem(49, buyItem);
        } else {
            List<String> lore = new ArrayList<>();
            lore.add("<gray>Bạn đã đạt giới hạn tối đa</gray>");
            lore.add("<gray>số lượng trang rương team (99 trang).</gray>");
            ItemStack maxItem = new ItemBuilder(Material.GRAY_DYE)
                .withName("<red>ĐÃ ĐẠᴛ ɢɪỚɪ ʜẠɴ</red>")
                .withLore(lore)
                .build();
            this.inventory.setItem(49, maxItem);
        }
        
        int maxPagesToShow = Math.min(99, unlockedCount + 1);
        if (maxPagesToShow > selectorPage * 45) {
            ItemStack nextButton = new ItemBuilder(Material.ARROW)
                .withName("<yellow>ᴛʀᴀɴɢ sᴀᴜ</yellow>")
                .withAction("selector-next-page")
                .build();
            this.inventory.setItem(53, nextButton);
        }
    }

    public int getSelectorPage() {
        return this.selectorPage;
    }

    @Override
    public Inventory getInventory() {
        return this.inventory;
    }

    public void open() {
        this.player.openInventory(this.inventory);
    }
}
