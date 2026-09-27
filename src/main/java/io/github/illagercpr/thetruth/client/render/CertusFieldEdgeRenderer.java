package io.github.illagercpr.thetruth.client.render;

import io.github.illagercpr.thetruth.blockentity.CertusAnchorBlockEntity;
import io.github.illagercpr.thetruth.blockentity.UmbilicalAnchorBlockEntity;
import io.github.illagercpr.thetruth.coverage.UmbilicalNetwork;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * "Written sector" edge renderer (M8, docs/00 §8): while an anchor projects
 * its deterministic coverage, the edge of the field is annotated with three
 * tidy latitude rings — the ledger's margin line around the writable area.
 * Low-information language: three cyan line loops, nothing else.
 */
@OnlyIn(Dist.CLIENT)
public final class CertusFieldEdgeRenderer {

    /** Latitude heights relative to the anchor block (the 16-block sphere). */
    private static final float[] RING_HEIGHTS = {0.0F, 8.0F, -8.0F};
    /** Segments per ring; 48 keeps a 16-block circle smooth enough. */
    private static final int SEGMENTS = 48;
    /** Data-cyan, half transparent (docs/00 §8 colour language). */
    private static final int COLOR = 0x7F7FDFCF;

    private CertusFieldEdgeRenderer() {
    }

    /** Renderer for the Certus anchor. */
    @OnlyIn(Dist.CLIENT)
    public static class CertusAnchor implements BlockEntityRenderer<CertusAnchorBlockEntity> {

        public CertusAnchor(final BlockEntityRendererProvider.Context context) {
        }

        @Override
        public net.minecraft.world.phys.AABB getRenderBoundingBox(final CertusAnchorBlockEntity anchor) {
            return new net.minecraft.world.phys.AABB(anchor.getBlockPos())
                .inflate(anchor.fieldRange() + 2.0);
        }

        @Override
        public void render(final CertusAnchorBlockEntity anchor, final float partialTick,
                           final com.mojang.blaze3d.vertex.PoseStack poseStack,
                           final MultiBufferSource buffers, final int light, final int overlay) {
            if (!anchor.isFieldActiveForRender()) {
                return;
            }
            renderRings(poseStack, buffers, anchor.getBlockPos(), anchor.fieldRange());
        }
    }

    /** Renderer for the umbilical anchor (its projection covers Certus). */
    @OnlyIn(Dist.CLIENT)
    public static class UmbilicalAnchor implements BlockEntityRenderer<UmbilicalAnchorBlockEntity> {

        public UmbilicalAnchor(final BlockEntityRendererProvider.Context context) {
        }

        @Override
        public net.minecraft.world.phys.AABB getRenderBoundingBox(final UmbilicalAnchorBlockEntity anchor) {
            return new net.minecraft.world.phys.AABB(anchor.getBlockPos())
                .inflate(UmbilicalNetwork.FIELD_RANGE + 2.0);
        }

        @Override
        public void render(final UmbilicalAnchorBlockEntity anchor, final float partialTick,
                           final com.mojang.blaze3d.vertex.PoseStack poseStack,
                           final MultiBufferSource buffers, final int light, final int overlay) {
            if (!anchor.isFieldActiveForRender()) {
                return;
            }
            renderRings(poseStack, buffers, anchor.getBlockPos(), UmbilicalNetwork.FIELD_RANGE);
        }
    }

    private static void renderRings(final com.mojang.blaze3d.vertex.PoseStack poseStack,
                                    final MultiBufferSource buffers, final BlockPos center,
                                    final double fieldRange) {
        final com.mojang.blaze3d.vertex.VertexConsumer consumer = buffers.getBuffer(RenderType.lines());
        final com.mojang.blaze3d.vertex.PoseStack.Pose pose = poseStack.last();

        for (final float ringHeight : RING_HEIGHTS) {
            // Ring height relative to the anchor's block centre; the ring is
            // the circle where a sphere of fieldRange cuts that plane.
            final double ringRadius = Math.sqrt(Math.max(0.0, fieldRange * fieldRange - ringHeight * ringHeight));
            final float y = 0.5F + ringHeight;
            double prevX = ringRadius;
            double prevZ = 0.0;
            for (int i = 1; i <= SEGMENTS; i++) {
                final double angle = Math.PI * 2.0 * i / SEGMENTS;
                final double x = Mth.cos((float) angle) * ringRadius;
                final double z = Mth.sin((float) angle) * ringRadius;
                addLine(consumer, pose, (float) prevX, y, (float) prevZ, (float) x, y, (float) z);
                prevX = x;
                prevZ = z;
            }
        }
    }

    private static void addLine(final com.mojang.blaze3d.vertex.VertexConsumer consumer,
                                final com.mojang.blaze3d.vertex.PoseStack.Pose pose,
                                final float x1, final float y1, final float z1,
                                final float x2, final float y2, final float z2) {
        // Normals point up along the ring plane; lines only use them for shading.
        consumer.addVertex(pose, x1, y1, z1)
            .setColor(COLOR)
            .setNormal(pose, 0.0F, 1.0F, 0.0F);
        consumer.addVertex(pose, x2, y2, z2)
            .setColor(COLOR)
            .setNormal(pose, 0.0F, 1.0F, 0.0F);
    }
}
