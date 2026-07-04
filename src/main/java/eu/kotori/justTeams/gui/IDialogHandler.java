package eu.kotori.justTeams.gui;

import org.bukkit.entity.Player;
import java.util.function.Consumer;

public interface IDialogHandler {
    void openSearchDialog(Player player, String title, String promptText, String defaultValue, Consumer<String> onConfirm, Runnable onCancel);
    void openPasswordDialog(Player player, String title, Consumer<String> onConfirm, Runnable onCancel);
    void openTeamCreationDialog(Player player, String title, Consumer<String[]> onConfirm, Runnable onCancel);
}
