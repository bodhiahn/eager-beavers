package bodhi.beaver.entity;

import bodhi.beaver.BeaverMod;
import bodhi.beaver.entity.client.ModEntities;
import com.google.common.collect.Lists;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.WorldLoader;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.GeoAnimatable;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.Level;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;

import java.util.*;
import java.util.function.Predicate;


public class Beaver extends Animal implements GeoEntity {
    private static final Ingredient BREEDING_INGREDIENT = Ingredient.of(Items.OAK_WOOD, Items.BIRCH_WOOD, Items.DARK_OAK_WOOD, Items.SPRUCE_SAPLING);
    private static final EntityDataAccessor<Optional<UUID>> OWNER = SynchedEntityData.defineId(Beaver.class, TrackedDataHandlerRegistry.OPTIONAL_UUID);
    private static final EntityDataAccessor<Optional<UUID>> OTHER_TRUSTED = SynchedEntityData.defineId(Beaver.class, TrackedDataHandlerRegistry.OPTIONAL_UUID);
    private static final EntityDataAccessor<Optional<BlockState>> CARRIED_BLOCK = SynchedEntityData.defineId(Beaver.class, TrackedDataHandlerRegistry.OPTIONAL_BLOCK_STATE);
    static final Predicate<ItemEntity> PICKABLE_DROP_FILTER = item -> !item.isPickable() && item.isAlive();
    private int eatingTime;
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    protected static final RawAnimation HOLD_IDLE_ANIM = RawAnimation.begin().thenLoop("animation.beaver.holdidle");
    protected static final RawAnimation HOLD_WALK_ANIM = RawAnimation.begin().thenLoop("animation.beaver.holdwalk");
    protected static final RawAnimation WALK_ANIM = RawAnimation.begin().thenLoop("animation.beaver.walk");
    protected static final RawAnimation IDLE_ANIM = RawAnimation.begin().thenLoop("animation.beaver.idle");
    protected static final RawAnimation SWIM_ANIM = RawAnimation.begin().thenLoop("animation.beaver.swim");
    private boolean isHat = false;

    public Beaver(EntityType<? extends Animal> entityType, Level world) {
        super(entityType, world);
        this.setCanPickUpLoot(true);
    }

