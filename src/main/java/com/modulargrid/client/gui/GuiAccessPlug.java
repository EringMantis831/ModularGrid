package com.modulargrid.client.gui;

import com.modulargrid.common.channel.ChannelInfo;
import com.modulargrid.common.channel.FirmChannelDefinition;
import com.modulargrid.common.container.ContainerAccessPlug;
import com.modulargrid.common.integration.fluxnetworks.FluxNetworksIntegration;
import com.modulargrid.common.network.NetworkHandler;
import com.modulargrid.common.network.PktChannelToggle;
import com.modulargrid.common.network.PktFluxNetworkCycle;
import com.modulargrid.common.tile.TileAccessPlug;
import hellfirepvp.modularmachinery.ModularMachinery;
import hellfirepvp.modularmachinery.client.gui.GuiContainerBase;
import hellfirepvp.modularmachinery.client.gui.widget.GuiScrollbar;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.input.Mouse;

import java.io.IOException;
import java.util.List;

/**
 * Access plug GUI - the list of channels the machine may use, with a per channel on/off switch.
 */
@SideOnly(Side.CLIENT)
public class GuiAccessPlug extends GuiContainerBase<ContainerAccessPlug> {

    private static final ResourceLocation TEXTURES_FACTORY =
            new ResourceLocation(ModularMachinery.MODID, "textures/gui/guifactory.png");
    /** Button sprites, three states stacked vertically: normal, disabled, highlighted. */
    private static final ResourceLocation TEXTURES_CHANNEL_BUTTON =
            new ResourceLocation(com.modulargrid.Tags.MOD_ID, "textures/gui/access_plug_toggle.png");
    private static final ResourceLocation TEXTURES_FLUX_BUTTON =
            new ResourceLocation(com.modulargrid.Tags.MOD_ID, "textures/gui/flux_toggle.png");

    private static final int BUTTON_STATE_NORMAL = 0;
    private static final int BUTTON_STATE_DISABLED = 1;
    private static final int BUTTON_STATE_HIGHLIGHTED = 2;

    /** Both button sprites are laid out on a 128x128 sheet. */
    private static final float SPRITE_SHEET = 128.0F;

    /** Same text scale Modular Machinery uses in that GUI. */
    private static final float FONT_SCALE = 0.72F;

    private static final int LIST_LEFT = 8;
    private static final int LIST_TOP = 8;
    private static final int ELEMENT_WIDTH = 85;
    private static final int ELEMENT_HEIGHT = 32;
    private static final int ELEMENT_STEP = 33;
    private static final int MAX_PAGE_ELEMENTS = 6;

    private static final int SCROLLBAR_LEFT = 94;
    private static final int SCROLLBAR_TOP = 8;
    private static final int SCROLLBAR_HEIGHT = 197;

    /**
     * The details panel of {@code guifactory.png} spans x 112..246, y 8..122, so the text and the Flux Networks
     * button stay inside those bounds instead of overlapping its frame.
     */
    private static final int DETAIL_LEFT = 116;
    private static final int DETAIL_TOP = 12;
    private static final int DETAIL_LINE = 11;
    private static final int DETAIL_WIDTH = 127;

    /** Vanilla-style Flux Networks selector, a single long button at the bottom of the details panel. */
    private static final int FLUX_TOP = 104;
    private static final int FLUX_HEIGHT = 16;

    /** The Flux Networks sprite block is exactly as wide as the panel's inner width, so it is drawn unstretched. */
    private static final int FLUX_BUTTON_WIDTH = 127;

    private static final int COLOR_NAME = 0xFFFFFF;
    private static final int COLOR_NAME_OFF = 0x7F7F7F;
    private static final int COLOR_META = 0x9E9E9E;
    private static final int COLOR_ENERGY = 0xFFC800;
    private static final int COLOR_TITLE = 0xFFFFFF;
    private static final int COLOR_ENABLED = 0x55FF55;
    private static final int COLOR_DISABLED = 0x808080;
    private static final int COLOR_HOVER = 0x40FFFFFF;

