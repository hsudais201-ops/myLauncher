package com.voidcraft.cosmetics;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityModelLayerRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityFeatureRendererRegistrationCallback;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.entity.model.EntityModelLayer;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.Identifier;

/** Client initializer for Minecraft 1.21.2+ render-state based entity rendering. */
public final class VoidCraftCosmeticsClient implements ClientModInitializer {
    public static final String MOD_ID = "voidcraft_cosmetics";
    public static final EntityModelLayer HALO_LAYER =
        new EntityModelLayer(identifier(MOD_ID, "halo"), "main");
    public static final Identifier HALO_TEXTURE =
        identifier("minecraft", "textures/block/white_concrete.png");

    public static Identifier identifier(String namespace, String path) {
        try {
            java.lang.reflect.Method factory =
                Identifier.class.getMethod("of", String.class, String.class);
            return (Identifier) factory.invoke(null, namespace, path);
        } catch (ReflectiveOperationException ignored) {
            // Older mappings expose the two-argument constructor instead.
        }
        try {
            java.lang.reflect.Constructor<Identifier> constructor =
                Identifier.class.getDeclaredConstructor(String.class, String.class);
            constructor.setAccessible(true);
            return constructor.newInstance(namespace, path);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Unsupported Minecraft Identifier factory.", exception);
        }
    }

    @Override
    public void onInitializeClient() {
        EntityModelLayerRegistry.registerModelLayer(
            HALO_LAYER, VoidCraftCosmeticModel::getTexturedModelData
        );
        LivingEntityFeatureRendererRegistrationCallback.EVENT.register(
            (EntityType<? extends LivingEntity> entityType,
             LivingEntityRenderer<?, ?, ?> renderer,
             LivingEntityFeatureRendererRegistrationCallback.RegistrationHelper helper,
             EntityRendererFactory.Context context) -> {
                if (renderer instanceof PlayerEntityRenderer playerRenderer) {
                    helper.register(new VoidCraftCosmeticFeatureRenderer(
                        playerRenderer,
                        context.getPart(HALO_LAYER)
                    ));
                }
            }
        );
    }
}
