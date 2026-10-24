package com.voidcraft.cosmetics;

import net.minecraft.client.model.ModelData;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.model.ModelPartBuilder;
import net.minecraft.client.model.ModelPartData;
import net.minecraft.client.model.ModelTransform;
import net.minecraft.client.model.TexturedModelData;

public final class VoidCraftCosmeticModel {
    private VoidCraftCosmeticModel() {}

    public static TexturedModelData getTexturedModelData() {
        ModelData modelData = new ModelData();
        ModelPartData root = modelData.getRoot();
        root.addChild("halo", ModelPartBuilder.create()
            .uv(0, 0).cuboid(-5.0F, -9.0F, -5.0F, 10.0F, 1.0F, 1.0F)
            .uv(0, 0).cuboid(-5.0F, -9.0F, 4.0F, 10.0F, 1.0F, 1.0F)
            .uv(0, 0).cuboid(-5.0F, -9.0F, -4.0F, 1.0F, 1.0F, 8.0F)
            .uv(0, 0).cuboid(4.0F, -9.0F, -4.0F, 1.0F, 1.0F, 8.0F),
            ModelTransform.NONE);
        return TexturedModelData.of(modelData, 16, 16);
    }
}
