package io.github.illagercpr.thetruth.client.model;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;

/**
 * Shared helpers for the M6 creature models: each creature is a small set of
 * geometric cubes — geometry, not matter (docs/00 section 8, low-information
 * art language). M8 will replace the placeholder textures.
 */
public final class CertusModels {

    private CertusModels() {
    }

    public static final float PIXEL = 1.0F / 16.0F;

    /** Simple hover bob for flying parts. */
    public static float hoverBob(final float ageInTicks) {
        return Mth.sin(ageInTicks * 0.08F) * 0.06F;
    }

    /** A single child cube, centered at the given pixel offset. */
    public static void addCube(final PartDefinition parent, final String name, final int texOffsX, final int texOffsY,
                               final float width, final float height, final float depth,
                               final float offsetX, final float offsetY, final float offsetZ) {
        parent.addOrReplaceChild(name, CubeListBuilder.create()
                .texOffs(texOffsX, texOffsY)
                .addBox(-width / 2.0F, -height / 2.0F, -depth / 2.0F,
                    width, height, depth),
            PartPose.offset(offsetX * PIXEL, offsetY * PIXEL, offsetZ * PIXEL));
    }

    /** A model whose only job is holding one root part. */
    public abstract static class SimpleModel<T extends net.minecraft.world.entity.Mob> extends EntityModel<T> {

        protected final ModelPart root;

        protected SimpleModel(final ModelPart root) {
            this.root = root;
        }

        @Override
        public void setupAnim(final T entity, final float limbSwing, final float limbSwingAmount,
                              final float ageInTicks, final float netHeadYaw, final float headPitch) {
            // Creatures of Certus are rigid; animation is bob only (see subclasses).
        }

        @Override
        public void renderToBuffer(final com.mojang.blaze3d.vertex.PoseStack poseStack,
                                   final com.mojang.blaze3d.vertex.VertexConsumer buffer,
                                   final int packedLight, final int packedOverlay, final int color) {
            this.root.render(poseStack, buffer, packedLight, packedOverlay, color);
        }

        /** Convenience: texture path under {@code textures/entity/}. */
        public static ResourceLocation texture(final String name) {
            return ResourceLocation.fromNamespaceAndPath("thetruth", "textures/entity/" + name + ".png");
        }
    }
}