    public boolean isHat() {
        return isHat;
    }
    public void setHat() {
        isHat = true;
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

    @Nullable
    @Override
    public AgeableMob getBreedOffspring(ServerLevel p_146743_, AgeableMob p_146744_) {
        return null;
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
            this.setFlags(EnumSet.of(Goal.Flag.JUMP, Goal.Flag.MOVE));
            beaver.getNavigation().setCanFloat(true);
        }

        @Override
        public boolean canUse() {
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
                Vec3 motion = beaver.getDeltaMovement();
                beaver.setDeltaMovement(motion.x, -0.03, motion.z); // Adjust -0.3 for faster or slower dives.
                diveTimer--;
            } else {
                // Usual swim behavior.
                if (beaver.getRandom().nextFloat() < 0.8F) {
                    beaver.getJumpControl().setActive();
                }
            }

            // Adjust the beaver's speed while swimming.
            if (beaver.isInLiquid()) {
                beaver.setSpeed((float) swimSpeed);
            }

            // Create bubbles.
            beaver.level().addParticle(ParticleTypes.BUBBLE, beaver.getX(), beaver.getY(), beaver.getZ(), 0.1, .3, 0.1);
        }
    }

    private boolean isTouchingWater() {
        return this.wasTouchingWater;
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
        controllers.add(hatController(this));
    }

    public static <T extends GeoAnimatable> AnimationController<Beaver> genericWalkIdleController(Beaver entity) {
        return new AnimationController<Beaver>(entity, "Walk/Idle", 0, state -> {
            boolean isHoldingItem = !entity.getItemInHand(InteractionHand.MAIN_HAND).isEmpty();
            boolean isSwimming = entity.isTouchingWater();
            boolean isHat = entity.isHat();
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

    public static <T extends GeoAnimatable> AnimationController<Beaver> hatController(Beaver entity) {
        return new AnimationController<Beaver>(entity, "Hat", 0, state -> {
            boolean isHat = entity.isHat();
            if (isHat){
                return PlayState.CONTINUE;
            }
            return PlayState.STOP;
        });
    }

    public static <T extends GeoAnimatable> AnimationController<Beaver> swimController(Beaver beaver) {
        return new AnimationController<Beaver>(beaver, "Swim", 0, state -> {
            boolean isHat = beaver.isHat();
            if (beaver.isTouchingWater() && !isHat)
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
        this..set(CARRIED_BLOCK, Optional.ofNullable(state));
    }

    @Nullable
    public BlockState getCarriedBlock() {
        return this.dataTracker.get(CARRIED_BLOCK).orElse(null);
    }

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
            while (!beaver.getWorld().getBlockState(targetPos).getFluidState().isIn(FluidTags.WATER) && targetPos.getY() > 1) {
                targetPos = targetPos.down();
            }

            return beaver.getWorld().getBlockState(targetPos).getFluidState().isIn(FluidTags.WATER) ? new Vec3d(targetX, targetY, targetZ) : null;
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
        return stack.getItem().isFood() && this.getTarget() == null && this.isOnGround() && !this.isSleeping();
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
            List<ItemEntity> list = this.beaver.getWorld().getEntitiesByClass(ItemEntity.class, this.beaver.getBoundingBox().expand(8.0, 8.0, 8.0), PICKABLE_DROP_FILTER);
            return !list.isEmpty() && this.beaver.getEquippedStack(EquipmentSlot.MAINHAND).isEmpty();
        }

        @Override
        public void tick() {
            List<ItemEntity> list = this.beaver.getWorld().getEntitiesByClass(ItemEntity.class, this.beaver.getBoundingBox().expand(8.0, 8.0, 8.0), PICKABLE_DROP_FILTER);
            ItemStack itemStack = this.beaver.getEquippedStack(EquipmentSlot.MAINHAND);
            if (itemStack.isEmpty() && !list.isEmpty()) {
                this.beaver.getNavigation().startMovingTo(list.get(0), .6f);
            }
        }

        @Override
        public void start() {
            List<ItemEntity> list = this.beaver.getWorld().getEntitiesByClass(ItemEntity.class, this.beaver.getBoundingBox().expand(8.0, 8.0, 8.0), PICKABLE_DROP_FILTER);
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
                World world = this.beaver.getWorld();
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
            if (!isLog(beaver.getWorld().getBlockState(this.targetPos))) {
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

                        world.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, this.beaver.getWorld().getBlockState(this.targetPos)),
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
            BlockState state = this.beaver.getWorld().getBlockState(this.targetPos);
            if (isLog(state) && isSidewaysLog(state)) {
                World world = this.beaver.getWorld();
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
            if (!isLog(beaver.getWorld().getBlockState(this.targetPos))) {
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

                        world.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, this.beaver.getWorld().getBlockState(this.targetPos)),
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
            if (!this.beaver.getWorld().getGameRules().getBoolean(GameRules.DO_MOB_GRIEFING)) {
                return;
            }
            BlockState blockState = this.beaver.getWorld().getBlockState(this.targetPos);
            if (isLog(blockState)) {
                // Add logs to a list starting from the targetPos and moving upward
                List<BlockPos> logs = new ArrayList<>();
                BlockPos current = this.targetPos;
                while (isLog(this.beaver.getWorld().getBlockState(current))) {
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
            World world = this.beaver.getWorld();
            this.beaver.equipStack(EquipmentSlot.MAINHAND, new ItemStack(state.getBlock()));
            this.beaver.playSound(SoundEvents.BLOCK_WOOD_BREAK, 1.0f, 1.0f);
            this.beaver.setCarriedBlock(state);
            world.breakBlock(this.targetPos, false);
        }

        private void repositionLog(List<BlockPos> logs) {
            World world = this.beaver.getWorld();
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

