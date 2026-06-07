package com.kajiwara.omnichest.mixin;

// 1.21.10 以下専用 Accessor。
//   1.21.11 で RenderStateShard/CompositeState は RenderSetup に置き換わったため、 このクラスは
//   1.21.10 以下でのみ生成・登録される (>=1.21.11 では本ファイルはコメントのみ = クラス無し。
//   mixins.json への登録も stonecutter の置換で <1.21.11 のみ付与する)。
//   RenderType.CompositeState.CompositeStateBuilder の setLineState / createCompositeState は
//   protected のため、 カスタム RenderType 構築用に Invoker で開放する。 線幅 (LineStateShard) は
//   旧版で per-vertex ではなく CompositeState 側に持たせる必要がある。
//? if <1.21.11 {
/*import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(RenderType.CompositeState.CompositeStateBuilder.class)
public interface CompositeStateBuilderAccessor {
    @Invoker("setLineState")
    RenderType.CompositeState.CompositeStateBuilder omnichest$setLineState(RenderStateShard.LineStateShard shard);

    @Invoker("createCompositeState")
    RenderType.CompositeState omnichest$createCompositeState(boolean affectsOutline);
}
*///?}
