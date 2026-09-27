package io.github.illagercpr.thetruth.client.model;

import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.world.entity.Entity;

/**
 * The M6 creature models: the Residue (jittering two cubes), the Surveyor
 * (flying eye with a lens), the Unobserved (tall rigid silhouette) and The
 * Last Measurer (large core in a ring frame). Placeholder art; M8 polishes.
 */
public final class CertusCreatureModels {

    private CertusCreatureModels() {
    }

    // ---------------------------------------------------------------- residue

    public static class ResidueModel extends CertusModels.SimpleModel<io.github.illagercpr.thetruth.entity.ResidueEntity> {

        public ResidueModel(final net.minecraft.client.model.geom.ModelPart root) {
            super(root);
        }

        public static LayerDefinition createBodyLayer() {
            final MeshDefinition mesh = new MeshDefinition();
            final PartDefinition root = mesh.getRoot();
            CertusModels.addCube(root, "body", 0, 0, 10, 8, 10, 0, 10, 0);
            CertusModels.addCube(root, "kernel", 0, 18, 4, 4, 4, 0, 15, 0);
            return LayerDefinition.create(mesh, 32, 32);
        }

        @Override
        public void setupAnim(final io.github.illagercpr.thetruth.entity.ResidueEntity entity, final float limbSwing, final float limbSwingAmount,
                              final float ageInTicks, final float netHeadYaw, final float headPitch) {
            this.root.yRot = ageInTicks * 0.02F;
            this.root.y += CertusModels.hoverBob(ageInTicks) * 0.5F;
        }
    }

    // --------------------------------------------------------------- surveyor

    public static class SurveyorModel extends CertusModels.SimpleModel<io.github.illagercpr.thetruth.entity.SurveyorEntity> {

        public SurveyorModel(final net.minecraft.client.model.geom.ModelPart root) {
            super(root);
        }

        public static LayerDefinition createBodyLayer() {
            final MeshDefinition mesh = new MeshDefinition();
            final PartDefinition root = mesh.getRoot();
            CertusModels.addCube(root, "shell", 0, 0, 9, 9, 9, 0, 16, 0);
            CertusModels.addCube(root, "lens", 16, 0, 3, 3, 1, 0, 16, -5);
            return LayerDefinition.create(mesh, 32, 32);
        }

        @Override
        public void setupAnim(final io.github.illagercpr.thetruth.entity.SurveyorEntity entity, final float limbSwing, final float limbSwingAmount,
                              final float ageInTicks, final float netHeadYaw, final float headPitch) {
            this.root.y = 16.0F * CertusModels.PIXEL + CertusModels.hoverBob(ageInTicks);
            this.root.xRot = headPitch * ((float) Math.PI / 180.0F) * 0.5F;
        }
    }

    // ------------------------------------------------------------- unobserved

    public static class UnobservedModel extends CertusModels.SimpleModel<io.github.illagercpr.thetruth.entity.UnobservedEntity> {

        public UnobservedModel(final net.minecraft.client.model.geom.ModelPart root) {
            super(root);
        }

        public static LayerDefinition createBodyLayer() {
            final MeshDefinition mesh = new MeshDefinition();
            final PartDefinition root = mesh.getRoot();
            CertusModels.addCube(root, "torso", 0, 0, 6, 14, 4, 0, 15, 0);
            CertusModels.addCube(root, "head", 0, 15, 6, 6, 6, 0, 25, 0);
            return LayerDefinition.create(mesh, 32, 32);
        }

        @Override
        public void setupAnim(final io.github.illagercpr.thetruth.entity.UnobservedEntity entity, final float limbSwing, final float limbSwingAmount,
                              final float ageInTicks, final float netHeadYaw, final float headPitch) {
            // Rigid: the unobserved does not animate. Being seen is its stillness.
        }
    }

    // ---------------------------------------------------------- last measurer

    public static class LastMeasurerModel extends CertusModels.SimpleModel<io.github.illagercpr.thetruth.entity.LastMeasurerEntity> {

        public LastMeasurerModel(final net.minecraft.client.model.geom.ModelPart root) {
            super(root);
        }

        public static LayerDefinition createBodyLayer() {
            final MeshDefinition mesh = new MeshDefinition();
            final PartDefinition root = mesh.getRoot();
            CertusModels.addCube(root, "core", 0, 0, 18, 18, 18, 0, 30, 0);
            CertusModels.addCube(root, "arm_x", 24, 0, 28, 2, 2, 0, 30, 0);
            CertusModels.addCube(root, "arm_z", 24, 5, 2, 2, 28, 0, 30, 0);
            CertusModels.addCube(root, "lens", 0, 37, 5, 5, 2, 0, 30, -10);
            return LayerDefinition.create(mesh, 64, 64);
        }

        @Override
        public void setupAnim(final io.github.illagercpr.thetruth.entity.LastMeasurerEntity entity, final float limbSwing, final float limbSwingAmount,
                              final float ageInTicks, final float netHeadYaw, final float headPitch) {
            this.root.y = 30.0F * CertusModels.PIXEL + CertusModels.hoverBob(ageInTicks);
            this.root.yRot = (float) Math.PI / 4.0F + ageInTicks * 0.01F;
        }
    }
}
