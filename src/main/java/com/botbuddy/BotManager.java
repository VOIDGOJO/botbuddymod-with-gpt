package com.botbuddy;

import com.mojang.authlib.GameProfile;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.command.argument.EntityAnchorArgumentType;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.network.packet.c2s.common.SyncedClientOptions;
import net.minecraft.registry.Registries;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.screen.SimpleNamedScreenHandlerFactory;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ConnectedClientData;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.Uuids;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;

import java.util.*;

public final class BotManager {
    /** Change this if you want more/less bots. Every bot is a full player, so be realistic! */
    public static final int MAX_BOTS = 100;
    public static final String[] KITS = {"Empty", "Starter", "Iron", "Diamond", "Netherite", "Archer", "Builder", "Food"};

    private record SpawnReq(UUID owner, String name, String kit, GameMode gm) {}

    private static final Map<String, BotPlayer> BOTS = new LinkedHashMap<>();
    private static final ArrayDeque<SpawnReq> PENDING = new ArrayDeque<>();
    private static final Random R = new Random();

    private BotManager() {}

    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(BotManager::tick);
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            for (BotPlayer b : new ArrayList<>(BOTS.values())) remove(server, b);
            BOTS.clear();
            PENDING.clear();
        });
    }

    // ---------------------------------------------------------------- ticking
    private static void tick(MinecraftServer server) {
        // spawn one queued bot per tick so adding many doesn't freeze the game
        SpawnReq req = PENDING.poll();
        if (req != null && BOTS.size() < MAX_BOTS) {
            ServerPlayerEntity owner = server.getPlayerManager().getPlayer(req.owner());
            if (owner != null) {
                String name = req.name();
                if (name == null || BOTS.containsKey(name) || server.getPlayerManager().getPlayer(name) != null) {
                    name = BotNames.next(server, BOTS.keySet());
                }
                Vec3d p = owner.getEntityPos();
                double a = R.nextDouble() * Math.PI * 2, r = 2 + R.nextDouble() * 2;
                BotPlayer b = spawn(server, owner.getServerWorld(),
                    new Vec3d(p.x + Math.cos(a) * r, p.y, p.z + Math.sin(a) * r),
                    owner.getYaw(), owner.getUuid(), name, req.gm());
                if (req.kit() != null && !req.kit().equals("Empty")) giveKit(b, req.kit());
            }
        }
        // clean up / respawn dead bots
        for (BotPlayer b : new ArrayList<>(BOTS.values())) {
            if (server.getPlayerManager().getPlayer(name(b)) == null) { BOTS.remove(name(b)); continue; }
            if (!b.isAlive() && ++b.deadTicks > 40) {
                String n = name(b);
                GameMode gm = b.interactionManager.getGameMode();
                UUID owner = b.ownerId;
                String kit = b.kit;
                remove(server, b);
                if (owner != null) PENDING.add(new SpawnReq(owner, n, kit, gm == GameMode.SPECTATOR ? GameMode.SURVIVAL : gm));
            }
        }
    }

    private static String name(BotPlayer b) { return b.getName().getString(); }

    // ---------------------------------------------------------------- spawn / remove
    private static BotPlayer spawn(MinecraftServer server, ServerWorld world, Vec3d pos, float yaw,
                                   UUID ownerId, String name, GameMode gm) {
        GameProfile profile = new GameProfile(Uuids.getOfflinePlayerUuid(name), name);
        BotPlayer bot = new BotPlayer(server, world, profile, SyncedClientOptions.createDefault());
        bot.ownerId = ownerId;
        server.getPlayerManager().onPlayerConnect(new FakeConnection(), bot,
            ConnectedClientData.createDefault(profile, false));
        bot.teleport(world, pos.x, pos.y, pos.z, Set.of(), yaw, 0f, false);
        bot.setHealth(20.0f);
        bot.unsetRemoved();
        bot.changeGameMode(gm);
        BOTS.put(name, bot);
        return bot;
    }

    private static void remove(MinecraftServer server, BotPlayer b) {
        BOTS.remove(name(b));
        server.getPlayerManager().remove(b);
    }

    public static String queueAdd(ServerPlayerEntity owner, int count) {
        int room = MAX_BOTS - BOTS.size() - PENDING.size();
        count = Math.min(count, room);
        if (count <= 0) return "Bot limit reached (" + MAX_BOTS + ").";
        for (int i = 0; i < count; i++) PENDING.add(new SpawnReq(owner.getUuid(), null, "Empty", GameMode.SURVIVAL));
        return "Adding " + count + " bot(s)...";
    }

    public static String rename(MinecraftServer server, String target, String newName) {
        BotPlayer old = BOTS.get(target);
        if (old == null) return "Select a bot first.";
        if (!newName.matches("[A-Za-z0-9_]{3,16}")) return "Name: 3-16 letters, numbers or _";
        if (BOTS.containsKey(newName) || server.getPlayerManager().getPlayer(newName) != null) return "That name is already used.";
        ServerWorld world = old.getServerWorld();
        Vec3d pos = old.getEntityPos();
        float yaw = old.getYaw();
        GameMode gm = old.interactionManager.getGameMode();
        List<ItemStack> items = new ArrayList<>();
        for (int i = 0; i < old.getInventory().size(); i++) items.add(old.getInventory().getStack(i).copy());
        BotPlayer.Mode mode = old.mode;
        UUID targetId = old.targetId, ownerId = old.ownerId;
        String kit = old.kit;
        remove(server, old);
        BotPlayer nb = spawn(server, world, pos, yaw, ownerId, newName, gm);
        for (int i = 0; i < items.size(); i++) nb.getInventory().setStack(i, items.get(i));
        nb.mode = mode; nb.targetId = targetId; nb.kit = kit;
        return target + " is now " + newName;
    }

    // ---------------------------------------------------------------- listing
    public static String listData() {
        StringBuilder sb = new StringBuilder();
        for (BotPlayer b : BOTS.values()) {
            if (sb.length() > 30000) break;
            sb.append(name(b)).append('\t')
              .append(b.interactionManager.getGameMode().getId()).append('\t')
              .append(b.mode.name().toLowerCase()).append('\t')
              .append((int) b.getHealth()).append('\n');
        }
        return sb.toString();
    }

    public static String openInventory(ServerPlayerEntity player, String target) {
        BotPlayer bot = BOTS.get(target);
        if (bot == null) return "Select a bot first.";
        player.openHandledScreen(new SimpleNamedScreenHandlerFactory(
            (syncId, inv, p) -> new GenericContainerScreenHandler(ScreenHandlerType.GENERIC_9X4, syncId, inv, bot.getInventory(), 4),
            Text.literal(target + "'s inventory")));
        return "Opened " + target + "'s inventory.";
    }

    // ---------------------------------------------------------------- commands
    public static String command(MinecraftServer server, ServerPlayerEntity owner, String target, String line) {
        line = line.trim();
        if (line.startsWith("/")) line = line.substring(1);
        if (line.isEmpty()) return "Type a command (try: help).";
        List<BotPlayer> targets = new ArrayList<>();
        if ("*".equals(target)) targets.addAll(BOTS.values());
        else if (BOTS.containsKey(target)) targets.add(BOTS.get(target));
        if (targets.isEmpty()) return "No bot selected (or no bots exist).";
        String last = "";
        for (BotPlayer b : targets) last = exec(server, owner, b, line);
        return targets.size() == 1 ? last : "'" + line + "' sent to " + targets.size() + " bots.";
    }

    private static ServerPlayerEntity who(MinecraftServer server, ServerPlayerEntity owner, String arg) {
        if (arg.isEmpty() || arg.equalsIgnoreCase("me")) return owner;
        return server.getPlayerManager().getPlayer(arg);
    }

    private static String exec(MinecraftServer server, ServerPlayerEntity owner, BotPlayer b, String line) {
        String[] p = line.split("\\s+", 2);
        String cmd = p[0].toLowerCase(Locale.ROOT);
        String rest = p.length > 1 ? p[1].trim() : "";
        String n = name(b);
        switch (cmd) {
            case "follow" -> {
                ServerPlayerEntity t = who(server, owner, rest);
                if (t == null) return "Player not found: " + rest;
                b.mode = BotPlayer.Mode.FOLLOW; b.targetId = t.getUuid();
                return n + " is following " + t.getName().getString();
            }
            case "attack", "kill", "fight" -> {
                ServerPlayerEntity t = who(server, owner, rest);
                if (t == null) return "Player not found: " + rest;
                b.mode = BotPlayer.Mode.ATTACK; b.targetId = t.getUuid();
                return n + " is attacking " + t.getName().getString();
            }
            case "guard", "protect" -> {
                ServerPlayerEntity t = who(server, owner, rest);
                if (t == null) return "Player not found: " + rest;
                b.mode = BotPlayer.Mode.GUARD; b.targetId = t.getUuid();
                return n + " is guarding " + t.getName().getString();
            }
            case "stop", "stay", "idle" -> {
                b.mode = BotPlayer.Mode.IDLE; b.setSneaking(false);
                return n + " stopped.";
            }
            case "come", "tp", "tphere" -> {
                b.teleport(owner.getServerWorld(), owner.getX(), owner.getY(), owner.getZ(), Set.of(), owner.getYaw(), 0f, false);
                return n + " teleported to you.";
            }
            case "look" -> {
                b.lookAt(EntityAnchorArgumentType.EntityAnchor.EYES, owner.getEyePos());
                return n + " is looking at you.";
            }
            case "jump" -> { b.jumpNextTick(); return n + " jumped."; }
            case "sneak" -> { b.setSneaking(true); return n + " is sneaking."; }
            case "unsneak" -> { b.setSneaking(false); return n + " stopped sneaking."; }
            case "say", "chat" -> {
                if (rest.isEmpty()) return "say what?";
                server.getPlayerManager().broadcast(
                    Text.translatable("chat.type.text", b.getDisplayName(), Text.literal(rest)), false);
                return n + " said it.";
            }
            case "gm", "gamemode" -> {
                GameMode gm = switch (rest.toLowerCase(Locale.ROOT)) {
                    case "s", "survival", "0" -> GameMode.SURVIVAL;
                    case "c", "creative", "1" -> GameMode.CREATIVE;
                    case "a", "adventure", "2" -> GameMode.ADVENTURE;
                    case "sp", "spectator", "3" -> GameMode.SPECTATOR;
                    default -> null;
                };
                if (gm == null) return "gamemode: survival / creative / adventure / spectator";
                b.changeGameMode(gm);
                return n + " is now in " + gm.getId() + ".";
            }
            case "kit" -> {
                if (rest.isEmpty()) return "Kits: " + String.join(", ", KITS);
                return giveKit(b, rest) ? n + " got kit " + b.kit + "." : "Unknown kit. Kits: " + String.join(", ", KITS);
            }
            case "give" -> {
                String[] a = rest.split("\\s+");
                if (a.length == 0 || a[0].isEmpty()) return "give <item> [count]";
                int count = 1;
                try { if (a.length > 1) count = Integer.parseInt(a[1]); } catch (NumberFormatException ignored) {}
                Identifier id = Identifier.tryParse(a[0].contains(":") ? a[0] : "minecraft:" + a[0].toLowerCase(Locale.ROOT));
                Optional<Item> item = id == null ? Optional.empty() : Registries.ITEM.getOptionalValue(id);
                if (item.isEmpty() || item.get() == Items.AIR) return "Unknown item: " + a[0];
                int left = Math.max(1, Math.min(count, 2304));
                while (left > 0) {
                    int c = Math.min(left, new ItemStack(item.get()).getMaxCount());
                    b.getInventory().insertStack(new ItemStack(item.get(), c));
                    left -= c;
                }
                return n + " received " + a[0] + ".";
            }
            case "clear" -> { b.getInventory().clear(); return n + "'s inventory cleared."; }
            case "remove", "leave", "kick" -> { remove(server, b); return n + " left."; }
            case "help" -> {
                return "follow [me|name], attack [me|name], guard, stop, come, look, jump, sneak, say <txt>, "
                     + "gamemode <s|c|a|sp>, kit <name>, give <item> [n], clear, remove";
            }
            default -> { return "Unknown command '" + cmd + "'. Try: help"; }
        }
    }

    // ---------------------------------------------------------------- kits
    private static void gear(BotPlayer b, Item head, Item chest, Item legs, Item feet) {
        b.equipStack(EquipmentSlot.HEAD, new ItemStack(head));
        b.equipStack(EquipmentSlot.CHEST, new ItemStack(chest));
        b.equipStack(EquipmentSlot.LEGS, new ItemStack(legs));
        b.equipStack(EquipmentSlot.FEET, new ItemStack(feet));
    }

    private static void add(BotPlayer b, Item item, int count) { b.getInventory().insertStack(new ItemStack(item, count)); }

    public static boolean giveKit(BotPlayer b, String kit) {
        String k = null;
        for (String s : KITS) if (s.equalsIgnoreCase(kit)) k = s;
        if (k == null) return false;
        PlayerInventory inv = b.getInventory();
        inv.clear();
        switch (k) {
            case "Starter" -> {
                gear(b, Items.LEATHER_HELMET, Items.LEATHER_CHESTPLATE, Items.LEATHER_LEGGINGS, Items.LEATHER_BOOTS);
                add(b, Items.STONE_SWORD, 1); add(b, Items.STONE_PICKAXE, 1); add(b, Items.STONE_AXE, 1);
                add(b, Items.STONE_SHOVEL, 1); add(b, Items.BREAD, 16); add(b, Items.TORCH, 32);
            }
            case "Iron" -> {
                gear(b, Items.IRON_HELMET, Items.IRON_CHESTPLATE, Items.IRON_LEGGINGS, Items.IRON_BOOTS);
                add(b, Items.IRON_SWORD, 1); add(b, Items.IRON_PICKAXE, 1); add(b, Items.IRON_AXE, 1);
                add(b, Items.COOKED_BEEF, 32);
                b.equipStack(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
            }
            case "Diamond" -> {
                gear(b, Items.DIAMOND_HELMET, Items.DIAMOND_CHESTPLATE, Items.DIAMOND_LEGGINGS, Items.DIAMOND_BOOTS);
                add(b, Items.DIAMOND_SWORD, 1); add(b, Items.DIAMOND_PICKAXE, 1); add(b, Items.DIAMOND_AXE, 1);
                add(b, Items.GOLDEN_APPLE, 8); add(b, Items.COOKED_BEEF, 32);
                b.equipStack(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
            }
            case "Netherite" -> {
                gear(b, Items.NETHERITE_HELMET, Items.NETHERITE_CHESTPLATE, Items.NETHERITE_LEGGINGS, Items.NETHERITE_BOOTS);
                add(b, Items.NETHERITE_SWORD, 1); add(b, Items.NETHERITE_PICKAXE, 1); add(b, Items.NETHERITE_AXE, 1);
                add(b, Items.GOLDEN_APPLE, 16); add(b, Items.ENDER_PEARL, 16); add(b, Items.COOKED_BEEF, 64);
                b.equipStack(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
            }
            case "Archer" -> {
                gear(b, Items.IRON_HELMET, Items.IRON_CHESTPLATE, Items.IRON_LEGGINGS, Items.IRON_BOOTS);
                add(b, Items.IRON_SWORD, 1); add(b, Items.BOW, 1); add(b, Items.ARROW, 64); add(b, Items.ARROW, 64);
                add(b, Items.COOKED_BEEF, 32);
            }
            case "Builder" -> {
                add(b, Items.IRON_PICKAXE, 1); add(b, Items.IRON_AXE, 1); add(b, Items.IRON_SHOVEL, 1);
                add(b, Items.COBBLESTONE, 64); add(b, Items.COBBLESTONE, 64); add(b, Items.OAK_PLANKS, 64);
                add(b, Items.GLASS, 64); add(b, Items.TORCH, 64); add(b, Items.BREAD, 32);
            }
            case "Food" -> {
                add(b, Items.COOKED_BEEF, 64); add(b, Items.GOLDEN_APPLE, 32); add(b, Items.BREAD, 64);
                add(b, Items.COOKED_PORKCHOP, 64);
            }
            default -> {}
        }
        inv.setSelectedSlot(0);
        b.kit = k;
        return true;
    }
}
