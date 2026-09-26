package io.github.illagercpr.thetruth.transport;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

/**
 * Entanglement pair data stored on an Entanglement Key (item data component).
 *
 * <p>{@code pairId} links the key to the home entrance core that minted it;
 * {@code homeDimension}/{@code homePos} remember where that core stands, so the
 * return trip works even if the Certus-side structure was torn down (the key is
 * the credential, docs/02 M4 decision 4).
 *
 * @param pairId        id shared by the key pair and the home core
 * @param homeDimension id of the dimension holding the home core (the Overworld)
 * @param homePos       position of the home core block
 */
public record EntanglementPairData(UUID pairId, ResourceLocation homeDimension, BlockPos homePos) {

    public static final Codec<EntanglementPairData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        UUIDUtil.CODEC.fieldOf("pair_id").forGetter(EntanglementPairData::pairId),
        ResourceLocation.CODEC.fieldOf("home_dimension").forGetter(EntanglementPairData::homeDimension),
        BlockPos.CODEC.fieldOf("home_pos").forGetter(EntanglementPairData::homePos)
    ).apply(instance, EntanglementPairData::new));

    public static final StreamCodec<ByteBuf, EntanglementPairData> STREAM_CODEC = StreamCodec.composite(
        UUIDUtil.STREAM_CODEC, EntanglementPairData::pairId,
        ResourceLocation.STREAM_CODEC, EntanglementPairData::homeDimension,
        BlockPos.STREAM_CODEC, EntanglementPairData::homePos,
        EntanglementPairData::new);
}