    /** Text colours the channel rows and the details panel always use, whatever state a channel is in. */
    private static final String COLOR_CHANNEL_NAME = "\u00a75";

    /** Marks the channel the machine currently draws energy from. */
    private static final String COLOR_ACTIVE = "\u00a7a";
    private static final String COLOR_CHANNEL_META = "\u00a79";
    private static final String COLOR_CHANNEL_VALUE = "\u00a76";

    private final TileAccessPlug plug;
    private final GuiScrollbar scrollbar = new GuiScrollbar();

    public GuiAccessPlug(TileAccessPlug plug, EntityPlayer player) {
        super(new ContainerAccessPlug(plug, player));
        this.plug = plug;
        this.xSize = 280;
        this.ySize = 213;
    }

    @Override
    protected void setWidthHeight() {
        // sizes are fixed by the background texture
    }

    // ------------------------------------------------------------------ drawing

    @Override
    protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        this.mc.getTextureManager().bindTexture(TEXTURES_FACTORY);
        final int x = (this.width - this.xSize) / 2;
        final int y = (this.height - this.ySize) / 2;
        Gui.drawModalRectWithCustomSizedTexture(x, y, 0, 0, this.xSize, this.ySize, this.xSize, this.ySize);

        scrollbar.setLeft(SCROLLBAR_LEFT + x).setTop(SCROLLBAR_TOP + y).setHeight(SCROLLBAR_HEIGHT);
        List<ChannelInfo> channels = plug.getSyncedChannels();
        scrollbar.setRange(0, Math.max(0, channels.size() - MAX_PAGE_ELEMENTS), 1);
        scrollbar.draw(this, mc);
    }

    @Override
    protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
        drawChannelArea(mouseX, mouseY);
        drawFluxRow(mouseX, mouseY);
    }

    /** The channel list and the details of the row the mouse is over. */
    private void drawChannelArea(int mouseX, int mouseY) {
        List<ChannelInfo> channels = plug.getSyncedChannels();

        if (!plug.isSyncedFormed()) {
            drawScaledText(I18n.format("gui.modulargrid.access_plug.not_formed"), LIST_LEFT + 2, LIST_TOP + 4,
                    COLOR_NAME_OFF, ELEMENT_WIDTH - 6, 3);
            drawDetails(null, mouseX, mouseY);
            return;
        }
        if (channels.isEmpty()) {
            drawScaledText(I18n.format("gui.modulargrid.access_plug.no_channels"), LIST_LEFT + 2, LIST_TOP + 4,
                    COLOR_NAME_OFF, ELEMENT_WIDTH - 6, 3);
            drawDetails(null, mouseX, mouseY);
            return;
        }

        int scroll = scrollbar.getCurrentScroll();
        ChannelInfo hovered = null;
        for (int row = 0; row < MAX_PAGE_ELEMENTS && row + scroll < channels.size(); row++) {
            ChannelInfo info = channels.get(row + scroll);
            if (drawChannelRow(info, row, mouseX, mouseY)) {
                hovered = info;
            }
        }
        drawDetails(hovered, mouseX, mouseY);
    }

    /**
     * @return {@code true} when the mouse is over this row
     */
    private boolean drawChannelRow(ChannelInfo info, int row, int mouseX, int mouseY) {
        int top = LIST_TOP + row * ELEMENT_STEP;
        boolean hovered = isPointInRegion(LIST_LEFT, top, ELEMENT_WIDTH, ELEMENT_HEIGHT, mouseX, mouseY);
        // the state belongs to this row alone: a click on one row must never restyle the others
        int state = hovered ? BUTTON_STATE_HIGHLIGHTED
                : info.isEnabled() ? BUTTON_STATE_NORMAL : BUTTON_STATE_DISABLED;
        drawButton(TEXTURES_CHANNEL_BUTTON, LIST_LEFT, top, ELEMENT_WIDTH, ELEMENT_HEIGHT, state);

        // vanilla pushes a pressed button's label down by one pixel
        int labelShift = hovered && Mouse.isButtonDown(0) ? 1 : 0;
        // the channel the machine actually runs on is marked: a list of enabled channels does not answer that
        drawScaledText((info.isActiveEnergy() ? COLOR_ACTIVE + "\u25b6 " : "") + COLOR_CHANNEL_NAME
                        + channelDisplayName(info), LIST_LEFT + 5, top + 3 + labelShift,
                COLOR_NAME, ELEMENT_WIDTH - 10, 1);
        drawScaledText(COLOR_CHANNEL_META + channelRowMeta(info),
                LIST_LEFT + 5, top + 13 + labelShift, COLOR_META, ELEMENT_WIDTH - 10, 1);
        drawScaledText(COLOR_CHANNEL_VALUE + valueText(info), LIST_LEFT + 5, top + 22 + labelShift,
                COLOR_ENERGY, ELEMENT_WIDTH - 10, 1);
        return hovered;
    }

    /**
     * @return the name a channel is shown under. A firm channel is named by the pack: the conventional language key
     *         wins, then the text the pack configured, and the id is the last resort.
     */
    private static String channelDisplayName(ChannelInfo info) {
        if (!info.isFirm()) {
            return info.getName();
        }
        String key = FirmChannelDefinition.LOCALIZATION_PREFIX + info.getName();
        if (I18n.hasKey(key)) {
            return I18n.format(key);
        }
        return info.getFirmLocalization().isEmpty() ? info.getName() : info.getFirmLocalization();
    }

    /** @return the second row of a channel button: its type and who it belongs to */
    private static String channelRowMeta(ChannelInfo info) {
        String type = I18n.format(info.isEnergy()
                ? "command.modulargrid.type.energy" : "command.modulargrid.type.normal");
        if (info.isFirm()) {
            return I18n.format("gui.modulargrid.access_plug.row_meta_firm", type);
        }
        return I18n.format("gui.modulargrid.access_plug.row_meta", type, info.getOwnerName());
    }

    private void drawDetails(ChannelInfo info, int mouseX, int mouseY) {
        int y = DETAIL_TOP;
        drawScaledText(I18n.format("gui.modulargrid.access_plug.details"), DETAIL_LEFT, y, COLOR_TITLE, DETAIL_WIDTH, 1);
        y += DETAIL_LINE + 3;

        if (info == null) {
            drawScaledText(I18n.format("gui.modulargrid.access_plug.hint"), DETAIL_LEFT, y, COLOR_META,
                    DETAIL_WIDTH, 3);
            return;
        }

        drawScaledText(COLOR_CHANNEL_NAME + channelDisplayName(info), DETAIL_LEFT, y, COLOR_NAME,
                DETAIL_WIDTH, 2);
        y += DRAW_LINE_HEIGHT * 2 + 8;

        y = drawDetailLine("gui.modulargrid.access_plug.type", y,
                I18n.format(info.isEnergy() ? "command.modulargrid.type.energy" : "command.modulargrid.type.normal"));
        y = drawDetailLine("gui.modulargrid.access_plug.visibility", y,
                I18n.format(info.isPublic() ? "command.modulargrid.visibility.public" : "command.modulargrid.visibility.private"));
        y = drawDetailLine("gui.modulargrid.access_plug.owner", y, info.isFirm()
                ? I18n.format("gui.modulargrid.access_plug.owner_firm") : info.getOwnerName());

        drawScaledText(COLOR_CHANNEL_VALUE + exactValueText(info), DETAIL_LEFT, y + 2, COLOR_ENERGY,
                DETAIL_WIDTH, 1);
        y += DETAIL_LINE + 4;
        // an energy channel that the machine actually works with says so in its state: a machine has exactly one, and
        // which one it is cannot be seen from the enable state alone
        drawScaledText(I18n.format(accessPlugStateKey(info)), DETAIL_LEFT, y,
                info.isEnabled() ? COLOR_ENABLED : COLOR_DISABLED, DETAIL_WIDTH, 1);
    }

    /** @return the language key of the state line, which mentions the energy source for the channel in use */
    private static String accessPlugStateKey(ChannelInfo info) {
        if (!info.isEnabled()) {
            return "gui.modulargrid.access_plug.state.disabled";
        }
        return info.isEnergy() && info.isActiveEnergy()
                ? "gui.modulargrid.access_plug.state.enabled.energy_source"
                : "gui.modulargrid.access_plug.state.enabled";
    }

    private int drawDetailLine(String labelKey, int y, String value) {
        drawScaledText(COLOR_CHANNEL_META + I18n.format(labelKey, value), DETAIL_LEFT, y, COLOR_META,
                DETAIL_WIDTH, 1);
        return y + DETAIL_LINE;
    }

    /**
     * Draws the Flux Networks row, which switches the network the plug belongs to. Only shown while that mod is
     * installed; Flux Networks' own connector GUI cannot be opened for the plug, so this is where its network is set.
     */
    private void drawFluxRow(int mouseX, int mouseY) {
        if (!FluxNetworksIntegration.isAvailable()) {
            return;
        }
        int networkID = plug.getSyncedFluxNetworkID();
        String name = plug.getSyncedFluxNetworkName();
        if (name.isEmpty()) {
            name = networkID < 0
                    ? I18n.format("gui.modulargrid.access_plug.flux_none")
                    : "#" + networkID;
        }
        boolean hovered = isPointInRegion(DETAIL_LEFT, FLUX_TOP, FLUX_BUTTON_WIDTH, FLUX_HEIGHT, mouseX, mouseY);
        int state = hovered ? BUTTON_STATE_HIGHLIGHTED
                : networkID >= 0 ? BUTTON_STATE_NORMAL : BUTTON_STATE_DISABLED;
        drawButton(TEXTURES_FLUX_BUTTON, DETAIL_LEFT, FLUX_TOP, FLUX_BUTTON_WIDTH, FLUX_HEIGHT, state);

        int labelShift = hovered && Mouse.isButtonDown(0) ? 1 : 0;
        drawScaledText(I18n.format("gui.modulargrid.access_plug.flux_network", name), DETAIL_LEFT + 5,
                FLUX_TOP + 4 + labelShift, networkID < 0 ? COLOR_META : COLOR_ENERGY, FLUX_BUTTON_WIDTH - 10, 1);
    }

    /**
     * Draws one state of a button sprite.
     *
     * @param blockHeight height of one state in the sprite, which is also the button's height
     */
    private void drawButton(ResourceLocation texture, int x, int y, int width, int height, int state, int blockHeight) {
        mc.getTextureManager().bindTexture(texture);
        GlStateManager.enableBlend();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        int v = state * blockHeight;
        int edge = Math.min(8, width / 4);
        drawScaledRect(x, y, edge, height, 0, v, edge, height);
        drawScaledRect(x + edge, y, width - 2 * edge, height, edge, v, width - 2 * edge, height);
        drawScaledRect(x + width - edge, y, edge, height, edge + (width - 2 * edge), v, edge, height);
    }

    /** Draws one state of the channel button, whose sprite block is exactly as large as the button itself. */
    private void drawButton(ResourceLocation texture, int x, int y, int width, int height, int state) {
        drawButton(texture, x, y, width, height, state, height);
    }

    /** @return the row form of the value: shortened once it would push the unit out of the row */
    private static String valueText(ChannelInfo info) {
        return I18n.format(info.isEnergy()
                ? "gui.modulargrid.access_plug.energy_value" : "gui.modulargrid.access_plug.value",
                info.getValueDisplay());
    }

    /** @return the details form of the value: always the whole value */
    private static String exactValueText(ChannelInfo info) {
        return I18n.format(info.isEnergy()
                ? "gui.modulargrid.access_plug.energy_value" : "gui.modulargrid.access_plug.value",
                info.getExactValueDisplay());
    }

    /**
     * Draws a texture region into an explicitly sized rectangle, which is what lets the button sprites be stretched.
     */
    private static void drawScaledRect(float x, float y, float width, float height,
                                       float u, float v, float sourceWidth, float sourceHeight) {
        float u0 = u / SPRITE_SHEET;
        float v0 = v / SPRITE_SHEET;
        float u1 = (u + sourceWidth) / SPRITE_SHEET;
        float v1 = (v + sourceHeight) / SPRITE_SHEET;
        BufferBuilder buffer = Tessellator.getInstance().getBuffer();
        buffer.begin(7, DefaultVertexFormats.POSITION_TEX);
        buffer.pos(x, y + height, 0.0D).tex(u0, v1).endVertex();
        buffer.pos(x + width, y + height, 0.0D).tex(u1, v1).endVertex();
        buffer.pos(x + width, y, 0.0D).tex(u1, v0).endVertex();
        buffer.pos(x, y, 0.0D).tex(u0, v0).endVertex();
        Tessellator.getInstance().draw();
    }

    /** How much vertical space one wrapped text line takes at the GUI's text scale. */
    private static final int DRAW_LINE_HEIGHT = (int) (9 * FONT_SCALE) + 1;

    /**
     * Draws text at the GUI's reduced text scale, wrapped to the given width.
     *
     * @param maxLines maximum amount of lines to draw
     */
    private void drawScaledText(String text, int x, int y, int color, int width, int maxLines) {
        if (text == null || text.isEmpty()) {
            return;
        }
        FontRenderer font = this.fontRenderer;
        int wrappedWidth = (int) (width / FONT_SCALE);
        List<String> lines = font.listFormattedStringToWidth(text, wrappedWidth);

        GlStateManager.pushMatrix();
        GlStateManager.scale(FONT_SCALE, FONT_SCALE, FONT_SCALE);
        int drawX = (int) (x / FONT_SCALE);
        int drawY = (int) (y / FONT_SCALE);
        int drawn = 0;
        for (String line : lines) {
            if (drawn++ >= maxLines) {
                break;
            }
            // the drop shadow is asked for explicitly: it is what makes the text readable on the grey background
            font.drawString(line, drawX, drawY, color, true);
            drawY += (int) (DRAW_LINE_HEIGHT / FONT_SCALE);
        }
        GlStateManager.popMatrix();
    }

    // ------------------------------------------------------------------- input

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel != 0) {
            scrollbar.wheel(wheel);
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        if (FluxNetworksIntegration.isAvailable()
                && isPointInRegion(DETAIL_LEFT, FLUX_TOP, FLUX_BUTTON_WIDTH, FLUX_HEIGHT, mouseX, mouseY)) {
            // the server picks the next network this player may join, see PktFluxNetworkCycle
            NetworkHandler.sendToServer(new PktFluxNetworkCycle(plug.getPos(), mouseButton == 1 ? -1 : 1));
            return;
        }
        if (mouseButton == 0) {
            ChannelInfo info = channelAt(mouseX, mouseY);
            if (info != null) {
                NetworkHandler.sendToServer(new PktChannelToggle(plug.getPos(), info.getAddress(), !info.isEnabled()));
                return;
            }
        }
        scrollbar.click(mouseX, mouseY);
        super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    /** @return the channel whose row is under the mouse, {@code null} if none */
    private ChannelInfo channelAt(int mouseX, int mouseY) {
        List<ChannelInfo> channels = plug.getSyncedChannels();
        if (channels.isEmpty() || !plug.isSyncedFormed()) {
            return null;
        }
        for (int row = 0; row < MAX_PAGE_ELEMENTS; row++) {
            int index = row + scrollbar.getCurrentScroll();
            if (index >= channels.size()) {
                return null;
            }
            int top = LIST_TOP + row * ELEMENT_STEP;
            if (isPointInRegion(LIST_LEFT, top, ELEMENT_WIDTH, ELEMENT_HEIGHT, mouseX, mouseY)) {
                return channels.get(index);
            }
        }
        return null;
    }

}
