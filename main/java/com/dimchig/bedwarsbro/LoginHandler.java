package com.dimchig.bedwarsbro;

import java.util.ArrayList;
import java.util.Date;

import com.dimchig.bedwarsbro.Main.CONFIG_MSG;
import com.dimchig.bedwarsbro.serializer.MySerializer;
import com.dimchig.bedwarsbro.stuff.BWBed;
import com.dimchig.bedwarsbro.stuff.HintsBedScanner;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.network.FMLNetworkEvent;

public class LoginHandler {
	Minecraft mc;
	
	public LoginHandler() {
		mc = Minecraft.getMinecraft();
	}
	
	@SubscribeEvent
	public void onJoinServer(FMLNetworkEvent.ClientConnectedToServerEvent e) {
		Main.baseProps.readProps();
		Main.baseProps.readMessages();
		
		ClientScheduler.schedule(new Runnable() {
			@Override
			public void run() {
				Main.updateAllBooleans();
				ChatSender.addText(Main.chatListener.PREFIX_BEDWARSBRO + "&fВсе настройки мода - &c/bwbro");
				ChatSender.addText(Main.chatListener.PREFIX_BEDWARSBRO + "&fАвтосообщения - &e/meow");

				String patchAuthor = Main.getPropPatchAuthor();
				if (patchAuthor == null || patchAuthor.length() <= 1) patchAuthor = "Supergrafiti";
				String modAuthor = Main.getPropModAuthor();
				if (modAuthor == null || modAuthor.length() <= 1) modAuthor = "DimCh1g";

				ChatSender.addText(Main.chatListener.PREFIX_BEDWARSBRO + "&fСоздатель патча: &a" + patchAuthor);
				ChatSender.addText(Main.chatListener.PREFIX_BEDWARSBRO + "&fСоздатель мода: &a" + modAuthor);
				ChatSender.addText(Main.chatListener.PREFIX_BEDWARSBRO + "&fДискорд сервер мода - &9/bwdiscord");
				Main.updateAllBooleans();
			}
		}, 3000L);
	}
}
