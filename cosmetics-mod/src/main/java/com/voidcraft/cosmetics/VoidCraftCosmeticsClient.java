package com.voidcraft.cosmetics;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.math.MathHelper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Local-only cosmetic aura rendered by Minecraft's vanilla particle renderer. */
public final class VoidCraftCosmeticsClient implements ClientModInitializer {
    private static final Path CONFIG = FabricLoader.getInstance()
            .getConfigDir().resolve("voidcraft-cosmetics.json");
    private static final int CONFIG_RELOAD_TICKS = 40;
    private static String equippedId = "";
    private static int tickCounter;

    @Override
    public void onInitializeClient() {
        reloadConfig();
        ClientTickEvents.END_CLIENT_TICK.register(this::onClientTick);
    }

    private void onClientTick(MinecraftClient client) {
        if (++tickCounter % CONFIG_RELOAD_TICKS == 0) reloadConfig();
        if (equippedId.isEmpty() || client.player == null || client.world == null) return;
        if (client.player.age % 3 != 0) return;

        ParticleEffect particle = particleFor(equippedId);
        if (particle == null) return;

        double baseX = client.player.getX();
        double baseY = client.player.getY();
        double baseZ = client.player.getZ();
        double phase = client.player.age * 0.13;
        for (int i = 0; i < 4; i++) {
            double angle = phase + (Math.PI * 2.0 * i / 4.0);
            double radius = 0.52;
            double x = baseX + MathHelper.cos((float) angle) * radius;
            double z = baseZ + MathHelper.sin((float) angle) * radius;
            double y = baseY + 0.35 + (i % 2) * 0.72;
            client.world.addParticle(particle, x, y, z,
                    -MathHelper.sin((float) angle) * 0.008, 0.015,
                    MathHelper.cos((float) angle) * 0.008);
        }
    }

    private static ParticleEffect particleFor(String id) {
        return switch (id) {
            case "void-aura" -> ParticleTypes.END_ROD;
            case "neon-mint" -> ParticleTypes.HAPPY_VILLAGER;
            case "arctic-ice" -> ParticleTypes.SNOWFLAKE;
            case "solar-ember" -> ParticleTypes.FLAME;
            default -> null;
        };
    }

    private static void reloadConfig() {
        try {
            if (!Files.isRegularFile(CONFIG)) {
                equippedId = "";
                return;
            }
            JsonObject object = JsonParser.parseString(
                    Files.readString(CONFIG, StandardCharsets.UTF_8)).getAsJsonObject();
            String next = object.has("equippedId") && !object.get("equippedId").isJsonNull()
                    ? object.get("equippedId").getAsString() : "";
            equippedId = switch (next) {
                case "void-aura", "neon-mint", "arctic-ice", "solar-ember" -> next;
                default -> "";
            };
        } catch (IOException | RuntimeException ignored) {
            equippedId = "";
        }
    }
}
