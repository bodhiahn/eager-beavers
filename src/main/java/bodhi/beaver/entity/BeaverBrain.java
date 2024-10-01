package bodhi.beaver.entity;

import bodhi.beaver.BeaverModClient;
import bodhi.beaver.entity.client.ModEntities;
import com.google.common.collect.ImmutableList;
import com.mojang.datafixers.util.Pair;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.PillarBlock;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.ai.brain.*;
import net.minecraft.entity.ai.brain.task.*;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.entity.ai.brain.sensor.Sensor;
import net.minecraft.entity.ai.brain.sensor.SensorType;
import net.minecraft.entity.ai.brain.task.BreedTask;
import net.minecraft.entity.ai.brain.task.FleeTask;
import net.minecraft.entity.ai.brain.task.LookAroundTask;
import net.minecraft.entity.ai.brain.task.LookAtMobTask;
import net.minecraft.entity.ai.brain.task.MultiTickTask;
import net.minecraft.entity.ai.brain.task.StayAboveWaterTask;
import net.minecraft.entity.ai.brain.task.TemptTask;
import net.minecraft.entity.ai.brain.task.TemptationCooldownTask;
import net.minecraft.entity.ai.brain.task.WanderAroundTask;
import net.minecraft.item.*;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.recipe.Ingredient;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.*;
import net.minecraft.util.math.intprovider.UniformIntProvider;
import net.minecraft.world.GameRules;
import net.minecraft.world.World;
import net.minecraft.world.event.GameEvent;
import org.spongepowered.include.com.google.common.collect.ImmutableMap;

import java.util.*;
import java.util.function.Predicate;

public class BeaverBrain {
    public static final List<MemoryModuleType<?>> MEMORY_MODULES = ImmutableList.of(

            MemoryModuleType.NEAREST_VISIBLE_WANTED_ITEM,
            MemoryModuleType.WALK_TARGET,
            MemoryModuleType.LOOK_TARGET,
            MemoryModuleType.BREED_TARGET,
            MemoryModuleType.PATH,
            MemoryModuleType.HURT_BY,
            MemoryModuleType.HURT_BY_ENTITY,
            MemoryModuleType.NEAREST_HOSTILE,
            MemoryModuleType.IS_PANICKING,
            BeaverModClient.BEAVER_TARGET,
            BeaverModClient.BEAVER_FALLEN_LOGS
    );

    public static final List<SensorType<? extends Sensor<? super Beaver>>> SENSORS = ImmutableList.of(
            SensorType.NEAREST_LIVING_ENTITIES,
            SensorType.NEAREST_PLAYERS,
            SensorType.NEAREST_ITEMS,
            SensorType.HURT_BY
    );

    public static Ingredient getTemptItems() {
        return Ingredient.ofItems(
            Items.OAK_WOOD, Items.BIRCH_WOOD, Items.DARK_OAK_WOOD, Items.SPRUCE_SAPLING
    ); }

    public static Brain<?> create(Brain<Beaver> brain) {
        BeaverBrain.addCoreActivities(brain);
        BeaverBrain.addIdleActivities(brain);
        BeaverBrain.addPanicActivities(brain);
        BeaverBrain.addSwimActivities(brain);
        BeaverBrain.addWorkActivities(brain);

        brain.setCoreActivities(Set.of(Activity.CORE));
        brain.setDefaultActivity(Activity.IDLE);
        brain.resetPossibleActivities();
        return brain;
    }

    private static void addCoreActivities(Brain<Beaver> brain) {
        brain.setTaskList(Activity.CORE, 0, ImmutableList.of(
                new StayAboveWaterTask(0.8f),
                new FleeTask(2.0f),
                new WanderAroundTask(500, 700),
                new LookAroundTask(45, 90),
                new TemptationCooldownTask(MemoryModuleType.TEMPTATION_COOLDOWN_TICKS)
        ));
    }

    private static void addIdleActivities(Brain<Beaver> brain) {
        brain.setTaskList(Activity.IDLE, ImmutableList.of(
                Pair.of(0, new BreedTask(ModEntities.BEAVER, 1.0f)),
                Pair.of(1, new TemptTask(beaver -> 1.25f)),
                Pair.of(2, new BeaverPickupItemTask()),
                Pair.of(3, new WanderAroundTask(500, 700)),
                Pair.of(4, new RandomLookAroundTask(UniformIntProvider.create(150, 250), 30.0f, 0.0f, 0.0f)),
                Pair.of(5, LookAtMobTask.create(EntityType.PLAYER, 8.0f)
                )));
    }

