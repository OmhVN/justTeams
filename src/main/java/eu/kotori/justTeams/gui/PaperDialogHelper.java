package eu.kotori.justTeams.gui;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import net.kyori.adventure.text.event.ClickCallback;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.entity.Player;
import java.util.List;
import java.util.function.Consumer;

public class PaperDialogHelper implements IDialogHandler {
    @Override
    public void openSearchDialog(Player player, String title, String promptText, String defaultValue, Consumer<String> onConfirm, Runnable onCancel) {
        Component deserializedPrompt = MiniMessage.miniMessage().deserialize(promptText);
        Component deserializedTitle = MiniMessage.miniMessage().deserialize(title);
        
        Dialog dialog = Dialog.create(builder -> {
            builder.empty()
                   .base(DialogBase.builder(deserializedTitle)
                       .body(List.of(DialogBody.plainMessage(deserializedPrompt)))
                       .inputs(List.of(
                           DialogInput.text("query", Component.text("Nhập"))
                               .initial(defaultValue == null ? "" : defaultValue)
                               .labelVisible(true)
                               .build()
                       ))
                       .canCloseWithEscape(true)
                       .build())
                   .type(DialogType.confirmation(
                       ActionButton.builder(Component.text("Xác nhận"))
                           .action(DialogAction.customClick((view, audience) -> {
                               String query = view.getText("query");
                               onConfirm.accept(query == null ? "" : query.trim());
                           }, ClickCallback.Options.builder().uses(1).build()))
                           .build(),
                       ActionButton.builder(Component.text("Hủy"))
                           .action(DialogAction.customClick((view, audience) -> {
                               onCancel.run();
                           }, ClickCallback.Options.builder().uses(1).build()))
                           .build()
                   ));
        });
        player.showDialog(dialog);
    }

    @Override
    public void openPasswordDialog(Player player, String title, Consumer<String> onConfirm, Runnable onCancel) {
        Component deserializedTitle = MiniMessage.miniMessage().deserialize(title);
        Dialog dialog = Dialog.create(builder -> {
            builder.empty()
                   .base(DialogBase.builder(deserializedTitle)
                       .body(List.of(
                           DialogBody.plainMessage(Component.text("Vui lòng đặt mật khẩu và xác nhận mật khẩu để khóa trang rương này.")),
                           DialogBody.plainMessage(Component.text("Thành viên khác sẽ cần nhập mật khẩu này để truy cập."))
                       ))
                       .inputs(List.of(
                           DialogInput.text("password", Component.text("Mật khẩu")).build(),
                           DialogInput.text("confirm", Component.text("Xác nhận")).build()
                       ))
                       .canCloseWithEscape(true)
                       .build())
                   .type(DialogType.confirmation(
                       ActionButton.builder(Component.text("Xác nhận"))
                           .action(DialogAction.customClick((view, audience) -> {
                               String pwd = view.getText("password");
                               String conf = view.getText("confirm");
                               if (pwd == null || pwd.trim().isEmpty()) {
                                   player.sendMessage("§cMật khẩu không được để trống!");
                                   onCancel.run();
                                   return;
                               }
                               if (!pwd.equals(conf)) {
                                   player.sendMessage("§cMật khẩu xác nhận không khớp!");
                                   onCancel.run();
                                   return;
                               }
                               onConfirm.accept(pwd);
                           }, ClickCallback.Options.builder().uses(1).build()))
                           .build(),
                       ActionButton.builder(Component.text("Hủy"))
                           .action(DialogAction.customClick((view, audience) -> {
                               onCancel.run();
                           }, ClickCallback.Options.builder().uses(1).build()))
                           .build()
                   ));
        });
        player.showDialog(dialog);
    }

    @Override
    public void openTeamCreationDialog(Player player, String title, Consumer<String[]> onConfirm, Runnable onCancel) {
        Component deserializedTitle = MiniMessage.miniMessage().deserialize(title);
        Dialog dialog = Dialog.create(builder -> {
            builder.empty()
                   .base(DialogBase.builder(deserializedTitle)
                       .body(List.of(
                           DialogBody.plainMessage(Component.text("Vui lòng nhập tên đội và thẻ (tag) đội để tiến hành tạo đội mới.")),
                           DialogBody.plainMessage(Component.text("Lưu ý: Thẻ (tag) đội có thể bỏ trống nếu không sử dụng."))
                       ))
                       .inputs(List.of(
                           DialogInput.text("name", Component.text("Tên đội")).build(),
                           DialogInput.text("tag", Component.text("Thẻ (Tag)")).build()
                       ))
                       .canCloseWithEscape(true)
                       .build())
                   .type(DialogType.confirmation(
                       ActionButton.builder(Component.text("Xác nhận"))
                           .action(DialogAction.customClick((view, audience) -> {
                               String name = view.getText("name");
                               String tag = view.getText("tag");
                               onConfirm.accept(new String[]{name == null ? "" : name.trim(), tag == null ? "" : tag.trim()});
                           }, ClickCallback.Options.builder().uses(1).build()))
                           .build(),
                       ActionButton.builder(Component.text("Hủy"))
                           .action(DialogAction.customClick((view, audience) -> {
                               onCancel.run();
                           }, ClickCallback.Options.builder().uses(1).build()))
                           .build()
                   ));
        });
        player.showDialog(dialog);
    }
}
