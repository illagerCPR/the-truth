package io.github.illagercpr.thetruth.registry;

import io.github.illagercpr.thetruth.TheTruth;
import io.github.illagercpr.thetruth.uncertainty.UncertaintyData;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * Data attachments of The Truth.
 *
 * <p>The uncertainty attachment is synced with {@code sync(...)}: NeoForge sends
 * updates to the owning player (and trackers) whenever {@code setData} stores a
 * changed value, and replays the current value on login — the client overlay
 * reads it straight from {@code LocalPlayer#getData} with no custom packet.
 */
public final class TheTruthAttachments {

    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
        DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, TheTruth.MOD_ID);

    public static final DeferredHolder<AttachmentType<?>, AttachmentType<UncertaintyData>> UNCERTAINTY =
        ATTACHMENTS.register("uncertainty", () -> AttachmentType
            .builder(() -> UncertaintyData.DEFAULT)
            // covered is runtime-only; skip writing default-value rows to disk
            .serialize(UncertaintyData.CODEC, data -> data.uncertainty() > 0
                || data.warningTicksLeft() > 0
                || data.nextRandomizeAt() > 0)
            .sync(UncertaintyData.STREAM_CODEC)
            .build());

    private TheTruthAttachments() {
    }
}
