package io.github.illagercpr.thetruth.blockentity;

import io.github.illagercpr.thetruth.registry.TheTruthBlockEntities;
import io.github.illagercpr.thetruth.registry.TheTruthItems;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.ItemStackHandler;

/**
 * The Data Port (M6 boss phase 3): a socket The Last Measurer exposes into the
 * arena. It accumulates measurement data; when a player siphons that data away
 * — an AE2 import bus, or the same ItemHandler capability any loader uses —
 * the siphoned unit is credited to the boss as overload. Enough credits and
 * the boss's own measurements tear it apart.
 */
public class DataPortBlockEntity extends BlockEntity {

    public static final int FILL_INTERVAL_TICKS = 100;
    public static final int OVERLOAD_NOTIFY_RADIUS = 32;

    private final PortStackHandler handler = new PortStackHandler();
    @Nullable
    private UUID bossId;

    public DataPortBlockEntity(final BlockPos pos, final BlockState state) {
        super(TheTruthBlockEntities.DATA_PORT.get(), pos, state);
    }

    public PortStackHandler getHandler() {
        return this.handler;
    }

    /** Records which boss this port belongs to (boss placement time). */
    public void bindBoss(final UUID bossId) {
        this.bossId = bossId;
        this.setChanged();
    }

    public boolean ownsBoss(@Nullable final UUID bossId) {
        return bossId != null && bossId.equals(this.bossId);
    }

    @Nullable
    public UUID bossId() {
        return this.bossId;
    }

    public static void serverTick(final Level level, final BlockPos pos, final BlockState state,
                                  final DataPortBlockEntity port) {
        if (level.getGameTime() % FILL_INTERVAL_TICKS != 0) {
            return;
        }
        if (port.bossId == null) {
            return;
        }
        if (port.handler.getStackInSlot(0).isEmpty()) {
            port.handler.setStackInSlot(0, new ItemStack(TheTruthItems.MEASUREMENT_DATA.get()));
        }
    }

    @Override
    protected void saveAdditional(final CompoundTag tag, final HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (this.bossId != null) {
            tag.putUUID("boss_id", this.bossId);
        }
        tag.put("inventory", this.handler.serializeNBT(registries));
    }

    @Override
    protected void loadAdditional(final CompoundTag tag, final HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        this.bossId = tag.hasUUID("boss_id") ? tag.getUUID("boss_id") : null;
        if (tag.contains("inventory")) {
            this.handler.deserializeNBT(registries, tag.getCompound("inventory"));
        }
    }

    /**
     * The port only ever yields measurement data. Every successful extraction
     * credits the boss with one overload unit; insertions are refused.
     */
    public class PortStackHandler extends ItemStackHandler {

        private PortStackHandler() {
            super(1);
        }

        @Override
        public boolean isItemValid(final int slot, final ItemStack stack) {
            return false;
        }

        @Override
        public ItemStack insertItem(final int slot, final ItemStack stack, final boolean simulate) {
            return stack; // take-only: the port is a source, not a sink
        }

        @Override
        protected void onContentsChanged(final int slot) {
            setChanged();
        }

        @Override
        public ItemStack extractItem(final int slot, final int amount, final boolean simulate) {
            final ItemStack extracted = super.extractItem(slot, amount, simulate);
            if (!simulate && !extracted.isEmpty() && getStackInSlot(slot).isEmpty()) {
                creditOverload();
            }
            return extracted;
        }
    }

    private void creditOverload() {
        if (!(this.level instanceof ServerLevel serverLevel) || this.bossId == null) {
            return;
        }
        final var boss = serverLevel.getEntity(this.bossId);
        if (boss instanceof io.github.illagercpr.thetruth.entity.LastMeasurerEntity measurer) {
            measurer.registerOverload();
            serverLevel.playSound(null, this.worldPosition,
                SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.HOSTILE, 1.5F, 1.4F);
            final Component progress = Component.translatable(
                "thetruth.message.port_overload", measurer.overloadProgress(),
                io.github.illagercpr.thetruth.entity.LastMeasurerEntity.OVERLOAD_THRESHOLD);
            for (final Player player : serverLevel.players()) {
                if (player.distanceToSqr(this.worldPosition.getX(), this.worldPosition.getY(),
                        this.worldPosition.getZ()) <= (double) OVERLOAD_NOTIFY_RADIUS * OVERLOAD_NOTIFY_RADIUS) {
                    player.displayClientMessage(progress, true);
                }
            }
        }
    }
}
