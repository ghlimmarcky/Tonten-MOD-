package com.tonten.tonten;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

public class TonkachiItem extends Item {
    private static final String MODE_KEY = "TontenMode";
    private static final String SPACING_KEY = "TontenSpacing";
    private static final String RANDOM_SIZE_KEY = "TontenRandomSize";
    private static final String FRAME_X_KEY = "TontenFrameX";
    private static final String FRAME_Y_KEY = "TontenFrameY";
    private static final String FRAME_Z_KEY = "TontenFrameZ";
    private static final String FRAME_DIMENSION_KEY = "TontenFrameDimension";
    private static final String FRAME_AIR_RESET_KEY = "TontenFrameAirReset";
    private static final String SOUND_KEY = "TontenSound";
    private static final String LAST_SOUND_TICK_KEY = "TontenLastSoundTick";
    private static final long RHYTHM_RESET_TICKS = 16L;
    private static final double VIEW_PLACE_REACH = 2.0D;
    private static final double VIEW_PLACE_STEP = 0.25D;
    private static final int MIN_SPACING = 1;
    private static final int MAX_SPACING = 5;
    private static final int SPACING_DISTANCE = 30;
    private static final int RANDOM_SIZE_MIN = 3;
    private static final int RANDOM_SIZE_MID = 5;
    private static final int RANDOM_SIZE_MAX = 9;
    private static final int FRAME_MAX_RANGE = 30;
    private static final int PLACE_FLAGS = 11;
    private final TonkachiTier tier;

    public TonkachiItem(TonkachiTier tier, Properties properties) {
        super(properties);
        this.tier = tier;
    }

    public TonkachiTier tier() {
        return this.tier;
    }

    private static InteractionResultHolder<ItemStack> resultHolder(InteractionResult result, ItemStack stack) {
        if (result == InteractionResult.FAIL) {
            return InteractionResultHolder.fail(stack);
        }
        if (result == InteractionResult.PASS) {
            return InteractionResultHolder.pass(stack);
        }
        return InteractionResultHolder.success(stack);
    }

