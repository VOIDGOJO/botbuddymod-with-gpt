package com.botbuddy.client;

import com.botbuddy.ActionPayload;
import com.botbuddy.ListPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

public class BotBuddyClient implements ClientModInitializer {
    public static List<String[]> bots = new ArrayList<>(); // name, gamemode, mode, health
    public static String status = "";
    private static KeyBinding openKey;

    @Override
    public void onInitializeClient() {
        KeyBinding.Category category = KeyBinding.Category.create(Identifier.of("botbuddy", "main"));
        openKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.botbuddy.open", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_B, category));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openKey.wasPressed()) {
                if (client.currentScreen == null && client.player != null) {
                    client.setScreen(new BotScreen());
                    ClientPlayNetworking.send(new ActionPayload("LIST", "", ""));
                }
            }
        });

        ClientPlayNetworking.registerGlobalReceiver(ListPayload.ID, (payload, context) -> {
            List<String[]> list = new ArrayList<>();
            for (String line : payload.data().split("\n")) {
                if (line.isBlank()) continue;
                String[] f = line.split("\t");
                if (f.length >= 4) list.add(f);
            }
            bots = list;
            if (!payload.status().isEmpty()) status = payload.status();
        });
    }
}
