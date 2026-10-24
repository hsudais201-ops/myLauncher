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

    private static void transformHead(ModelPart head, MatrixStack matrices) {
        // The render-state API shipped across several 1.21 releases while the
        // ModelPart transform method name changed between 1.21.4 and 1.21.5.
        try {
            try {
                ModelPart.class.getMethod("applyTransform", MatrixStack.class).invoke(head, matrices);
                return;
            } catch (NoSuchMethodException ignored) {
                // Minecraft 1.21.2–1.21.4 exposes rotate(MatrixStack) instead.
            }
            ModelPart.class.getMethod("rotate", MatrixStack.class).invoke(head, matrices);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Unsupported Minecraft player-head transform API.", exception);
        }
    }


    /**
     * ModelPart's MatrixStack transform API changed within Minecraft 1.21.x.
     * Prefer the modern applyTransform name, and fall back to the older rotate
     * name when compiling/running against 1.21.2–1.21.4 mappings.
     */
    private static void applyHeadTransform(ModelPart head, MatrixStack matrices) {
        for (String methodName : new String[] {"applyTransform", "rotate"}) {
            try {
                java.lang.reflect.Method method =
                    ModelPart.class.getMethod(methodName, MatrixStack.class);
                method.invoke(head, matrices);
                return;
            } catch (NoSuchMethodException ignored) {
                // Try the transform name used by the other 1.21 API generation.
            } catch (ReflectiveOperationException exception) {
                throw new IllegalStateException(
                    "Could not apply the player head transform for VoidCraft cosmetics.",
                    exception
                );
            }
        }
        throw new IllegalStateException(
            "This Minecraft version exposes no supported ModelPart head transform API."
        );
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
        transformHead(getContextModel().head, matrices);
        VertexConsumer vertices = vertexConsumers.getBuffer(
            RenderLayer.getEntityTranslucent(VoidCraftCosmeticsClient.HALO_TEXTURE)
        );
        halo.render(matrices, vertices, light, OverlayTexture.DEFAULT_UV, tint());
        matrices.pop();
    }
}
