package com.dimchig.bedwarsbro.supergrafiti;

import java.util.List;

import com.dimchig.bedwarsbro.stuff.HintsValidator;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.entity.item.EntityEnderPearl;
import net.minecraft.init.Items;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.client.C0DPacketCloseWindow;
import net.minecraft.network.play.client.C16PacketClientStatus;
import net.minecraft.util.AxisAlignedBB;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

public final class AutoEjection {
    private static final Minecraft MC = Minecraft.getMinecraft();
    private static final double ACTIVATION_HEIGHT = 20.0D;
    private static final double MIN_FALL_SPEED = -0.7D;
    private static final double HORIZONTAL_PREDICTION_TICKS = 2.0D;
    private static final double PEARL_TRACKING_RADIUS = 8.0D;

    public static boolean isActive;

    private EjectionState state = EjectionState.IDLE;
    private EntityPlayerSP lastPlayer;
    private EntityEnderPearl trackedPearl;
    private int nextActionTick;
    private int lastPearlScanTick = -4;
    private boolean inventorySessionOpen;

    private enum EjectionState {
        IDLE,
        EJECTING,
        PAUSED_FOR_PEARL,
        COMPLETE
    }

    public void updateBooleans() {
        isActive = HintsValidator.AutoEjectionActive();
        if (!isActive) resetAll();
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        EntityPlayerSP player = MC == null ? null : MC.thePlayer;
        if (!isActive || player == null || MC.theWorld == null || MC.playerController == null) {
            resetAll();
            return;
        }

        if (lastPlayer != player) {
            resetAll();
            lastPlayer = player;
        }

        boolean fallingIntoVoid = isFallingIntoVoid(player);
        updateTrackedPearl(player, fallingIntoVoid);

        if (!fallingIntoVoid) {
            resetEjectionCycle(player);
            return;
        }

        if (trackedPearl != null) {
            closeInventorySession(player);
            state = EjectionState.PAUSED_FOR_PEARL;
            return;
        }

        if (state == EjectionState.COMPLETE) return;
        if (state == EjectionState.IDLE || state == EjectionState.PAUSED_FOR_PEARL) {
            state = EjectionState.EJECTING;
        }

        if (player.openContainer != player.inventoryContainer) {
            closeInventorySession(player);
            return;
        }
        if (player.ticksExisted < nextActionTick) return;

        Slot targetSlot = findNextTargetSlot(player);
        if (targetSlot == null) {
            closeInventorySession(player);
            state = EjectionState.COMPLETE;
            return;
        }

        openInventorySession(player);

        MC.playerController.windowClick(
                player.inventoryContainer.windowId,
                targetSlot.slotNumber,
                1,
                4,
                player);
        nextActionTick = player.ticksExisted + 1;
    }

    private boolean isFallingIntoVoid(EntityPlayerSP player) {
        if (player.posY >= ACTIVATION_HEIGHT || player.motionY >= MIN_FALL_SPEED) return false;

        AxisAlignedBB bounds = player.getEntityBoundingBox();
        if (bounds == null || bounds.minY <= 0.0D) return true;

        double predictedX = player.motionX * HORIZONTAL_PREDICTION_TICKS;
        double predictedZ = player.motionZ * HORIZONTAL_PREDICTION_TICKS;
        AxisAlignedBB sweptAreaBelow = new AxisAlignedBB(
                bounds.minX + Math.min(0.0D, predictedX),
                0.0D,
                bounds.minZ + Math.min(0.0D, predictedZ),
                bounds.maxX + Math.max(0.0D, predictedX),
                bounds.minY - 0.01D,
                bounds.maxZ + Math.max(0.0D, predictedZ));

        return MC.theWorld.getCollidingBoundingBoxes(player, sweptAreaBelow).isEmpty();
    }

    private Slot findNextTargetSlot(EntityPlayerSP player) {
        List<Slot> slots = player.inventoryContainer.inventorySlots;

        for (Slot slot : slots) {
            if (slot.slotNumber >= 36 && slot.slotNumber <= 44 && isTargetSlot(slot, player)) return slot;
        }
        for (Slot slot : slots) {
            if (slot.slotNumber >= 9 && slot.slotNumber <= 35 && isTargetSlot(slot, player)) return slot;
        }
        return null;
    }

    private boolean isTargetSlot(Slot slot, EntityPlayerSP player) {
        if (slot == null || slot.inventory != player.inventory || !slot.canTakeStack(player)) return false;
        ItemStack stack = slot.getStack();
        return stack != null && stack.stackSize > 0 && isTargetItem(stack);
    }

    private boolean isTargetItem(ItemStack stack) {
        return stack.getItem() == Items.iron_ingot
                || stack.getItem() == Items.gold_ingot
                || stack.getItem() == Items.diamond
                || stack.getItem() == Items.emerald;
    }

    private void updateTrackedPearl(EntityPlayerSP player, boolean forceScan) {
        if (trackedPearl != null) {
            if (!trackedPearl.isDead && trackedPearl.worldObj == player.worldObj) return;
            trackedPearl = null;
        }

        if (!forceScan && player.ticksExisted - lastPearlScanTick < 4) return;
        lastPearlScanTick = player.ticksExisted;

        AxisAlignedBB searchArea = player.getEntityBoundingBox().expand(
                PEARL_TRACKING_RADIUS,
                PEARL_TRACKING_RADIUS,
                PEARL_TRACKING_RADIUS);
        List<EntityEnderPearl> pearls = player.worldObj.getEntitiesWithinAABB(EntityEnderPearl.class, searchArea);
        for (EntityEnderPearl pearl : pearls) {
            if (pearl != null && !pearl.isDead && pearl.getThrower() == player) {
                trackedPearl = pearl;
                return;
            }
        }
    }

    private void openInventorySession(EntityPlayerSP player) {
        if (inventorySessionOpen || player == null || player.sendQueue == null) return;
        player.sendQueue.addToSendQueue(new C16PacketClientStatus(
                C16PacketClientStatus.EnumState.OPEN_INVENTORY_ACHIEVEMENT));
        inventorySessionOpen = true;
    }

    private void closeInventorySession(EntityPlayerSP player) {
        if (!inventorySessionOpen) return;
        if (player != null && player.sendQueue != null && player.inventoryContainer != null) {
            player.sendQueue.addToSendQueue(new C0DPacketCloseWindow(player.inventoryContainer.windowId));
        }
        inventorySessionOpen = false;
    }

    private void resetEjectionCycle(EntityPlayerSP player) {
        closeInventorySession(player);
        state = EjectionState.IDLE;
        nextActionTick = 0;
    }

    private void resetAll() {
        resetEjectionCycle(MC == null ? null : MC.thePlayer);
        trackedPearl = null;
        lastPlayer = null;
        lastPearlScanTick = -4;
    }
}
