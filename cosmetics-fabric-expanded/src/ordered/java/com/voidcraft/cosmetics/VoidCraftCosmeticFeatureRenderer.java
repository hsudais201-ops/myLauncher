package com.voidcraft.cosmetics;

import com.google.gson.JsonObject;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.util.Identifier;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
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
            Method modern = com.google.gson.JsonParser.class.getMethod("parseString", String.class);
            return ((com.google.gson.JsonElement) modern.invoke(null, raw)).getAsJsonObject();
        } catch (NoSuchMethodException ignored) {
            // Older Minecraft versions ship a Gson without parseString(String).
        }
        Constructor<com.google.gson.JsonParser> constructor =
            com.google.gson.JsonParser.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        Object parser = constructor.newInstance();
        Method parse = com.google.gson.JsonParser.class.getMethod("parse", String.class);
        return ((com.google.gson.JsonElement) parse.invoke(parser, raw)).getAsJsonObject();
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

    private static RenderLayer translucentEntityLayer(Identifier texture) {
        // Minecraft 1.21.9 still exposes RenderLayer.getEntityTranslucent;
        // 1.21.10+ moved the factory into RenderLayers.entityTranslucent.
        try {
            Class<?> renderLayers = Class.forName("net.minecraft.client.render.RenderLayers");
            Method modern = renderLayers.getMethod("entityTranslucent", Identifier.class);
            return (RenderLayer) modern.invoke(null, texture);
        } catch (ClassNotFoundException | NoSuchMethodException ignored) {
            // Older late-1.21 builds have the static factory on RenderLayer.
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Could not create the VoidCraft cosmetic render layer.", exception);
        }
        try {
            Method legacy = RenderLayer.class.getMethod("getEntityTranslucent", Identifier.class);
            return (RenderLayer) legacy.invoke(null, texture);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("No supported translucent entity render layer factory was found.", exception);
        }
    }

    private static void transformHead(ModelPart head, MatrixStack matrices) {
        for (String methodName : new String[] {"applyTransform", "rotate"}) {
            try {
                Method method = ModelPart.class.getMethod(methodName, MatrixStack.class);
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
        OrderedRenderCommandQueue queue,
        int light,
        PlayerEntityRenderState state,
        float limbAngle,
        float limbDistance
    ) {
        if ("none".equals(EQUIPPED_ID)) return;
        matrices.push();
        transformHead(getContextModel().head, matrices);
        RenderLayer layer = translucentEntityLayer(VoidCraftCosmeticsClient.HALO_TEXTURE);
        queue.submitModelPart(
            halo, matrices, layer, light, OverlayTexture.DEFAULT_UV,
            null, false, false, tint(), null, 0
        );
        matrices.pop();
    }
}
