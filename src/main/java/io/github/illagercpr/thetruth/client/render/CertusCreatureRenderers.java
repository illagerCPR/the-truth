package io.github.illagercpr.thetruth.client.render;

import io.github.illagercpr.thetruth.client.model.CertusCreatureModels;
import io.github.illagercpr.thetruth.client.model.CertusModels;
import io.github.illagercpr.thetruth.entity.LastMeasurerEntity;
import io.github.illagercpr.thetruth.entity.ResidueEntity;
import io.github.illagercpr.thetruth.entity.SurveyorEntity;
import io.github.illagercpr.thetruth.entity.UnobservedEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/**
 * Renderers for the M6 creatures. All four reuse {@link MobRenderer} with the
 * geometric placeholder models; entity textures live under
 * {@code textures/entity/}.
 */
public final class CertusCreatureRenderers {

    private CertusCreatureRenderers() {
    }

    public static void onRegisterRenderers(final EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(
            io.github.illagercpr.thetruth.registry.TheTruthEntities.RESIDUE.get(), ResidueRenderer::new);
        event.registerEntityRenderer(
            io.github.illagercpr.thetruth.registry.TheTruthEntities.SURVEYOR.get(), SurveyorRenderer::new);
        event.registerEntityRenderer(
            io.github.illagercpr.thetruth.registry.TheTruthEntities.UNOBSERVED.get(), UnobservedRenderer::new);
        event.registerEntityRenderer(
            io.github.illagercpr.thetruth.registry.TheTruthEntities.LAST_MEASURER.get(), MeasurerRenderer::new);
    }

    public static void onRegisterLayerDefinitions(final EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(ResidueRenderer.LAYER, CertusCreatureModels.ResidueModel::createBodyLayer);
        event.registerLayerDefinition(SurveyorRenderer.LAYER, CertusCreatureModels.SurveyorModel::createBodyLayer);
        event.registerLayerDefinition(UnobservedRenderer.LAYER, CertusCreatureModels.UnobservedModel::createBodyLayer);
        event.registerLayerDefinition(MeasurerRenderer.LAYER,
            CertusCreatureModels.LastMeasurerModel::createBodyLayer);
    }

    public static class ResidueRenderer extends MobRenderer<ResidueEntity, CertusCreatureModels.ResidueModel> {

        public static final net.minecraft.client.model.geom.ModelLayerLocation LAYER =
            new net.minecraft.client.model.geom.ModelLayerLocation(
                ResourceLocation.fromNamespaceAndPath(io.github.illagercpr.thetruth.TheTruth.MOD_ID, "residue"),
                "main");

        public ResidueRenderer(final EntityRendererProvider.Context context) {
            super(context, new CertusCreatureModels.ResidueModel(context.bakeLayer(LAYER)), 0.6F);
        }

        @Override
        public ResourceLocation getTextureLocation(final ResidueEntity entity) {
            return CertusModels.SimpleModel.texture("residue");
        }
    }

    public static class SurveyorRenderer extends MobRenderer<SurveyorEntity, CertusCreatureModels.SurveyorModel> {

        public static final net.minecraft.client.model.geom.ModelLayerLocation LAYER =
            new net.minecraft.client.model.geom.ModelLayerLocation(
                ResourceLocation.fromNamespaceAndPath(io.github.illagercpr.thetruth.TheTruth.MOD_ID, "surveyor"),
                "main");

        public SurveyorRenderer(final EntityRendererProvider.Context context) {
            super(context, new CertusCreatureModels.SurveyorModel(context.bakeLayer(LAYER)), 0.5F);
        }

        @Override
        public ResourceLocation getTextureLocation(final SurveyorEntity entity) {
            return CertusModels.SimpleModel.texture("surveyor");
        }
    }

    public static class UnobservedRenderer
        extends MobRenderer<UnobservedEntity, CertusCreatureModels.UnobservedModel> {

        public static final net.minecraft.client.model.geom.ModelLayerLocation LAYER =
            new net.minecraft.client.model.geom.ModelLayerLocation(
                ResourceLocation.fromNamespaceAndPath(io.github.illagercpr.thetruth.TheTruth.MOD_ID, "unobserved"),
                "main");

        public UnobservedRenderer(final EntityRendererProvider.Context context) {
            super(context, new CertusCreatureModels.UnobservedModel(context.bakeLayer(LAYER)), 0.5F);
        }

        @Override
        public ResourceLocation getTextureLocation(final UnobservedEntity entity) {
            return CertusModels.SimpleModel.texture("unobserved");
        }
    }

    public static class MeasurerRenderer
        extends MobRenderer<LastMeasurerEntity, CertusCreatureModels.LastMeasurerModel> {

        public static final net.minecraft.client.model.geom.ModelLayerLocation LAYER =
            new net.minecraft.client.model.geom.ModelLayerLocation(
                ResourceLocation.fromNamespaceAndPath(io.github.illagercpr.thetruth.TheTruth.MOD_ID, "last_measurer"),
                "main");

        public MeasurerRenderer(final EntityRendererProvider.Context context) {
            super(context, new CertusCreatureModels.LastMeasurerModel(context.bakeLayer(LAYER)), 1.2F);
        }

        @Override
        public ResourceLocation getTextureLocation(final LastMeasurerEntity entity) {
            return CertusModels.SimpleModel.texture("last_measurer");
        }
    }
}
