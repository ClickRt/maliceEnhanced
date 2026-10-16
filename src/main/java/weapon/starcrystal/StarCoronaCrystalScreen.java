package weapon.starcrystal;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

public class StarCoronaCrystalScreen extends AbstractContainerScreen<StarCoronaCrystalMenu> {
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath("malice_enhanced", "textures/gui/star_corona_crystal.png");

    private static final int TITLE_COLOR = 0xE8E0FF;
    private static final int LABEL_COLOR = 0x9B8FD0;

    public StarCoronaCrystalScreen(StarCoronaCrystalMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = StarCoronaCrystalMenu.PANEL_WIDTH;
        this.imageHeight = StarCoronaCrystalMenu.PANEL_HEIGHT;
        this.inventoryLabelY = StarCoronaCrystalMenu.INV_Y - 11;
        this.titleLabelY = 8;
    }

    @Override
    protected void init() {
        super.init();
        this.titleLabelX = (this.imageWidth - this.font.width(this.title)) / 2;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        this.renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int x = (this.leftPos);
        int y = (this.topPos);
        graphics.blit(TEXTURE, x, y, 0, 0, this.imageWidth, this.imageHeight);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(this.font, this.title, this.titleLabelX, this.titleLabelY, TITLE_COLOR, false);
        graphics.drawString(this.font, this.playerInventoryTitle, this.inventoryLabelX, this.inventoryLabelY,
                LABEL_COLOR, false);
    }
}
