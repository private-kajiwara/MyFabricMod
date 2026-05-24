package com.kajiwara.omnichest.mixin;

import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * パッケージプライベートな {@link RenderType#create(String, RenderSetup)} を呼び出すための Accessor。
 * X-ray 用のカスタム RenderPipeline をラップした RenderType を構築する目的で使う。
 */
@Mixin(RenderType.class)
public interface RenderTypeAccessor {
    @Invoker("create")
    static RenderType omnichest$create(String name, RenderSetup setup) {
        throw new AssertionError();
    }
}
