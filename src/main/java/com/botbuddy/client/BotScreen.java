package com.botbuddy.client;

import com.botbuddy.ActionPayload;
import com.botbuddy.BotManager;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class BotScreen extends Screen {
    private static final int ROW_H = 22, LIST_TOP = 56, LIST_W = 160, WHITE = 0xFFFFFFFF;

    private TextFieldWidget search, renameField, cmdField;
    private ButtonWidget targetBtn, kitBtn;
    private ButtonWidget[] rowBtns;
    private int page = 0, kitIdx = 2, tickCount = 0, rows = 6;
    private String selected = null;
    private boolean all = false;

    public BotScreen() { super(Text.literal("BotBuddy")); }

    private void send(String action, String target, String arg) {
        ClientPlayNetworking.send(new ActionPayload(action, target, arg));
    }

    private String tgt() { return all ? "*" : (selected == null ? "" : selected); }

    private void cmd(String text) {
        if (!all && selected == null) { BotBuddyClient.status = "Select a bot first (or switch Target to ALL)."; return; }
        send("CMD", tgt(), text);
    }

    private ButtonWidget btn(String label, int x, int y, int w, Runnable r) {
        return addDrawableChild(ButtonWidget.builder(Text.literal(label), b -> r.run()).dimensions(x, y, w, 20).build());
    }

    @Override
    protected void init() {
        int lx = 10, rx = 185;

        // --- add buttons
        btn("+", lx, 6, 24, () -> send("ADD", "", "1"));
        btn("+5", lx + 28, 6, 30, () -> send("ADD", "", "5"));
        btn("+10", lx + 62, 6, 34, () -> send("ADD", "", "10"));

        // --- search
        search = new TextFieldWidget(textRenderer, lx, 30, LIST_W, 18, Text.literal("Search"));
        search.setPlaceholder(Text.literal("Search bots..."));
        search.setChangedListener(s -> page = 0);
        addDrawableChild(search);

        // --- bot list rows (buttons)
        rows = Math.max(3, Math.min(12, (height - LIST_TOP - 90) / ROW_H));
        rowBtns = new ButtonWidget[rows];
        for (int i = 0; i < rows; i++) {
            final int row = i;
            rowBtns[i] = addDrawableChild(ButtonWidget.builder(Text.empty(), b -> {
                List<String[]> list = visible();
                int idx = page * rows + row;
                if (idx < list.size()) selected = list.get(idx)[0];
            }).dimensions(lx, LIST_TOP + i * ROW_H, LIST_W, 20).build());
        }

        // --- page buttons
        int by = LIST_TOP + rows * ROW_H + 2;
        btn("<", lx, by, 24, () -> page = Math.max(0, page - 1));
        btn(">", lx + LIST_W - 24, by, 24, () -> page++);

        // --- right panel
        targetBtn = btn("", rx, 30, 175, () -> { all = !all; refreshLabels(); });
        renameField = new TextFieldWidget(textRenderer, rx, 58, 115, 18, Text.literal("Rename"));
        renameField.setPlaceholder(Text.literal("New name..."));
        renameField.setMaxLength(16);
        addDrawableChild(renameField);
        btn("Rename", rx + 119, 57, 56, () -> {
            if (selected == null) { BotBuddyClient.status = "Select a bot first."; return; }
            send("RENAME", selected, renameField.getText());
            renameField.setText("");
        });

        btn("Surv", rx, 84, 42, () -> cmd("gamemode survival"));
        btn("Crea", rx + 44, 84, 42, () -> cmd("gamemode creative"));
        btn("Adv", rx + 88, 84, 42, () -> cmd("gamemode adventure"));
        btn("Spec", rx + 132, 84, 43, () -> cmd("gamemode spectator"));

        kitBtn = btn("", rx, 108, 115, () -> { kitIdx = (kitIdx + 1) % BotManager.KITS.length; refreshLabels(); });
        btn("Give Kit", rx + 119, 108, 56, () -> cmd("kit " + BotManager.KITS[kitIdx]));

        btn("Follow me", rx, 132, 86, () -> cmd("follow me"));
        btn("Attack me", rx + 89, 132, 86, () -> cmd("attack me"));
        btn("Guard me", rx, 156, 86, () -> cmd("guard me"));
        btn("Stop", rx + 89, 156, 86, () -> cmd("stop"));
        btn("Come here", rx, 180, 86, () -> cmd("come"));
        btn("Inventory", rx + 89, 180, 86, () -> {
            if (selected == null) BotBuddyClient.status = "Select a bot first."; else send("INV", selected, "");
        });
        btn("Remove", rx, 204, 175, () -> cmd("remove"));

        // --- command chat
        cmdField = new TextFieldWidget(textRenderer, 10, height - 24, width - 90, 18, Text.literal("Command"));
        cmdField.setPlaceholder(Text.literal("Command chat: follow me, attack me, say hi, give diamond 64, kit iron, help"));
        cmdField.setMaxLength(200);
        addDrawableChild(cmdField);
        btn("Send", width - 76, height - 25, 66, () -> {
            String t = cmdField.getText();
            if (t.isBlank()) return;
            cmd(t);
            cmdField.setText("");
        });
        refreshLabels();
    }

    private void refreshLabels() {
        targetBtn.setMessage(Text.literal(all ? "Target: ALL BOTS" : "Target: selected bot"));
        kitBtn.setMessage(Text.literal("Kit: " + BotManager.KITS[kitIdx]));
    }

    private List<String[]> visible() {
        String q = search == null ? "" : search.getText().toLowerCase(Locale.ROOT);
        List<String[]> out = new ArrayList<>();
        for (String[] b : BotBuddyClient.bots) if (q.isEmpty() || b[0].toLowerCase(Locale.ROOT).contains(q)) out.add(b);
        return out;
    }

    private void refreshRows() {
        List<String[]> list = visible();
        int maxPage = Math.max(0, (list.size() - 1) / rows);
        page = Math.min(page, maxPage);
        for (int i = 0; i < rows; i++) {
            int idx = page * rows + i;
            if (idx < list.size()) {
                String[] b = list.get(idx);
                String label = (b[0].equals(selected) ? "> " : "") + b[0] + "  (" + b[1].substring(0, 1).toUpperCase() + ")";
                rowBtns[i].setMessage(Text.literal(label));
                rowBtns[i].visible = true;
            } else {
                rowBtns[i].visible = false;
            }
        }
    }

    @Override
    public void tick() {
        if (++tickCount % 20 == 0) send("LIST", "", "");
    }

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        refreshRows();
        super.render(ctx, mx, my, delta);
        int maxPage = Math.max(0, (visible().size() - 1) / rows);

        ctx.drawTextWithShadow(textRenderer, "Bots: " + BotBuddyClient.bots.size() + "/" + BotManager.MAX_BOTS, 102, 12, WHITE);
        ctx.drawTextWithShadow(textRenderer, (page + 1) + "/" + (maxPage + 1), 10 + LIST_W / 2 - 8, LIST_TOP + rows * ROW_H + 8, 0xFFAAAAAA);

        String sel = all ? "ALL bots (" + BotBuddyClient.bots.size() + ")" : (selected == null ? "none - click a bot" : selected);
        ctx.drawTextWithShadow(textRenderer, "Selected: " + sel, 185, 16, 0xFFFFFF55);
        ctx.drawTextWithShadow(textRenderer, BotBuddyClient.status, 10, height - 38, 0xFF55FFFF);
    }

    @Override
    public boolean shouldPause() { return false; }
}
