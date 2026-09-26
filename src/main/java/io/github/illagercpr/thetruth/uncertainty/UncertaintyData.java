package io.github.illagercpr.thetruth.uncertainty;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * Player-level uncertainty state (M3, red line 2). Attached to every player via
 * {@code TheTruthAttachments#UNCERTAINTY}.
 *
 * @param uncertainty     accumulated uncertainty, 0..{@link UncertaintyCurve#CAP}
 * @param warningTicksLeft ticks left in the deep-layer warning window (0 = none);
 *                        while nonzero the client shows violent flicker and the
 *                        expiry decides between salvage and deletion
 * @param covered          last server-side coverage verdict; synced only, never saved
 * @param nextRandomizeAt  level game time of the next middle-layer re-randomization
 */
public record UncertaintyData(int uncertainty, int warningTicksLeft, boolean covered, long nextRandomizeAt) {

    /** Persisted fields only; {@code covered} is runtime state and never written to disk. */
    public static final Codec<UncertaintyData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Codec.intRange(0, UncertaintyCurve.CAP).optionalFieldOf("uncertainty", 0)
            .forGetter(UncertaintyData::uncertainty),
        Codec.intRange(0, UncertaintyCurve.DEEP_WARNING_TICKS).optionalFieldOf("warning_ticks_left", 0)
            .forGetter(UncertaintyData::warningTicksLeft),
        Codec.LONG.optionalFieldOf("next_randomize_at", 0L)
            .forGetter(UncertaintyData::nextRandomizeAt))
        .apply(instance, (uncertainty, warning, nextRandomize) ->
            new UncertaintyData(uncertainty, warning, false, nextRandomize)));

    public static final StreamCodec<ByteBuf, UncertaintyData> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.VAR_INT, UncertaintyData::uncertainty,
        ByteBufCodecs.VAR_INT, UncertaintyData::warningTicksLeft,
        ByteBufCodecs.BOOL, UncertaintyData::covered,
        ByteBufCodecs.VAR_LONG, UncertaintyData::nextRandomizeAt,
        UncertaintyData::new);

    public static final UncertaintyData DEFAULT = new UncertaintyData(0, 0, false, 0L);

    public UncertaintyData {
        uncertainty = Math.clamp(uncertainty, 0, UncertaintyCurve.CAP);
        if (warningTicksLeft < 0) {
            warningTicksLeft = 0;
        }
    }

    public UncertaintyData withUncertainty(int value) {
        return new UncertaintyData(value, warningTicksLeft, covered, nextRandomizeAt);
    }

    public UncertaintyData withWarningTicks(int value) {
        return new UncertaintyData(uncertainty, Math.max(0, value), covered, nextRandomizeAt);
    }

    public UncertaintyData withCovered(boolean value) {
        return new UncertaintyData(uncertainty, warningTicksLeft, value, nextRandomizeAt);
    }

    public UncertaintyData withNextRandomizeAt(long value) {
        return new UncertaintyData(uncertainty, warningTicksLeft, covered, value);
    }

    /** True when any synced field differs; used to avoid resending identical state every second. */
    public boolean differsExceptCovered(UncertaintyData other) {
        return uncertainty != other.uncertainty
            || warningTicksLeft != other.warningTicksLeft
            || nextRandomizeAt != other.nextRandomizeAt;
    }
}
