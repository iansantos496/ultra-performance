package com.ultraperformance.mixin.client;

import com.ultraperformance.client.gui.ConfigScreen;
import com.ultraperformance.core.HookStatus;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.option.OptionsScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.LiteralText;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Acrescenta o botão "Ultra Performance" no canto superior direito da tela de Opções (um canto vazio, para
 * não brigar com os botões do jogo nem de outros mods). A tecla Shift direito também abre a tela.
 */
@Mixin(OptionsScreen.class)
public abstract class OptionsScreenMixin extends Screen {
	protected OptionsScreenMixin(Text title) {
		super(title);
	}

	@Inject(method = "init()V", at = @At("RETURN"), require = 0)
	private void ultra$addButton(CallbackInfo ci) {
		if (HookStatus.isFailed(HookStatus.OPTIONS_BUTTON)) {
			return;
		}
		try {
			HookStatus.hit(HookStatus.OPTIONS_BUTTON);
			int w = 130;
			Screen parent = this;
			this.addButton(new ButtonWidget(this.width - w - 6, 6, w, 20, new LiteralText("Ultra Performance"),
					button -> this.client.openScreen(new ConfigScreen(parent))));
			HookStatus.effect(HookStatus.OPTIONS_BUTTON);
		} catch (Throwable t) {
			HookStatus.fail(HookStatus.OPTIONS_BUTTON, t);
		}
	}
}
