package com.benji.titlestudio.title;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class TitleTemplateTriggerMatcher {

    private static final int PROBE_HORIZONTAL = 5;
    private static final int PROBE_VERTICAL = 3;
    private static final int MAX_WORLD_PROBES = 14;
    private static final int MAX_LOCAL_MATCHES_PER_PROBE = 160;
    private static final int MAX_CANDIDATES_TO_VERIFY = 20;
    private static final int VERIFY_SAMPLE_COUNT = 36;
    private static final int NEGATIVE_RESCAN_TICKS = 10;
    private static final float REQUIRED_MATCH_RATIO = 0.72F;

    private static final Mirror[] MIRRORS = {Mirror.NONE, Mirror.LEFT_RIGHT, Mirror.FRONT_BACK};

    private static final Map<ResourceLocation, CachedTemplate> TEMPLATE_CACHE = new HashMap<>();
    private static final Map<UUID, Map<PlacementKey, LocatedPlacement>> LOCATED = new HashMap<>();
    private static final Map<UUID, Map<PlacementKey, Long>> NEXT_SCAN = new HashMap<>();

    private TitleTemplateTriggerMatcher() {
    }

    public static boolean matches(ServerPlayer player, ResourceLocation templateId) {
        if (player == null || templateId == null) {
            return false;
        }

        ServerLevel level = player.serverLevel();
        Optional<StructureTemplate> optional = level.getStructureManager().get(templateId);
        if (optional.isEmpty()) {
            return false;
        }

        StructureTemplate template = optional.get();
        CachedTemplate cached = TEMPLATE_CACHE.get(templateId);

        /*
         * StructureTemplateManager replaces cached template instances on resource reload.
         * Comparing identity lets us rebuild automatically without another reload hook.
         */
        if (cached == null || cached.source() != template) {
            cached = build(level, template);
            TEMPLATE_CACHE.put(templateId, cached);
        }

        if (!cached.valid()) {
            return false;
        }

        PlacementKey key = new PlacementKey(level.dimension().location(), templateId);
        Map<PlacementKey, LocatedPlacement> playerPlacements = LOCATED.computeIfAbsent(player.getUUID(), ignored -> new HashMap<>());

        LocatedPlacement known = playerPlacements.get(key);
        BlockPos playerPos = player.blockPosition();

        if (known != null && known.contains(playerPos)) {
            return true;
        }

        long gameTime = level.getGameTime();
        Map<PlacementKey, Long> playerScans = NEXT_SCAN.computeIfAbsent(player.getUUID(), ignored -> new HashMap<>());

        long nextScan = playerScans.getOrDefault(key, Long.MIN_VALUE);
        if (gameTime < nextScan) {
            return false;
        }

        playerScans.put(key, gameTime + NEGATIVE_RESCAN_TICKS);

        for (TransformData transform : cached.transforms()) {
            BlockPos origin = findOrigin(level, playerPos, transform);
            if (origin == null) {
                continue;
            }

            LocatedPlacement located = new LocatedPlacement(origin, transform.minOffset(), transform.maxOffset());

            playerPlacements.put(key, located);
            playerScans.remove(key);
            return true;
        }

        return false;
    }

    public static void clearPlayer(UUID playerId) {
        if (playerId == null) {
            return;
        }

        LOCATED.remove(playerId);
        NEXT_SCAN.remove(playerId);
    }

    private static CachedTemplate build(ServerLevel level, StructureTemplate template) {
        try {
            Vec3i size = template.getSize();
            if (size.getX() <= 0 || size.getY() <= 0 || size.getZ() <= 0) {
                return CachedTemplate.invalid(template);
            }

            CompoundTag root = template.save(new CompoundTag());
            ListTag paletteTag = primaryPalette(root);
            ListTag blocksTag = root.getList(StructureTemplate.BLOCKS_TAG, Tag.TAG_COMPOUND);

            if (paletteTag.isEmpty() || blocksTag.isEmpty()) {
                return CachedTemplate.invalid(template);
            }

            var blockLookup = level.registryAccess().registryOrThrow(Registries.BLOCK).asLookup();
            List<Block> palette = new ArrayList<>(paletteTag.size());

            for (int i = 0; i < paletteTag.size(); i++) {
                BlockState state = NbtUtils.readBlockState(blockLookup, paletteTag.getCompound(i));
                palette.add(state.getBlock());
            }

            List<TemplateBlock> baseBlocks = new ArrayList<>();

            for (int i = 0; i < blocksTag.size(); i++) {
                CompoundTag blockTag = blocksTag.getCompound(i);
                int paletteIndex = blockTag.getInt(StructureTemplate.BLOCK_TAG_STATE);

                if (paletteIndex < 0 || paletteIndex >= palette.size()) {
                    continue;
                }

                ListTag pos = blockTag.getList(StructureTemplate.BLOCK_TAG_POS, Tag.TAG_INT);
                if (pos.size() != 3) {
                    continue;
                }

                Block block = palette.get(paletteIndex);
                if (isIgnoredTemplateBlock(block)) {
                    continue;
                }

                baseBlocks.add(new TemplateBlock(new BlockPos(pos.getInt(0), pos.getInt(1), pos.getInt(2)), block));
            }

            if (baseBlocks.isEmpty()) {
                return CachedTemplate.invalid(template);
            }

            List<TransformData> transforms = new ArrayList<>();

            for (Mirror mirror : MIRRORS) {
                for (Rotation rotation : Rotation.values()) {
                    transforms.add(buildTransform(size, baseBlocks, mirror, rotation));
                }
            }

            return new CachedTemplate(template, List.copyOf(transforms), true);
        } catch (Exception ignored) {
            return CachedTemplate.invalid(template);
        }
    }

    private static ListTag primaryPalette(CompoundTag root) {
        if (root.contains(StructureTemplate.PALETTE_TAG, Tag.TAG_LIST)) {
            return root.getList(StructureTemplate.PALETTE_TAG, Tag.TAG_COMPOUND);
        }

        ListTag palettes = root.getList(StructureTemplate.PALETTE_LIST_TAG, Tag.TAG_LIST);
        if (!palettes.isEmpty() && palettes.get(0) instanceof ListTag first) {
            return first;
        }

        return new ListTag();
    }

    private static TransformData buildTransform(Vec3i size, List<TemplateBlock> baseBlocks, Mirror mirror, Rotation rotation) {
        Map<Block, List<BlockPos>> byBlock = new HashMap<>();
        List<TemplateBlock> transformedBlocks = new ArrayList<>(baseBlocks.size());

        for (TemplateBlock base : baseBlocks) {
            BlockPos transformedPos = StructureTemplate.transform(base.pos(), mirror, rotation, BlockPos.ZERO);

            transformedBlocks.add(new TemplateBlock(transformedPos, base.block()));
            byBlock.computeIfAbsent(base.block(), ignored -> new ArrayList<>()).add(transformedPos);
        }

        BlockPos[] bounds = transformedBounds(size, mirror, rotation);

        Map<Block, Integer> frequency = new HashMap<>();
        for (TemplateBlock block : transformedBlocks) {
            frequency.merge(block.block(), 1, Integer::sum);
        }

        List<TemplateBlock> verification = new ArrayList<>(transformedBlocks);
        verification.sort(Comparator.comparingInt((TemplateBlock block) -> frequency.getOrDefault(block.block(), Integer.MAX_VALUE)).thenComparingLong(block -> spatialHash(block.pos())));

        if (verification.size() > VERIFY_SAMPLE_COUNT) {
            verification = new ArrayList<>(verification.subList(0, VERIFY_SAMPLE_COUNT));
        }

        return new TransformData(mirror, rotation, bounds[0], bounds[1], byBlock, List.copyOf(verification));
    }

    private static BlockPos[] transformedBounds(Vec3i size, Mirror mirror, Rotation rotation) {
        int maxX = Math.max(0, size.getX() - 1);
        int maxY = Math.max(0, size.getY() - 1);
        int maxZ = Math.max(0, size.getZ() - 1);

        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int outMaxX = Integer.MIN_VALUE;
        int outMaxY = Integer.MIN_VALUE;
        int outMaxZ = Integer.MIN_VALUE;

        for (int x : new int[]{0, maxX}) {
            for (int y : new int[]{0, maxY}) {
                for (int z : new int[]{0, maxZ}) {
                    BlockPos transformed = StructureTemplate.transform(new BlockPos(x, y, z), mirror, rotation, BlockPos.ZERO);

                    minX = Math.min(minX, transformed.getX());
                    minY = Math.min(minY, transformed.getY());
                    minZ = Math.min(minZ, transformed.getZ());
                    outMaxX = Math.max(outMaxX, transformed.getX());
                    outMaxY = Math.max(outMaxY, transformed.getY());
                    outMaxZ = Math.max(outMaxZ, transformed.getZ());
                }
            }
        }

        return new BlockPos[]{new BlockPos(minX, minY, minZ), new BlockPos(outMaxX, outMaxY, outMaxZ)};
    }

    private static BlockPos findOrigin(ServerLevel level, BlockPos playerPos, TransformData transform) {
        List<WorldProbe> probes = collectWorldProbes(level, playerPos, transform.byBlock());
        if (probes.isEmpty()) {
            return null;
        }

        probes.sort(Comparator.comparingInt(probe -> probe.localMatches().size()));

        Map<BlockPos, Integer> votes = new LinkedHashMap<>();
        int probeCount = Math.min(MAX_WORLD_PROBES, probes.size());

        for (int probeIndex = 0; probeIndex < probeCount; probeIndex++) {
            WorldProbe probe = probes.get(probeIndex);
            List<BlockPos> locals = probe.localMatches();
            int localCount = Math.min(MAX_LOCAL_MATCHES_PER_PROBE, locals.size());

            if (localCount <= 0) {
                continue;
            }

            /*
             * For very common floor/wall blocks, spread samples over the whole template list
             * instead of always testing only the first entries.
             */
            double step = locals.size() / (double) localCount;
            double start = Math.floorMod(spatialHash(probe.worldPos()), Math.max(1, locals.size())) / (double) Math.max(1, localCount);

            for (int i = 0; i < localCount; i++) {
                int index = Math.min(locals.size() - 1, (int) Math.floor(start + i * step));
                BlockPos local = locals.get(index);
                BlockPos origin = probe.worldPos().subtract(local);

                if (!contains(playerPos, origin, transform.minOffset(), transform.maxOffset())) {
                    continue;
                }

                votes.merge(origin.immutable(), 1, Integer::sum);
            }
        }

        if (votes.isEmpty()) {
            return null;
        }

        List<Map.Entry<BlockPos, Integer>> candidates = new ArrayList<>(votes.entrySet());
        candidates.sort(Map.Entry.<BlockPos, Integer>comparingByValue().reversed().thenComparingLong(entry -> distanceSquaredToBoundsCenter(playerPos, entry.getKey(), transform)));

        int count = Math.min(MAX_CANDIDATES_TO_VERIFY, candidates.size());

        for (int i = 0; i < count; i++) {
            BlockPos origin = candidates.get(i).getKey();

            if (verify(level, origin, transform)) {
                return origin;
            }
        }

        return null;
    }

    private static List<WorldProbe> collectWorldProbes(ServerLevel level, BlockPos center, Map<Block, List<BlockPos>> byBlock) {
        List<WorldProbe> result = new ArrayList<>();

        for (int dy = -PROBE_VERTICAL; dy <= PROBE_VERTICAL; dy++) {
            for (int dx = -PROBE_HORIZONTAL; dx <= PROBE_HORIZONTAL; dx++) {
                for (int dz = -PROBE_HORIZONTAL; dz <= PROBE_HORIZONTAL; dz++) {
                    BlockPos worldPos = center.offset(dx, dy, dz);
                    Block block = level.getBlockState(worldPos).getBlock();
                    List<BlockPos> localMatches = byBlock.get(block);

                    if (localMatches == null || localMatches.isEmpty()) {
                        continue;
                    }

                    result.add(new WorldProbe(worldPos.immutable(), localMatches));
                }
            }
        }

        return result;
    }

    private static boolean verify(ServerLevel level, BlockPos origin, TransformData transform) {
        List<TemplateBlock> samples = transform.verificationSamples();
        if (samples.isEmpty()) {
            return false;
        }

        int checked = 0;
        int matched = 0;

        for (TemplateBlock sample : samples) {
            BlockPos worldPos = origin.offset(sample.pos());

            if (!level.hasChunkAt(worldPos)) {
                continue;
            }

            checked++;

            if (level.getBlockState(worldPos).getBlock() == sample.block()) {
                matched++;
            }
        }

        int minimumChecks = Math.min(6, samples.size());
        if (checked < minimumChecks) {
            return false;
        }

        return matched / (float) checked >= REQUIRED_MATCH_RATIO;
    }

    private static boolean contains(BlockPos position, BlockPos origin, BlockPos minOffset, BlockPos maxOffset) {
        int minX = origin.getX() + minOffset.getX();
        int minY = origin.getY() + minOffset.getY();
        int minZ = origin.getZ() + minOffset.getZ();
        int maxX = origin.getX() + maxOffset.getX();
        int maxY = origin.getY() + maxOffset.getY();
        int maxZ = origin.getZ() + maxOffset.getZ();

        return position.getX() >= minX && position.getX() <= maxX && position.getY() >= minY && position.getY() <= maxY && position.getZ() >= minZ && position.getZ() <= maxZ;
    }

    private static boolean isIgnoredTemplateBlock(Block block) {
        return block == Blocks.AIR || block == Blocks.CAVE_AIR || block == Blocks.VOID_AIR || block == Blocks.STRUCTURE_VOID || block == Blocks.STRUCTURE_BLOCK || block == Blocks.JIGSAW;
    }

    private static long spatialHash(BlockPos pos) {
        long value = pos.asLong();
        value ^= value >>> 33;
        value *= 0xff51afd7ed558ccdl;
        value ^= value >>> 33;
        value *= 0xc4ceb9fe1a85ec53l;
        value ^= value >>> 33;
        return value;
    }

    private static long distanceSquaredToBoundsCenter(BlockPos playerPos, BlockPos origin, TransformData transform) {
        long centerX = origin.getX() + (transform.minOffset().getX() + transform.maxOffset().getX()) / 2L;
        long centerY = origin.getY() + (transform.minOffset().getY() + transform.maxOffset().getY()) / 2L;
        long centerZ = origin.getZ() + (transform.minOffset().getZ() + transform.maxOffset().getZ()) / 2L;

        long dx = playerPos.getX() - centerX;
        long dy = playerPos.getY() - centerY;
        long dz = playerPos.getZ() - centerZ;
        return dx * dx + dy * dy + dz * dz;
    }

    private record CachedTemplate(StructureTemplate source, List<TransformData> transforms, boolean valid) {
        static CachedTemplate invalid(StructureTemplate source) {
            return new CachedTemplate(source, List.of(), false);
        }
    }

    private record TemplateBlock(BlockPos pos, Block block) {
    }

    private record TransformData(Mirror mirror, Rotation rotation, BlockPos minOffset, BlockPos maxOffset,
                                 Map<Block, List<BlockPos>> byBlock, List<TemplateBlock> verificationSamples) {
    }

    private record WorldProbe(BlockPos worldPos, List<BlockPos> localMatches) {
    }

    private record PlacementKey(ResourceLocation dimension, ResourceLocation template) {
    }

    private record LocatedPlacement(BlockPos origin, BlockPos minOffset, BlockPos maxOffset) {
        boolean contains(BlockPos position) {
            return TitleTemplateTriggerMatcher.contains(position, origin, minOffset, maxOffset);
        }
    }
}
