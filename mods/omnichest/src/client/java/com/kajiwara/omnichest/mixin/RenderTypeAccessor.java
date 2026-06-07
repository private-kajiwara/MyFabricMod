package com.kajiwara.omnichest.mixin;

//? if >=1.21.11 {
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
//?} else {
/*import net.minecraft.client.renderer.RenderType;
import com.mojang.blaze3d.pipeline.RenderPipeline;*/
//?}
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * パッケージプライベートな {@code RenderType.create(...)} を呼び出すための Accessor。
 * X-ray 用のカスタム RenderPipeline をラップした RenderType を構築する目的で使う。
 *
 * <p>
 * 1.21.11 / 26.1: {@code create(String, RenderSetup)} (RenderSetup は 1.21.11 で導入)。
 * 1.21.10 以下:  {@code create(String, int, RenderPipeline, RenderType.CompositeState)} (旧シグネチャ)。
 * CompositeState の構築 (protected) は {@link CompositeStateBuilderAccessor} に委譲する。
 */
@Mixin(RenderType.class)
public interface RenderTypeAccessor {
    //? if >=1.21.11 {
    @Invoker("create")
    static RenderType omnichest$create(String name, RenderSetup setup) {
        throw new AssertionError();
    }
    //?} else {
    /*@Invoker("create")
    static RenderType.CompositeRenderType omnichest$create(String name, int bufferSize, RenderPipeline pipeline,
            RenderType.CompositeState state) {
        throw new AssertionError();
    }*/
    //?}
}
