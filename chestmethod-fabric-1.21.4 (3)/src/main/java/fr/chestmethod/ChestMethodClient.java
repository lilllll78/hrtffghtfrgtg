package fr.chestmethod;

import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.block.entity.BarrelBlockEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.block.entity.EnderChestBlockEntity;
import net.minecraft.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.ShaderProgramKeys;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkStatus;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

public class ChestMethodClient implements ClientModInitializer {

    private record Target(BlockPos pos, float r, float g, float b) {}

    private static KeyBinding toggleKey;
    private static boolean enabled = true;
    private static volatile List<Target> targets = new ArrayList<>();
    private static int tickCounter = 0;

    @Override
    public void onInitializeClient() {
        toggleKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.chestmethod.toggle", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_J, "category.chestmethod"));
        ClientTickEvents.END_CLIENT_TICK.register(ChestMethodClient::onTick);
        WorldRenderEvents.LAST.register(ChestMethodClient::onRender);
    }

    private static void onTick(MinecraftClient mc) {
        while (toggleKey.wasPressed()) {
            enabled = !enabled;
            if (mc.player != null) {
                mc.player.sendMessage(Text.literal("Chest Method : " + (enabled ? "ON" : "OFF")), true);
            }
        }
        if (!enabled || mc.world == null || mc.player == null) {
            targets = new ArrayList<>();
            return;
        }
        if (++tickCounter % 10 != 0) return; // scan toutes les 0,5 s

        List<Target> found = new ArrayList<>();
        int radius = mc.options.getClampedViewDistance();
        ChunkPos pc = mc.player.getChunkPos();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                Chunk chunk = mc.world.getChunk(pc.x + dx, pc.z + dz, ChunkStatus.FULL, false);
                if (chunk == null) continue;
                for (BlockPos p : new ArrayList<>(chunk.getBlockEntityPositions())) {
                    BlockEntity be = chunk.getBlockEntity(p);
                    if (be instanceof EnderChestBlockEntity) found.add(new Target(p.toImmutable(), 0.7f, 0.2f, 1f));
                    else if (be instanceof ChestBlockEntity) found.add(new Target(p.toImmutable(), 1f, 0.7f, 0.1f));
                    else if (be instanceof BarrelBlockEntity) found.add(new Target(p.toImmutable(), 0.6f, 0.4f, 0.2f));
                    else if (be instanceof ShulkerBoxBlockEntity) found.add(new Target(p.toImmutable(), 1f, 0.4f, 0.8f));
                }
            }
        }
        targets = found;
    }

    private static void onRender(WorldRenderContext ctx) {
        List<Target> list = targets;
        if (!enabled || list.isEmpty() || ctx.matrixStack() == null) return;

        Camera cam = ctx.camera();
        Vec3d cp = cam.getPos();
        Matrix4f m = ctx.matrixStack().peek().getPositionMatrix();
        // depart des lignes : un peu devant la camera (centre de l'ecran)
        Vec3d start = Vec3d.fromPolar(cam.getPitch(), cam.getYaw()).multiply(0.6);

        RenderSystem.disableDepthTest();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        RenderSystem.lineWidth(2.0f);

        BufferBuilder b = RenderSystem.renderThreadTesselator()
                .begin(VertexFormat.DrawMode.DEBUG_LINES, VertexFormats.POSITION_COLOR);

        for (Target t : list) {
            float x = (float) (t.pos().getX() - cp.x);
            float y = (float) (t.pos().getY() - cp.y);
            float z = (float) (t.pos().getZ() - cp.z);

            // ligne joueur -> centre du coffre
            b.vertex(m, (float) start.x, (float) start.y, (float) start.z).color(t.r(), t.g(), t.b(), 0.9f);
            b.vertex(m, x + 0.5f, y + 0.5f, z + 0.5f).color(t.r(), t.g(), t.b(), 0.9f);

            box(b, m, x, y, z, t.r(), t.g(), t.b());
        }

        BufferRenderer.drawWithGlobalProgram(b.end());

        RenderSystem.lineWidth(1.0f);
        RenderSystem.disableBlend();
        RenderSystem.enableDepthTest();
    }

    private static void line(BufferBuilder b, Matrix4f m, float x1, float y1, float z1,
                             float x2, float y2, float z2, float r, float g, float bl) {
        b.vertex(m, x1, y1, z1).color(r, g, bl, 1f);
        b.vertex(m, x2, y2, z2).color(r, g, bl, 1f);
    }

    private static void box(BufferBuilder b, Matrix4f m, float x, float y, float z, float r, float g, float bl) {
        float x2 = x + 1, y2 = y + 1, z2 = z + 1;
        line(b, m, x, y, z, x2, y, z, r, g, bl);     line(b, m, x2, y, z, x2, y, z2, r, g, bl);
        line(b, m, x2, y, z2, x, y, z2, r, g, bl);   line(b, m, x, y, z2, x, y, z, r, g, bl);
        line(b, m, x, y2, z, x2, y2, z, r, g, bl);   line(b, m, x2, y2, z, x2, y2, z2, r, g, bl);
        line(b, m, x2, y2, z2, x, y2, z2, r, g, bl); line(b, m, x, y2, z2, x, y2, z, r, g, bl);
        line(b, m, x, y, z, x, y2, z, r, g, bl);     line(b, m, x2, y, z, x2, y2, z, r, g, bl);
        line(b, m, x2, y, z2, x2, y2, z2, r, g, bl); line(b, m, x, y, z2, x, y2, z2, r, g, bl);
    }
}
