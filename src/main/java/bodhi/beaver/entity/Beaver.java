package bodhi.beaver.entity;

import bodhi.beaver.BeaverMod;
import bodhi.beaver.entity.client.ModEntities;
import net.minecraft.advancement.criterion.Criteria;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.PillarBlock;
import net.minecraft.component.DataComponentType;
import net.minecraft.component.type.FoodComponent;
import net.minecraft.entity.*;
import net.minecraft.entity.ai.goal.*;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.passive.PassiveEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.*;
import net.minecraft.loot.LootTable;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ItemStackParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.recipe.Ingredient;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.stat.Stats;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.*;
import net.minecraft.world.event.GameEvent;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.*;
import software.bernie.geckolib.util.GeckoLibUtil;


import java.util.*;
import java.util.function.Predicate;

public class Beaver extends AnimalEntity implements GeoEntity {
    private static final Ingredient BREEDING_INGREDIENT = Ingredient.ofItems(Items.OAK_WOOD, Items.BIRCH_WOOD, Items.DARK_OAK_WOOD, Items.SPRUCE_SAPLING);
    private static final TrackedData<Optional<BlockState>> CARRIED_BLOCK = DataTracker.registerData(Beaver.class, TrackedDataHandlerRegistry.OPTIONAL_BLOCK_STATE);

    static final Predicate<ItemEntity> PICKABLE_DROP_FILTER = item -> {
        if (item.cannotPickup() || !item.isAlive()) {
            return false;
        }

        Item itemType = item.getStack().getItem();

        // Check if the item is food
        if (itemType.isFood()) {
            return true;
        }

        // Check if the item is a block
        if (itemType instanceof BlockItem) {
            return true;
        }

        // Check if the item is a sapling
        if (itemType == Items.OAK_SAPLING || itemType == Items.SPRUCE_SAPLING ||
                itemType == Items.BIRCH_SAPLING || itemType == Items.JUNGLE_SAPLING ||
                itemType == Items.ACACIA_SAPLING || itemType == Items.DARK_OAK_SAPLING) {
            return true;
        }

        // Check if the item is a stick
        return itemType == Items.STICK;
    };
    private int eatingTime;
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    private final List<BlockPos> storedTreeLogs = new ArrayList<>();

    protected static final RawAnimation HOLD_IDLE_ANIM = RawAnimation.begin().thenLoop("animation.beaver.holdidle");
    protected static final RawAnimation HOLD_WALK_ANIM = RawAnimation.begin().thenLoop("animation.beaver.holdwalk");
    protected static final RawAnimation WALK_ANIM = RawAnimation.begin().thenLoop("animation.beaver.walk");
    protected static final RawAnimation IDLE_ANIM = RawAnimation.begin().thenLoop("animation.beaver.idle");
    protected static final RawAnimation SWIM_ANIM = RawAnimation.begin().thenLoop("animation.beaver.swim");


    private boolean isHat = false;
    public Beaver(EntityType<? extends AnimalEntity> entityType, World world) {
        super(entityType, world);
        this.setCanPickUpLoot(true);
        this.getNavigation().setCanSwim(true);
    }

    @Override
    public int getMaxAir() {
        return 4800;
    }

    // Check if the block is a log
    private boolean isLog(BlockState state) {
        return state.isIn(BlockTags.LOGS);
    }

    public boolean isHat() {
        return isHat;
    }
    public void setHat() {
        isHat = true;
    }
    @Override
    public boolean isPushedByFluids() {
        return false;
    }