    private static void addPanicActivities(Brain<Beaver> brain) {
        brain.setTaskList(Activity.PANIC, 0, ImmutableList.of(
                new FleeTask(1.25f)
        ));
    }

    private static void addSwimActivities(Brain<Beaver> brain) {
        brain.setTaskList(Activity.SWIM, ImmutableList.of(
                Pair.of(0, new BeaverSwimTask())
        ));
    }

    private static void addWorkActivities(Brain<Beaver> brain) {
        brain.setTaskList(Activity.WORK, ImmutableList.of(
                Pair.of(0, new BuildDamTask(0.6f, 20)),
                Pair.of(1, new FinishLogTask()),
                Pair.of(2, new CollectLogsTask(0.6f, 25, 3))
        ));
    }

    static void updateActivities(Beaver beaver) {
        beaver.getBrain().resetPossibleActivities(ImmutableList.of(Activity.WORK, Activity.IDLE));
    }

    // Custom Task Classes

    public static class BeaverSwimTask extends MultiTickTask<Beaver> {
        private final double swimSpeed;
        private final int diveFrequency;
        private int diveTimer;

        public BeaverSwimTask() {
            super(ImmutableMap.of());
            this.swimSpeed = 1.5;
            this.diveFrequency = 300;
            this.diveTimer = 0;
        }

        @Override
        protected boolean shouldRun(ServerWorld world, Beaver beaver) {
            return beaver.isTouchingWater() && beaver.getFluidHeight(FluidTags.WATER) > (beaver.isBaby() ? 0.1D : 0.2D) || beaver.isInLava();
        }

        @Override
        protected void run(ServerWorld world, Beaver beaver, long time) {
            this.diveTimer = 0;
        }

        @Override
        protected void keepRunning(ServerWorld world, Beaver beaver, long time) {
            if (diveTimer == 0 && beaver.getRandom().nextInt(diveFrequency) == 0) {
                diveTimer = beaver.getRandom().nextInt(40) + 40; // Dive for 40 to 80 ticks.
            }
            if (diveTimer > 0) {
                // Move beaver downwards.
                Vec3d motion = beaver.getVelocity();
                beaver.setVelocity(motion.x, -0.03, motion.z);
                diveTimer--;
            } else {
                if (beaver.getRandom().nextFloat() < 0.8F) {
                    beaver.getJumpControl().setActive();
                }
            }

            if (beaver.isInsideWaterOrBubbleColumn()) {
                beaver.setMovementSpeed((float) swimSpeed);
            }

            // Create bubbles.
            beaver.getWorld().addParticle(ParticleTypes.BUBBLE, beaver.getX(), beaver.getY(), beaver.getZ(), 0.1, .3, 0.1);
        }

        @Override
        public Status getStatus() {
            return null;
        }

        @Override
        public String getName() {
            return "";
        }
    }

    public static class BeaverPickupItemTask extends MultiTickTask<Beaver> {
        private final Predicate<ItemEntity> PICKABLE_DROP_FILTER = item -> {
            if (item.cannotPickup() || !item.isAlive()) {
                return false;
            }

            Item itemType = item.getStack().getItem();

            // Check if the item is allowed
            return isAllowedItem(itemType);
        };

        public BeaverPickupItemTask() {
            super(ImmutableMap.of(MemoryModuleType.NEAREST_VISIBLE_WANTED_ITEM, MemoryModuleState.VALUE_PRESENT));
        }

        @Override
        protected boolean shouldRun(ServerWorld world, Beaver beaver) {
            return beaver.getEquippedStack(EquipmentSlot.MAINHAND).isEmpty() && !beaver.getBrain().hasMemoryModule(MemoryModuleType.NEAREST_VISIBLE_WANTED_ITEM);
        }

        @Override
        protected void run(ServerWorld world, Beaver beaver, long time) {
            List<ItemEntity> items = world.getEntitiesByClass(ItemEntity.class, beaver.getBoundingBox().expand(8.0, 8.0, 8.0), PICKABLE_DROP_FILTER);
            if (!items.isEmpty()) {
                beaver.getBrain().remember(MemoryModuleType.NEAREST_VISIBLE_WANTED_ITEM, items.get(0));
                beaver.getNavigation().startMovingTo(items.get(0), 0.6f);
            }
        }