    private static void hurtHammer(ItemStack hammer, Player player, InteractionHand hand) {
        hammer.hurtAndBreak(1, player, brokenPlayer -> brokenPlayer.broadcastBreakEvent(hand));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack hammer = player.getItemInHand(hand);
        TonkachiMode mode = getMode(hammer);
        if (hand == InteractionHand.MAIN_HAND && mode == TonkachiMode.FRAME) {
            if (level.isClientSide()) {
                return InteractionResultHolder.success(hammer);
            }
            return resultHolder(handleFrameAirReset(player, hammer), hammer);
        }
        if (hand != InteractionHand.MAIN_HAND || (mode != TonkachiMode.EXTEND && mode != TonkachiMode.AIR)) {
            return InteractionResultHolder.pass(hammer);
        }
        if (mode == TonkachiMode.AIR && !this.tier.canAirPlace()) {
            return InteractionResultHolder.pass(hammer);
        }
        if (level.isClientSide()) {
            return InteractionResultHolder.success(hammer);
        }

        return resultHolder(placeViewedOffhandBlock((ServerLevel) level, player, hammer, hand, mode), hammer);
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slotId, boolean isSelected) {
        if (entity instanceof Player player && hasFrameStart(stack) && player.getMainHandItem() != stack) {
            clearFrameStart(stack);
            return;
        }
        if (level instanceof ServerLevel serverLevel && entity instanceof Player player && player.getMainHandItem() == stack) {
            showFrameStartMarker(stack, serverLevel);
        }
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
        InteractionResult specialPlacement = trySpecialOffhandPlacement(context, serverLevel, player, hammer, mode);
        if (specialPlacement != InteractionResult.PASS) {
            return specialPlacement;
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
        if (mode == TonkachiMode.FRAME) {
            return handleFrameMode(context, serverLevel, player, hammer);
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
            hurtHammer(hammer, player, context.getHand());
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

        hurtHammer(hammer, player, context.getHand());
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
        } else if ((mode == TonkachiMode.SPACING || mode == TonkachiMode.RANDOM) && this.tier != TonkachiTier.COPPER) {
            mode = TonkachiMode.VERTICAL;
            setMode(hammer, mode);
        } else if (mode == TonkachiMode.FRAME && this.tier != TonkachiTier.GOLD) {
            mode = TonkachiMode.VERTICAL;
            setMode(hammer, mode);
        } else if (mode == TonkachiMode.AIR && !this.tier.canAirPlace()) {
            mode = TonkachiMode.FLAT;
            setMode(hammer, mode);
        }
        return mode;
    }

    private static InteractionResult trySpecialOffhandPlacement(UseOnContext context, ServerLevel level, Player player, ItemStack hammer, TonkachiMode mode) {
        if (mode != TonkachiMode.EXTEND && mode != TonkachiMode.AIR) {
            return InteractionResult.PASS;
        }

        Item offhandItem = player.getOffhandItem().getItem();
        if (canPlaceSpecialOffhandOnClickedFace(mode, offhandItem, hammer)) {
            BlockPos pos = context.getClickedPos().relative(context.getClickedFace());
            if (!isWithinViewPlaceReach(player, pos)) {
                message(player, "message.tonten.no_place");
                return InteractionResult.FAIL;
            }
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
        player.displayClientMessage(Component.translatable("message.tonten.mode", next.displayName()), true);
    }

    @Override
    public int getEnchantmentValue(ItemStack stack) {
        return switch (this.tier) {
            case WOOD -> 15;
            case STONE -> 5;
            case COPPER -> 14;
            case IRON -> 14;
            case GOLD -> 22;
            case DIAMOND -> 10;
        };
    }

    @Override
    public boolean isValidRepairItem(ItemStack stack, ItemStack repairCandidate) {
        return switch (this.tier) {
            case WOOD -> repairCandidate.is(net.minecraft.tags.ItemTags.LOGS);
            case STONE -> repairCandidate.is(Items.COBBLESTONE);
            case COPPER -> repairCandidate.is(Items.COPPER_INGOT);
            case IRON -> repairCandidate.is(Items.IRON_INGOT);
            case GOLD -> repairCandidate.is(Items.GOLD_INGOT);
            case DIAMOND -> repairCandidate.is(Items.DIAMOND);
        };
    }

    public static TonkachiMode getMode(ItemStack stack) {
        return TonkachiMode.byOrdinal(getTontenTag(stack).getInt(MODE_KEY));
    }

    private static void setMode(ItemStack stack, TonkachiMode mode) {
        getTontenTag(stack).putInt(MODE_KEY, mode.ordinal());
    }

    private static int getSpacing(ItemStack stack) {
        CompoundTag tag = getTontenTag(stack);
        int spacing = tag.contains(SPACING_KEY) ? tag.getInt(SPACING_KEY) : MIN_SPACING;
        return Math.max(MIN_SPACING, Math.min(MAX_SPACING, spacing));
    }

    private static void cycleSpacing(ItemStack stack, Player player) {
        int next = getSpacing(stack) >= MAX_SPACING ? MIN_SPACING : getSpacing(stack) + 1;
        getTontenTag(stack).putInt(SPACING_KEY, next);
        player.displayClientMessage(Component.translatable("message.tonten.spacing", next), true);
    }

    private static int getRandomSize(ItemStack stack) {
        CompoundTag tag = getTontenTag(stack);
        int size = tag.contains(RANDOM_SIZE_KEY) ? tag.getInt(RANDOM_SIZE_KEY) : RANDOM_SIZE_MIN;
        return switch (size) {
            case RANDOM_SIZE_MID -> RANDOM_SIZE_MID;
            case RANDOM_SIZE_MAX -> RANDOM_SIZE_MAX;
            default -> RANDOM_SIZE_MIN;
        };
    }

    private static void cycleRandomSize(ItemStack stack, Player player) {
        int current = getRandomSize(stack);
        int next = current == RANDOM_SIZE_MIN ? RANDOM_SIZE_MID : current == RANDOM_SIZE_MID ? RANDOM_SIZE_MAX : RANDOM_SIZE_MIN;
        getTontenTag(stack).putInt(RANDOM_SIZE_KEY, next);
        player.displayClientMessage(Component.translatable("message.tonten.random_size", next, next), true);
    }

    private static InteractionResult handleFrameMode(UseOnContext context, ServerLevel level, Player player, ItemStack hammer) {
        ItemStack offhandStack = player.getOffhandItem();
        if (!(offhandStack.getItem() instanceof BlockItem)) {
            message(player, "message.tonten.no_offhand_block");
            return InteractionResult.FAIL;
        }
        if (Tonten.isSolidifySpaceBlockItem(offhandStack.getItem())) {
            message(player, "message.tonten.solidify_diamond_extend_only");
            return InteractionResult.FAIL;
        }

        BlockPos clickedPos = context.getClickedPos();
        String dimension = level.dimension().toString();
        clearFrameAirReset(hammer);
        FrameStart start = getFrameStart(hammer, dimension);
        if (start == null) {
            setFrameStart(hammer, clickedPos, dimension);
            player.displayClientMessage(Component.translatable("message.tonten.frame_start"), true);
            return InteractionResult.SUCCESS;
        }

        InteractionResult result = placeFrameBlocks(context, level, player, hammer, start.pos(), clickedPos);
        if (result == InteractionResult.SUCCESS) {
            clearFrameStart(hammer);
        }
        return result;
    }

    private static void showFrameStartMarker(ItemStack stack, ServerLevel level) {
        if (level.getGameTime() % 8L != 0L) {
            return;
        }
        FrameStart start = getFrameStart(stack, level.dimension().toString());
        if (start == null) {
            return;
        }
        BlockPos pos = start.pos();
        level.sendParticles(ParticleTypes.END_ROD, pos.getX() + 0.5D, pos.getY() + 0.55D, pos.getZ() + 0.5D, 2, 0.22D, 0.22D, 0.22D, 0.0D);
    }

    private static InteractionResult handleFrameAirReset(Player player, ItemStack hammer) {
        if (!hasFrameStart(hammer)) {
            return InteractionResult.PASS;
        }
        if (isFrameAirResetArmed(hammer)) {
            clearFrameStart(hammer);
            player.displayClientMessage(Component.translatable("message.tonten.frame_reset"), true);
            return InteractionResult.SUCCESS;
        }

        getTontenTag(hammer).putBoolean(FRAME_AIR_RESET_KEY, true);
        player.displayClientMessage(Component.translatable("message.tonten.frame_reset_confirm"), true);
        return InteractionResult.SUCCESS;
    }

    private static boolean hasFrameStart(ItemStack stack) {
        CompoundTag tag = getTontenTag(stack);
        return tag.contains(FRAME_X_KEY) && tag.contains(FRAME_Y_KEY) && tag.contains(FRAME_Z_KEY);
    }

    private static boolean isFrameAirResetArmed(ItemStack stack) {
        CompoundTag tag = getTontenTag(stack);
        return tag.contains(FRAME_AIR_RESET_KEY) && tag.getBoolean(FRAME_AIR_RESET_KEY);
    }

    private static FrameStart getFrameStart(ItemStack stack, String dimension) {
        CompoundTag tag = getTontenTag(stack);
        String savedDimension = tag.contains(FRAME_DIMENSION_KEY) ? tag.getString(FRAME_DIMENSION_KEY) : "";
        if (!dimension.equals(savedDimension)) {
            return null;
        }
        Integer x = tag.contains(FRAME_X_KEY) ? tag.getInt(FRAME_X_KEY) : null;
        Integer y = tag.contains(FRAME_Y_KEY) ? tag.getInt(FRAME_Y_KEY) : null;
        Integer z = tag.contains(FRAME_Z_KEY) ? tag.getInt(FRAME_Z_KEY) : null;
        if (x == null || y == null || z == null) {
            return null;
        }
        return new FrameStart(new BlockPos(x, y, z));
    }

    private static void setFrameStart(ItemStack stack, BlockPos pos, String dimension) {
        CompoundTag tag = getTontenTag(stack);
        tag.putInt(FRAME_X_KEY, pos.getX());
        tag.putInt(FRAME_Y_KEY, pos.getY());
        tag.putInt(FRAME_Z_KEY, pos.getZ());
        tag.putString(FRAME_DIMENSION_KEY, dimension);
        tag.remove(FRAME_AIR_RESET_KEY);
    }

    private static void clearFrameStart(ItemStack stack) {
        CompoundTag tag = getTontenTag(stack);
        tag.remove(FRAME_X_KEY);
        tag.remove(FRAME_Y_KEY);
        tag.remove(FRAME_Z_KEY);
        tag.remove(FRAME_DIMENSION_KEY);
        tag.remove(FRAME_AIR_RESET_KEY);
    }

    private static void clearFrameAirReset(ItemStack stack) {
        getTontenTag(stack).remove(FRAME_AIR_RESET_KEY);
    }

    private static boolean usesOffhandBlock(TonkachiMode mode) {
        return mode == TonkachiMode.EXTEND || mode == TonkachiMode.AIR;
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

        int spacing = getSpacing(hammer);
        int step = spacing + 1;
        BlockState placementState = blockItem.getBlock().defaultBlockState();
        Direction direction = context.getClickedFace().getOpposite();
        BlockPos origin = context.getClickedPos();
        for (int distance = step; distance <= SPACING_DISTANCE; distance += step) {
            BlockPos pos = origin.relative(direction, distance);
            if (!placeSingleOffhandBlock(level, player, offhandStack, placementState, pos)) {
                continue;
            }
            hurtHammer(hammer, player, context.getHand());
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
            hurtHammer(hammer, player, context.getHand());
            playTonkachiSound(level, pos, hammer);
            return InteractionResult.SUCCESS;
        }

        message(player, "message.tonten.no_place");
        return InteractionResult.FAIL;
    }

    private static InteractionResult placeFrameBlocks(UseOnContext context, ServerLevel level, Player player, ItemStack hammer, BlockPos start, BlockPos end) {
        if (Math.abs(start.getX() - end.getX()) > FRAME_MAX_RANGE
                || Math.abs(start.getY() - end.getY()) > FRAME_MAX_RANGE
                || Math.abs(start.getZ() - end.getZ()) > FRAME_MAX_RANGE) {
            message(player, "message.tonten.frame_invalid");
            return InteractionResult.FAIL;
        }
        if (!isFramePlaneValid(start, end)) {
            message(player, "message.tonten.frame_invalid");
            return InteractionResult.FAIL;
        }

        ItemStack offhandStack = player.getOffhandItem();
        if (!(offhandStack.getItem() instanceof BlockItem blockItem)) {
            message(player, "message.tonten.no_offhand_block");
            return InteractionResult.FAIL;
        }

        BlockState placementState = blockItem.getBlock().defaultBlockState();
        BlockPos placementStart = start.above();
        BlockPos placementEnd = end.above();
        List<BlockPos> positions = framePositions(placementStart, placementEnd);
        int placed = 0;
        for (BlockPos pos : positions) {
            if (!player.getAbilities().instabuild && offhandStack.isEmpty()) {
                break;
            }
            if (!placeSingleOffhandBlock(level, player, offhandStack, placementState, pos)) {
                continue;
            }
            placed++;
        }

        if (placed <= 0) {
            message(player, "message.tonten.no_place");
            return InteractionResult.FAIL;
        }

        hurtHammer(hammer, player, context.getHand());
        playTonkachiSound(level, placementEnd, hammer);
        return InteractionResult.SUCCESS;
    }

    private static List<BlockPos> framePositions(BlockPos start, BlockPos end) {
        int minX = Math.min(start.getX(), end.getX());
        int minY = Math.min(start.getY(), end.getY());
        int minZ = Math.min(start.getZ(), end.getZ());
        int maxX = Math.max(start.getX(), end.getX());
        int maxY = Math.max(start.getY(), end.getY());
        int maxZ = Math.max(start.getZ(), end.getZ());
        List<BlockPos> positions = new ArrayList<>();
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
        if (start.getZ() == end.getZ()) {
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

    private static boolean isFramePlaneValid(BlockPos start, BlockPos end) {
        return start.getX() == end.getX() || start.getY() == end.getY() || start.getZ() == end.getZ();
    }

    private static void shuffle(List<BlockPos> positions, RandomSource random) {
        for (int i = positions.size() - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            BlockPos swap = positions.get(i);
            positions.set(i, positions.get(j));
            positions.set(j, swap);
        }
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

        hurtHammer(hammer, player, hand);
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

        hurtHammer(hammer, player, hand);
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

    private static BlockPos findViewedPlacementPos(ServerLevel level, Player player, BlockState placementState, boolean requireNeighbor) {
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        BlockPos lastChecked = null;
        BlockPos result = null;
        for (double distance = VIEW_PLACE_STEP; distance <= VIEW_PLACE_REACH; distance += VIEW_PLACE_STEP) {
            BlockPos pos = BlockPos.containing(eye.add(look.scale(distance)));
            if (pos.equals(lastChecked)) {
                continue;
            }
            lastChecked = pos;
            if ((!requireNeighbor || hasNeighborBlock(level, pos)) && canPlace(level, pos, placementState)) {
                result = pos;
            }
        }
        return result;
    }

    private static boolean isWithinViewPlaceReach(Player player, BlockPos pos) {
        Vec3 eye = player.getEyePosition();
        double dx = distanceOutside(eye.x, pos.getX(), pos.getX() + 1.0D);
        double dy = distanceOutside(eye.y, pos.getY(), pos.getY() + 1.0D);
        double dz = distanceOutside(eye.z, pos.getZ(), pos.getZ() + 1.0D);
        return dx * dx + dy * dy + dz * dz <= VIEW_PLACE_REACH * VIEW_PLACE_REACH;
    }

    private static double distanceOutside(double value, double min, double max) {
        if (value < min) {
            return min - value;
        }
        if (value > max) {
            return value - max;
        }
        return 0.0D;
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
            case EXTEND, UPSIDE_DOWN, ROTATE, SPACING, RANDOM, FRAME, AIR -> List.of(context.getClickedPos().relative(face));
        };
        positions = limitViewedPlacementReach(context, mode, positions);
        boolean lineMode = isLineMode(mode);
        List<BlockPos> orderedPositions = lineMode ? positions : orderByDistanceFromCenter(positions, context.getClickedPos());
        return new PlacementPlan(placementState, orderedPositions, usesOffhandBlock(mode), itemToConsume, lineMode ? 1 : Integer.MAX_VALUE);
    }

    private static List<BlockPos> limitViewedPlacementReach(UseOnContext context, TonkachiMode mode, List<BlockPos> positions) {
        Player player = context.getPlayer();
        if (player == null || mode != TonkachiMode.EXTEND && mode != TonkachiMode.AIR) {
            return positions;
        }

        List<BlockPos> reachable = new ArrayList<>(positions.size());
        for (BlockPos pos : positions) {
            if (isWithinViewPlaceReach(player, pos)) {
                reachable.add(pos);
            }
        }
        return reachable;
    }

    private static boolean isLineMode(TonkachiMode mode) {
        return mode == TonkachiMode.STAIRS
                || mode == TonkachiMode.VERTICAL
                || mode == TonkachiMode.VERTICAL_UP
                || mode == TonkachiMode.VERTICAL_DOWN
                || mode == TonkachiMode.VERTICAL_LEFT
                || mode == TonkachiMode.VERTICAL_RIGHT
                || mode == TonkachiMode.EXTEND
                || mode == TonkachiMode.UPSIDE_DOWN
                || mode == TonkachiMode.ROTATE
                || mode == TonkachiMode.SPACING
                || mode == TonkachiMode.RANDOM
                || mode == TonkachiMode.FRAME
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
        Direction horizontal = horizontalAwayFromPlayer(context);
        int yStep = player != null && player.getY() > origin.getY() + 0.5D ? -1 : 1;
        List<BlockPos> positions = new ArrayList<>(limit);
        for (int distance = 1; distance <= limit; distance++) {
            positions.add(origin.relative(horizontal, distance).above(yStep * distance));
        }
        return positions;
    }

    private static Direction horizontalAwayFromPlayer(UseOnContext context) {
        BlockPos origin = context.getClickedPos();
        Player player = context.getPlayer();
        if (player == null) {
            return context.getHorizontalDirection();
        }

        double dx = player.getX() - (origin.getX() + 0.5D);
        double dz = player.getZ() - (origin.getZ() + 0.5D);
        if (Math.abs(dx) >= Math.abs(dz)) {
            return dx >= 0.0D ? Direction.WEST : Direction.EAST;
        }
        return dz >= 0.0D ? Direction.NORTH : Direction.SOUTH;
    }

    private static Direction horizontalSideDirection(UseOnContext context, boolean right) {
        Player player = context.getPlayer();
        Direction facing = player != null ? player.getDirection() : context.getHorizontalDirection();
        return right ? facing.getClockWise() : facing.getCounterClockWise();
    }

    private static List<BlockPos> linePositions(BlockPos origin, Direction direction, int limit) {
        List<BlockPos> positions = new ArrayList<>(limit);
        for (int distance = 1; distance <= limit; distance++) {
            positions.add(origin.relative(direction, distance));
        }
        return positions;
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

    private static void consumePlacementStack(ServerLevel level, Player player, ItemStack stack) {
        if (Tonten.isUtsusemiBlockItem(stack.getItem()) || Tonten.isSolidifySpaceBlockItem(stack.getItem())) {
            stack.hurtAndBreak(1, player, brokenPlayer -> brokenPlayer.broadcastBreakEvent(InteractionHand.OFF_HAND));
        } else {
            stack.shrink(1);
        }
    }

    private static boolean hasNeighborBlock(ServerLevel level, BlockPos pos) {
        for (Direction direction : Direction.values()) {
            if (!level.getBlockState(pos.relative(direction)).canBeReplaced()) {
                return true;
            }
        }
        return false;
    }

    private static ItemStack findMatchingBlockStack(Player player, Item item) {
        for (ItemStack stack : player.getInventory().items) {
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
        CompoundTag tag = getTontenTag(stack);
        return tag.contains(SOUND_KEY) ? tag.getInt(SOUND_KEY) : 0;
    }

    private static long getLastSoundTick(ItemStack stack) {
        CompoundTag tag = getTontenTag(stack);
        return tag.contains(LAST_SOUND_TICK_KEY) ? tag.getLong(LAST_SOUND_TICK_KEY) : -RHYTHM_RESET_TICKS;
    }

    private static void setSoundState(ItemStack stack, int step, long tick) {
        CompoundTag tag = getTontenTag(stack);
        tag.putInt(SOUND_KEY, step);
        tag.putLong(LAST_SOUND_TICK_KEY, tick);
    }

    private static void message(Player player, String key) {
        player.displayClientMessage(Component.translatable(key), true);
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.tonten.tonkachi.desc").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.tonten.tonkachi.mode", getMode(stack).displayName()).withStyle(ChatFormatting.DARK_AQUA));
        if (this.tier.flatSize() > 1) {
            tooltip.add(Component.translatable("item.tonten.tonkachi.flat_range", this.tier.flatSize(), this.tier.flatSize()).withStyle(ChatFormatting.BLUE));
        }
        tooltip.add(Component.translatable("item.tonten.tonkachi.line_limit", this.tier.lineLimit()).withStyle(ChatFormatting.BLUE));
        if (this.tier == TonkachiTier.COPPER) {
            tooltip.add(Component.translatable("item.tonten.tonkachi.spacing", getSpacing(stack)).withStyle(ChatFormatting.GOLD));
            tooltip.add(Component.translatable("item.tonten.tonkachi.spacing_hint").withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.translatable("item.tonten.tonkachi.random_size", getRandomSize(stack), getRandomSize(stack)).withStyle(ChatFormatting.GOLD));
            tooltip.add(Component.translatable("item.tonten.tonkachi.random_hint").withStyle(ChatFormatting.GRAY));
        }
        if (this.tier == TonkachiTier.GOLD) {
            tooltip.add(Component.translatable("item.tonten.tonkachi.frame_hint").withStyle(ChatFormatting.GRAY));
        }
    }

    private record PlacementPlan(BlockState state, List<BlockPos> positions, boolean offhandMode, Item itemToConsume, int maxPlacements) {
    }

    private record FrameStart(BlockPos pos) {
    }

    private static CompoundTag getTontenTag(ItemStack stack) {
        return stack.getOrCreateTag();
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
