package com.tonten.tonten;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

public class TonkachiItem extends Item {
    private static final String MODE_KEY = "TontenMode";
    private static final String SPACING_KEY = "TontenSpacing";
    private static final String RANDOM_SIZE_KEY = "TontenRandomSize";
    private static final String SOUND_KEY = "TontenSound";
    private static final String LAST_SOUND_TICK_KEY = "TontenLastSoundTick";
    private static final long RHYTHM_RESET_TICKS = 16L;
    private static final double VIEW_PLACE_REACH = 2.0D;
    private static final double VIEW_PLACE_STEP = 0.25D;
    private static final int PLACE_FLAGS = 11;
    private static final int MIN_SPACING = 1;
    private static final int MAX_SPACING = 5;
    private static final int SPACING_DISTANCE = 30;
    private static final int RANDOM_SIZE_MIN = 3;
    private static final int RANDOM_SIZE_MID = 5;
    private static final int RANDOM_SIZE_MAX = 9;
    private static final int FRAME_MAX_RANGE = 30;
    private static final String FRAME_START_X_KEY = "TontenFrameStartX";
    private static final String FRAME_START_Y_KEY = "TontenFrameStartY";
    private static final String FRAME_START_Z_KEY = "TontenFrameStartZ";
    private static final String FRAME_END_X_KEY = "TontenFrameEndX";
    private static final String FRAME_END_Y_KEY = "TontenFrameEndY";
    private static final String FRAME_END_Z_KEY = "TontenFrameEndZ";
    private static final String FRAME_FACE_KEY = "TontenFrameFace";
    private static final int CYLINDER_MIN_RADIUS = 1;
    private static final int CYLINDER_MAX_RADIUS = 15;
    private static final int CYLINDER_DEFAULT_RADIUS = 3;
    private static final int CYLINDER_MAX_HEIGHT = 30;
    private static final String CYLINDER_CENTER_X_KEY = "TontenCylinderCenterX";
    private static final String CYLINDER_CENTER_Y_KEY = "TontenCylinderCenterY";
    private static final String CYLINDER_CENTER_Z_KEY = "TontenCylinderCenterZ";
    private static final String CYLINDER_END_X_KEY = "TontenCylinderEndX";
    private static final String CYLINDER_END_Y_KEY = "TontenCylinderEndY";
    private static final String CYLINDER_END_Z_KEY = "TontenCylinderEndZ";
    private static final String CYLINDER_FACE_KEY = "TontenCylinderFace";
    private static final String CYLINDER_RADIUS_KEY = "TontenCylinderRadius";
    private static final int PREVIEW_MAX_BLOCKS = 768;
    private final TonkachiTier tier;

    public TonkachiItem(TonkachiTier tier, Properties properties) {
        super(properties);
        this.tier = tier;
    }

    public TonkachiTier tier() {
        return this.tier;
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack hammer = player.getItemInHand(hand);
        TonkachiMode mode = getMode(hammer);
        if (hand != InteractionHand.MAIN_HAND) {
            return InteractionResult.PASS;
        }
        if (mode == TonkachiMode.FRAME) {
            if (level.isClientSide()) {
                return InteractionResult.SUCCESS;
            }
            return clearFrameSelectionStep(player, hammer);
        }
        if (mode == TonkachiMode.CYLINDER_FRAME) {
            if (level.isClientSide()) {
                return InteractionResult.SUCCESS;
            }
            if (player.isShiftKeyDown()) {
                return cycleCylinderRadius(player, hammer);
            }
            return clearCylinderSelectionStep(player, hammer);
        }
        if (mode != TonkachiMode.EXTEND && mode != TonkachiMode.AIR) {
            return InteractionResult.PASS;
        }
        if (mode == TonkachiMode.AIR && !this.tier.canAirPlace()) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }

