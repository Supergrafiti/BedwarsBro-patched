package com.dimchig.bedwarsbro.stuff;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

import com.dimchig.bedwarsbro.ChatSender;
import com.dimchig.bedwarsbro.CustomScoreboard;
import com.dimchig.bedwarsbro.Main;
import com.dimchig.bedwarsbro.Main.CONFIG_MSG;
import com.dimchig.bedwarsbro.MyChatListener;
import com.dimchig.bedwarsbro.gui.GuiPlayerFocus;
import com.dimchig.bedwarsbro.particles.ParticleController;
import com.dimchig.bedwarsbro.stuff.BWItemsHandler.BWItemType;
import com.dimchig.bedwarsbro.stuff.HintsPlayerScanner.BWPlayer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;

public class DangerAlert {
	private static long prev_sound_time = 0;
	private static long sound_freq = 150;
	private static long prev_message_time = 0;
	private static long message_freq = 3000;
	private static final int REQUIRED_AIM_SAMPLES = 2;
	private final HashMap<Integer, Integer> aimConfirmations = new HashMap<Integer, Integer>();
	
	public void scan(ArrayList<BWPlayer> players, EntityPlayerSP mod_player) {
		long t = new Date().getTime();
		World world = Minecraft.getMinecraft().theWorld;
		Main.playerFocus.clearLines();
		Set<Integer> checkedPlayers = new HashSet<Integer>();
		for (BWPlayer p: players) {
			if (p.en.getName().equals(mod_player.getName())) continue;
			if (mod_player.getTeam() == p.en.getTeam()) continue;
			if (p.item_in_hand == null) continue;
			if (p.item_in_hand.type == BWItemType.BOW || p.item_in_hand.type == BWItemType.FIREBALL) {
				int entityId = p.en.getEntityId();
				checkedPlayers.add(entityId);
				boolean isInDanger = Main.projectileTrajectoryPreview != null
						&& Main.projectileTrajectoryPreview.isPlayerAimingAt(p.en, mod_player);
				int samples = isInDanger ? aimConfirmations.containsKey(entityId) ? aimConfirmations.get(entityId) + 1 : 1 : 0;
				if (samples == 0) aimConfirmations.remove(entityId);
				else aimConfirmations.put(entityId, samples);
				if (samples >= REQUIRED_AIM_SAMPLES) {
		    			
		    			if (GuiPlayerFocus.STATE == true) {
		    				Vec3 p1 = null;
		    				Vec3 p2 = new Vec3(p.en.posX, p.en.posY + p.en.eyeHeight, p.en.posZ);
		    				
		    				Main.playerFocus.addLine(p1, p2, Main.playerFocus.getColorByTeam(Main.chatListener.getEntityTeamColor(p.en)));
		    			}
		    			
		    			//
		    			if (t - prev_sound_time > sound_freq && Main.getConfigBool(CONFIG_MSG.DANGER_ALERT_SOUND)) {
		    				prev_sound_time = t;
		    				float volume = mod_player.getDistanceToEntity(p.en) / 12f;
		    				//note.hat - less agressive
		    				world.playSound(p.en.posX, p.en.posY + p.en.eyeHeight, p.en.posZ, "note.pling", volume, 1.0f, false);
		    			}
		    			if (t - prev_message_time > message_freq) {
		    				prev_message_time = t;
		    				if (p.item_in_hand.type == BWItemType.BOW) { 
		    					ChatSender.addText(MyChatListener.PREFIX_DANGER_ALERT + "&fНа тебя целятся из &cЛУКА");
		    				} else if (p.item_in_hand.type == BWItemType.FIREBALL) {
		    					ChatSender.addText(MyChatListener.PREFIX_DANGER_ALERT + "&fНа тебя целятся &6ФАЕРБОЛОМ");
		    				}
		    			}
				}
	    		
			}
		}
		for (Integer entityId : new ArrayList<Integer>(aimConfirmations.keySet())) {
			if (!checkedPlayers.contains(entityId)) aimConfirmations.remove(entityId);
		}
	}
	
}
