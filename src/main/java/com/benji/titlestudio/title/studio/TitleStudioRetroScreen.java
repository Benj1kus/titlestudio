package com.benji.titlestudio.title.studio;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public abstract class TitleStudioRetroScreen extends Screen {

    protected TitleStudioRetroScreen(Component title) {
        super(title);
    }

    @Override
    public void renderBackground(GuiGraphics graphics) {
        TitleStudioRetroTheme.drawDesktop(graphics, this.width, this.height);

        if (this.width > 18 && this.height > 18) {
            TitleStudioRetroTheme.drawPanel(graphics, 3, 3, this.width - 3, this.height - 3);
            TitleStudioRetroTheme.drawTitleBar(graphics, 6, 6, this.width - 6, 18);
        }
    }
}
