package bodhi.beaver.entity;

import bodhi.beaver.entity.client.ModEntities;
import bodhi.beaver.sound.ModSounds;
import com.google.common.collect.Lists;
import net.minecraft.advancement.criterion.Criteria;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.PillarBlock;
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
import net.minecraft.fluid.Fluids;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.nbt.NbtList;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ItemStackParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.recipe.Ingredient;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.stat.Stats;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.*;
import net.minecraft.world.event.GameEvent;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.GeoAnimatable;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;


import java.util.*;
import java.util.function.Predicate;

public class Beaver extends AnimalEntity implements GeoEntity {
    private static final Ingredient BREEDING_INGREDIENT = Ingredient.ofItems(Items.OAK_WOOD, Items.BIRCH_WOOD, Items.DARK_OAK_WOOD, Items.SPRUCE_SAPLING);
    private static final TrackedData<Optional<UUID>> OWNER = DataTracker.registerData(Beaver.class, TrackedDataHandlerRegistry.OPTIONAL_UUID);
    private static final TrackedData<Optional<UUID>> OTHER_TRUSTED = DataTracker.registerData(Beaver.class, TrackedDataHandlerRegistry.OPTIONAL_UUID);
    private static final TrackedData<Optional<BlockState>> CARRIED_BLOCK = DataTracker.registerData(Beaver.class, TrackedDataHandlerRegistry.OPTIONAL_BLOCK_STATE);
    static final Predicate<ItemEntity> PICKABLE_DROP_FILTER = item -> !item.cannotPickup() && item.isAlive();
    private int eatingTime;
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    protected static final RawAnimation HOLD_IDLE_ANIM = RawAnimation.begin().thenLoop("animation.beaver.holdidle");
    protected static final RawAnimation HOLD_WALK_ANIM = RawAnimation.begin().thenLoop("animation.beaver.holdwalk");
    protected static final RawAnimation WALK_ANIM = RawAnimation.begin().thenLoop("animation.beaver.walk");
    protected static final RawAnimation IDLE_ANIM = RawAnimation.begin().thenLoop("animation.beaver.idle");
    protected static final RawAnimation SWIM_ANIM = RawAnimation.begin().thenLoop("animation.beaver.swim");