    public static DefaultAttributeContainer.Builder setAttributes() {
        return AnimalEntity.createMobAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, 14.0D)
                .add(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, .1D)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.4f);
    }

    @Override
    protected RegistryKey<LootTable> getLootTableId() {
        return RegistryKey.of(RegistryKeys.LOOT_TABLE, new Identifier("beavermod", "entities/beaver"));
    }

    private boolean isEatingTree = false;

    public void startEatingTree() {
        this.isEatingTree = true;
    }

    public void stopEatingTree() {
        this.isEatingTree = false;
    }

    public boolean isEatingTree() {
        return this.isEatingTree;
    }

    @Override
    public void remove(Entity.RemovalReason reason) {
        BeaverReservationSystem.releaseBeaverReservation(this.getUuid());
        super.remove(reason);
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(genericWalkIdleController());
        controllers.add(swimController());
        controllers.add(eatingController());
        controllers.add(hatController());
    }



    private <T extends GeoAnimatable> AnimationController<Beaver> genericWalkIdleController() {
        return new AnimationController<Beaver>(Beaver.this, "Walk/Idle", 0, state -> {
            boolean isHoldingItem = !Beaver.this.getEquippedStack(EquipmentSlot.MAINHAND).isEmpty();
            boolean isSwimming = Beaver.this.isTouchingWater();
            boolean isHat = Beaver.this.isHat();
            if (!isSwimming && !isHat) {
                if (state.isMoving()) {
                    return state.setAndContinue(isHoldingItem ? HOLD_WALK_ANIM : WALK_ANIM);
                } else {
                    return state.setAndContinue(isHoldingItem ? HOLD_IDLE_ANIM : IDLE_ANIM);
                }
            }
            return PlayState.STOP;
        });
    }

    private <T extends GeoAnimatable> AnimationController<Beaver> hatController() {
        return new AnimationController<Beaver>(Beaver.this, "Hat", 0, state -> {
            boolean isHat = Beaver.this.isHat();
            if (isHat){
                return PlayState.CONTINUE;
            }
            return PlayState.STOP;
        });
    }

    private <T extends GeoAnimatable> AnimationController<Beaver> swimController() {
        return new AnimationController<Beaver>(Beaver.this, "Swim", 0, state -> {
            boolean isHat = Beaver.this.isHat();
            if (Beaver.this.isTouchingWater() && !isHat)
                return state.setAndContinue(SWIM_ANIM);
            return PlayState.STOP;
            });
    }

    private <T extends GeoAnimatable> AnimationController<Beaver> eatingController() {
        return new AnimationController<Beaver>(Beaver.this, "Eat", 0, state -> {
            if (Beaver.this.isEatingTree())
                return state.setAndContinue(HOLD_IDLE_ANIM);
            return PlayState.STOP;
        });
    }


    public void setCarriedBlock(@Nullable BlockState state) {
        this.dataTracker.set(CARRIED_BLOCK, Optional.ofNullable(state));
    }

    @Nullable
    public BlockState getCarriedBlock() {
        return this.dataTracker.get(CARRIED_BLOCK).orElse(null);
    }

    @Override
    public void tickMovement() {
        if (!this.getWorld().isClient && this.isAlive() && this.canMoveVoluntarily()) {
            ++this.eatingTime;
            ItemStack itemStack = this.getEquippedStack(EquipmentSlot.MAINHAND);
            if (this.canEat(itemStack)) {
                if (this.eatingTime > 600) {
                    // Handle the eating process for normal food
                    ItemStack itemStack2 = itemStack.finishUsing(this.getWorld(), this);
                    if (!itemStack2.isEmpty()) {
                        this.equipStack(EquipmentSlot.MAINHAND, itemStack2);
                    }
                    this.eatingTime = 0;
                } else if (this.eatingTime > 560 && this.random.nextFloat() < 0.1f) {
                    this.playSound(this.getEatSound(itemStack), .7f, 2.5f);
                    this.getWorld().sendEntityStatus(this, EntityStatuses.CREATE_EATING_PARTICLES);
                }
            } else if (itemStack.getItem() == Items.STICK) {
                // Consume the stick
                if (!this.getWorld().isClient) {
                    itemStack.decrement(1);
                    this.playSound(this.getEatSound(Items.CARROT.getDefaultStack()), .7f, 2.5f);
                    this.eatingTime = 0;
                }
            } else if (itemStack.isIn(ItemTags.SAPLINGS)) {
                if (this.getWorld().getBlockState(this.getBlockPos().down()).isOpaque()) {
                    BlockState blockState = this.getCarriedBlock();
                    if (blockState == null) {
                        return;
                    }
                    World world = this.getWorld();
                    boolean blockPlaced = world.setBlockState(this.getBlockPos(), blockState, Block.NOTIFY_ALL);
                    if (blockPlaced) {
                        world.emitGameEvent(GameEvent.BLOCK_PLACE, this.getBlockPos(), GameEvent.Emitter.of(this, blockState));
                        this.setCarriedBlock(null);
                        if (blockState.getBlock().asItem() == itemStack.getItem()) {
                            itemStack.decrement(1);
                        }
                    }
                }
            }
        }
        super.tickMovement();
    }


    public class WaterWanderGoal extends Goal {
        private final double speed;
        private double targetX;
        private double targetY;
        private double targetZ;

        public WaterWanderGoal(double speed) {
            this.speed = speed;
            setControls(EnumSet.of(Goal.Control.MOVE));
        }

        @Override
        public boolean canStart() {
            if (Beaver.this.isTouchingWater() && Beaver.this.getNavigation().isIdle()) {
                // We'll generate a random destination in the water every few ticks
                if (Beaver.this.getRandom().nextInt(10) == 0) {
                    Vec3d targetVec = this.generateWaterTarget();
                    if (targetVec != null) {
                        this.targetX = targetVec.x;
                        this.targetY = targetVec.y;
                        this.targetZ = targetVec.z;
                        return true;
                    }
                }
            }
            return false;
        }

        @Override
        public boolean shouldContinue() {
            return !Beaver.this.getNavigation().isIdle();
        }

        @Override
        public void start() {
            Beaver.this.getNavigation().startMovingTo(targetX, targetY, targetZ, speed);
        }

        private Vec3d generateWaterTarget() {
            Random random = Beaver.this.getRandom();
            double angle = random.nextDouble() * 2 * Math.PI;
            double distance = 8 + random.nextDouble() * 8;
            double targetX = Beaver.this.getX() + Math.sin(angle) * distance;
            double targetZ = Beaver.this.getZ() + Math.cos(angle) * distance;
            double targetY = Beaver.this.getY() + random.nextDouble() * 6 - 3;

            BlockPos targetPos = new BlockPos((int) targetX, (int) targetY, (int) targetZ);
            while (!Beaver.this.getWorld().getBlockState(targetPos).getFluidState().isIn(FluidTags.WATER) && targetPos.getY() > 1) {
                targetPos = targetPos.down();
            }

            return Beaver.this.getWorld().getBlockState(targetPos).getFluidState().isIn(FluidTags.WATER) ? new Vec3d(targetX, targetY, targetZ) : null;
        }
    }

    class MateGoal extends AnimalMateGoal {
        public MateGoal(double chance) {
            super(Beaver.this, chance);
        }

        @Override
        public void start() {
            super.start();
        }

        @Override
        public boolean canStart() {
            // Only allow mating if a player has interacted with the beaver
            return super.canStart() && Beaver.this.getLovingPlayer() != null;
        }

        @Override
        protected void breed() {
            super.breed();
              ServerWorld serverWorld = (ServerWorld) this.world;
                Beaver beaver = (Beaver) this.animal.createChild(serverWorld, this.mate);
                if (beaver != null) {
                    ServerPlayerEntity serverPlayerEntity = this.animal.getLovingPlayer();
                    assert this.mate != null;

                    if (serverPlayerEntity != null) {
                        serverPlayerEntity.incrementStat(Stats.ANIMALS_BRED);
                        Criteria.BRED_ANIMALS.trigger(serverPlayerEntity, this.animal, this.mate, beaver);
                    }
                    this.animal.setBreedingAge(6000);
                    this.mate.setBreedingAge(6000);
                    this.animal.resetLoveTicks();
                    this.mate.resetLoveTicks();
                    beaver.setBreedingAge(-24000);
                    beaver.refreshPositionAndAngles(this.animal.getX(), this.animal.getY(), this.animal.getZ(), 0.0f, 0.0f);
                    serverWorld.spawnEntityAndPassengers(beaver);
                    this.world.sendEntityStatus(this.animal, EntityStatuses.ADD_BREEDING_PARTICLES);
                    if (this.world.getGameRules().getBoolean(GameRules.DO_MOB_LOOT)) {
                        this.world.spawnEntity(new ExperienceOrbEntity(this.world, this.animal.getX(), this.animal.getY(), this.animal.getZ(), this.animal.getRandom().nextInt(7) + 1));
                    }
            }
        }
    }

    private void dropItem(ItemStack stack) {
        ItemEntity itemEntity = new ItemEntity(this.getWorld(), this.getX(), this.getY(), this.getZ(), stack);
        this.getWorld().spawnEntity(itemEntity);
    }

    @Override
    protected void loot(ItemEntity item) {
        ItemStack itemStack = item.getStack();
        if (this.canPickupItem(itemStack)) {
            int i = itemStack.getCount();
            if (i > 1) {
                this.dropItem(itemStack.split(i - 1));
            }
            if (itemStack.getItem() instanceof BlockItem blockItem) {
                this.setCarriedBlock(blockItem.getBlock().getDefaultState());
            }
            this.triggerItemPickedUpByEntityCriteria(item);
            this.equipStack(EquipmentSlot.MAINHAND, itemStack.split(1));
            this.updateDropChances(EquipmentSlot.MAINHAND);
            this.sendPickup(item, itemStack.getCount());

            item.discard();
            this.eatingTime = 0;
        }
    }


    @Override
    public void handleStatus(byte status) {
        if (status == EntityStatuses.CREATE_EATING_PARTICLES) {
            ItemStack itemStack = this.getEquippedStack(EquipmentSlot.MAINHAND);
            if (!itemStack.isEmpty()) {
                for (int i = 0; i < 8; ++i) {
                    Vec3d vec3d = new Vec3d(((double)this.random.nextFloat() - 0.5) * 0.1, Math.random() * 0.1 + 0.1, 0.0).rotateX(-this.getPitch() * ((float)Math.PI / 180)).rotateY(-this.getYaw() * ((float)Math.PI / 180));
                    this.getWorld().addParticle(new ItemStackParticleEffect(ParticleTypes.ITEM, itemStack), this.getX() + this.getRotationVector().x / 2.0, this.getY(), this.getZ() + this.getRotationVector().z / 2.0, vec3d.x, vec3d.y + 0.05, vec3d.z);
                }
            }
        } else {
            super.handleStatus(status);
        }
    }

    @Override
    public boolean canEquip(ItemStack stack) {
        EquipmentSlot equipmentSlot = MobEntity.getPreferredEquipmentSlot(stack);
        if (!this.getEquippedStack(equipmentSlot).isEmpty()) {
            return false;
        }
        return equipmentSlot == EquipmentSlot.MAINHAND && super.canEquip(stack);
    }

    private boolean isFood(Item item) {
        return Boolean.TRUE.equals(item.getComponents().get(FoodComponent));
    }
    private boolean canEat(ItemStack stack) {
        return stack.getItem().getComponents(). && this.getTarget() == null && this.isOnGround() && !this.isSleeping();
    }

    @Override
    public boolean canPickupItem(ItemStack stack) {
        Item item = stack.getItem();
        ItemStack itemStack = this.getEquippedStack(EquipmentSlot.MAINHAND);

        // Check if the beaver's main hand is empty
        if (itemStack.isEmpty()) {
            return isAllowedItem(item);
        }

        // Check if the beaver is in the process of eating and the new item is food while the current item is not food
        return this.eatingTime > 0 && item.isFood() && !itemStack.getItem().isFood();
    }

    private boolean isAllowedItem(Item item) {
        // Check if the item is food
        if (item.isFood()) {
            return true;
        }

        // Check if the item is a block
        if (item instanceof BlockItem) {
            return true;
        }

        // Check if the item is a sapling
        if (item == Items.OAK_SAPLING || item == Items.SPRUCE_SAPLING ||
                item == Items.BIRCH_SAPLING || item == Items.JUNGLE_SAPLING ||
                item == Items.ACACIA_SAPLING || item == Items.DARK_OAK_SAPLING) {
            return true;
        }

        // Check if the item is a stick
        return item == Items.STICK;
    }

    boolean wantsToPickupItem() {
        return !this.isSleeping();
    }

    class PickupItemGoal extends Goal {
        public PickupItemGoal() {
            this.setControls(EnumSet.of(Goal.Control.MOVE));
        }

        @Override
        public boolean canStart() {
            if (!Beaver.this.getEquippedStack(EquipmentSlot.MAINHAND).isEmpty()) {
                return false;
            }
            if (Beaver.this.getTarget() != null || Beaver.this.getAttacker() != null) {
                return false;
            }
            if (!Beaver.this.wantsToPickupItem()) {
                return false;
            }
            if (Beaver.this.getRandom().nextInt(Beaver.PickupItemGoal.toGoalTicks(10)) != 0) {
                return false;
            }
            List<ItemEntity> list = Beaver.this.getWorld().getEntitiesByClass(ItemEntity.class, Beaver.this.getBoundingBox().expand(8.0, 8.0, 8.0), PICKABLE_DROP_FILTER);
            return !list.isEmpty() && Beaver.this.getEquippedStack(EquipmentSlot.MAINHAND).isEmpty();
        }

        @Override
        public void tick() {
            List<ItemEntity> list = Beaver.this.getWorld().getEntitiesByClass(ItemEntity.class, Beaver.this.getBoundingBox().expand(8.0, 8.0, 8.0), PICKABLE_DROP_FILTER);
            ItemStack itemStack = Beaver.this.getEquippedStack(EquipmentSlot.MAINHAND);
            if (itemStack.isEmpty() && !list.isEmpty()) {
                Beaver.this.getNavigation().startMovingTo(list.get(0), .6f);
            }
        }

        @Override
        public void start() {
            List<ItemEntity> list = Beaver.this.getWorld().getEntitiesByClass(ItemEntity.class, Beaver.this.getBoundingBox().expand(8.0, 8.0, 8.0), PICKABLE_DROP_FILTER);
            if (!list.isEmpty()) {
                Beaver.this.getNavigation().startMovingTo(list.get(0), .6f);
            }
        }
    }

    @Override
    protected void initDataTracker() {
        super.initDataTracker();
        this.dataTracker.startTracking(CARRIED_BLOCK, Optional.empty());
    }

    @Override
    @Nullable
    public EntityData initialize(ServerWorldAccess world, LocalDifficulty difficulty, SpawnReason spawnReason, @Nullable EntityData entityData, @Nullable NbtCompound entityNbt) {
        this.initEquipment(world.getRandom(), difficulty);
        return super.initialize(world, difficulty, spawnReason, entityData, entityNbt);
    }

    @Override
    public boolean isBreedingItem(ItemStack stack) {
        return stack.isIn(ItemTags.LOGS);
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return BeaverMod.BEAVER_AMBIENT;
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        this.playSound(SoundEvents.ENTITY_PIG_STEP, 0.15f, 1.5f);
    }

    @Override
    public void playAmbientSound() {
        if (isBaby()) {
            this.playSound(getAmbientSound(), 0.12f, getSoundPitch());
            return;
        }
        this.playSound(getAmbientSound(), 0.07f, getSoundPitch());
    }

    @Override
    protected void initGoals() {
        // Prioritize dam-building over collecting logs
        this.goalSelector.add(0, new EscapeDangerGoal(this, 1.0f));
        this.goalSelector.add(1, new MateGoal(1.0));
        this.goalSelector.add(2, new DamGoal(0.6f, 20));
        this.goalSelector.add(3, new FinishLogGoal());
        this.goalSelector.add(4, new BeavGoal(0.6f, 25, 3));
        this.goalSelector.add(0, new SwimGoal(this));
        this.goalSelector.add(7, new TemptGoal(this, 0.5f, BREEDING_INGREDIENT, false));
        this.goalSelector.add(8, new PickupItemGoal());
        this.goalSelector.add(9, new WanderAroundGoal(this, 0.5f));
        this.goalSelector.add(10, new WaterWanderGoal(1.0));
        this.goalSelector.add(11, new LookAtEntityGoal(this, PlayerEntity.class, 6.0f));
        this.goalSelector.add(12, new LookAroundGoal(this));
    }



    @Nullable
    @Override
    public Beaver createChild(ServerWorld world, PassiveEntity entity) {
        return ModEntities.BEAVER.create(world);
    }

    public class DamGoal extends Goal {
        private static final Direction[] HORIZONTAL_DIRECTIONS = {
                Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST
        };
        private final double speed;
        private final int range;
        private BlockPos targetPos;

        public DamGoal(double speed, int range) {
            this.range = range;
            this.speed = speed;
            this.setControls(EnumSet.of(Goal.Control.MOVE));
        }

        @Override
        public boolean canStart() {
            ItemStack itemStack = Beaver.this.getEquippedStack(EquipmentSlot.MAINHAND);
            if (!itemStack.isEmpty() && itemStack.getItem() instanceof BlockItem) {
                BlockState carriedBlock = Beaver.this.getCarriedBlock();
                if (carriedBlock != null) {
                    this.targetPos = findOptimalWaterTarget();
                    return this.targetPos != null;
                }
            }
            return false;
        }

        @Override
        public boolean shouldContinue() {
            return Beaver.this.getEquippedStack(EquipmentSlot.MAINHAND).getItem() instanceof BlockItem;
        }

        @Override
        public void start() {
            if (this.targetPos != null) {
                if (!Beaver.this.isTouchingWater()) {
                    Beaver.this.getNavigation().startMovingTo(
                            this.targetPos.getX() + 0.5,
                            this.targetPos.getY(),
                            this.targetPos.getZ() + 0.5,
                            this.speed
                    );
                } else {
                    Beaver.this.getNavigation().startMovingTo(
                            this.targetPos.getX() + 0.5,
                            this.targetPos.getY(),
                            this.targetPos.getZ() + 0.5,
                            1.5
                    );
                }
            }
        }

        @Override
        public void stop() {
            BeaverReservationSystem.releaseBeaverReservation(Beaver.this.getUuid());
            this.targetPos = null;
            Beaver.this.getNavigation().stop();
        }

        @Override
        public void tick() {
            super.tick();
            if (this.targetPos == null) {
                return;
            }

            if (Beaver.this.squaredDistanceTo(Vec3d.ofCenter(this.targetPos)) < 2.0D) {
                placeDamBlock();
            } else {
                Beaver.this.getNavigation().startMovingTo(
                        this.targetPos.getX() + 0.5,
                        this.targetPos.getY(),
                        this.targetPos.getZ() + 0.5,
                        this.speed
                );
            }
        }

        // Method to place the block once the beaver reaches the target position
        private void placeDamBlock() {
            ItemStack itemStack = Beaver.this.getEquippedStack(EquipmentSlot.MAINHAND);
            BlockState carriedBlock = Beaver.this.getCarriedBlock();

            if (carriedBlock == null || !(itemStack.getItem() instanceof BlockItem)) {
                return;  // No block to place
            }

            World world = Beaver.this.getWorld();
            boolean blockPlaced = world.setBlockState(targetPos, carriedBlock, Block.NOTIFY_ALL);  // Try placing the block

            if (blockPlaced) {
                // Emit dam building particles and sounds
                world.emitGameEvent(GameEvent.BLOCK_PLACE, targetPos, GameEvent.Emitter.of(Beaver.this, carriedBlock));
                Beaver.this.setCarriedBlock(null);  // Clear the carried block
                itemStack.decrement(1);  // Decrement the item stack after placing the block
                BeaverReservationSystem.releaseBlock(targetPos);
                this.stop();  // Stop the goal after placing the block
            }
        }

        private BlockPos findOptimalWaterTarget() {
            World world = Beaver.this.getWorld();
            BlockPos beaverPos = Beaver.this.getBlockPos();
            int searchRadius = range;

            BlockPos bestPos = null;
            int bestScore = Integer.MIN_VALUE;

            for (BlockPos pos : BlockPos.iterateOutwards(beaverPos, searchRadius, searchRadius, searchRadius)) {
                BlockState blockState = world.getBlockState(pos);

                if (!world.getBlockState(pos.up()).isAir()){
                    continue;
                }

                if (!blockState.getFluidState().isIn(FluidTags.WATER)) {
                    continue;
                }

                if (!BeaverReservationSystem.isBlockFree(pos, Beaver.this.getUuid())) {
                    continue;
                }

                // Check if adjacent to solid block and log (cardinal directions only)
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

                // Count adjacent water blocks (including diagonals)
                int adjacentWaterCount = 0;

                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dx == 0 && dz == 0) {
                            continue; // Skip the current position
                        }
                        BlockPos adjPos = pos.add(dx, 0, dz);
                        BlockState adjState = world.getBlockState(adjPos);
                        if (adjState.getFluidState().isIn(FluidTags.WATER)) {
                            adjacentWaterCount++;
                        }
                    }
                }

                // Compute score based on criteria
                int score = 0;
                if (adjacentToLog) {
                    score += 10; // Prioritize connecting to existing logs
                }
                score += adjacentWaterCount; // More adjacent water blocks preferred

                if (score > bestScore) {
                    bestScore = score;
                    bestPos = pos.toImmutable();
                }
            }

            if (bestPos != null) {
                BeaverReservationSystem.reserveBlock(bestPos, Beaver.this.getUuid());
            }

            return bestPos;
        }

    }


    public class FinishLogGoal extends Goal {
        private static final int EATING_TIME = 30;
        protected int timer = 0;
        private BlockPos targetPos;

        public FinishLogGoal() {
            this.setControls(EnumSet.of(Goal.Control.MOVE));
        }

        @Override
        public boolean canStart() {
            // Only start if there are logs in the queue and the beaver is not holding a block
            return Beaver.this.getEquippedStack(EquipmentSlot.MAINHAND).isEmpty() && !Beaver.this.storedTreeLogs.isEmpty();
        }

        @Override
        public boolean shouldContinue() {
            // If the beaver cannot pathfind to the block, stop.
//            if (Beaver.this.getNavigation().findPathTo(this.targetPos, 2) == null) {
//                if (Beaver.this.getPos().distanceTo(this.targetPos.toCenterPos()) > 2) {
//                    Beaver.this.storedTreeLogs.remove(0);
//                    return false;
//                }
//            }

            // Additional check to prevent the beaver from continuing if it already holds a log
            ItemStack itemStack = Beaver.this.getEquippedStack(EquipmentSlot.MAINHAND);
            if (!itemStack.isEmpty()) {
                return false;
            }
            // Stop after processing one block so the beaver can dam
            return this.timer < EATING_TIME;
        }

        @Override
        public void start() {
            this.timer = 0;  // Reset the eating timer
            this.targetPos = Beaver.this.storedTreeLogs.get(0);  // Get the first log in the list
            super.start();
        }

        @Override
        public void stop() {
            // Ensure to call super.stop() to fully stop the goal
            super.stop();
        }

        @Override
        public void tick() {
            super.tick();
            ServerWorld world = (ServerWorld) Beaver.this.getWorld();

            // Ensure there's a valid target log to process
            if (!Beaver.this.storedTreeLogs.isEmpty()) {
                this.targetPos = Beaver.this.storedTreeLogs.get(0);  // Continuously update the target log block

                // Check if the beaver has reached the log
                if (this.hasReached()) {
                    Beaver.this.startEatingTree();  // Signal that the beaver is eating

                    // Play particles and sound every 5 ticks (twice per second)
                    if (this.timer % 5 == 0) {
                        // Play particle effects and sounds while eating
                        Vec3d blockCenter = new Vec3d(this.targetPos.getX() + 0.5, this.targetPos.getY() + 0.5, this.targetPos.getZ() + 0.5);
                        Vec3d beaverPos = new Vec3d(Beaver.this.getX(), Beaver.this.getY(), Beaver.this.getZ());
                        Vec3d direction = blockCenter.subtract(beaverPos).normalize();
                        double spawnX = blockCenter.x + direction.x * 0.5;
                        double spawnY = blockCenter.y + direction.y * 0.5;
                        double spawnZ = blockCenter.z + direction.z * 0.5;

                        world.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, Beaver.this.getWorld().getBlockState(this.targetPos)),
                                spawnX, spawnY, spawnZ, 200, 0.0D, 0.0D, 0.0D, 2.0D);
                        Beaver.this.playSound(SoundEvents.ENTITY_GENERIC_EAT, .4f, 1.5f);  // Play the eating sound
                    }

                    // Increment the timer
                    this.timer++;

                    // Check if the beaver is done eating
                    if (this.timer >= EATING_TIME) {
                        // Once eating is done, pick up the log
                        this.pickUpLog();
                        Beaver.this.stopEatingTree();  // Stop eating animation
                        Beaver.this.storedTreeLogs.remove(0);  // Remove the log from the list after processing
                        this.timer = 0;  // Reset the timer for the next log
                        this.stop();  // Stop the goal after processing one log to allow DamGoal to take over
                    }
                } else {
                    Beaver.this.getNavigation().startMovingTo(targetPos.getX(), targetPos.getY(), targetPos.getZ(), 0.6);
                }
            }
        }

        private boolean hasReached() {
            // Check if the beaver has reached the target position
            return targetPos.isWithinDistance(Beaver.this.getPos(), 1.5);
        }

        // Method to pick up the log after it's "eaten"
        private void pickUpLog() {
            BlockState state = Beaver.this.getWorld().getBlockState(this.targetPos);
            World world = Beaver.this.getWorld();

            // Equip the log in the beaver's main hand
            Beaver.this.equipStack(EquipmentSlot.MAINHAND, new ItemStack(state.getBlock()));
            Beaver.this.playSound(SoundEvents.BLOCK_WOOD_BREAK, 1.0f, 1.0f);  // Play log breaking sound
            Beaver.this.setCarriedBlock(state);
            world.breakBlock(this.targetPos, false);  // Remove the block from the world
        }
    }

    public class BeavGoal extends MoveToTargetPosGoal {
        private static final int EATING_TIME = 30;
        protected int timer;

        public BeavGoal(double speed, int range, int maxYDifference) {
            super(Beaver.this, speed, range, maxYDifference);
        }

        @Override
        public double getDesiredDistanceToTarget() {
            return 2.95;
        }

        @Override
        public boolean shouldContinue() {
            if (isEatingTree) {
                return true;
            }

            // If the target block is no longer a log, stop.
            if (!isLog(Beaver.this.getWorld().getBlockState(this.targetPos))) {
                return false;
            }

            // If the beaver cannot pathfind to the block, stop.
            if (Beaver.this.getNavigation().findPathTo(this.targetPos, 1) == null) {
                return false;
            }

            // Additional check to prevent the beaver from continuing if it already holds a log
            ItemStack itemStack = Beaver.this.getEquippedStack(EquipmentSlot.MAINHAND);
            if (!itemStack.isEmpty()) {
                return false;
            }

            return super.shouldContinue();
        }

        @Override
        protected boolean isTargetPos(WorldView world, BlockPos pos) {
            BlockState blockState = world.getBlockState(pos);

            if (!blockState.isIn(BlockTags.LOGS)) {
                return false;
            }

            // Check for water around the targeted log.
            if (world.getBlockState(pos.east()).getFluidState().isIn(FluidTags.WATER) ||
                    world.getBlockState(pos.west()).getFluidState().isIn(FluidTags.WATER) ||
                    world.getBlockState(pos.north()).getFluidState().isIn(FluidTags.WATER) ||
                    world.getBlockState(pos.south()).getFluidState().isIn(FluidTags.WATER) ||
                    world.getBlockState(pos.down()).getFluidState().isIn(FluidTags.WATER))
            {
                return false;
            }

            // Ensure it's the second log by checking there's a log below
            if  (!world.getBlockState(pos.down()).isIn(BlockTags.LOGS)) {
                return false;
            }

            //check the second block down which should be dirt or grass or something
            if (world.getBlockState(pos.down().down()).isIn(BlockTags.LOGS)) {
                return false;
            }

            if (!hasLeavesAbove(world, pos)) {
                return false;
            }

            return BeaverReservationSystem.isBlockFree(pos, Beaver.this.getUuid());
        }

        private boolean hasLeavesAbove(WorldView world, BlockPos pos) {
            for (int i = 1; i <= 20; i++) {
                BlockState blockAbove = world.getBlockState(pos.up(i));
                if (blockAbove.isIn(BlockTags.LEAVES)) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public boolean canStop() {
            return this.timer >= EATING_TIME;
        }

        @Override
        public void tick() {
            super.tick();
            ServerWorld world = (ServerWorld) Beaver.this.getWorld();

            if (this.hasReached()) {
                if (this.timer < EATING_TIME) {
                    Beaver.this.startEatingTree();
                    if (this.timer % 5 == 0) { // Play particle effect and sound every second
                        Beaver.this.playSound(SoundEvents.ENTITY_GENERIC_EAT, .4f, 1.5f);
                        Vec3d blockCenter = new Vec3d(this.targetPos.getX() + 0.5, this.targetPos.getY() - 0.5, this.targetPos.getZ() + 0.5);
                        Vec3d beaverPos = new Vec3d(Beaver.this.getX(), Beaver.this.getY(), Beaver.this.getZ());

                        Vec3d direction = blockCenter.subtract(beaverPos).normalize();

                        // Adjust the spawn position to be on the side of the block facing the beaver
                        double spawnX = blockCenter.x + direction.x * 0.5;
                        double spawnY = blockCenter.y + direction.y * 0.5;
                        double spawnZ = blockCenter.z + direction.z * 0.5;

                        world.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, Beaver.this.getWorld().getBlockState(this.targetPos)),
                                spawnX, spawnY, spawnZ, 200,
                                0.0D, 0.0D, 0.0D, 2.0D);
                    }
                    this.timer++;
                } else {
                    this.eatWood();
                    Beaver.this.stopEatingTree();
                    timer = 0; // Reset the timer for the next log
                }
            }
        }

        protected void eatWood() {
            if (!Beaver.this.getWorld().getGameRules().getBoolean(GameRules.DO_MOB_GRIEFING)) {
                return;
            }

            BlockState blockState = Beaver.this.getWorld().getBlockState(this.targetPos);
            if (isLog(blockState)) {
                List<BlockPos> logs = new ArrayList<>();
                BlockPos current = this.targetPos;

                // Collect all logs in the tree before repositioning
                while (isLog(Beaver.this.getWorld().getBlockState(current))) {
                    logs.add(current);
                    current = current.up();
                }

                // Consume the first log
                this.eatLog(blockState);

                // Remove the target position from logs list, so we don’t reprocess it
                logs.remove(this.targetPos);

                // Reposition the remaining logs
                repositionLog(logs);
            }
        }

        private void eatLog(BlockState state) {
            World world = Beaver.this.getWorld();
            Beaver.this.equipStack(EquipmentSlot.MAINHAND, new ItemStack(state.getBlock()));
            Beaver.this.playSound(SoundEvents.BLOCK_WOOD_BREAK, 1.0f, 1.0f);
            Beaver.this.setCarriedBlock(state);
            world.breakBlock(this.targetPos, false);
        }


        private void repositionLog(List<BlockPos> logs) {
            World world = Beaver.this.getWorld();
            for (BlockPos logPos : logs) {
                BlockState logState = world.getBlockState(logPos);

                // Calculate horizontal offset based on the height difference from the target block
                int offset = logPos.getY() - this.targetPos.getY();

                // Adjust the position based on the offset
                BlockPos newPos = this.targetPos.add(offset + 1, -1, 0);
                while (!world.getBlockState(newPos).isAir() && world.getBlockState(newPos).isOpaque()) {
                    newPos = newPos.up();
                }
                while (world.getBlockState(newPos.down()).isAir() || !world.getBlockState(newPos.down()).isOpaque()) {
                    newPos = newPos.down();
                }

                // After repositioning, add the new position to the storedTreeLogs list
                world.setBlockState(newPos, getSidewaysLogState(logState), 3);
                world.removeBlock(logPos, false);
                storedTreeLogs.add(newPos);  // Add the repositioned log to the list
            }
        }

        // Get the sideways state of a log
        private BlockState getSidewaysLogState(BlockState original) {
            if (original.getBlock() instanceof PillarBlock) {
                return original.with(PillarBlock.AXIS, Direction.Axis.X);
            }
            // If the block isn't a PillarBlock (or doesn't have the AXIS property),
            // simply return the original state or some default.
            return original;
        }


        @Override
        public boolean canStart() {
            // Don't begin to beav if they already have a block
            if (!Beaver.this.getEquippedStack(EquipmentSlot.MAINHAND).isEmpty() || !storedTreeLogs.isEmpty()) {
                return false;
            }

            if (super.canStart()) {
                BeaverReservationSystem.reserveBlock(this.targetPos, Beaver.this.getUuid());
                return true;
            }
            return false;
        }

        @Override
        public void stop() {
            BeaverReservationSystem.releaseBeaverReservation(Beaver.this.getUuid());
            targetPos = null;
            timer = 0;
            super.stop();
        }

        @Override
        public void start() {
            this.timer = 0;
            super.start();
        }
    }
}

