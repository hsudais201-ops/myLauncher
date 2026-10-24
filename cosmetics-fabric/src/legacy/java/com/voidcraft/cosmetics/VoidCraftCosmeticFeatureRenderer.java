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
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.network.AbstractClientPlayerEntity;

public final class VoidCraftCosmeticFeatureRenderer extends FeatureRenderer<AbstractClientPlayerEntity, PlayerEntityModel<AbstractClientPlayerEntity>> {
    private final ModelPart halo;
    private static final String EQUIPPED_ID = readEquippedId();

    public VoidCraftCosmeticFeatureRenderer(
        FeatureRendererContext<AbstractClientPlayerEntity, PlayerEntityModel<AbstractClientPlayerEntity>> context,
        ModelPart halo
    ) {
        super(context);
        this.halo = halo;
    }

    private static JsonObject parseConfigJson(String raw) throws ReflectiveOperationException {
        try {
            java.lang.reflect.Method modern = JsonParser.class.getMethod("parseString", String.class);
            return ((com.google.gson.JsonElement) modern.invoke(null, raw)).getAsJsonObject();
        } catch (NoSuchMethodException ignored) {
            // Older Minecraft versions ship a Gson without parseString(String).
        }
        Object parser = JsonParser.class.getDeclaredConstructor().newInstance();
        java.lang.reflect.Method legacy = JsonParser.class.getMethod("parse", String.class);
        return ((com.google.gson.JsonElement) legacy.invoke(parser, raw)).getAsJsonObject();
    }

    private static String readEquippedId() {
        Path config = FabricLoader.getInstance().getGameDir()
            .resolve("config")
            .resolve("voidcraft-cosmetics.json");
        try {
            if (!Files.isRegularFile(config)) return "none";
            JsonObject json = parseConfigJson(Files.readString(config));
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

    private static void renderHalo(
        ModelPart part,
        MatrixStack matrices,
        VertexConsumer vertices,
        int light,
        int color
    ) {
        // 1.21 uses the packed-color overload; older game releases use RGBA floats.
        try {
            java.lang.reflect.Method modern = ModelPart.class.getMethod(
                "render", MatrixStack.class, VertexConsumer.class,
                int.class, int.class, int.class
            );
            modern.invoke(part, matrices, vertices, light, OverlayTexture.DEFAULT_UV, color);
            return;
        } catch (NoSuchMethodException ignored) {
            // Try the older RGBA overload below.
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Could not render VoidCraft cosmetic on this version.", exception);
        }

        float red = ((color >> 16) & 0xFF) / 255.0F;
        float green = ((color >> 8) & 0xFF) / 255.0F;
        float blue = (color & 0xFF) / 255.0F;
        float alpha = ((color >>> 24) & 0xFF) / 255.0F;
        try {
            java.lang.reflect.Method legacy = ModelPart.class.getMethod(
                "render", MatrixStack.class, VertexConsumer.class,
                int.class, int.class, float.class, float.class, float.class, float.class
            );
            legacy.invoke(part, matrices, vertices, light, OverlayTexture.DEFAULT_UV,
                red, green, blue, alpha);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("This Minecraft version has no supported ModelPart renderer.", exception);
        }
    }

    @Override
    public void render(
        MatrixStack matrices,
        net.minecraft.client.render.VertexConsumerProvider vertexConsumers,
        int light,
        AbstractClientPlayerEntity entity,
        float limbAngle,
        float limbDistance,
        float tickDelta,
        float animationProgress,
        float headYaw,
        float headPitch
    ) {
        if ("none".equals(EQUIPPED_ID)) return;
        matrices.push();
        getContextModel().head.rotate(matrices);
        VertexConsumer vertices = vertexConsumers.getBuffer(
            RenderLayer.getEntityTranslucent(VoidCraftCosmeticsClient.HALO_TEXTURE)
        );
        renderHalo(halo, matrices, vertices, light, tint());
        matrices.pop();
    }
}
