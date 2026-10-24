package com.voidcraft.cosmetics;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;

public final class VoidCraftCosmeticFeatureRenderer
    extends FeatureRenderer<PlayerEntityRenderState, PlayerEntityModel> {
    private final ModelPart halo;
    private static final String EQUIPPED_ID = readEquippedId();

    public VoidCraftCosmeticFeatureRenderer(
        FeatureRendererContext<PlayerEntityRenderState, PlayerEntityModel> context,
        ModelPart halo
    ) {
        super(context);
        this.halo = halo;
    }

    private static String readEquippedId() {
        Path config = FabricLoader.getInstance().getGameDir()
            .resolve("config")
            .resolve("voidcraft-cosmetics.json");
        try {
            if (!Files.isRegularFile(config)) return "none";
            JsonObject json = JsonParser.parseString(Files.readString(config)).getAsJsonObject();
            String id = json.has("equipped") ? json.get("equipped").getAsString() : "none";
            return switch (id) {
                case "void-aura", "neon-mint", "arctic-ice", "solar-ember" -> id;
                default -> "none";
            };
        } catch (Exception ignored) {
            return "none";
        }
    }

    private static int tint() {
        return switch (EQUIPPED_ID) {
            case "void-aura" -> 0xFF9C7BFF;
            case "neon-mint" -> 0xFF63E8C1;
            case "arctic-ice" -> 0xFF63E8ED;
            case "solar-ember" -> 0xFFFF966B;
            default -> 0x00000000;
        };
    }

    @Override
    public void render(
        MatrixStack matrices,
        VertexConsumerProvider vertexConsumers,
        int light,
        PlayerEntityRenderState state,
        float limbAngle,
        float limbDistance
    ) {
        if ("none".equals(EQUIPPED_ID)) return;
        matrices.push();
        getContextModel().head.rotate(matrices);
        VertexConsumer vertices = vertexConsumers.getBuffer(
            RenderLayer.getEntityTranslucent(VoidCraftCosmeticsClient.HALO_TEXTURE)
        );
        halo.render(matrices, vertices, light, OverlayTexture.DEFAULT_UV, tint());
        matrices.pop();
    }
}
