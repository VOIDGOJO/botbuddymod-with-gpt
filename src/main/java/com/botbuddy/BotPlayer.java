package com.botbuddy;

import com.mojang.authlib.GameProfile;
import net.minecraft.command.argument.EntityAnchorArgumentType;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.network.packet.c2s.common.SyncedClientOptions;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Hand;

import java.util.Comparator;
import java.util.Set;
import java.util.UUID;

/** A server-side fake player with simple follow / attack / guard behaviour. */
public class BotPlayer extends ServerPlayerEntity {
    public enum Mode { IDLE, FOLLOW, ATTACK, GUARD }

    public Mode mode = Mode.IDLE;
    public UUID targetId;
    public UUID ownerId;
    public String kit = "Empty";
    public int deadTicks = 0;
    private boolean jumpOnce = false;
    private Entity guardVictim;

    public BotPlayer(MinecraftServer server, ServerWorld world, GameProfile profile, SyncedClientOptions options) {
        super(server, world, profile, options);
    }

    public void jumpNextTick() { jumpOnce = true; }

    @Override
    public void tick() {
        if (getServerWorld().getServer().getTicks() % 10 == 0) {
            networkHandler.syncWithPlayerPosition();
        }
        try {
            super.tick();
            playerTick();
        } catch (NullPointerException ignored) {
            // fake connection edge cases
        }
        think();
    }

    private Entity nearestHostile(Entity around) {
        return getServerWorld()
            .getEntitiesByClass(HostileEntity.class, around.getBoundingBox().expand(12), e -> e.isAlive())
            .stream().min(Comparator.comparingDouble(e -> e.squaredDistanceTo(this))).orElse(null);
    }

    private void think() {
        forwardSpeed = 0;
        sidewaysSpeed = 0;
        boolean jump = jumpOnce;
        jumpOnce = false;

        if (!isAlive() || mode == Mode.IDLE) { setSprinting(false); setJumping(jump); return; }

        Entity who = targetId == null ? null : getServerWorld().getEntity(targetId);
        if (who == null || !who.isAlive()) { mode = Mode.IDLE; setJumping(jump); return; }

        Entity victim = null;
        double stop = 3.0;
        if (mode == Mode.ATTACK) {
            victim = who; stop = 1.8;
        } else if (mode == Mode.GUARD) {
            if (age % 10 == 0 || guardVictim == null || !guardVictim.isAlive()) guardVictim = nearestHostile(who);
            victim = guardVictim; stop = 1.8;
        }
        Entity goal = victim != null ? victim : who;

        double dx = goal.getX() - getX(), dz = goal.getZ() - getZ();
        double dist = Math.sqrt(dx * dx + dz * dz);

        if (mode != Mode.ATTACK && victim == null && dist > 30) {
            teleport(getServerWorld(), who.getX(), who.getY(), who.getZ(), Set.of(), getYaw(), getPitch(), false);
            return;
        }

        lookAt(EntityAnchorArgumentType.EntityAnchor.EYES, goal.getEyePos());

        if (dist > stop) {
            forwardSpeed = 1.0f;
            setSprinting(victim != null || dist > 8);
            if (isOnGround() && (horizontalCollision || goal.getY() - getY() > 0.9)) jump = true;
        } else {
            setSprinting(false);
        }

        if (victim != null && distanceTo(victim) < 3.0f && getAttackCooldownProgress(0.5f) >= 1.0f) {
            swingHand(Hand.MAIN_HAND);
            attack(victim);
        }
        setJumping(jump);
    }
}
