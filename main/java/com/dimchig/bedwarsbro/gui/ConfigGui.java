package com.dimchig.bedwarsbro.gui;

import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.resources.I18n;
import net.minecraftforge.common.config.ConfigElement;
import net.minecraftforge.fml.client.config.GuiConfig;
import net.minecraftforge.fml.client.config.IConfigElement;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;

import com.dimchig.bedwarsbro.ColorCodesManager;
import com.dimchig.bedwarsbro.Main;

public class ConfigGui extends GuiConfig {

    private static final int MINIMAP_POSITION_BUTTON_ID = 9100;

    @Mod.Instance
    private static Main asInstance;

    public ConfigGui(GuiScreen parentScreen) {
        super(parentScreen, getConfigElements(), Main.MODID, false, false, ColorCodesManager.replaceColorCodesInString("&eКонфиг &7для &cBedwars&fBro &7| &b&lНаводи мышкой на названия! &7| &2&ltrue &7= &aВключить&7, &4&lfalse &7= &cВыключить"));
    }

    @Override
    public void initGui() {
        super.initGui();
        buttonList.add(new GuiButton(MINIMAP_POSITION_BUTTON_ID, width - 125, 5, 120, 20,
                ColorCodesManager.replaceColorCodesInString("&6Перетащить карту")));
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == MINIMAP_POSITION_BUTTON_ID) {
            mc.displayGuiScreen(new GuiMinimapPositionEditor(this));
            return;
        }
        super.actionPerformed(button);
    }

    private static List<IConfigElement> getConfigElements() {
        return new ArrayList<IConfigElement>(new ConfigElement(asInstance.getClientConfig()).getChildElements());
    }
}
