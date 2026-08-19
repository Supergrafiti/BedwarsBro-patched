package com.dimchig.bedwarsbro.supergrafiti;

import com.dimchig.bedwarsbro.stuff.HintsValidator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.entity.Entity;
import net.minecraft.entity.projectile.EntityFireball;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

public class CorrectFireball {
    private static final int CORRECTION_WINDOW_TICKS = 80;
    private static final int MAX_TARGET_AGE_TICKS = 40;
    private static final int MAX_HIT_ATTEMPTS = 3;
    private static final int HIT_RETRY_DELAY_TICKS = 2;
    private static final int CLEANUP_INTERVAL_TICKS = 40;
    private static final double MAX_INITIAL_TARGET_DISTANCE_SQ = 25.0D;
    private static final double RAYTRACE_MARGIN = 0.35D;

    private final Minecraft mc;
    private final Map<Integer, HitState> hitFireballs = new HashMap<Integer, HitState>();

    public static boolean isActive;

    private boolean useItemWasDown;
    private int correctionTicksLeft;
    private int targetFireballId = -1;
    private World activeWorld;

    public CorrectFireball() {
        mc = Minecraft.getMinecraft();
    }

    public void updateBooleans() {
        isActive = HintsValidator.isCorrectFireballActive();
        if (!isActive) resetState();
    }

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || mc == null || mc.thePlayer == null
                || event.player != mc.thePlayer || mc.theWorld == null || mc.playerController == null) {
            return;
        }

        if (!isActive) {
            resetState();
            return;
        }

        EntityPlayerSP player = mc.thePlayer;
        if (activeWorld != mc.theWorld) {
            resetState();
            activeWorld = mc.theWorld;
        }

        updateUseItemState();
        if (correctionTicksLeft <= 0) {
            cleanupHitStates(player.ticksExisted);
            return;
        }
        correctionTicksLeft--;

        EntityFireball fireball = findTargetFireball(player, player.ticksExisted);
        if (fireball == null || !canHitFireball(fireball, player.ticksExisted)) {
            cleanupHitStates(player.ticksExisted);
            return;
        }

        hitFireball(player, fireball);
        registerHit(fireball, player.ticksExisted);
        cleanupHitStates(player.ticksExisted);
    }

    private void updateUseItemState() {
        boolean useItemDown = mc.currentScreen == null && mc.gameSettings.keyBindUseItem.isKeyDown();

        if (useItemDown) {
            correctionTicksLeft = CORRECTION_WINDOW_TICKS;
            if (!useItemWasDown) targetFireballId = -1;
        }

        useItemWasDown = useItemDown;
    }

    private EntityFireball findTargetFireball(EntityPlayerSP player, int currentTick) {
        if (targetFireballId != -1) {
            Entity target = mc.theWorld.getEntityByID(targetFireballId);
            if (!(target instanceof EntityFireball)
                    || !isValidTarget(player, (EntityFireball) target, false, useItemWasDown)
                    || !canHitFireball((EntityFireball) target, currentTick)) {
                targetFireballId = -1;
                return null;
            }

            EntityFireball lockedTarget = (EntityFireball) target;
            return (isUnderCrosshair(player, lockedTarget)
                    || (useItemWasDown && isMovingTowardsPlayer(player, lockedTarget)))
                    ? lockedTarget : null;
        }

        EntityFireball target = useItemWasDown ? findIncomingFireball(player, currentTick) : null;
        if (target == null) target = getObjectMouseOverFireball(player, currentTick, useItemWasDown);
        if (target == null) target = rayTraceFireball(player, currentTick, useItemWasDown);

        if (target != null && isValidTarget(player, target, true, useItemWasDown)
                && canHitFireball(target, currentTick)) {
            targetFireballId = target.getEntityId();
            return target;
        }
        return null;
    }

    private EntityFireball getObjectMouseOverFireball(EntityPlayerSP player, int currentTick, boolean holdingUseItem) {
        if (mc.objectMouseOver == null || !(mc.objectMouseOver.entityHit instanceof EntityFireball)) return null;

        EntityFireball fireball = (EntityFireball) mc.objectMouseOver.entityHit;
        return isValidTarget(player, fireball, targetFireballId == -1, holdingUseItem)
                && canHitFireball(fireball, currentTick) ? fireball : null;
    }

    private EntityFireball rayTraceFireball(EntityPlayerSP player, int currentTick, boolean holdingUseItem) {
        double reach = mc.playerController.getBlockReachDistance();
        Vec3 eyes = player.getPositionEyes(1.0F);
        Vec3 look = player.getLook(1.0F);
        Vec3 rayEnd = eyes.addVector(look.xCoord * reach, look.yCoord * reach, look.zCoord * reach);
        MovingObjectPosition blockHit = mc.theWorld.rayTraceBlocks(eyes, rayEnd, false, true, false);
        AxisAlignedBB searchBox = player.getEntityBoundingBox()
                .addCoord(look.xCoord * reach, look.yCoord * reach, look.zCoord * reach)
                .expand(1.0D, 1.0D, 1.0D);

        List<EntityFireball> fireballs = mc.theWorld.getEntitiesWithinAABB(EntityFireball.class, searchBox);
        EntityFireball closest = null;
        double closestDistanceSq = blockHit == null
                ? reach * reach
                : eyes.squareDistanceTo(blockHit.hitVec);

        for (int pass = 0; pass < (holdingUseItem ? 2 : 1); pass++) {
            boolean incomingOnly = holdingUseItem && pass == 0;
            for (EntityFireball fireball : fireballs) {
                if (incomingOnly && !isMovingTowardsPlayer(player, fireball)) continue;
                if (!isValidTarget(player, fireball, targetFireballId == -1, holdingUseItem)
                        || !canHitFireball(fireball, currentTick)) continue;

                AxisAlignedBB hitBox = fireball.getEntityBoundingBox().expand(
                        RAYTRACE_MARGIN, RAYTRACE_MARGIN, RAYTRACE_MARGIN);
                MovingObjectPosition intercept = hitBox.calculateIntercept(eyes, rayEnd);
                double distanceSq;

                if (hitBox.isVecInside(eyes)) {
                    distanceSq = 0.0D;
                } else if (intercept != null) {
                    distanceSq = eyes.squareDistanceTo(intercept.hitVec);
                } else {
                    continue;
                }

                if (distanceSq < closestDistanceSq) {
                    closestDistanceSq = distanceSq;
                    closest = fireball;
                }
            }

            if (closest != null || !incomingOnly) break;
        }

        return closest;
    }

    private EntityFireball findIncomingFireball(EntityPlayerSP player, int currentTick) {
        double reach = mc.playerController.getBlockReachDistance() + RAYTRACE_MARGIN;
        AxisAlignedBB searchBox = player.getEntityBoundingBox().expand(reach, reach, reach);
        List<EntityFireball> fireballs = mc.theWorld.getEntitiesWithinAABB(EntityFireball.class, searchBox);
        EntityFireball closest = null;
        double closestDistanceSq = reach * reach;

        for (EntityFireball fireball : fireballs) {
            if (!isMovingTowardsPlayer(player, fireball)
                    || !isValidTarget(player, fireball, true, true)
                    || !canHitFireball(fireball, currentTick)
                    || !hasLineOfSight(player, fireball)) {
                continue;
            }

            double distanceSq = player.getDistanceSqToEntity(fireball);
            if (distanceSq < closestDistanceSq) {
                closestDistanceSq = distanceSq;
                closest = fireball;
            }
        }

        return closest;
    }

    private boolean isUnderCrosshair(EntityPlayerSP player, EntityFireball fireball) {
        if (mc.objectMouseOver != null && mc.objectMouseOver.entityHit == fireball) return true;

        double reach = mc.playerController.getBlockReachDistance();
        Vec3 eyes = player.getPositionEyes(1.0F);
        Vec3 look = player.getLook(1.0F);
        Vec3 rayEnd = eyes.addVector(look.xCoord * reach, look.yCoord * reach, look.zCoord * reach);
        AxisAlignedBB hitBox = fireball.getEntityBoundingBox().expand(
                RAYTRACE_MARGIN, RAYTRACE_MARGIN, RAYTRACE_MARGIN);
        if (hitBox.isVecInside(eyes)) return true;

        MovingObjectPosition entityHit = hitBox.calculateIntercept(eyes, rayEnd);
        if (entityHit == null) return false;

        MovingObjectPosition blockHit = mc.theWorld.rayTraceBlocks(eyes, rayEnd, false, true, false);
        return blockHit == null
                || eyes.squareDistanceTo(entityHit.hitVec) < eyes.squareDistanceTo(blockHit.hitVec);
    }

    private boolean hasLineOfSight(EntityPlayerSP player, EntityFireball fireball) {
        Vec3 eyes = player.getPositionEyes(1.0F);
        Vec3 fireballCenter = new Vec3(fireball.posX, fireball.posY + fireball.height * 0.5D, fireball.posZ);
        MovingObjectPosition blockHit = mc.theWorld.rayTraceBlocks(eyes, fireballCenter, false, true, false);
        return blockHit == null || eyes.squareDistanceTo(blockHit.hitVec)
                >= eyes.squareDistanceTo(fireballCenter) - RAYTRACE_MARGIN * RAYTRACE_MARGIN;
    }

    private boolean isValidTarget(EntityPlayerSP player, EntityFireball fireball, boolean initialTarget,
            boolean holdingUseItem) {
        if (fireball == null || fireball.isDead || fireball.worldObj != mc.theWorld
                || (fireball.ticksExisted > MAX_TARGET_AGE_TICKS
                        && !(holdingUseItem && isMovingTowardsPlayer(player, fireball)))) {
            return false;
        }

        double distanceSq = player.getDistanceSqToEntity(fireball);
        double reach = mc.playerController.getBlockReachDistance() + RAYTRACE_MARGIN;
        if (distanceSq > reach * reach) return false;

        return !initialTarget || distanceSq <= MAX_INITIAL_TARGET_DISTANCE_SQ;
    }

    private boolean isMovingTowardsPlayer(EntityPlayerSP player, EntityFireball fireball) {
        double speedSq = fireball.motionX * fireball.motionX
                + fireball.motionY * fireball.motionY
                + fireball.motionZ * fireball.motionZ;
        if (speedSq < 1.0E-6D) return false;

        double toPlayerX = player.posX - fireball.posX;
        double toPlayerY = player.posY + player.getEyeHeight() - fireball.posY;
        double toPlayerZ = player.posZ - fireball.posZ;
        double dotProduct = fireball.motionX * toPlayerX
                + fireball.motionY * toPlayerY
                + fireball.motionZ * toPlayerZ;
        return dotProduct > 0.0D;
    }

    private boolean canHitFireball(EntityFireball fireball, int currentTick) {
        HitState state = hitFireballs.get(fireball.getEntityId());
        return state == null || (state.attempts < MAX_HIT_ATTEMPTS
                && currentTick - state.lastAttemptTick >= HIT_RETRY_DELAY_TICKS);
    }

    private void registerHit(EntityFireball fireball, int currentTick) {
        int fireballId = fireball.getEntityId();
        HitState state = hitFireballs.get(fireballId);
        if (state == null) {
            state = new HitState();
            hitFireballs.put(fireballId, state);
        }
        state.attempts++;
        state.lastAttemptTick = currentTick;
    }

    private void hitFireball(EntityPlayerSP player, EntityFireball fireball) {
        player.swingItem();
        mc.playerController.attackEntity(player, fireball);
    }

    private void cleanupHitStates(int currentTick) {
        if (currentTick % CLEANUP_INTERVAL_TICKS != 0) return;

        Iterator<Map.Entry<Integer, HitState>> iterator = hitFireballs.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Integer, HitState> entry = iterator.next();
            Entity entity = mc.theWorld.getEntityByID(entry.getKey());
            if (!(entity instanceof EntityFireball) || entity.isDead
                    || currentTick - entry.getValue().lastAttemptTick > MAX_TARGET_AGE_TICKS) {
                iterator.remove();
            }
        }
    }

    private void resetState() {
        hitFireballs.clear();
        useItemWasDown = false;
        correctionTicksLeft = 0;
        targetFireballId = -1;
        activeWorld = null;
    }

    private static final class HitState {
        private int attempts;
        private int lastAttemptTick = Integer.MIN_VALUE;
    }
}
