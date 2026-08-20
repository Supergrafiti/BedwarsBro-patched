package com.dimchig.bedwarsbro.gui;

import java.io.IOException;

import com.dimchig.bedwarsbro.ColorCodesManager;
import com.dimchig.bedwarsbro.Main;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;

/** Visual editor for the minimap position. Coordinates are stored in the normal mod config. */
public class GuiMinimapPositionEditor extends GuiScreen {

    private static final int BUTTON_DONE = 0;
    private static final int BUTTON_RESET = 1;

    private final GuiScreen parent;
    private int mapLeft;
    private int mapTop;
    private int mapSize;
    private boolean dragging;
    private boolean resizing;
    private int dragOffsetX;
    private int dragOffsetY;

    public GuiMinimapPositionEditor(GuiScreen parent) {
        this.parent = parent;
    }

    @Override
    public void initGui() {
        GuiMinimap.updateSizes();
        mapLeft = GuiMinimap.getTopX();
        mapTop = GuiMinimap.getTopY();
        mapSize = GuiMinimap.map_size;
        clampPosition();

        buttonList.add(new GuiButton(BUTTON_DONE, width / 2 - 102, height - 28, 100, 20,
                ColorCodesManager.replaceColorCodesInString("&aГотово")));
        buttonList.add(new GuiButton(BUTTON_RESET, width / 2 + 2, height - 28, 100, 20,
                ColorCodesManager.replaceColorCodesInString("&eСбросить")));
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        drawCenteredString(fontRendererObj, ColorCodesManager.replaceColorCodesInString("&6Положение миникарты"), width / 2, 12, 0xFFFFFF);
        drawCenteredString(fontRendererObj, ColorCodesManager.replaceColorCodesInString("&7Зажми миникарту левой кнопкой мыши и перетащи её"), width / 2, 27, 0xFFFFFF);
        drawCenteredString(fontRendererObj, ColorCodesManager.replaceColorCodesInString("&7Тяни за угол справа снизу, чтобы изменить размер"), width / 2, 39, 0xFFFFFF);

        int border = GuiMinimap.getFrameBorderSize();
        int outerLeft = mapLeft - border;
        int outerTop = mapTop - border;
        int outerRight = mapLeft + mapSize + border;
        int outerBottom = mapTop + mapSize + border;

        drawRect(outerLeft, outerTop, outerRight, outerBottom, 0xFF555555);
        drawRect(mapLeft, mapTop, mapLeft + mapSize, mapTop + mapSize, 0xCC151515);
        drawRect(mapLeft + 2, mapTop + 2, mapLeft + mapSize - 2, mapTop + mapSize - 2, 0xAA244A30);
        int centerX = mapLeft + mapSize / 2;
        int centerY = mapTop + mapSize / 2;
        drawRect(centerX - 1, mapTop + 4, centerX + 1, mapTop + mapSize - 4, 0x6677DD77);
        drawRect(mapLeft + 4, centerY - 1, mapLeft + mapSize - 4, centerY + 1, 0x6677DD77);
        drawCenteredString(fontRendererObj, ColorCodesManager.replaceColorCodesInString("&aМиникарта"), centerX, centerY - 4, 0xFFFFFF);
        int handleColor = 0xFF9A9A9A;
        drawRect(mapLeft + mapSize - 12, mapTop + mapSize - 3, mapLeft + mapSize - 3, mapTop + mapSize - 2, handleColor);
        drawRect(mapLeft + mapSize - 3, mapTop + mapSize - 12, mapLeft + mapSize - 2, mapTop + mapSize - 2, handleColor);
        drawRect(mapLeft + mapSize - 8, mapTop + mapSize - 6, mapLeft + mapSize - 6, mapTop + mapSize - 5, handleColor);

        String coordinates = "X: " + (mapLeft - border) + "  Y: " + (mapTop - border);
        drawCenteredString(fontRendererObj, coordinates, width / 2, height - 48, 0xFFFFFF);
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        super.mouseClicked(mouseX, mouseY, mouseButton);
        if (mouseButton != 0) return;

        int border = GuiMinimap.getFrameBorderSize();
        if (mouseX >= mapLeft + mapSize - 12 && mouseX <= mapLeft + mapSize + border
                && mouseY >= mapTop + mapSize - 12 && mouseY <= mapTop + mapSize + border) {
            resizing = true;
        } else if (mouseX >= mapLeft - border && mouseX <= mapLeft + mapSize + border
                && mouseY >= mapTop - border && mouseY <= mapTop + mapSize + border) {
            dragging = true;
            dragOffsetX = mouseX - mapLeft;
            dragOffsetY = mouseY - mapTop;
        }
    }

    @Override
    protected void mouseClickMove(int mouseX, int mouseY, int clickedMouseButton, long timeSinceLastClick) {
        if (dragging && clickedMouseButton == 0) {
            mapLeft = mouseX - dragOffsetX;
            mapTop = mouseY - dragOffsetY;
            clampPosition();
        } else if (resizing && clickedMouseButton == 0) {
            int border = GuiMinimap.getFrameBorderSize();
            int maxSize = Math.min(500, Math.min(width - mapLeft - border, height - mapTop - border));
            mapSize = Math.max(50, Math.min(Math.max(50, maxSize), Math.max(mouseX - mapLeft, mouseY - mapTop)));
        }
    }

    @Override
    protected void mouseReleased(int mouseX, int mouseY, int state) {
        super.mouseReleased(mouseX, mouseY, state);
        if (state == 0 && dragging) {
            dragging = false;
            savePosition();
        } else if (state == 0 && resizing) {
            resizing = false;
            savePosition();
        }
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == BUTTON_RESET) {
            mapSize = 100;
            mapLeft = GuiMinimap.getFrameBorderSize();
            mapTop = GuiMinimap.getFrameBorderSize();
            savePosition();
        } else if (button.id == BUTTON_DONE) {
            savePosition();
            mc.displayGuiScreen(parent);
        }
    }

    @Override
    public boolean doesGuiPauseGame() {
        return true;
    }

    private void clampPosition() {
        int border = GuiMinimap.getFrameBorderSize();
        int maxLeft = Math.max(border, width - mapSize - border);
        int maxTop = Math.max(border, height - mapSize - border);
        mapLeft = Math.max(border, Math.min(mapLeft, maxLeft));
        mapTop = Math.max(border, Math.min(mapTop, maxTop));
    }

    private void savePosition() {
        clampPosition();
        GuiMinimap.saveEditorLayout(mapLeft, mapTop, mapSize);
        mapLeft = GuiMinimap.getTopX();
        mapTop = GuiMinimap.getTopY();
        mapSize = GuiMinimap.map_size;
    }
}