        @Override
        protected boolean shouldKeepRunning(ServerWorld world, Beaver beaver, long time) {
            return beaver.getBrain().hasMemoryModule(MemoryModuleType.NEAREST_VISIBLE_WANTED_ITEM) && !beaver.getNavigation().isIdle();
        }

        @Override
        protected void keepRunning(ServerWorld world, Beaver beaver, long time) {
            ItemEntity item = beaver.getBrain().getOptionalMemory(MemoryModuleType.NEAREST_VISIBLE_WANTED_ITEM).orElse(null);
            if (item != null && item.isAlive()) {
                beaver.getNavigation().startMovingTo(item, 0.6f);
            } else {
                beaver.getBrain().forget(MemoryModuleType.NEAREST_VISIBLE_WANTED_ITEM);
            }
        }

        private boolean isAllowedItem(Item item) {
            if (item.isFood()) {
                return true;
            }
            if (item instanceof BlockItem) {
                return true;
            }
            if (item == Items.OAK_SAPLING || item == Items.SPRUCE_SAPLING ||
                    item == Items.BIRCH_SAPLING || item == Items.JUNGLE_SAPLING ||
                    item == Items.ACACIA_SAPLING || item == Items.DARK_OAK_SAPLING) {
                return true;
            }
            return item == Items.STICK;
        }
    }

    public static class BuildDamTask extends MultiTickTask<Beaver> {
        private static final Direction[] HORIZONTAL_DIRECTIONS = {
                Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST
        };
        private final double speed;
        private final int range;
        private BlockPos targetPos;

        public BuildDamTask(double speed, int range) {
            super(ImmutableMap.of());
            this.speed = speed;
            this.range = range;
        }

        @Override
        protected boolean shouldRun(ServerWorld world, Beaver beaver) {
            ItemStack itemStack = beaver.getEquippedStack(EquipmentSlot.MAINHAND);
            if (!itemStack.isEmpty() && itemStack.getItem() instanceof BlockItem) {
                BlockState carriedBlock = beaver.getCarriedBlock();
                if (carriedBlock != null) {
                    this.targetPos = findOptimalWaterTarget(beaver);
                    return this.targetPos != null;
                }
            }
            return false;
        }

        @Override
        protected void run(ServerWorld world, Beaver beaver, long time) {
            if (this.targetPos != null) {
                beaver.getNavigation().startMovingTo(
                        this.targetPos.getX() + 0.5,
                        this.targetPos.getY(),
                        this.targetPos.getZ() + 0.5,
                        this.speed
                );
            }
        }

        @Override
        protected boolean shouldKeepRunning(ServerWorld world, Beaver beaver, long time) {
            return beaver.getEquippedStack(EquipmentSlot.MAINHAND).getItem() instanceof BlockItem
                    && !beaver.getNavigation().isIdle();
        }

        @Override
        protected void keepRunning(ServerWorld world, Beaver beaver, long time) {
            if (this.targetPos == null) {
                return;
            }

            if (beaver.squaredDistanceTo(Vec3d.ofCenter(this.targetPos)) < 2.0D) {
                placeDamBlock(world, beaver);
            } else {
                beaver.getNavigation().startMovingTo(
                        this.targetPos.getX() + 0.5,
                        this.targetPos.getY(),
                        this.targetPos.getZ() + 0.5,
                        this.speed
                );
            }
        }

        @Override
        protected void finishRunning(ServerWorld world, Beaver beaver, long time) {
            BeaverReservationSystem.releaseBlock(this.targetPos);
            this.targetPos = null;
            beaver.getNavigation().stop();
        }

        private void placeDamBlock(ServerWorld world, Beaver beaver) {
            ItemStack itemStack = beaver.getEquippedStack(EquipmentSlot.MAINHAND);
            BlockState carriedBlock = beaver.getCarriedBlock();

            if (carriedBlock == null || !(itemStack.getItem() instanceof BlockItem)) {
                return;
            }

            boolean blockPlaced = world.setBlockState(targetPos, carriedBlock, Block.NOTIFY_ALL);

            if (blockPlaced) {
                world.emitGameEvent(GameEvent.BLOCK_PLACE, targetPos, GameEvent.Emitter.of(beaver, carriedBlock));
                beaver.setCarriedBlock(null);
                itemStack.decrement(1);
                BeaverReservationSystem.releaseBlock(targetPos);
                this.finishRunning(world, beaver, 0);
            }
        }

