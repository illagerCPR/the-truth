package io.github.illagercpr.thetruth.endgame;

import io.github.illagercpr.thetruth.TheTruth;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Client sync of the endgame marker (M7). The flag itself lives in the
 * Overworld's {@link EndgameState} (server-authoritative); clients get a
 * one-way mirror pushed on login and on the irreversible flip, so the
 * storage-cell mark can render without any client-side guesswork.
 */
@EventBusSubscriber(modid = TheTruth.MOD_ID)
public final class EndgameSync {

    /** Client mirror of the server flag; written only by the payload handler. */
    public static volatile boolean clientTruthBroughtBack = false;

    /**
     * Play-to-client payload carrying the flag. Registered on both sides
     * (protocol declaration); the handler only ever runs on the client.
     */
    public record TruthBroughtBackPayload(boolean unlocked) implements CustomPacketPayload {

        public static final Type<TruthBroughtBackPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(TheTruth.MOD_ID, "truth_brought_back"));

        public static final StreamCodec<ByteBuf, TruthBroughtBackPayload> STREAM_CODEC =
            StreamCodec.composite(
                ByteBufCodecs.BOOL, TruthBroughtBackPayload::unlocked,
                TruthBroughtBackPayload::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Mod-bus hook: declares the payload and installs the client handler. */
    public static void register(final RegisterPayloadHandlersEvent event) {
        final PayloadRegistrar registrar = event.registrar("1").optional();
        registrar.playToClient(TruthBroughtBackPayload.TYPE, TruthBroughtBackPayload.STREAM_CODEC,
            (payload, context) -> clientTruthBroughtBack = payload.unlocked());
    }

    /** Pushes the flip to every online player the moment it happens. */
    public static void broadcastOnUnlock(final MinecraftServer server) {
        for (final ServerPlayer player : server.getPlayerList().getPlayers()) {
            sendMirror(player);
        }
    }

    /** Late joiners must see the marked world too. */
    @SubscribeEvent
    public static void onPlayerLoggedIn(final PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && player.getServer() != null) {
            sendMirror(player);
        }
    }

    /**
     * The mirror is visual-only, and some players cannot receive mod payloads
     * at all: GameTest mock players hold a vanilla connection that never
     * negotiated the payload, and NeoForge rejects the send with an
     * {@link UnsupportedOperationException} — skip instead of blowing up
     * their login (or a GameTest).
     */
    private static void sendMirror(final ServerPlayer player) {
        try {
            PacketDistributor.sendToPlayer(player,
                new TruthBroughtBackPayload(EndgameState.isUnlocked(player.getServer())));
        } catch (final UnsupportedOperationException denied) {
            TheTruth.LOGGER.debug("Endgame mirror skipped for {}: {}", player, denied.getMessage());
        }
    }

    private EndgameSync() {
    }
}