    public Beaver(EntityType<? extends AnimalEntity> entityType, World world) {
        super(entityType, world);
        this.setCanPickUpLoot(true);
    }
    @Override
    public boolean isPushedByFluids() {
        return false;
    }
    public static DefaultAttributeContainer.Builder setAttributes() {
        return AnimalEntity.createMobAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, 20.0D)
                .add(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, 20)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.4f);
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

    public class BeaverSwimGoal extends Goal {
        private final Beaver beaver;
        private final double swimSpeed;
        private final int diveFrequency; // How often the beaver decides to dive.
        private int diveTimer; // Timer to handle diving and surfacing.

        public BeaverSwimGoal(Beaver beaver) {
            this.beaver = beaver;
            this.swimSpeed = 1.5; // Adjust as needed
            this.diveFrequency = 80; // Every 80 ticks on average, adjust as needed.
            this.diveTimer = 0;
            this.setControls(EnumSet.of(Goal.Control.JUMP, Goal.Control.MOVE));
            beaver.getNavigation().setCanSwim(true);
        }

        @Override
        public boolean canStart() {
            return beaver.isTouchingWater() && beaver.getFluidHeight(FluidTags.WATER) > (beaver.isBaby() ? 0.1D : 0.2D) || beaver.isInLava();
        }

        @Override
        public void tick() {
            // Randomly decide to dive or come up based on diveTimer.
            if (diveTimer == 0 && beaver.getRandom().nextInt(diveFrequency) == 0) {
                diveTimer = beaver.getRandom().nextInt(40) + 40; // Dive for 40 to 80 ticks.
            }
            if (diveTimer > 0) {
                // When diveTimer is active, move beaver downwards.
                Vec3d motion = beaver.getVelocity();
                beaver.setVelocity(motion.x, -0.03, motion.z); // Adjust -0.3 for faster or slower dives.
                diveTimer--;
            } else {
                // Usual swim behavior.
                if (beaver.getRandom().nextFloat() < 0.8F) {
                    beaver.getJumpControl().setActive();
                }
            }

            // Adjust the beaver's speed while swimming.
            if (beaver.isInsideWaterOrBubbleColumn()) {
                beaver.setMovementSpeed((float) swimSpeed);
            }

            // Create bubbles.
            beaver.world.addParticle(ParticleTypes.BUBBLE, beaver.getX(), beaver.getY(), beaver.getZ(), 0.1, .3, 0.1);
        }
    }
    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(genericWalkIdleController(this));
        controllers.add(swimController(this));
        controllers.add(eatingController(this));
    }

    public static <T extends GeoAnimatable> AnimationController<Beaver> genericWalkIdleController(Beaver entity) {
        return new AnimationController<Beaver>(entity, "Walk/Idle", 0, state -> {
            boolean isHoldingItem = !entity.getEquippedStack(EquipmentSlot.MAINHAND).isEmpty();
            boolean isSwimming = entity.isTouchingWater();

            if (!isSwimming) {
                if (state.isMoving()) {
                    return state.setAndContinue(isHoldingItem ? HOLD_WALK_ANIM : WALK_ANIM);
                } else {
                    return state.setAndContinue(isHoldingItem ? HOLD_IDLE_ANIM : IDLE_ANIM);
                }
            }
            return PlayState.STOP;
        });
    }
    public static <T extends GeoAnimatable> AnimationController<Beaver> swimController(Beaver beaver) {
        return new AnimationController<Beaver>(beaver, "Swim", 0, state -> {
            if (beaver.isTouchingWater())
                return state.setAndContinue(SWIM_ANIM);
            return PlayState.STOP;
            });
    }

    public static <T extends GeoAnimatable> AnimationController<Beaver> eatingController(Beaver beaver) {
        return new AnimationController<Beaver>(beaver, "Eat", 0, state -> {
            if (beaver.isEatingTree())
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

    void addTrustedUuid(@Nullable UUID uuid) {
        if (this.dataTracker.get(OWNER).isPresent()) {
            this.dataTracker.set(OTHER_TRUSTED, Optional.ofNullable(uuid));
        } else {
            this.dataTracker.set(OWNER, Optional.ofNullable(uuid));
        }
    }

    public class WaterWanderGoal extends Goal {
        private final Beaver beaver;
        private final double speed;
        private double targetX;
        private double targetY;
        private double targetZ;

        public WaterWanderGoal(Beaver beaver, double speed) {
            this.beaver = beaver;
            this.speed = speed;
            setControls(EnumSet.of(Goal.Control.MOVE));
        }

        @Override
        public boolean canStart() {
            if (beaver.isTouchingWater() && beaver.getNavigation().isIdle()) {
                // We'll generate a random destination in the water every few ticks
                if (beaver.getRandom().nextInt(10) == 0) {
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
            return !beaver.getNavigation().isIdle();
        }

        @Override
        public void start() {
            beaver.getNavigation().startMovingTo(targetX, targetY, targetZ, speed);
        }

        private Vec3d generateWaterTarget() {
            Random random = beaver.getRandom();
            double angle = random.nextDouble() * 2 * Math.PI;
            double distance = 8 + random.nextDouble() * 8;
            double targetX = beaver.getX() + Math.sin(angle) * distance;
            double targetZ = beaver.getZ() + Math.cos(angle) * distance;
            double targetY = beaver.getY() + random.nextDouble() * 6 - 3;

            BlockPos targetPos = new BlockPos((int) targetX, (int) targetY, (int) targetZ);
            while (!beaver.world.getBlockState(targetPos).getFluidState().isIn(FluidTags.WATER) && targetPos.getY() > 1) {
                targetPos = targetPos.down();
            }

            return beaver.world.getBlockState(targetPos).getFluidState().isIn(FluidTags.WATER) ? new Vec3d(targetX, targetY, targetZ) : null;
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
        protected void breed() {
            ServerWorld serverWorld = (ServerWorld)this.world;
            Beaver beaver = (Beaver)this.animal.createChild(serverWorld, this.mate);
            if (beaver == null) {
                return;
            }
            ServerPlayerEntity serverPlayerEntity = this.animal.getLovingPlayer();
            ServerPlayerEntity serverPlayerEntity2 = this.mate.getLovingPlayer();
            ServerPlayerEntity serverPlayerEntity3 = serverPlayerEntity;
            if (serverPlayerEntity != null) {
                beaver.addTrustedUuid(serverPlayerEntity.getUuid());
            } else {
                serverPlayerEntity3 = serverPlayerEntity2;
            }
            if (serverPlayerEntity2 != null && serverPlayerEntity != serverPlayerEntity2) {
                beaver.addTrustedUuid(serverPlayerEntity2.getUuid());
            }
            if (serverPlayerEntity3 != null) {
                serverPlayerEntity3.incrementStat(Stats.ANIMALS_BRED);
                Criteria.BRED_ANIMALS.trigger(serverPlayerEntity3, this.animal, this.mate, beaver);
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

    private void dropItem(ItemStack stack) {
        ItemEntity itemEntity = new ItemEntity(this.world, this.getX(), this.getY(), this.getZ(), stack);
        this.world.spawnEntity(itemEntity);
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
                    this.world.addParticle(new ItemStackParticleEffect(ParticleTypes.ITEM, itemStack), this.getX() + this.getRotationVector().x / 2.0, this.getY(), this.getZ() + this.getRotationVector().z / 2.0, vec3d.x, vec3d.y + 0.05, vec3d.z);
                }
            }
        } else {
            super.handleStatus(status);
        }
    }
    @Override
    protected void initEquipment(Random random, LocalDifficulty localDifficulty) {
        //used to have them start with logs occasionally, but its unnecessary
    }

    @Override
    public boolean canEquip(ItemStack stack) {
        EquipmentSlot equipmentSlot = MobEntity.getPreferredEquipmentSlot(stack);
        if (!this.getEquippedStack(equipmentSlot).isEmpty()) {
            return false;
        }
        return equipmentSlot == EquipmentSlot.MAINHAND && super.canEquip(stack);
    }
    private boolean canEat(ItemStack stack) {
        return stack.getItem().isFood() && this.getTarget() == null && this.onGround && !this.isSleeping();
    }

    List<UUID> getTrustedUuids() {
        ArrayList<UUID> list = Lists.newArrayList();
        list.add(this.dataTracker.get(OWNER).orElse(null));
        list.add(this.dataTracker.get(OTHER_TRUSTED).orElse(null));
        return list;
    }

    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        List<UUID> list = this.getTrustedUuids();
        NbtList nbtList = new NbtList();
        for (UUID uUID : list) {
            if (uUID == null) continue;
            nbtList.add(NbtHelper.fromUuid(uUID));
        }
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        NbtList nbtList = nbt.getList("Trusted", NbtElement.INT_ARRAY_TYPE);
        for (int i = 0; i < nbtList.size(); ++i) {
            this.addTrustedUuid(NbtHelper.toUuid(nbtList.get(i)));
        }
    }

    @Override
    public boolean canPickupItem(ItemStack stack) {
        Item item = stack.getItem();
        ItemStack itemStack = this.getEquippedStack(EquipmentSlot.MAINHAND);
        return itemStack.isEmpty() || this.eatingTime > 0 && item.isFood() && !itemStack.getItem().isFood();
    }

    boolean wantsToPickupItem() {
        return !this.isSleeping();
    }

    class PickupItemGoal extends Goal {
        private final Beaver beaver;
        public PickupItemGoal(Beaver mob) {
            this.setControls(EnumSet.of(Goal.Control.MOVE));
            this.beaver = mob;
        }

        @Override
        public boolean canStart() {
            if (!this.beaver.getEquippedStack(EquipmentSlot.MAINHAND).isEmpty()) {
                return false;
            }
            if (this.beaver.getTarget() != null || this.beaver.getAttacker() != null) {
                return false;
            }
            if (!this.beaver.wantsToPickupItem()) {
                return false;
            }
            if (this.beaver.getRandom().nextInt(Beaver.PickupItemGoal.toGoalTicks(10)) != 0) {
                return false;
            }
            List<ItemEntity> list = this.beaver.world.getEntitiesByClass(ItemEntity.class, this.beaver.getBoundingBox().expand(8.0, 8.0, 8.0), PICKABLE_DROP_FILTER);
            return !list.isEmpty() && this.beaver.getEquippedStack(EquipmentSlot.MAINHAND).isEmpty();
        }

        @Override
        public void tick() {
            List<ItemEntity> list = this.beaver.world.getEntitiesByClass(ItemEntity.class, this.beaver.getBoundingBox().expand(8.0, 8.0, 8.0), PICKABLE_DROP_FILTER);
            ItemStack itemStack = this.beaver.getEquippedStack(EquipmentSlot.MAINHAND);
            if (itemStack.isEmpty() && !list.isEmpty()) {
                this.beaver.getNavigation().startMovingTo(list.get(0), .6f);
            }
        }

        @Override
        public void start() {
            List<ItemEntity> list = this.beaver.world.getEntitiesByClass(ItemEntity.class, this.beaver.getBoundingBox().expand(8.0, 8.0, 8.0), PICKABLE_DROP_FILTER);
            if (!list.isEmpty()) {
                this.beaver.getNavigation().startMovingTo(list.get(0), .6f);
            }
        }
    }
    @Override
    public boolean canBreatheInWater() {
        return true;
    }


    @Override
    protected void initDataTracker() {
        super.initDataTracker();
        this.dataTracker.startTracking(CARRIED_BLOCK, Optional.empty());
        this.dataTracker.startTracking(OWNER, Optional.empty());
        this.dataTracker.startTracking(OTHER_TRUSTED, Optional.empty());
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
        return ModSounds.BEAVER_AMBIENT;
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        this.playSound(SoundEvents.ENTITY_PIG_STEP, 0.15f, 1.5f);
    }

    @Override
    public void playAmbientSound() {
        if (isBaby()) {
            this.playSound(getAmbientSound(), 0.3f, getSoundPitch());
            return;
        }
        this.playSound(getAmbientSound(), 0.10f, getSoundPitch());
    }

    @Override
    protected void initGoals() {
        this.goalSelector.add(0, new BeaverSwimGoal(this));
        this.goalSelector.add(1, new EscapeDangerGoal(this, 1.0f));
        this.goalSelector.add(8, new PickupItemGoal(this));
        this.goalSelector.add(3, new MateGoal(1.0));
        this.goalSelector.add(15, new WaterWanderGoal(this, 1.0));
        this.goalSelector.add(2, new Beaver.DamGoal(this, 0.6f, 30, 1));
        this.goalSelector.add(4, new Beaver.BeavGoal(this, .6f, 16, 3));
        this.goalSelector.add(3, new Beaver.PickupSidewaysLogGoal(this, .6f));
        this.goalSelector.add(7, new TemptGoal(this, .5f, BREEDING_INGREDIENT, false));
        this.goalSelector.add(8, new FollowParentGoal(this, .7f));
        this.goalSelector.add(9, new WanderAroundGoal(this, .5f));
        this.goalSelector.add(10, new LookAtEntityGoal(this, PlayerEntity.class, 6.0f));
        this.goalSelector.add(11, new LookAroundGoal(this));
    }

    @Nullable
    @Override
    public Beaver createChild(ServerWorld world, PassiveEntity entity) {
        return ModEntities.BEAVER.create(world);
    }
    public class DamGoal extends MoveToTargetPosGoal {
        private final Beaver beaver;
        public DamGoal(Beaver mob, double speed, int range, int maxYDifference) {
            super(mob, speed, range, maxYDifference);
            this.beaver = mob;
        }

        @Override
        public double getDesiredDistanceToTarget() {
            return 2.0;
        }

        @Override
        public void tick() {
            super.tick();
            if (this.hasReached()) {
                ItemStack itemStack = this.beaver.getEquippedStack(EquipmentSlot.MAINHAND);
                BlockState blockState = this.beaver.getCarriedBlock();
                if (blockState == null) {
                    return;
                }
                World world = this.beaver.world;
                boolean blockPlaced = world.setBlockState(targetPos, blockState, Block.NOTIFY_ALL);
                if(blockPlaced){
                    world.emitGameEvent(GameEvent.BLOCK_PLACE, targetPos, GameEvent.Emitter.of(this.beaver, blockState));
                    this.beaver.setCarriedBlock(null);
                    if (blockState.getBlock().asItem() == itemStack.getItem()) {
                        itemStack.decrement(1);
                    }
                    this.stop();
                }
            }
        }

        @Override
        public boolean canStart() {
            ItemStack itemStack = this.beaver.getEquippedStack(EquipmentSlot.MAINHAND);
            BlockState carriedBlock = this.beaver.getCarriedBlock();
            if (!itemStack.isEmpty() || carriedBlock != null) {
                return super.canStart();
            }
            return false;
        }
        @Override
        public boolean shouldResetPath() {
            return this.tryingTime % 50 == 0;
        }

        @Override
        protected boolean isTargetPos(WorldView world, BlockPos pos) {
            BlockState blockState = world.getBlockState(pos);

            // Early exit if the current block is not water.
            if (blockState.getFluidState().getFluid() != Fluids.WATER) {
                return false;
            }

            boolean touchingSolid = false;

            // Count the number of adjacent water blocks and check if there's a solid block.
            for (Direction dir : Direction.values()) {
                BlockState adjacentState = world.getBlockState(pos.offset(dir));
                if (dir != Direction.UP && adjacentState.isOpaque() && dir != Direction.DOWN) {
                    touchingSolid = true;
                }
                if (world.getBlockState(pos.up()).getFluidState().getFluid() == Fluids.WATER) {
                    touchingSolid = false;
                }
            }

            // Add a condition to check if the current location has the maximum number of adjacent water blocks.
            return touchingSolid;
        }
    }

    // Check if the block is a log
    private boolean isLog(BlockState state) {
        return state.isIn(BlockTags.LOGS);
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

    public class PickupSidewaysLogGoal extends MoveToTargetPosGoal {
        private static final int EATING_TIME = 30;
        private final Beaver beaver;
        protected int timer = 0;
        @Override
        public double getDesiredDistanceToTarget() {
            return 2.0;
        }

        public PickupSidewaysLogGoal(Beaver beaver, double speed) {
            super(beaver, speed, 10);
            this.beaver = beaver;
        }

        @Override
        public boolean canStop() {
            return this.timer >= EATING_TIME;
        }

        private boolean isSidewaysLog(BlockState state) {
            if (state.getBlock() instanceof PillarBlock) {
                Direction.Axis axis = state.get(PillarBlock.AXIS);
                return axis == Direction.Axis.X || axis == Direction.Axis.Z;
            }
            return false;
        }
        @Override
        protected boolean isTargetPos(WorldView world, BlockPos pos) {
            BlockState state = world.getBlockState(pos);
            // First check if it's a log and then check its orientation.
            return isLog(state) && isSidewaysLog(state) && !isNearWater(world, pos);
        }

        private boolean isNearWater(WorldView world, BlockPos pos) {
            return world.getBlockState(pos.east()).getFluidState().isIn(FluidTags.WATER) ||
                    world.getBlockState(pos.west()).getFluidState().isIn(FluidTags.WATER) ||
                    world.getBlockState(pos.north()).getFluidState().isIn(FluidTags.WATER) ||
                    world.getBlockState(pos.south()).getFluidState().isIn(FluidTags.WATER);
        }

        @Override
        public boolean shouldContinue() {
            // If the target block is no longer a log, stop.
            if (!isLog(beaver.world.getBlockState(this.targetPos))) {
                return false;
            }

            // If the beaver cannot pathfind to the block, stop.
//            if (this.beaver.getNavigation().findPathTo(this.targetPos, 1) == null) {
//                return false;
//            }

            return super.shouldContinue();
        }

        @Override
        public void tick() {
            super.tick();
            ServerWorld world = (ServerWorld) beaver.getWorld();
            if (this.hasReached()) {
                if (this.timer < EATING_TIME) {
                    this.beaver.startEatingTree();
                    if (this.timer % 5 == 0) {
                        Vec3d blockCenter = new Vec3d(this.targetPos.getX() + 0.5, this.targetPos.getY() + 0.5, this.targetPos.getZ() + 0.5);
                        Vec3d beaverPos = new Vec3d(beaver.getX(), beaver.getY(), beaver.getZ());

                        Vec3d direction = blockCenter.subtract(beaverPos).normalize();

                        // Adjust the spawn position to be on the side of the block facing the beaver
                        double spawnX = blockCenter.x + direction.x * 0.5;
                        double spawnY = blockCenter.y + direction.y * 0.5;
                        double spawnZ = blockCenter.z + direction.z * 0.5;

                        world.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, this.beaver.world.getBlockState(this.targetPos)),
                                spawnX, spawnY, spawnZ, 200,
                                0.0D, 0.0D, 0.0D, 2.0D);

                        this.beaver.playSound(SoundEvents.ENTITY_GENERIC_EAT, .4f, 1.5f);
                    }
                    this.timer++;
                } else {
                    this.pickUpLog();
                    this.beaver.stopEatingTree();
                    timer = 0; // Reset the timer for the next log
                }
            }
        }

        private void pickUpLog() {
            BlockState state = this.beaver.world.getBlockState(this.targetPos);
            if (isLog(state) && isSidewaysLog(state)) {
                World world = this.beaver.world;
                this.beaver.equipStack(EquipmentSlot.MAINHAND, new ItemStack(state.getBlock()));
                this.beaver.playSound(SoundEvents.BLOCK_WOOD_BREAK, 1.0f, 1.0f);
                this.beaver.setCarriedBlock(state);
                world.breakBlock(this.targetPos, false);
            }
        }
    }


    public class BeavGoal extends MoveToTargetPosGoal {
        private static final int EATING_TIME = 30;
        protected int timer;
        private final Beaver beaver;
        public BeavGoal(Beaver mob, double speed, int range, int maxYDifference) {
            super(mob, speed, range, maxYDifference);
            this.beaver = mob;
        }

        @Override
        public double getDesiredDistanceToTarget() {
            return 2.95;
        }
        @Override
        public boolean shouldContinue() {
            // If the target block is no longer a log, stop.
            if (!isLog(beaver.world.getBlockState(this.targetPos))) {
                return false;
            }

            // If the beaver cannot pathfind to the block, stop.
//            if (this.beaver.getNavigation().findPathTo(this.targetPos, 1) == null) {
//                return false;
//            }

            return super.shouldContinue();
        }
        @Override
        public boolean shouldResetPath() {
            return this.tryingTime % 100 == 0;
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
                    world.getBlockState(pos.south()).getFluidState().isIn(FluidTags.WATER)) {
                return false;
            }
            // Ensure it's the second log by checking there's a log below, and NOT a log above.
            return world.getBlockState(pos.down()).isIn(BlockTags.LOGS);
        }
        @Override
        public boolean canStop() {
            return this.timer >= EATING_TIME;
        }
        @Override
        public void tick() {
            super.tick();
            ServerWorld world = (ServerWorld) beaver.getWorld();
            if (this.hasReached()) {
                if (this.timer < EATING_TIME) {
                    this.beaver.startEatingTree();
                    if (this.timer % 5 == 0) { // Play particle effect and sound every second
                        Vec3d blockCenter = new Vec3d(this.targetPos.getX() + 0.5, this.targetPos.getY() + 0.5, this.targetPos.getZ() + 0.5);
                        Vec3d beaverPos = new Vec3d(beaver.getX(), beaver.getY(), beaver.getZ());

                        Vec3d direction = blockCenter.subtract(beaverPos).normalize();

                        // Adjust the spawn position to be on the side of the block facing the beaver
                        double spawnX = blockCenter.x + direction.x * 0.5;
                        double spawnY = blockCenter.y + direction.y * 0.5;
                        double spawnZ = blockCenter.z + direction.z * 0.5;

                        world.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, this.beaver.world.getBlockState(this.targetPos)),
                                spawnX, spawnY, spawnZ, 200,
                                0.0D, 0.0D, 0.0D, 2.0D);
                        this.beaver.playSound(SoundEvents.ENTITY_GENERIC_EAT, .4f, 1.5f);
                    }
                    this.timer++;
                } else {
                    this.eatWood();
                    this.beaver.stopEatingTree();
                    timer = 0; // Reset the timer for the next log
                }
            }
        }

        protected void eatWood() {
            if (!this.beaver.world.getGameRules().getBoolean(GameRules.DO_MOB_GRIEFING)) {
                return;
            }
            BlockState blockState = this.beaver.world.getBlockState(this.targetPos);
            if (isLog(blockState)) {
                // Add logs to a list starting from the targetPos and moving upward
                List<BlockPos> logs = new ArrayList<>();
                BlockPos current = this.targetPos;
                while (isLog(this.beaver.world.getBlockState(current))) {
                    logs.add(current);
                    current = current.up();
                }

                // Consume the first log in the list
                this.eatLog(blockState);

                // Remove the target position from logs list, so we don't reprocess it
                logs.remove(this.targetPos);

                // Reposition the remaining logs
                repositionLog(logs);

            }
        }

        private void eatLog(BlockState state) {
            World world = this.beaver.world;
            this.beaver.equipStack(EquipmentSlot.MAINHAND, new ItemStack(state.getBlock()));
            this.beaver.playSound(SoundEvents.BLOCK_WOOD_BREAK, 1.0f, 1.0f);
            this.beaver.setCarriedBlock(state);
            world.breakBlock(this.targetPos, false);
        }

        private void repositionLog(List<BlockPos> logs) {
            World world = this.beaver.world;
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
                world.setBlockState(newPos, getSidewaysLogState(logState), 3);
                world.removeBlock(logPos, false);
            }
        }

        @Override
        public boolean canStart() {
            if (!super.canStart()) {
                return false; // Return false if the superclass's canStart method returns false
            }
            ItemStack itemStack = this.beaver.getEquippedStack(EquipmentSlot.MAINHAND);
            return itemStack.isEmpty();
        }


        @Override
        public void start() {
            this.timer = 0;
            super.start();
        }
    }
}

