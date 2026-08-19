/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.Minecraft
 *  net.minecraft.client.entity.EntityPlayerSP
 *  net.minecraft.entity.EntityLivingBase
 *  net.minecraftforge.fml.common.eventhandler.SubscribeEvent
 *  net.minecraftforge.fml.common.gameevent.TickEvent$PlayerTickEvent
 */
package com.dimchig.bedwarsbro.supergrafiti;

import com.dimchig.bedwarsbro.stuff.HintsValidator;
import java.lang.reflect.Field;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.EntityLivingBase;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.ReflectionHelper;

public class Fast_jump {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final Field JUMP_TICKS_FIELD = findJumpTicksField();
    public static boolean isActive = false;

    public void updateBooleans() {
        isActive = HintsValidator.isFastJumpActive();
    }

    @SubscribeEvent
	public void onClientTick(TickEvent.ClientTickEvent event) {
		if (Fast_jump.mc == null || Fast_jump.mc.thePlayer == null || Fast_jump.mc.theWorld == null
				|| !isActive || JUMP_TICKS_FIELD == null) {
            return;
        }
        try {
			JUMP_TICKS_FIELD.setInt(Fast_jump.mc.thePlayer, 0);
        }
		catch (IllegalAccessException e) {
            e.printStackTrace();
        }
    }

	private static Field findJumpTicksField() {
		try {
			return ReflectionHelper.findField(EntityLivingBase.class, "jumpTicks", "field_70773_bE");
		} catch (RuntimeException exception) {
			exception.printStackTrace();
			return null;
		}
	}
}
