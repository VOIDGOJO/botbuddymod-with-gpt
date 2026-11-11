package com.botbuddy;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

public class BotBuddy implements ModInitializer {
    @Override
    public void onInitialize() {
        PayloadTypeRegistry.playC2S().register(ActionPayload.ID, ActionPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(ListPayload.ID, ListPayload.CODEC);
        BotManager.init();

        ServerPlayNetworking.registerGlobalReceiver(ActionPayload.ID, (payload, context) -> {
            ServerPlayerEntity player = context.player();
            MinecraftServer server = context.server();
            String status = "";
            if (!server.isSingleplayer() && !server.getPlayerManager().isOperator(player.getPlayerConfigEntry())) {
                status = "You need OP (level 2) to use BotBuddy.";
            } else {
                switch (payload.action()) {
                    case "ADD" -> {
                        int n = 1;
                        try { n = Integer.parseInt(payload.arg()); } catch (NumberFormatException ignored) {}
                        status = BotManager.queueAdd(player, Math.max(1, n));
                    }
                    case "RENAME" -> status = BotManager.rename(server, payload.target(), payload.arg().trim());
                    case "INV" -> status = BotManager.openInventory(player, payload.target());
                    case "CMD" -> status = BotManager.command(server, player, payload.target(), payload.arg());
                    default -> {}
                }
            }
            ServerPlayNetworking.send(player, new ListPayload(status, BotManager.listData()));
        });
    }
}
