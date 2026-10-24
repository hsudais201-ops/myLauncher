package com.voidcraft.cosmetics;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityModelLayerRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityFeatureRendererRegistrationCallback;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.model.EntityModelLayer;
import net.minecraft.util.Identifier;

public final class VoidCraftCosmeticsClient implements ClientModInitializer {
    public static final String MOD_ID = "voidcraft_cosmetics";
    public static final EntityModelLayer HALO_LAYER =
        new EntityModelLayer(Identifier.of(MOD_ID, "halo"), "main");
    public static final Identifier HALO_TEXTURE =
        Identifier.of(MOD_ID, "textures/cosmetic/white.png");

    @Override
    public void onInitializeClient() {
        EntityModelLayerRegistry.registerModelLayer(
            HALO_LAYER, VoidCraftCosmeticModel::getTexturedModelData
        );
        LivingEntityFeatureRendererRegistrationCallback.EVENT.register(
            (EntityType<? extends LivingEntity> entityType,
             LivingEntityRenderer<?, ?> renderer,
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
