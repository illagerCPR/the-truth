package io.github.illagercpr.thetruth.gametest;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.illagercpr.thetruth.TheTruth;
import io.github.illagercpr.thetruth.blockentity.CertusSolidifierBlockEntity;
import io.github.illagercpr.thetruth.gui.CertusSolidifierMenu;
import io.github.illagercpr.thetruth.registry.TheTruthBlocks;
import io.github.illagercpr.thetruth.registry.TheTruthItems;
import io.github.illagercpr.thetruth.registry.TheTruthParticles;
import io.github.illagercpr.thetruth.registry.TheTruthSounds;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * M8 tests: resource integrity (language key parity, sound mapping, particle
 * registration) and the solidifier GUI contract. Resources are read straight
 * from the classpath: the *server* resource manager only covers {@code data/},
 * while lang and sounds live under {@code assets/} (client-side resources).
 * Visual polish itself stays with human verification (docs/02 verification
 * strategy).
 */
@GameTestHolder(TheTruth.MOD_ID)
@PrefixGameTestTemplate(false)
public final class TheTruthM8Tests {

    private static final BlockPos MACHINE_POS = new BlockPos(1, 1, 1);

    private TheTruthM8Tests() {
    }

    private static void check(final GameTestHelper helper, final boolean condition, final String message) {
        if (!condition) {
            helper.fail(message);
        }
    }

    /** Reads a classpath resource as a JSON object. */
    private static JsonObject readJson(final String path) throws Exception {
        try (InputStream stream = TheTruthM8Tests.class.getResourceAsStream(path)) {
            if (stream == null) {
                throw new IllegalStateException("classpath resource missing: " + path);
            }
            try (InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                return JsonParser.parseReader(reader).getAsJsonObject();
            }
        }
    }

    @GameTest(template = "smoke")
    public static void languageFilesHaveIdenticalKeySets(final GameTestHelper helper) {
        helper.startSequence()
            .thenExecute(() -> {
                try {
                    final Set<String> en = readJson("/assets/thetruth/lang/en_us.json").keySet();
                    final Set<String> zh = readJson("/assets/thetruth/lang/zh_cn.json").keySet();
                    final Set<String> onlyEn = new HashSet<>(en);
                    onlyEn.removeAll(zh);
                    final Set<String> onlyZh = new HashSet<>(zh);
                    onlyZh.removeAll(en);
                    check(helper, onlyEn.isEmpty(), "keys only in en_us: " + onlyEn);
                    check(helper, onlyZh.isEmpty(), "keys only in zh_cn: " + onlyZh);
                    check(helper, en.contains("thetruth.gui.solidifier.power"),
                        "the M8 solidifier power key must exist in both languages");
                } catch (final Exception e) {
                    helper.fail("language files could not be read: " + e);
                }
            })
            .thenSucceed();
    }

    @GameTest(template = "smoke")
    public static void m8SoundEventsAreRegisteredAndMapped(final GameTestHelper helper) {
        final var sounds = List.of(
            TheTruthSounds.DATA_INGEST,
            TheTruthSounds.UNCERTAINTY_WARNING,
            TheTruthSounds.UNCERTAINTY_COLLAPSE,
            TheTruthSounds.UNCERTAINTY_SETTLE,
            TheTruthSounds.UNCERTAINTY_SCRAMBLE);
        helper.startSequence()
            .thenExecute(() -> {
                for (final var holder : sounds) {
                    check(helper, BuiltInRegistries.SOUND_EVENT.containsKey(holder.getId()),
                        "sound event must be registered: " + holder.getId());
                }
                try {
                    final JsonObject json = readJson("/assets/thetruth/sounds.json");
                    for (final var holder : sounds) {
                        check(helper, json.has(holder.getId().getPath()),
                            "sounds.json must map " + holder.getId().getPath());
                    }
                } catch (final Exception e) {
                    helper.fail("sounds.json could not be read: " + e);
                }
            })
            .thenSucceed();
    }

    @GameTest(template = "smoke")
    public static void dataStreamParticleIsRegistered(final GameTestHelper helper) {
        helper.startSequence()
            .thenExecute(() -> {
                check(helper, BuiltInRegistries.PARTICLE_TYPE.containsKey(
                        TheTruth.id("data_stream")),
                    "the data-stream particle type must be registered");
            })
            .thenSucceed();
    }

    @GameTest(template = "smoke")
    public static void anchorUpdateTagMirrorsFieldState(final GameTestHelper helper) {
        helper.setBlock(MACHINE_POS, TheTruthBlocks.CERTUS_ANCHOR.get());
        helper.startSequence()
            .thenIdle(2)
            .thenExecute(() -> {
                if (!(helper.getBlockEntity(MACHINE_POS) instanceof
                    io.github.illagercpr.thetruth.blockentity.CertusAnchorBlockEntity anchor)) {
                    helper.fail("anchor block entity missing after idle: state="
                        + helper.getBlockState(MACHINE_POS));
                    return;
                }
                // Simulate the client mirror: the update tag written on the
                // server side is read back through handleUpdateTag.
                final CompoundTag tag = anchor.getUpdateTag(helper.getLevel().registryAccess());
                anchor.handleUpdateTag(tag, helper.getLevel().registryAccess());
                check(helper, tag.getBoolean("field_active") == anchor.isFieldActiveForRender(),
                    "the update tag must mirror the server field state");
            })
            .thenSucceed();
    }

    @GameTest(template = "smoke")
    public static void solidifierMenuMovesUnformedMatter(final GameTestHelper helper) {
        helper.setBlock(MACHINE_POS, TheTruthBlocks.CERTUS_SOLIDIFIER.get());
        helper.startSequence()
            .thenIdle(2)
            .thenExecute(() -> {
                if (!(helper.getBlockEntity(MACHINE_POS) instanceof CertusSolidifierBlockEntity solidifier)) {
                    helper.fail("solidifier block entity missing: state="
                        + helper.getBlockState(MACHINE_POS));
                    return;
                }
                final var mock = helper.makeMockServerPlayerInLevel();
                // The mock spawns near the world spawn, not in the plot; move
                // it next to the machine so the "still usable" distance check
                // (<= 8 blocks) holds.
                final var machineCenter = helper.absolutePos(MACHINE_POS);
                mock.teleportTo(helper.getLevel(), machineCenter.getX() + 0.5,
                    machineCenter.getY() + 1.0, machineCenter.getZ() + 0.5, 0.0F, 0.0F);
                final CertusSolidifierMenu menu =
                    new CertusSolidifierMenu(0, mock.getInventory(), solidifier);
                check(helper, menu.stillValid(mock), "the menu must be usable next to the block");

                // Menu slots: 0 = machine input, 1 = machine output,
                // 2..28 = main inventory (inv index 9..35). Put the matter in
                // the first main-inventory slot, then quick-move it in.
                final ItemStack matter = new ItemStack(TheTruthItems.UNFORMED_MATTER.get(), 3);
                mock.getInventory().setItem(9, matter);
                final ItemStack moved = menu.quickMoveStack(mock, 2);
                check(helper, !moved.isEmpty(),
                    "unformed matter must be movable into the machine input");
                check(helper, !solidifier.getExternalHandler().getStackInSlot(0).isEmpty(),
                    "the machine input slot must now hold the matter");
                check(helper, solidifier.getStoredAEPower() >= 0.0,
                    "the power readout must be safe without a network");
            })
            .thenSucceed();
    }
}