        return placeViewedOffhandBlock((ServerLevel) level, player, hammer, hand, mode);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        Level level = context.getLevel();
        if (player == null || context.getHand() != InteractionHand.MAIN_HAND) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }

        ServerLevel serverLevel = (ServerLevel) level;
        ItemStack hammer = context.getItemInHand();
        TonkachiMode mode = normalizeMode(hammer);
        if (mode == TonkachiMode.FRAME) {
            return handleFrameUseOn(context, serverLevel, player, hammer);
        }
        if (mode == TonkachiMode.CYLINDER_FRAME) {
            return handleCylinderFrameUseOn(context, serverLevel, player, hammer);
        }
        if (mode == TonkachiMode.SPACING) {
            if (player.isShiftKeyDown()) {
                cycleSpacing(hammer, player);
                return InteractionResult.SUCCESS;
            }
            return placeSpacingBlocks(context, serverLevel, player, hammer);
        }
        if (mode == TonkachiMode.RANDOM) {
            if (player.isShiftKeyDown()) {
                cycleRandomSize(hammer, player);
                return InteractionResult.SUCCESS;
            }
            return placeRandomBlocks(context, serverLevel, player, hammer);
        }
        InteractionResult specialPlacement = trySpecialOffhandPlacement(context, serverLevel, player, hammer, mode);
        if (specialPlacement != InteractionResult.PASS) {
            return specialPlacement;
        }

        BlockPos clickedPos = context.getClickedPos();
        BlockState clickedState = level.getBlockState(clickedPos);
        if (mode == TonkachiMode.UPSIDE_DOWN || mode == TonkachiMode.ROTATE) {
            BlockState changedState = mode == TonkachiMode.UPSIDE_DOWN ? upsideDown(clickedState) : rotate(clickedState);
            if (changedState == clickedState) {
                message(player, "message.tonten.no_transform");
                return InteractionResult.FAIL;
            }
            serverLevel.setBlock(clickedPos, changedState, PLACE_FLAGS);
            hammer.hurtAndBreak(1, player, context.getHand());
            playTonkachiSound(level, clickedPos, hammer);
            return InteractionResult.SUCCESS;
        }
        PlacementPlan plan;
        ItemStack offhandPlacementStack = ItemStack.EMPTY;
        if (usesOffhandBlock(mode)) {
            offhandPlacementStack = player.getOffhandItem();
            if (!(offhandPlacementStack.getItem() instanceof BlockItem blockItem)) {
                message(player, "message.tonten.no_offhand_block");
                return InteractionResult.FAIL;
            }
            plan = createPlan(context, mode, blockItem.getBlock().defaultBlockState(), blockItem);
        } else {
            Item clickedItem = clickedState.getBlock().asItem();
            if (clickedItem == Items.AIR) {
                message(player, "message.tonten.no_source_block");
                return InteractionResult.FAIL;
            }
            if (Tonten.isSolidifySpaceBlockItem(clickedItem) && this.tier != TonkachiTier.DIAMOND) {
                message(player, "message.tonten.solidify_diamond_extend_only");
                return InteractionResult.FAIL;
            }
            if (!player.getAbilities().instabuild && findMatchingBlockStack(player, clickedItem).isEmpty()) {
                message(player, "message.tonten.no_matching_block");
                return InteractionResult.FAIL;
            }
            plan = createPlan(context, mode, resetPlacementLifetime(clickedState), clickedItem);
        }
        if (plan.positions().isEmpty()) {
            message(player, "message.tonten.no_place");
            return InteractionResult.FAIL;
        }

        int placed = placePlannedBlocks(serverLevel, player, offhandPlacementStack, plan);
        if (placed <= 0) {
            message(player, "message.tonten.no_place");
            return InteractionResult.FAIL;
        }

        hammer.hurtAndBreak(1, player, context.getHand());
        playTonkachiSound(level, clickedPos, hammer);
        return InteractionResult.SUCCESS;
    }

    private TonkachiMode normalizeMode(ItemStack hammer) {
        TonkachiMode mode = getMode(hammer);
        if ((mode == TonkachiMode.VERTICAL_UP || mode == TonkachiMode.VERTICAL_DOWN) && this.tier != TonkachiTier.IRON) {
            mode = TonkachiMode.VERTICAL;
            setMode(hammer, mode);
        } else if ((mode == TonkachiMode.VERTICAL_LEFT || mode == TonkachiMode.VERTICAL_RIGHT) && this.tier != TonkachiTier.GOLD) {
            mode = TonkachiMode.VERTICAL;
            setMode(hammer, mode);
        } else if ((mode == TonkachiMode.UPSIDE_DOWN || mode == TonkachiMode.ROTATE) && this.tier != TonkachiTier.STONE) {
            mode = TonkachiMode.VERTICAL;
            setMode(hammer, mode);
        } else if (mode == TonkachiMode.FRAME && this.tier != TonkachiTier.GOLD) {
            mode = TonkachiMode.VERTICAL;
            setMode(hammer, mode);
        } else if (mode == TonkachiMode.CYLINDER_FRAME && this.tier != TonkachiTier.DIAMOND) {
            mode = TonkachiMode.VERTICAL;
            setMode(hammer, mode);
        } else if ((mode == TonkachiMode.SPACING || mode == TonkachiMode.RANDOM) && this.tier != TonkachiTier.COPPER) {
            mode = TonkachiMode.VERTICAL;
            setMode(hammer, mode);
        } else if (mode == TonkachiMode.AIR && !this.tier.canAirPlace()) {
            mode = TonkachiMode.FLAT;
            setMode(hammer, mode);
        }
        return mode;
    }


    private static InteractionResult handleFrameUseOn(UseOnContext context, ServerLevel level, Player player, ItemStack hammer) {
        BlockPos clickedPos = context.getClickedPos();
        ItemStack offhandStack = player.getOffhandItem();
        if (!(offhandStack.getItem() instanceof BlockItem blockItem)) {
            message(player, "message.tonten.no_offhand_block");
            return InteractionResult.FAIL;
        }

        BlockPos start = getFrameStart(hammer);
        BlockPos end = getFrameEnd(hammer);
        if (start == null) {
            setFrameStart(hammer, clickedPos, context.getClickedFace());
            clearFrameEnd(hammer);
            message(player, "message.tonten.frame_start_set");
            return InteractionResult.SUCCESS;
        }
        if (end == null) {
            if (!isValidFrameSelection(start, clickedPos)) {
                message(player, "message.tonten.frame_invalid");
                return InteractionResult.FAIL;
            }
            setFrameEnd(hammer, clickedPos);
            message(player, "message.tonten.frame_end_set");
            return InteractionResult.SUCCESS;
        }

        if (!isValidFrameSelection(start, end)) {
            clearFrameSelection(hammer);
            message(player, "message.tonten.frame_invalid");
            return InteractionResult.FAIL;
        }

        List<BlockPos> positions = offsetPositions(framePositions(start, end), getFrameFace(hammer));
        int placed = 0;
        boolean creative = player.getAbilities().instabuild;
        BlockState state = blockItem.getBlock().defaultBlockState();
        for (BlockPos pos : positions) {
            if (!creative && offhandStack.isEmpty()) {
                break;
            }
            if (!canPlace(level, pos, state)) {
                continue;
            }
            level.setBlock(pos, state, PLACE_FLAGS);
            if (!creative) {
                consumePlacementStack(level, player, offhandStack);
            }
            placed++;
        }
        if (placed <= 0) {
            message(player, "message.tonten.no_place");
            return InteractionResult.FAIL;
        }

        clearFrameSelection(hammer);
        hammer.hurtAndBreak(1, player, context.getHand());
        playTonkachiSound(level, clickedPos, hammer);
        return InteractionResult.SUCCESS;
    }

    private static InteractionResult clearFrameSelectionStep(Player player, ItemStack hammer) {
        if (getFrameEnd(hammer) != null) {
            clearFrameEnd(hammer);
            message(player, "message.tonten.frame_end_cleared");
            return InteractionResult.SUCCESS;
        }
        if (getFrameStart(hammer) != null) {
            clearFrameSelection(hammer);
            message(player, "message.tonten.frame_start_cleared");
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }

    private static InteractionResult handleCylinderFrameUseOn(UseOnContext context, ServerLevel level, Player player, ItemStack hammer) {
        if (player.isShiftKeyDown()) {
            return cycleCylinderRadius(player, hammer);
        }

        ItemStack offhandStack = player.getOffhandItem();
        if (!(offhandStack.getItem() instanceof BlockItem blockItem)) {
            message(player, "message.tonten.no_offhand_block");
            return InteractionResult.FAIL;
        }

        BlockPos clickedPos = context.getClickedPos();
        BlockPos center = getCylinderCenter(hammer);
        BlockPos end = getCylinderEnd(hammer);
        if (center == null) {
            setCylinderCenter(hammer, clickedPos, context.getClickedFace());
            clearCylinderEnd(hammer);
            message(player, "message.tonten.cylinder_center_set");
            return InteractionResult.SUCCESS;
        }
        if (end == null) {
            if (!isValidCylinderSelection(center, clickedPos)) {
                message(player, "message.tonten.frame_invalid");
                return InteractionResult.FAIL;
            }
            setCylinderEnd(hammer, clickedPos);
            message(player, "message.tonten.cylinder_end_set");
            return InteractionResult.SUCCESS;
        }

        if (!isValidCylinderSelection(center, end)) {
            clearCylinderSelection(hammer);
            message(player, "message.tonten.frame_invalid");
            return InteractionResult.FAIL;
        }

        List<BlockPos> positions = offsetPositions(cylinderFramePositions(center, end, getCylinderRadius(hammer)), getCylinderFace(hammer));
        int placed = 0;
        boolean creative = player.getAbilities().instabuild;
        BlockState state = blockItem.getBlock().defaultBlockState();
        for (BlockPos pos : positions) {
            if (!creative && offhandStack.isEmpty()) {
                break;
            }
            if (!canPlace(level, pos, state)) {
                continue;
            }
            level.setBlock(pos, state, PLACE_FLAGS);
            if (!creative) {
                consumePlacementStack(level, player, offhandStack);
            }
            placed++;
        }
        if (placed <= 0) {
            message(player, "message.tonten.no_place");
            return InteractionResult.FAIL;
        }

        clearCylinderSelection(hammer);
        hammer.hurtAndBreak(1, player, context.getHand());
        playTonkachiSound(level, clickedPos, hammer);
        return InteractionResult.SUCCESS;
    }

    private static InteractionResult cycleCylinderRadius(Player player, ItemStack hammer) {
        int next = getCylinderRadius(hammer) + 1;
        if (next > CYLINDER_MAX_RADIUS) {
            next = CYLINDER_MIN_RADIUS;
        }
        setCylinderRadius(hammer, next);
        player.sendOverlayMessage(Component.translatable("message.tonten.cylinder_radius", next));
        return InteractionResult.SUCCESS;
    }

    private static InteractionResult clearCylinderSelectionStep(Player player, ItemStack hammer) {
        if (getCylinderEnd(hammer) != null) {
            clearCylinderEnd(hammer);
            message(player, "message.tonten.cylinder_end_cleared");
            return InteractionResult.SUCCESS;
        }
        if (getCylinderCenter(hammer) != null) {
            clearCylinderSelection(hammer);
            message(player, "message.tonten.cylinder_center_cleared");
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }

    public static boolean isPreviewActive(ItemStack stack) {
        TonkachiMode mode = getMode(stack);
        return stack.getItem() instanceof TonkachiItem
                && (mode == TonkachiMode.FRAME && getFrameStart(stack) != null
                || mode == TonkachiMode.CYLINDER_FRAME && getCylinderCenter(stack) != null);
    }

    public static List<BlockPos> getPreviewPositions(ItemStack stack) {
        TonkachiMode mode = getMode(stack);
        if (mode == TonkachiMode.FRAME) {
            return getFramePreviewPositions(stack);
        }
        if (mode == TonkachiMode.CYLINDER_FRAME) {
            return getCylinderPreviewPositions(stack);
        }
        return List.of();
    }

    public static List<PreviewBlock> getClientPreview(Level level, Player player, ItemStack hammer, BlockPos hitPos, Direction hitFace) {
        if (!(hammer.getItem() instanceof TonkachiItem tonkachi)) {
            return List.of();
        }

        TonkachiMode mode = getMode(hammer);
        if ((mode == TonkachiMode.VERTICAL_UP || mode == TonkachiMode.VERTICAL_DOWN) && tonkachi.tier != TonkachiTier.IRON) {
            mode = TonkachiMode.VERTICAL;
        } else if ((mode == TonkachiMode.UPSIDE_DOWN || mode == TonkachiMode.ROTATE) && tonkachi.tier != TonkachiTier.STONE) {
            return List.of();
        } else if (mode == TonkachiMode.FRAME && tonkachi.tier != TonkachiTier.GOLD) {
            mode = TonkachiMode.VERTICAL;
        } else if (mode == TonkachiMode.CYLINDER_FRAME && tonkachi.tier != TonkachiTier.DIAMOND) {
            mode = TonkachiMode.VERTICAL;
        } else if (mode == TonkachiMode.AIR && !tonkachi.tier.canAirPlace()) {
            mode = TonkachiMode.FLAT;
        }

        BlockState previewState = previewPlacementState(level, player, mode, hitPos);
        if (mode == TonkachiMode.FRAME) {
            return previewFromPositions(level, previewState, getFramePreviewPositions(hammer), getFramePreviewAnchor(hammer));
        }
        if (mode == TonkachiMode.CYLINDER_FRAME) {
            return previewFromPositions(level, previewState, getCylinderPreviewPositions(hammer), getCylinderPreviewAnchor(hammer));
        }

        if (previewState == null) {
            return List.of();
        }
        List<BlockPos> positions;
        if (mode == TonkachiMode.EXTEND || mode == TonkachiMode.AIR) {
            BlockPos viewed = findViewedPlacementPos(level, player, previewState, mode == TonkachiMode.EXTEND);
            positions = viewed == null ? List.of() : List.of(viewed);
        } else if (hitPos == null || hitFace == null) {
            return List.of();
        } else {
            positions = switch (mode) {
                case FLAT -> facePlanePositions(hitPos, hitFace, tonkachi.tier.flatSize());
                case STAIRS -> stairLinePositions(hitPos, player, hitFace, tonkachi.tier.lineLimit());
                case VERTICAL -> linePositions(hitPos, hitFace.getOpposite(), tonkachi.tier.lineLimit());
                case VERTICAL_UP -> linePositions(hitPos, Direction.UP, tonkachi.tier.lineLimit());
                case VERTICAL_DOWN -> linePositions(hitPos, Direction.DOWN, tonkachi.tier.lineLimit());
                case VERTICAL_LEFT -> linePositions(hitPos, horizontalSideDirection(player, hitFace, false), tonkachi.tier.lineLimit());
                case VERTICAL_RIGHT -> linePositions(hitPos, horizontalSideDirection(player, hitFace, true), tonkachi.tier.lineLimit());
                case SPACING -> firstSpacingPreviewPosition(level, previewState, hitPos, hitFace.getOpposite(), getSpacing(hammer));
                case RANDOM -> randomPreviewPositions(hitPos, hitFace, getRandomSize(hammer));
                default -> List.of();
            };
            if (mode == TonkachiMode.RANDOM) {
                positions = firstLinePreviewPosition(level, previewState, positions);
            } else if (!isLineMode(mode)) {
                positions = orderByDistanceFromCenter(positions, hitPos);
            } else {
                positions = firstLinePreviewPosition(level, previewState, positions);
            }
        }
        return previewFromPositions(level, previewState, positions, null);
    }

    private static List<BlockPos> firstLinePreviewPosition(Level level, BlockState state, List<BlockPos> positions) {
        if (positions.isEmpty()) {
            return positions;
        }
        for (BlockPos pos : positions) {
            if (canPlacePreview(level, pos, state)) {
                return List.of(pos);
            }
        }
        return List.of(positions.getFirst());
    }

    private static List<BlockPos> firstSpacingPreviewPosition(Level level, BlockState state, BlockPos origin, Direction direction, int spacing) {
        return firstLinePreviewPosition(level, state, spacingPositions(origin, direction, spacing));
    }

    private static BlockState previewPlacementState(Level level, Player player, TonkachiMode mode, BlockPos hitPos) {
        ItemStack offhand = player.getOffhandItem();
        if (offhand.getItem() instanceof BlockItem blockItem
                && (usesOffhandBlock(mode) || mode == TonkachiMode.SPACING || mode == TonkachiMode.RANDOM
                || mode == TonkachiMode.FRAME || mode == TonkachiMode.CYLINDER_FRAME)) {
            return blockItem.getBlock().defaultBlockState();
        }
        if (usesOffhandBlock(mode) || mode == TonkachiMode.SPACING || mode == TonkachiMode.RANDOM
                || mode == TonkachiMode.FRAME || mode == TonkachiMode.CYLINDER_FRAME || hitPos == null) {
            return null;
        }
        BlockState clickedState = level.getBlockState(hitPos);
        if (clickedState.getBlock().asItem() == Items.AIR) {
            return null;
        }
        return resetPlacementLifetime(clickedState);
    }

    private static List<PreviewBlock> previewFromPositions(Level level, BlockState state, List<BlockPos> positions, BlockPos anchor) {
        if (positions.isEmpty()) {
            return anchor == null ? List.of() : List.of(new PreviewBlock(anchor, true, true));
        }
        List<PreviewBlock> preview = new ArrayList<>(Math.min(positions.size(), PREVIEW_MAX_BLOCKS));
        int count = 0;
        for (BlockPos pos : positions) {
            if (count >= PREVIEW_MAX_BLOCKS) {
                break;
            }
            boolean placeable = state == null || canPlacePreview(level, pos, state);
            preview.add(new PreviewBlock(pos, placeable, anchor != null && pos.equals(anchor)));
            count++;
        }
        if (anchor != null && positions.stream().noneMatch(anchor::equals) && preview.size() < PREVIEW_MAX_BLOCKS) {
            preview.add(new PreviewBlock(anchor, true, true));
        }
        return preview;
    }

    private static boolean canPlacePreview(Level level, BlockPos pos, BlockState state) {
        BlockState existing = level.getBlockState(pos);
        return existing.canBeReplaced()
                && state.canSurvive(level, pos)
                && level.isInWorldBounds(pos);
    }

    private static int getSpacing(ItemStack stack) {
        CustomData data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        int spacing = data.copyTag().getInt(SPACING_KEY).orElse(MIN_SPACING);
        return Math.clamp(spacing, MIN_SPACING, MAX_SPACING);
    }

    private static void cycleSpacing(ItemStack stack, Player player) {
        int next = getSpacing(stack) >= MAX_SPACING ? MIN_SPACING : getSpacing(stack) + 1;
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putInt(SPACING_KEY, next));
        player.sendOverlayMessage(Component.translatable("message.tonten.spacing", next));
    }

    private static int getRandomSize(ItemStack stack) {
        CustomData data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        int size = data.copyTag().getInt(RANDOM_SIZE_KEY).orElse(RANDOM_SIZE_MIN);
        if (size <= RANDOM_SIZE_MIN) {
            return RANDOM_SIZE_MIN;
        }
        if (size <= RANDOM_SIZE_MID) {
            return RANDOM_SIZE_MID;
        }
        return RANDOM_SIZE_MAX;
    }

    private static void cycleRandomSize(ItemStack stack, Player player) {
        int current = getRandomSize(stack);
        int next = current == RANDOM_SIZE_MIN ? RANDOM_SIZE_MID : current == RANDOM_SIZE_MID ? RANDOM_SIZE_MAX : RANDOM_SIZE_MIN;
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putInt(RANDOM_SIZE_KEY, next));
        player.sendOverlayMessage(Component.translatable("message.tonten.random_size", next, next));
    }

    public static BlockPos getFrameStart(ItemStack stack) {
        return getStoredFramePos(stack, FRAME_START_X_KEY, FRAME_START_Y_KEY, FRAME_START_Z_KEY);
    }

    public static BlockPos getFrameEnd(ItemStack stack) {
        return getStoredFramePos(stack, FRAME_END_X_KEY, FRAME_END_Y_KEY, FRAME_END_Z_KEY);
    }

    public static BlockPos getFramePreviewAnchor(ItemStack stack) {
        return offsetPosition(getFrameStart(stack), getFrameFace(stack));
    }

    public static List<BlockPos> getFramePreviewPositions(ItemStack stack) {
        BlockPos start = getFrameStart(stack);
        BlockPos end = getFrameEnd(stack);
        if (start == null) {
            return List.of();
        }
        Direction face = getFrameFace(stack);
        if (end == null || !isValidFrameSelection(start, end)) {
            return List.of(offsetPosition(start, face));
        }
        return offsetPositions(framePositions(start, end), face);
    }

    public static BlockPos getCylinderCenter(ItemStack stack) {
        return getStoredFramePos(stack, CYLINDER_CENTER_X_KEY, CYLINDER_CENTER_Y_KEY, CYLINDER_CENTER_Z_KEY);
    }

    public static BlockPos getCylinderEnd(ItemStack stack) {
        return getStoredFramePos(stack, CYLINDER_END_X_KEY, CYLINDER_END_Y_KEY, CYLINDER_END_Z_KEY);
    }

    public static int getCylinderRadius(ItemStack stack) {
        CustomData data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        int radius = data.copyTag().getInt(CYLINDER_RADIUS_KEY).orElse(CYLINDER_DEFAULT_RADIUS);
        return Math.clamp(radius, CYLINDER_MIN_RADIUS, CYLINDER_MAX_RADIUS);
    }

    public static BlockPos getCylinderPreviewAnchor(ItemStack stack) {
        return offsetPosition(getCylinderCenter(stack), getCylinderFace(stack));
    }

    public static List<BlockPos> getCylinderPreviewPositions(ItemStack stack) {
        BlockPos center = getCylinderCenter(stack);
        BlockPos end = getCylinderEnd(stack);
        if (center == null) {
            return List.of();
        }
        Direction face = getCylinderFace(stack);
        if (end == null || !isValidCylinderSelection(center, end)) {
            return List.of(offsetPosition(center, face));
        }
        return offsetPositions(cylinderFramePositions(center, end, getCylinderRadius(stack)), face);
    }

    private static BlockPos getStoredFramePos(ItemStack stack, String xKey, String yKey, String zKey) {
        CustomData data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        CompoundTag tag = data.copyTag();
        if (!tag.contains(xKey) || !tag.contains(yKey) || !tag.contains(zKey)) {
            return null;
        }
        return new BlockPos(tag.getInt(xKey).orElse(0), tag.getInt(yKey).orElse(0), tag.getInt(zKey).orElse(0));
    }

    private static void setFrameStart(ItemStack stack, BlockPos pos, Direction face) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
            tag.putInt(FRAME_START_X_KEY, pos.getX());
            tag.putInt(FRAME_START_Y_KEY, pos.getY());
            tag.putInt(FRAME_START_Z_KEY, pos.getZ());
            tag.putInt(FRAME_FACE_KEY, face.ordinal());
        });
    }

    private static void setFrameEnd(ItemStack stack, BlockPos pos) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
            tag.putInt(FRAME_END_X_KEY, pos.getX());
            tag.putInt(FRAME_END_Y_KEY, pos.getY());
            tag.putInt(FRAME_END_Z_KEY, pos.getZ());
        });
    }

    private static void clearFrameSelection(ItemStack stack) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
            tag.remove(FRAME_START_X_KEY);
            tag.remove(FRAME_START_Y_KEY);
            tag.remove(FRAME_START_Z_KEY);
            tag.remove(FRAME_END_X_KEY);
            tag.remove(FRAME_END_Y_KEY);
            tag.remove(FRAME_END_Z_KEY);
            tag.remove(FRAME_FACE_KEY);
        });
    }

    private static void clearFrameEnd(ItemStack stack) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
            tag.remove(FRAME_END_X_KEY);
            tag.remove(FRAME_END_Y_KEY);
            tag.remove(FRAME_END_Z_KEY);
        });
    }

    private static void setCylinderCenter(ItemStack stack, BlockPos pos, Direction face) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
            tag.putInt(CYLINDER_CENTER_X_KEY, pos.getX());
            tag.putInt(CYLINDER_CENTER_Y_KEY, pos.getY());
            tag.putInt(CYLINDER_CENTER_Z_KEY, pos.getZ());
            tag.putInt(CYLINDER_FACE_KEY, face.ordinal());
        });
    }

    private static void setCylinderEnd(ItemStack stack, BlockPos pos) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
            tag.putInt(CYLINDER_END_X_KEY, pos.getX());
            tag.putInt(CYLINDER_END_Y_KEY, pos.getY());
            tag.putInt(CYLINDER_END_Z_KEY, pos.getZ());
        });
    }

    private static void setCylinderRadius(ItemStack stack, int radius) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putInt(CYLINDER_RADIUS_KEY, radius));
    }

    private static void clearCylinderSelection(ItemStack stack) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
            tag.remove(CYLINDER_CENTER_X_KEY);
            tag.remove(CYLINDER_CENTER_Y_KEY);
            tag.remove(CYLINDER_CENTER_Z_KEY);
            tag.remove(CYLINDER_END_X_KEY);
            tag.remove(CYLINDER_END_Y_KEY);
            tag.remove(CYLINDER_END_Z_KEY);
            tag.remove(CYLINDER_FACE_KEY);
        });
    }

    private static void clearCylinderEnd(ItemStack stack) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
            tag.remove(CYLINDER_END_X_KEY);
            tag.remove(CYLINDER_END_Y_KEY);
            tag.remove(CYLINDER_END_Z_KEY);
        });
    }

    private static Direction getFrameFace(ItemStack stack) {
        return getStoredDirection(stack, FRAME_FACE_KEY);
    }

    private static Direction getCylinderFace(ItemStack stack) {
        return getStoredDirection(stack, CYLINDER_FACE_KEY);
    }

    private static Direction getStoredDirection(ItemStack stack, String key) {
        CustomData data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        CompoundTag tag = data.copyTag();
        if (!tag.contains(key)) {
            return null;
        }
        int ordinal = tag.getInt(key).orElse(-1);
        Direction[] directions = Direction.values();
        if (ordinal < 0 || ordinal >= directions.length) {
            return null;
        }
        return directions[ordinal];
    }

    private static BlockPos offsetPosition(BlockPos pos, Direction face) {
        if (pos == null || face == null) {
            return pos;
        }
        return pos.relative(face);
    }

    private static List<BlockPos> offsetPositions(List<BlockPos> positions, Direction face) {
        if (face == null || positions.isEmpty()) {
            return positions;
        }
        List<BlockPos> shifted = new ArrayList<>(positions.size());
        for (BlockPos pos : positions) {
            shifted.add(pos.relative(face));
        }
        return shifted;
    }
    private static boolean isValidFrameSelection(BlockPos start, BlockPos end) {
        int dx = Math.abs(end.getX() - start.getX());
        int dy = Math.abs(end.getY() - start.getY());
        int dz = Math.abs(end.getZ() - start.getZ());
        boolean planar = dx == 0 || dy == 0 || dz == 0;
        return planar && dx <= FRAME_MAX_RANGE && dy <= FRAME_MAX_RANGE && dz <= FRAME_MAX_RANGE;
    }

    private static boolean isValidCylinderSelection(BlockPos center, BlockPos end) {
        return Math.abs(end.getY() - center.getY()) <= CYLINDER_MAX_HEIGHT;
    }

    private static List<BlockPos> framePositions(BlockPos start, BlockPos end) {
        List<BlockPos> positions = new ArrayList<>();
        int minX = Math.min(start.getX(), end.getX());
        int maxX = Math.max(start.getX(), end.getX());
        int minY = Math.min(start.getY(), end.getY());
        int maxY = Math.max(start.getY(), end.getY());
        int minZ = Math.min(start.getZ(), end.getZ());
        int maxZ = Math.max(start.getZ(), end.getZ());

        if (start.getY() == end.getY()) {
            int y = start.getY();
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    if (x == minX || x == maxX || z == minZ || z == maxZ) {
                        positions.add(new BlockPos(x, y, z));
                    }
                }
            }
            return positions;
        }
        if (start.getX() == end.getX()) {
            int x = start.getX();
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    if (y == minY || y == maxY || z == minZ || z == maxZ) {
                        positions.add(new BlockPos(x, y, z));
                    }
                }
            }
            return positions;
        }

        int z = start.getZ();
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                if (x == minX || x == maxX || y == minY || y == maxY) {
                    positions.add(new BlockPos(x, y, z));
                }
            }
        }
        return positions;
    }

    private static List<BlockPos> cylinderFramePositions(BlockPos center, BlockPos end, int radius) {
        Set<BlockPos> positions = new LinkedHashSet<>();
        int minY = Math.min(center.getY(), end.getY());
        int maxY = Math.max(center.getY(), end.getY());
        for (int y = minY; y <= maxY; y++) {
            for (int xOffset = -radius; xOffset <= radius; xOffset++) {
                for (int zOffset = -radius; zOffset <= radius; zOffset++) {
                    double distance = Math.sqrt(xOffset * xOffset + zOffset * zOffset);
                    if (Math.abs(distance - radius) <= 0.5D) {
                        positions.add(new BlockPos(center.getX() + xOffset, y, center.getZ() + zOffset));
                    }
                }
            }
        }
        return List.copyOf(positions);
    }
    private static InteractionResult trySpecialOffhandPlacement(UseOnContext context, ServerLevel level, Player player, ItemStack hammer, TonkachiMode mode) {
        if (mode != TonkachiMode.EXTEND && mode != TonkachiMode.AIR) {
            return InteractionResult.PASS;
        }

        Item offhandItem = player.getOffhandItem().getItem();
        if (canPlaceSpecialOffhandOnClickedFace(mode, offhandItem, hammer)) {
            BlockPos pos = context.getClickedPos().relative(context.getClickedFace());
            return placeOffhandBlockAt(level, player, hammer, context.getHand(), pos);
        }
        if (mode == TonkachiMode.EXTEND) {
            return placeViewedOffhandBlock(level, player, hammer, context.getHand(), mode);
        }
        return InteractionResult.PASS;
    }

    private static boolean canPlaceSpecialOffhandOnClickedFace(TonkachiMode mode, Item offhandItem, ItemStack hammer) {
        return mode == TonkachiMode.EXTEND && Tonten.isUtsusemiBlockItem(offhandItem)
                || Tonten.isSolidifySpaceBlockItem(offhandItem) && canUseSolidifySpace(mode, hammer);
    }

    public void cycleMode(ItemStack stack, Player player, int direction) {
        TonkachiMode next = getMode(stack).cycle(direction > 0 ? 1 : -1, this.tier);
        setMode(stack, next);
        if (next != TonkachiMode.FRAME) {
            clearFrameSelection(stack);
        }
        if (next != TonkachiMode.CYLINDER_FRAME) {
            clearCylinderSelection(stack);
        }
        player.sendOverlayMessage(Component.translatable("message.tonten.mode", next.displayName()));
    }

    @Override
    public boolean isPrimaryItemFor(ItemStack stack, Holder<Enchantment> enchantment) {
        return isAllowedEnchantment(enchantment);
    }

    @Override
    public boolean supportsEnchantment(ItemStack stack, Holder<Enchantment> enchantment) {
        return isAllowedEnchantment(enchantment);
    }

    private static boolean isAllowedEnchantment(Holder<Enchantment> enchantment) {
        return enchantment.is(Enchantments.UNBREAKING);
    }

    public static TonkachiMode getMode(ItemStack stack) {
        CustomData data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        CompoundTag tag = data.copyTag();
        return TonkachiMode.byOrdinal(tag.getInt(MODE_KEY).orElse(0));
    }

    private static void setMode(ItemStack stack, TonkachiMode mode) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putInt(MODE_KEY, mode.ordinal()));
    }

    private static boolean usesOffhandBlock(TonkachiMode mode) {
        return mode == TonkachiMode.EXTEND || mode == TonkachiMode.AIR;
    }

    private static InteractionResult placeViewedOffhandBlock(ServerLevel level, Player player, ItemStack hammer, InteractionHand hand, TonkachiMode mode) {
        ItemStack offhandStack = player.getOffhandItem();
        if (!(offhandStack.getItem() instanceof BlockItem blockItem)) {
            message(player, "message.tonten.no_offhand_block");
            return InteractionResult.FAIL;
        }
        boolean solidifySpace = Tonten.isSolidifySpaceBlockItem(offhandStack.getItem());
        if (solidifySpace && !canUseSolidifySpace(mode, hammer)) {
            message(player, "message.tonten.solidify_diamond_extend_only");
            return InteractionResult.FAIL;
        }

        BlockState placementState = blockItem.getBlock().defaultBlockState();
        boolean requireNeighbor = mode == TonkachiMode.EXTEND;
        BlockPos pos = findViewedPlacementPos(level, player, placementState, requireNeighbor);
        if (pos == null) {
            message(player, requireNeighbor ? "message.tonten.no_anchor_block" : "message.tonten.no_place");
            return InteractionResult.FAIL;
        }
        if (!placeSingleOffhandBlock(level, player, offhandStack, placementState, pos)) {
            message(player, "message.tonten.no_place");
            return InteractionResult.FAIL;
        }

        hammer.hurtAndBreak(1, player, hand);
        playTonkachiSound(level, pos, hammer);
        return InteractionResult.SUCCESS;
    }

    private static InteractionResult placeOffhandBlockAt(ServerLevel level, Player player, ItemStack hammer, InteractionHand hand, BlockPos pos) {
        ItemStack offhandStack = player.getOffhandItem();
        if (!(offhandStack.getItem() instanceof BlockItem blockItem)) {
            message(player, "message.tonten.no_offhand_block");
            return InteractionResult.FAIL;
        }

        BlockState placementState = blockItem.getBlock().defaultBlockState();
        if (!placeSingleOffhandBlock(level, player, offhandStack, placementState, pos)) {
            message(player, "message.tonten.no_place");
            return InteractionResult.FAIL;
        }

        hammer.hurtAndBreak(1, player, hand);
        playTonkachiSound(level, pos, hammer);
        return InteractionResult.SUCCESS;
    }

    private static boolean canUseSolidifySpace(TonkachiMode mode, ItemStack hammer) {
        return (mode == TonkachiMode.EXTEND || mode == TonkachiMode.AIR)
                && hammer.getItem() instanceof TonkachiItem tonkachi
                && tonkachi.tier() == TonkachiTier.DIAMOND;
    }

    private static BlockState resetPlacementLifetime(BlockState state) {
        if (state.is(Tonten.UTSUSEMI_BLOCK.get())) {
            return state.setValue(UtsusemiBlock.AGE, 0);
        }
        return state;
    }

    private static BlockPos findViewedPlacementPos(Level level, Player player, BlockState placementState, boolean requireNeighbor) {
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.calculateViewVector(player.getXRot(), player.getYRot());
        BlockPos lastChecked = null;
        BlockPos result = null;
        for (double distance = VIEW_PLACE_STEP; distance <= VIEW_PLACE_REACH; distance += VIEW_PLACE_STEP) {
            BlockPos pos = BlockPos.containing(eye.add(look.scale(distance)));
            if (pos.equals(lastChecked)) {
                continue;
            }
            lastChecked = pos;
            boolean placeable = level instanceof ServerLevel serverLevel
                    ? canPlace(serverLevel, pos, placementState)
                    : canPlacePreview(level, pos, placementState);
            if ((!requireNeighbor || hasNeighborBlock(level, pos)) && placeable) {
                result = pos;
            }
        }
        return result;
    }

    private PlacementPlan createPlan(UseOnContext context, TonkachiMode mode, BlockState placementState, Item itemToConsume) {
        Direction face = context.getClickedFace();
        List<BlockPos> positions = switch (mode) {
            case FLAT -> facePlanePositions(context.getClickedPos(), face, this.tier.flatSize());
            case STAIRS -> stairLinePositions(context, this.tier.lineLimit());
            case VERTICAL -> linePositions(context.getClickedPos(), face.getOpposite(), this.tier.lineLimit());
            case VERTICAL_UP -> linePositions(context.getClickedPos(), Direction.UP, this.tier.lineLimit());
            case VERTICAL_DOWN -> linePositions(context.getClickedPos(), Direction.DOWN, this.tier.lineLimit());
            case VERTICAL_LEFT -> linePositions(context.getClickedPos(), horizontalSideDirection(context, false), this.tier.lineLimit());
            case VERTICAL_RIGHT -> linePositions(context.getClickedPos(), horizontalSideDirection(context, true), this.tier.lineLimit());
            case EXTEND, SPACING, RANDOM, UPSIDE_DOWN, ROTATE, FRAME, AIR, CYLINDER_FRAME -> List.of(context.getClickedPos().relative(face));
        };
        boolean lineMode = isLineMode(mode);
        List<BlockPos> orderedPositions = lineMode ? positions : orderByDistanceFromCenter(positions, context.getClickedPos());
        return new PlacementPlan(placementState, orderedPositions, usesOffhandBlock(mode), itemToConsume, lineMode ? 1 : Integer.MAX_VALUE);
    }

    private static boolean isLineMode(TonkachiMode mode) {
        return mode == TonkachiMode.STAIRS
                || mode == TonkachiMode.VERTICAL
                || mode == TonkachiMode.VERTICAL_UP
                || mode == TonkachiMode.VERTICAL_DOWN
                || mode == TonkachiMode.VERTICAL_LEFT
                || mode == TonkachiMode.VERTICAL_RIGHT
                || mode == TonkachiMode.EXTEND
                || mode == TonkachiMode.SPACING
                || mode == TonkachiMode.RANDOM
                || mode == TonkachiMode.UPSIDE_DOWN
                || mode == TonkachiMode.ROTATE
                || mode == TonkachiMode.AIR;
    }

    private static BlockState upsideDown(BlockState state) {
        BlockState changed = trySetEnumByName(state, "half", "top", "bottom");
        if (changed != state) {
            return changed;
        }
        changed = trySetEnumByName(state, "type", "top", "bottom");
        if (changed != state) {
            return changed;
        }
        changed = trySetEnumByName(state, "vertical_direction", "up", "down");
        if (changed != state) {
            return changed;
        }
        return toggleDirection(state, "facing", Direction.UP, Direction.DOWN);
    }

    private static BlockState rotate(BlockState state) {
        BlockState changed = rotateHorizontalDirection(state, "facing");
        if (changed != state) {
            return changed;
        }
        changed = rotateHorizontalDirection(state, "horizontal_facing");
        if (changed != state) {
            return changed;
        }
        changed = trySetEnumByName(state, "axis", "x", "z");
        if (changed != state) {
            return changed;
        }
        return trySetEnumByName(state, "horizontal_axis", "x", "z");
    }

    private static BlockState rotateHorizontalDirection(BlockState state, String propertyName) {
        Direction direction = getDirectionValue(state, propertyName);
        if (direction == null || direction.getAxis().isVertical()) {
            return state;
        }
        return setPropertyValue(state, propertyName, direction.getClockWise());
    }

    private static BlockState toggleDirection(BlockState state, String propertyName, Direction first, Direction second) {
        Direction direction = getDirectionValue(state, propertyName);
        if (direction == first) {
            return setPropertyValue(state, propertyName, second);
        }
        if (direction == second) {
            return setPropertyValue(state, propertyName, first);
        }
        return state;
    }

    private static Direction getDirectionValue(BlockState state, String propertyName) {
        Property<?> property = findProperty(state, propertyName);
        if (property == null || !(state.getValue(property) instanceof Direction direction)) {
            return null;
        }
        return direction;
    }

    private static BlockState trySetEnumByName(BlockState state, String propertyName, String first, String second) {
        Property<?> property = findProperty(state, propertyName);
        if (property == null) {
            return state;
        }

        Comparable<?> currentValue = state.getValue(property);
        String currentName = getPropertyValueName(property, currentValue);
        if (currentName.equals(first)) {
            return setPropertyByName(state, property, second);
        }
        if (currentName.equals(second)) {
            return setPropertyByName(state, property, first);
        }
        return state;
    }

    private static Property<?> findProperty(BlockState state, String name) {
        for (Property<?> property : state.getProperties()) {
            if (property.getName().equals(name)) {
                return property;
            }
        }
        return null;
    }

    private static BlockState setPropertyByName(BlockState state, Property<?> property, String name) {
        for (Comparable<?> value : property.getPossibleValues()) {
            if (getPropertyValueName(property, value).equals(name)) {
                return setPropertyValue(state, property, value);
            }
        }
        return state;
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private static String getPropertyValueName(Property property, Comparable value) {
        return property.getName(value);
    }

    private static BlockState setPropertyValue(BlockState state, String propertyName, Comparable<?> value) {
        Property<?> property = findProperty(state, propertyName);
        if (property == null) {
            return state;
        }
        return setPropertyValue(state, property, value);
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private static BlockState setPropertyValue(BlockState state, Property property, Comparable value) {
        return state.setValue(property, value);
    }

    private static List<BlockPos> stairLinePositions(UseOnContext context, int limit) {
        BlockPos origin = context.getClickedPos();
        Player player = context.getPlayer();
        Direction horizontal = horizontalAwayFromPlayer(player, origin, context.getHorizontalDirection());
        int yStep = player != null && player.getY() > origin.getY() + 0.5D ? -1 : 1;
        return stairLinePositions(origin, player, horizontal, limit, yStep);
    }

    private static List<BlockPos> stairLinePositions(BlockPos origin, Player player, Direction fallback, int limit) {
        Direction horizontal = horizontalAwayFromPlayer(player, origin, fallback);
        int yStep = player != null && player.getY() > origin.getY() + 0.5D ? -1 : 1;
        return stairLinePositions(origin, player, horizontal, limit, yStep);
    }

    private static List<BlockPos> stairLinePositions(BlockPos origin, Player player, Direction horizontal, int limit, int yStep) {
        List<BlockPos> positions = new ArrayList<>(limit);
        for (int distance = 1; distance <= limit; distance++) {
            positions.add(origin.relative(horizontal, distance).above(yStep * distance));
        }
        return positions;
    }

    private static Direction horizontalAwayFromPlayer(UseOnContext context) {
        return horizontalAwayFromPlayer(context.getPlayer(), context.getClickedPos(), context.getHorizontalDirection());
    }

    private static Direction horizontalSideDirection(UseOnContext context, boolean right) {
        return horizontalSideDirection(context.getPlayer(), context.getHorizontalDirection(), right);
    }

    private static Direction horizontalSideDirection(Player player, Direction fallback, boolean right) {
        Direction facing = player != null ? player.getDirection() : fallback;
        return right ? facing.getClockWise() : facing.getCounterClockWise();
    }

    private static Direction horizontalAwayFromPlayer(Player player, BlockPos origin, Direction fallback) {
        if (player == null) {
            return fallback;
        }

        double dx = player.getX() - (origin.getX() + 0.5D);
        double dz = player.getZ() - (origin.getZ() + 0.5D);
        if (Math.abs(dx) >= Math.abs(dz)) {
            return dx >= 0.0D ? Direction.WEST : Direction.EAST;
        }
        return dz >= 0.0D ? Direction.NORTH : Direction.SOUTH;
    }

    private static List<BlockPos> linePositions(BlockPos origin, Direction direction, int limit) {
        List<BlockPos> positions = new ArrayList<>(limit);
        for (int distance = 1; distance <= limit; distance++) {
            positions.add(origin.relative(direction, distance));
        }
        return positions;
    }

    private static List<BlockPos> spacingPositions(BlockPos origin, Direction direction, int spacing) {
        int step = spacing + 1;
        List<BlockPos> positions = new ArrayList<>(SPACING_DISTANCE / step);
        for (int distance = step; distance <= SPACING_DISTANCE; distance += step) {
            positions.add(origin.relative(direction, distance));
        }
        return positions;
    }

    private static List<BlockPos> randomPreviewPositions(BlockPos origin, Direction face, int size) {
        List<BlockPos> positions = facePlanePositions(origin, face, size);
        long seed = origin.asLong() ^ ((long) size << 32) ^ ((long) face.ordinal() << 48);
        positions.sort((left, right) -> Long.compare(mixBlockPos(left, seed), mixBlockPos(right, seed)));
        return positions;
    }

    private static long mixBlockPos(BlockPos pos, long seed) {
        long value = pos.asLong() ^ seed;
        value ^= value >>> 33;
        value *= 0xff51afd7ed558ccdL;
        value ^= value >>> 33;
        value *= 0xc4ceb9fe1a85ec53L;
        value ^= value >>> 33;
        return value;
    }

    private static List<BlockPos> facePlanePositions(BlockPos origin, Direction face, int size) {
        int radius = size / 2;
        List<BlockPos> positions = new ArrayList<>(size * size);
        if (face.getAxis().isVertical()) {
            for (int x = -radius; x <= radius; x++) {
                for (int z = -radius; z <= radius; z++) {
                    positions.add(origin.offset(x, 0, z));
                }
            }
            return positions;
        }

        Direction horizontal = face.getClockWise();
        for (int h = -radius; h <= radius; h++) {
            for (int y = -radius; y <= radius; y++) {
                positions.add(origin.relative(horizontal, h).above(y));
            }
        }
        return positions;
    }

    private static List<BlockPos> orderByDistanceFromCenter(List<BlockPos> positions, BlockPos origin) {
        positions.sort((left, right) -> Double.compare(left.distSqr(origin), right.distSqr(origin)));
        return positions;
    }

    private int placePlannedBlocks(ServerLevel level, Player player, ItemStack offhandPlacementStack, PlacementPlan plan) {
        int placed = 0;
        boolean creative = player.getAbilities().instabuild;
        ItemStack consumeStack = plan.offhandMode() ? offhandPlacementStack : ItemStack.EMPTY;
        for (BlockPos pos : plan.positions()) {
            if (!creative) {
                if (consumeStack.isEmpty()) {
                    consumeStack = plan.offhandMode() ? offhandPlacementStack : findMatchingBlockStack(player, plan.itemToConsume());
                }
                if (consumeStack.isEmpty()) {
                    break;
                }
            }
            if (!canPlace(level, pos, plan.state())) {
                continue;
            }
            level.setBlock(pos, plan.state(), PLACE_FLAGS);
            if (!creative) {
                consumePlacementStack(level, player, consumeStack);
            }
            placed++;
            if (placed >= plan.maxPlacements()) {
                break;
            }
        }
        return placed;
    }

    private static boolean placeSingleOffhandBlock(ServerLevel level, Player player, ItemStack offhandStack, BlockState state, BlockPos pos) {
        if (!canPlace(level, pos, state)) {
            return false;
        }
        level.setBlock(pos, state, PLACE_FLAGS);
        if (!player.getAbilities().instabuild) {
            consumePlacementStack(level, player, offhandStack);
        }
        return true;
    }

    private static InteractionResult placeSpacingBlocks(UseOnContext context, ServerLevel level, Player player, ItemStack hammer) {
        ItemStack offhandStack = player.getOffhandItem();
        if (!(offhandStack.getItem() instanceof BlockItem blockItem)) {
            message(player, "message.tonten.no_offhand_block");
            return InteractionResult.FAIL;
        }
        if (Tonten.isSolidifySpaceBlockItem(offhandStack.getItem())) {
            message(player, "message.tonten.solidify_diamond_extend_only");
            return InteractionResult.FAIL;
        }

        BlockState placementState = blockItem.getBlock().defaultBlockState();
        List<BlockPos> positions = spacingPositions(context.getClickedPos(), context.getClickedFace().getOpposite(), getSpacing(hammer));
        for (BlockPos pos : positions) {
            if (!placeSingleOffhandBlock(level, player, offhandStack, placementState, pos)) {
                continue;
            }
            hammer.hurtAndBreak(1, player, context.getHand());
            playTonkachiSound(level, pos, hammer);
            return InteractionResult.SUCCESS;
        }

        message(player, "message.tonten.no_place");
        return InteractionResult.FAIL;
    }

    private static InteractionResult placeRandomBlocks(UseOnContext context, ServerLevel level, Player player, ItemStack hammer) {
        ItemStack offhandStack = player.getOffhandItem();
        if (!(offhandStack.getItem() instanceof BlockItem blockItem)) {
            message(player, "message.tonten.no_offhand_block");
            return InteractionResult.FAIL;
        }
        if (Tonten.isSolidifySpaceBlockItem(offhandStack.getItem())) {
            message(player, "message.tonten.solidify_diamond_extend_only");
            return InteractionResult.FAIL;
        }

        BlockState placementState = blockItem.getBlock().defaultBlockState();
        List<BlockPos> positions = facePlanePositions(context.getClickedPos(), context.getClickedFace(), getRandomSize(hammer));
        shuffle(positions, level.getRandom());
        for (BlockPos pos : positions) {
            if (!level.getBlockState(pos).isAir()) {
                continue;
            }
            if (!placeSingleOffhandBlock(level, player, offhandStack, placementState, pos)) {
                continue;
            }
            hammer.hurtAndBreak(1, player, context.getHand());
            playTonkachiSound(level, pos, hammer);
            return InteractionResult.SUCCESS;
        }

        message(player, "message.tonten.no_place");
        return InteractionResult.FAIL;
    }

    private static void shuffle(List<BlockPos> positions, RandomSource random) {
        for (int i = positions.size() - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            BlockPos swap = positions.get(i);
            positions.set(i, positions.get(j));
            positions.set(j, swap);
        }
    }

    private static void consumePlacementStack(ServerLevel level, Player player, ItemStack stack) {
        if (Tonten.isUtsusemiBlockItem(stack.getItem()) || Tonten.isSolidifySpaceBlockItem(stack.getItem())) {
            stack.hurtAndBreak(1, level, player, ignored -> {
            });
        } else {
            stack.shrink(1);
        }
    }

    private static boolean hasNeighborBlock(Level level, BlockPos pos) {
        for (Direction direction : Direction.values()) {
            if (!level.getBlockState(pos.relative(direction)).canBeReplaced()) {
                return true;
            }
        }
        return false;
    }

    private static ItemStack findMatchingBlockStack(Player player, Item item) {
        for (ItemStack stack : player.getInventory().getNonEquipmentItems()) {
            if (!stack.isEmpty() && stack.is(item)) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }

    private static boolean canPlace(ServerLevel level, BlockPos pos, BlockState state) {
        BlockState existing = level.getBlockState(pos);
        return existing.canBeReplaced()
                && state.canSurvive(level, pos)
                && level.isUnobstructed(state, pos, CollisionContext.empty());
    }

    private static void playTonkachiSound(Level level, BlockPos pos, ItemStack stack) {
        long now = level.getGameTime();
        long last = getLastSoundTick(stack);
        int step = now - last > RHYTHM_RESET_TICKS ? 0 : getSoundStep(stack);
        TonkachiTone tone = TonkachiTone.forStep(step);
        setSoundState(stack, step + 1, now);
        level.playSound(null, pos, tone.sound(), SoundSource.BLOCKS, tone.volume(), tone.pitch());
    }

    private static int getSoundStep(ItemStack stack) {
        CustomData data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        return data.copyTag().getInt(SOUND_KEY).orElse(0);
    }

    private static long getLastSoundTick(ItemStack stack) {
        CustomData data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        return data.copyTag().getLong(LAST_SOUND_TICK_KEY).orElse(-RHYTHM_RESET_TICKS);
    }

    private static void setSoundState(ItemStack stack, int step, long tick) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
            tag.putInt(SOUND_KEY, step);
            tag.putLong(LAST_SOUND_TICK_KEY, tick);
        });
    }

    private static void message(Player player, String key) {
        player.sendOverlayMessage(Component.translatable(key));
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag) {
        tooltip.accept(Component.translatable("item.tonten.tonkachi.desc").withStyle(ChatFormatting.GRAY));
        tooltip.accept(Component.translatable("item.tonten.tonkachi.mode", getMode(stack).displayName()).withStyle(ChatFormatting.DARK_AQUA));
        if (this.tier.flatSize() > 1) {
            tooltip.accept(Component.translatable("item.tonten.tonkachi.flat_range", this.tier.flatSize(), this.tier.flatSize()).withStyle(ChatFormatting.BLUE));
        }
        tooltip.accept(Component.translatable("item.tonten.tonkachi.line_limit", this.tier.lineLimit()).withStyle(ChatFormatting.BLUE));
        if (this.tier == TonkachiTier.COPPER) {
            tooltip.accept(Component.translatable("item.tonten.tonkachi.spacing", getSpacing(stack)).withStyle(ChatFormatting.GOLD));
            tooltip.accept(Component.translatable("item.tonten.tonkachi.spacing_hint").withStyle(ChatFormatting.GRAY));
            tooltip.accept(Component.translatable("item.tonten.tonkachi.random_size", getRandomSize(stack), getRandomSize(stack)).withStyle(ChatFormatting.GOLD));
            tooltip.accept(Component.translatable("item.tonten.tonkachi.random_hint").withStyle(ChatFormatting.GRAY));
        }
    }

    private record PlacementPlan(BlockState state, List<BlockPos> positions, boolean offhandMode, Item itemToConsume, int maxPlacements) {
    }

    public record PreviewBlock(BlockPos pos, boolean placeable, boolean anchor) {
    }

    private enum TonkachiTone {
        TON(SoundEvents.BAMBOO_WOOD_HIT, 0.48F, 0.86F),
        TEN(SoundEvents.CHERRY_WOOD_HIT, 0.34F, 1.38F),
        KAN(SoundEvents.BAMBOO_WOOD_BUTTON_CLICK_ON, 0.28F, 1.68F);

        private final net.minecraft.sounds.SoundEvent sound;
        private final float volume;
        private final float pitch;

        TonkachiTone(net.minecraft.sounds.SoundEvent sound, float volume, float pitch) {
            this.sound = sound;
            this.volume = volume;
            this.pitch = pitch;
        }

        static TonkachiTone forStep(int step) {
            return switch (Math.floorMod(step, 4)) {
                case 0 -> TON;
                case 1, 3 -> TEN;
                default -> KAN;
            };
        }

        net.minecraft.sounds.SoundEvent sound() {
            return this.sound;
        }

        float volume() {
            return this.volume;
        }

        float pitch() {
            return this.pitch;
        }
    }
}


