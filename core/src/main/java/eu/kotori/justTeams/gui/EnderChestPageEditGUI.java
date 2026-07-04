package eu.kotori.justTeams.gui;

import eu.kotori.justTeams.JustTeams;
import eu.kotori.justTeams.core.team.Team;
import eu.kotori.justTeams.core.team.TeamManager.EnderChestPageMetadata;
import eu.kotori.justTeams.core.util.ItemBuilder;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import java.util.ArrayList;
import java.util.List;

public class EnderChestPageEditGUI implements InventoryHolder {
    private final JustTeams plugin;
    private final Player player;
    private final Team team;
    private final int page;
    private final Inventory inventory;

    public EnderChestPageEditGUI(JustTeams plugin, Player player, Team team, int page) {
        this.plugin = plugin;
        this.player = player;
        this.team = team;
        this.page = page;
        this.inventory = Bukkit.createInventory(this, 27, Component.text("ᴇᴅɪᴛ ᴘᴀɢᴇ " + page));
        this.initializeItems();
    }

    public void initializeItems() {
        this.inventory.clear();
        
        List<EnderChestPageMetadata> pages = this.plugin.getTeamManager().loadEnderChestPages(team);
        EnderChestPageMetadata meta = (page - 1 < pages.size()) ? pages.get(page - 1) : new EnderChestPageMetadata();
        
        
        List<String> nameLore = new ArrayList<>();
        nameLore.add("<gray>Tên hiện tại: <white>" + meta.name + "</white></gray>");
        nameLore.add("");
        nameLore.add("<yellow>Nhấp để đổi tên trang rương.</yellow>");
        ItemStack nameItem = new ItemBuilder(Material.NAME_TAG)
            .withName("<gradient:#4C9DDE:#4C96D2>ʀᴇɴᴀᴍᴇ ᴘᴀɢᴇ</gradient>")
            .withLore(nameLore)
            .withAction("edit-name-" + page)
            .build();
        this.inventory.setItem(10, nameItem);
        
        
        List<String> roleLore = new ArrayList<>();
        roleLore.add("<gray>Quyền tối thiểu: <yellow>" + meta.minRole + "</yellow></gray>");
        roleLore.add("");
        roleLore.add("<yellow>Nhấp để thay đổi quyền truy cập.</yellow>");
        ItemStack roleItem = new ItemBuilder(Material.PLAYER_HEAD)
            .withName("<gradient:#4C9DDE:#4C96D2>ᴍɪɴɪᴍᴜᴍ ʀᴏʟᴇ</gradient>")
            .withLore(roleLore)
            .withAction("edit-role-" + page)
            .build();
        this.inventory.setItem(12, roleItem);
        
        
        List<String> lockLore = new ArrayList<>();
        lockLore.add("<gray>Trạng thái khóa: <yellow>" + (meta.locked ? "Đang khóa" : "Mở khóa") + "</yellow></gray>");
        lockLore.add("");
        lockLore.add("<yellow>Nhấp để thay đổi trạng thái khóa.</yellow>");
        ItemStack lockItem = new ItemBuilder(meta.locked ? Material.REDSTONE_TORCH : Material.LEVER)
            .withName("<gradient:#4C9DDE:#4C96D2>ʟᴏᴄᴋ sᴛᴀᴛᴜs</gradient>")
            .withLore(lockLore)
            .withAction("edit-lock-" + page)
            .build();
        this.inventory.setItem(14, lockItem);
        
        
        ItemStack backButton = new ItemBuilder(Material.ARROW)
            .withName("<red>ʙᴀᴄᴋ</red>")
            .withAction("back-to-selector")
            .build();
        this.inventory.setItem(16, backButton);
    }

    public int getPage() {
        return this.page;
    }

    public Team getTeam() {
        return this.team;
    }

    @Override
    public Inventory getInventory() {
        return this.inventory;
    }

    public void open() {
        this.player.openInventory(this.inventory);
    }
}
