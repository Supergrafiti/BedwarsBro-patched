package com.dimchig.bedwarsbro;

import com.dimchig.bedwarsbro.stuff.HintsValidator;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.potion.Potion;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

public class AutoSprint {
	
	private final Minecraft mc;
    private boolean isAutoSprintActive = false;
	private boolean wasSprintKeyForced = false;
    
    public AutoSprint() {
    	mc = Minecraft.getMinecraft();
    	updateBooleans();
    }
    
    public void updateBooleans() {
    	isAutoSprintActive = HintsValidator.isAutoSprintActive();
		if (!isAutoSprintActive) releaseSprintKey();
    }
	
	@SubscribeEvent
	public void onClientTick(TickEvent.ClientTickEvent event) {
		if (mc == null || mc.gameSettings == null) return;
		if (!isAutoSprintActive) {
			releaseSprintKey();
			return;
		}

		KeyBinding sprintBinding = mc.gameSettings.keyBindSprint;
		KeyBinding.setKeyBindState(sprintBinding.getKeyCode(), true);
		wasSprintKeyForced = true;

		if (event.phase != TickEvent.Phase.END || mc.thePlayer == null || mc.theWorld == null) return;
		EntityPlayerSP player = mc.thePlayer;
		if (canSprint(player)) player.setSprinting(true);
    }

	private boolean canSprint(EntityPlayerSP player) {
		return player.movementInput != null
				&& player.movementInput.moveForward >= 0.8F
				&& !player.isSneaking()
				&& !player.isUsingItem()
				&& !player.isPotionActive(Potion.blindness)
				&& !player.isCollidedHorizontally
				&& (player.getFoodStats().getFoodLevel() > 6 || player.capabilities.allowFlying);
	}

	private void releaseSprintKey() {
		if (!wasSprintKeyForced || mc == null || mc.gameSettings == null) return;
		KeyBinding sprintBinding = mc.gameSettings.keyBindSprint;
		KeyBinding.setKeyBindState(sprintBinding.getKeyCode(), GameSettings.isKeyDown(sprintBinding));
		wasSprintKeyForced = false;
	}
}	