        private BlockPos findOptimalWaterTarget(Beaver beaver) {
            World world = beaver.getWorld();
            BlockPos beaverPos = beaver.getBlockPos();
            int searchRadius = range;

            BlockPos bestPos = null;
            int bestScore = Integer.MIN_VALUE;

            for (BlockPos pos : BlockPos.iterateOutwards(beaverPos, searchRadius, searchRadius, searchRadius)) {
                BlockState blockState = world.getBlockState(pos);

                if (!world.getBlockState(pos.up()).isAir()) {
                    continue;
                }

                if (!blockState.getFluidState().isIn(FluidTags.WATER)) {
                    continue;
                }

                if (!BeaverReservationSystem.isBlockFree(pos, beaver.getUuid())) {
                    continue;
                }

                boolean adjacentToSolid = false;
                boolean adjacentToLog = false;

                for (Direction dir : HORIZONTAL_DIRECTIONS) {
                    BlockPos adjPos = pos.offset(dir);
                    BlockState adjState = world.getBlockState(adjPos);

                    if (adjState.isOpaqueFullCube(world, adjPos)) {
                        adjacentToSolid = true;
                    }
                    if (adjState.isIn(BlockTags.LOGS)) {
                        adjacentToLog = true;
                    }
                }

                if (!adjacentToSolid) {
                    continue;
                }

                int adjacentWaterCount = 0;

                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dx == 0 && dz == 0) {
                            continue;
                        }
                        BlockPos adjPos = pos.add(dx, 0, dz);
                        BlockState adjState = world.getBlockState(adjPos);
                        if (adjState.getFluidState().isIn(FluidTags.WATER)) {
                            adjacentWaterCount++;
                        }
                    }
                }

                int score = 0;
                if (adjacentToLog) {
                    score += 10;
                }
                score += adjacentWaterCount;

