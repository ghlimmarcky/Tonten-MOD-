package com.tonten.tonten;

import java.util.ArrayList;
import java.util.List;
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
    private static final String SOUND_KEY = "TontenSound";
    private static final String LAST_SOUND_TICK_KEY = "TontenLastSoundTick";
    private static final long RHYTHM_RESET_TICKS = 16L;
    private static final double VIEW_PLACE_REACH = 2.0D;
    private static final double VIEW_PLACE_STEP = 0.25D;
    private static final int PLACE_FLAGS = 11;
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
        if (hand != InteractionHand.MAIN_HAND || (mode != TonkachiMode.EXTEND && mode != TonkachiMode.AIR)) {
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
        } else if ((mode == TonkachiMode.UPSIDE_DOWN || mode == TonkachiMode.ROTATE) && this.tier != TonkachiTier.STONE) {
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

    private static BlockPos findViewedPlacementPos(ServerLevel level, Player player, BlockState placementState, boolean requireNeighbor) {
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
            if ((!requireNeighbor || hasNeighborBlock(level, pos)) && canPlace(level, pos, placementState)) {
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
            case EXTEND, UPSIDE_DOWN, ROTATE, AIR -> List.of(context.getClickedPos().relative(face));
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
                || mode == TonkachiMode.EXTEND
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
            stack.hurtAndBreak(1, level, player, ignored -> {
            });
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
    }

    private record PlacementPlan(BlockState state, List<BlockPos> positions, boolean offhandMode, Item itemToConsume, int maxPlacements) {
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