                if (score > bestScore) {
                    bestScore = score;
                    bestPos = pos.toImmutable();
                }
            }

            if (bestPos != null) {
                BeaverReservationSystem.reserveBlock(bestPos, beaver.getUuid());
            }

            return bestPos;
        }
    }

    public static class CollectLogsTask extends MultiTickTask<Beaver> {
        private final double speed;
        private final int range;
        private final int maxYDifference;
        private BlockPos targetPos;
        private int timer;

        public CollectLogsTask(double speed, int range, int maxYDifference) {
            super(ImmutableMap.of());
            this.speed = speed;
            this.range = range;
            this.maxYDifference = maxYDifference;
        }


        @Override
        protected boolean shouldRun(ServerWorld world, Beaver beaver) {
            if (!beaver.getEquippedStack(EquipmentSlot.MAINHAND).isEmpty() || !beaver.storedTreeLogs.isEmpty()) {
                return false;
            }

            this.targetPos = findLogBlock(beaver);
            beaver.getBrain().remember(MemoryModuleType.WALK_TARGET, new WalkTarget(targetPos, .6f, 0));
            return this.targetPos != null;
        }

        @Override
        protected void run(ServerWorld world, Beaver beaver, long time) {
            if (this.targetPos != null) {
                beaver.getNavigation().startMovingTo(this.targetPos.getX(), this.targetPos.getY(), this.targetPos.getZ(), this.speed);
                this.timer = 0;
            }
        }

        @Override
        protected boolean shouldKeepRunning(ServerWorld world, Beaver beaver, long time) {
            return this.timer < 30 && this.targetPos != null && isLog(world.getBlockState(this.targetPos));
        }

        @Override
        protected void keepRunning(ServerWorld world, Beaver beaver, long time) {
            if (this.hasReached(beaver)) {
                beaver.startEatingTree();
                if (this.timer % 5 == 0) {
                    beaver.playSound(SoundEvents.ENTITY_GENERIC_EAT, .4f, 1.5f);
                    // Particle effects
                    spawnEatingParticles(world, beaver);
                }
                this.timer++;
            } else {
                beaver.getNavigation().startMovingTo(targetPos.getX(), targetPos.getY(), targetPos.getZ(), this.speed);
            }
        }

        @Override
        protected void finishRunning(ServerWorld world, Beaver beaver, long time) {
            if (this.timer >= 30) {
                eatWood(world, beaver);
                beaver.stopEatingTree();
                this.timer = 0;
            }
        }

        private boolean hasReached(Beaver beaver) {
            return this.targetPos.isWithinDistance(beaver.getPos(), 1.5);
        }

        private void eatWood(ServerWorld world, Beaver beaver) {
            if (!world.getGameRules().getBoolean(GameRules.DO_MOB_GRIEFING)) {
                return;
            }

            BlockState blockState = world.getBlockState(this.targetPos);
            if (isLog(blockState)) {
                List<BlockPos> logs = new ArrayList<>();
                BlockPos current = this.targetPos;

                while (isLog(world.getBlockState(current))) {
                    logs.add(current);
                    current = current.up();
                }

                eatLog(beaver, blockState);
                logs.remove(this.targetPos);
                repositionLog(beaver, logs);
            }
        }

        private void eatLog(Beaver beaver, BlockState state) {
            World world = beaver.getWorld();
            beaver.equipStack(EquipmentSlot.MAINHAND, new ItemStack(state.getBlock()));
            beaver.playSound(SoundEvents.BLOCK_WOOD_BREAK, 1.0f, 1.0f);
            beaver.setCarriedBlock(state);
            world.breakBlock(this.targetPos, false);
        }

        private void repositionLog(Beaver beaver, List<BlockPos> logs) {
            World world = beaver.getWorld();
            List<GlobalPos> fallenPos = new ArrayList<>(List.of());
            for (BlockPos logPos : logs) {
                BlockState logState = world.getBlockState(logPos);
                int offset = logPos.getY() - this.targetPos.getY();
                BlockPos newPos = this.targetPos.add(offset + 1, -1, 0);
                while (!world.getBlockState(newPos).isAir() && world.getBlockState(newPos).isOpaque()) {
                    newPos = newPos.up();
                }
                while (world.getBlockState(newPos.down()).isAir() || !world.getBlockState(newPos.down()).isOpaque()) {
                    newPos = newPos.down();
                }
                world.setBlockState(newPos, getSidewaysLogState(logState), 3);
                world.removeBlock(logPos, false);
                fallenPos.add(GlobalPos.create(world.getRegistryKey(), newPos));
            }
            beaver.getBrain().remember(BeaverModClient.BEAVER_FALLEN_LOGS, fallenPos);
        }

        private BlockState getSidewaysLogState(BlockState original) {
            if (original.getBlock() instanceof PillarBlock) {
                return original.with(PillarBlock.AXIS, Direction.Axis.X);
            }
            return original;
        }

        private BlockPos findLogBlock(Beaver beaver) {
            World world = beaver.getWorld();
            BlockPos beaverPos = beaver.getBlockPos();
            int searchRadius = range;

            for (BlockPos pos : BlockPos.iterateOutwards(beaverPos, searchRadius, maxYDifference, searchRadius)) {
                if (isTargetPos(world, pos, beaver)) {
                    BeaverReservationSystem.reserveBlock(pos, beaver.getUuid());
                    return pos.toImmutable();
                }
            }
            return null;
        }

        private boolean isTargetPos(World world, BlockPos pos, Beaver beaver) {
            BlockState blockState = world.getBlockState(pos);

            if (!blockState.isIn(BlockTags.LOGS)) {
                return false;
            }

            if (world.getBlockState(pos.east()).getFluidState().isIn(FluidTags.WATER) ||
                    world.getBlockState(pos.west()).getFluidState().isIn(FluidTags.WATER) ||
                    world.getBlockState(pos.north()).getFluidState().isIn(FluidTags.WATER) ||
                    world.getBlockState(pos.south()).getFluidState().isIn(FluidTags.WATER) ||
                    world.getBlockState(pos.down()).getFluidState().isIn(FluidTags.WATER)) {
                return false;
            }

            if (!world.getBlockState(pos.down()).isIn(BlockTags.LOGS)) {
                return false;
            }

            if (world.getBlockState(pos.down().down()).isIn(BlockTags.LOGS)) {
                return false;
            }

            if (!hasLeavesAbove(world, pos)) {
                return false;
            }

            return BeaverReservationSystem.isBlockFree(pos, beaver.getUuid());
        }

        private boolean hasLeavesAbove(World world, BlockPos pos) {
            for (int i = 1; i <= 20; i++) {
                BlockState blockAbove = world.getBlockState(pos.up(i));
                if (blockAbove.isIn(BlockTags.LEAVES)) {
                    return true;
                }
            }
            return false;
        }

        private boolean isLog(BlockState state) {
            return state.isIn(BlockTags.LOGS);
        }

        private void spawnEatingParticles(ServerWorld world, Beaver beaver) {
            Vec3d blockCenter = new Vec3d(this.targetPos.getX() + 0.5, this.targetPos.getY() - 0.5, this.targetPos.getZ() + 0.5);
            Vec3d beaverPos = beaver.getPos();
            Vec3d direction = blockCenter.subtract(beaverPos).normalize();
            double spawnX = blockCenter.x + direction.x * 0.5;
            double spawnY = blockCenter.y + direction.y * 0.5;
            double spawnZ = blockCenter.z + direction.z * 0.5;

            world.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, world.getBlockState(this.targetPos)),
                    spawnX, spawnY, spawnZ, 200, 0.0D, 0.0D, 0.0D, 2.0D);
        }
    }

    public static class FinishLogTask extends MultiTickTask<Beaver> {
        private static final int EATING_TIME = 30;
        private int timer = 0;
        private BlockPos targetPos;

        public FinishLogTask() {
            super(ImmutableMap.of());
        }

        @Override
        protected boolean shouldRun(ServerWorld world, Beaver beaver) {
            return beaver.getEquippedStack(EquipmentSlot.MAINHAND).isEmpty() && !beaver.storedTreeLogs.isEmpty();
        }

        @Override
        protected void run(ServerWorld world, Beaver beaver, long time) {
            this.timer = 0;
            this.targetPos = beaver.storedTreeLogs.get(0);
        }

        @Override
        protected boolean shouldKeepRunning(ServerWorld world, Beaver beaver, long time) {
            return this.timer < EATING_TIME && this.targetPos != null;
        }

        @Override
        protected void keepRunning(ServerWorld world, Beaver beaver, long time) {
            if (this.hasReached(beaver)) {
                beaver.startEatingTree();
                if (this.timer % 5 == 0) {
                    beaver.playSound(SoundEvents.ENTITY_GENERIC_EAT, .4f, 1.5f);
                    // Particle effects
                    spawnEatingParticles(world, beaver);
                }
                this.timer++;
            } else {
                beaver.getNavigation().startMovingTo(targetPos.getX(), targetPos.getY(), targetPos.getZ(), 0.6);
            }
        }

        @Override
        protected void finishRunning(ServerWorld world, Beaver beaver, long time) {
            pickUpLog(world, beaver);
            beaver.stopEatingTree();
            beaver.storedTreeLogs.remove(0);
            this.timer = 0;
        }

        private boolean hasReached(Beaver beaver) {
            return targetPos.isWithinDistance(beaver.getPos(), 1.5);
        }

        private void pickUpLog(ServerWorld world, Beaver beaver) {
            BlockState state = world.getBlockState(this.targetPos);
            beaver.equipStack(EquipmentSlot.MAINHAND, new ItemStack(state.getBlock()));
            beaver.playSound(SoundEvents.BLOCK_WOOD_BREAK, 1.0f, 1.0f);
            beaver.setCarriedBlock(state);
            world.breakBlock(this.targetPos, false);
        }

        private void spawnEatingParticles(ServerWorld world, Beaver beaver) {
            Vec3d blockCenter = new Vec3d(this.targetPos.getX() + 0.5, this.targetPos.getY() + 0.5, this.targetPos.getZ() + 0.5);
            Vec3d beaverPos = beaver.getPos();
            Vec3d direction = blockCenter.subtract(beaverPos).normalize();
            double spawnX = blockCenter.x + direction.x * 0.5;
            double spawnY = blockCenter.y + direction.y * 0.5;
            double spawnZ = blockCenter.z + direction.z * 0.5;

            world.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, world.getBlockState(this.targetPos)),
                    spawnX, spawnY, spawnZ, 200, 0.0D, 0.0D, 0.0D, 2.0D);
        }
    }
}
