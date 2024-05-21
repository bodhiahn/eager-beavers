package bodhi.beaver.entity;

import bodhi.beaver.BeaverMod;
import bodhi.beaver.entity.client.ModEntities;
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
import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.fluid.Fluids;
import net.minecraft.item.*;
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
import net.minecraft.util.ActionResult;
import net.minecraft.util.DyeColor;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
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

public class Beaver extends TameableEntity implements GeoEntity {
    private static final Ingredient BREEDING_INGREDIENT = Ingredient.ofItems(Items.OAK_WOOD, Items.BIRCH_WOOD, Items.DARK_OAK_WOOD, Items.SPRUCE_SAPLING);
    private static final TrackedData<Optional<BlockState>> CARRIED_BLOCK = DataTracker.registerData(Beaver.class, TrackedDataHandlerRegistry.OPTIONAL_BLOCK_STATE);
    static final Predicate<ItemEntity> PICKABLE_DROP_FILTER = item -> !item.cannotPickup() && item.isAlive();
    private int eatingTime;
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    protected static final RawAnimation HOLD_IDLE_ANIM = RawAnimation.begin().thenLoop("animation.beaver.holdidle");
    protected static final RawAnimation HOLD_WALK_ANIM = RawAnimation.begin().thenLoop("animation.beaver.holdwalk");
    protected static final RawAnimation WALK_ANIM = RawAnimation.begin().thenLoop("animation.beaver.walk");
    protected static final RawAnimation IDLE_ANIM = RawAnimation.begin().thenLoop("animation.beaver.idle");
    protected static final RawAnimation SWIM_ANIM = RawAnimation.begin().thenLoop("animation.beaver.swim");
    private boolean isHat = false;

    public Beaver(EntityType<? extends TameableEntity> entityType, World world) {
        super(entityType, world);
        this.setCanPickUpLoot(true);
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
                .add(EntityAttributes.GENERIC_MAX_HEALTH, 20.0D)
                .add(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, 20)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.4f);
    }

    @Override
    protected Identifier getLootTableId() {
        return new Identifier("beavermod", "entities/beaver");
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
    public EntityView method_48926() {
        return this.getWorld();
    }

    @Nullable
    @Override
    public LivingEntity getOwner() {
        return super.getOwner();
    }


    public class BeaverSwimGoal extends Goal {
        private final double swimSpeed;
        private final int diveFrequency; // How often the beaver decides to dive.
        private int diveTimer; // Timer to handle diving and surfacing.

        public BeaverSwimGoal() {
            this.swimSpeed = 1.5; // Adjust as needed
            this.diveFrequency = 80; // Every 80 ticks on average, adjust as needed.
            this.diveTimer = 0;
            this.setControls(EnumSet.of(Goal.Control.JUMP, Goal.Control.MOVE));
            Beaver.this.getNavigation().setCanSwim(true);
        }

        @Override
        public boolean canStart() {
            return Beaver.this.isTouchingWater() && Beaver.this.getFluidHeight(FluidTags.WATER) > (Beaver.this.isBaby() ? 0.1D : 0.2D) || Beaver.this.isInLava();
        }

        @Override
        public void tick() {
            // Randomly decide to dive or come up based on diveTimer.
            if (diveTimer == 0 && Beaver.this.getRandom().nextInt(diveFrequency) == 0) {
                diveTimer = Beaver.this.getRandom().nextInt(40) + 40; // Dive for 40 to 80 ticks.
            }
            if (diveTimer > 0) {
                // When diveTimer is active, move beaver downwards.
                Vec3d motion = Beaver.this.getVelocity();
                Beaver.this.setVelocity(motion.x, -0.03, motion.z); // Adjust -0.3 for faster or slower dives.
                diveTimer--;
            } else {
                // Usual swim behavior.
                if (Beaver.this.getRandom().nextFloat() < 0.8F) {
                    Beaver.this.getJumpControl().setActive();
                }
            }

            // Adjust the beaver's speed while swimming.
            if (Beaver.this.isInsideWaterOrBubbleColumn()) {
                Beaver.this.setMovementSpeed((float) swimSpeed);
            }

            // Create bubbles.
            Beaver.this.getWorld().addParticle(ParticleTypes.BUBBLE, Beaver.this.getX(), Beaver.this.getY(), Beaver.this.getZ(), 0.1, .3, 0.1);
        }
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
        protected void breed() {
            ServerWorld serverWorld = (ServerWorld) this.world;
            Beaver beaver = (Beaver) this.animal.createChild(serverWorld, this.mate);
            if (beaver != null) {
                ServerPlayerEntity serverPlayerEntity = this.animal.getLovingPlayer();
                ServerPlayerEntity serverPlayerEntity2 = this.mate.getLovingPlayer();
                ServerPlayerEntity serverPlayerEntity3 = serverPlayerEntity;
                if (serverPlayerEntity != null) {
                    beaver.setOwner(serverPlayerEntity);
                    beaver.setTamed(true);
                } else {
                    serverPlayerEntity3 = serverPlayerEntity2;
                }
                if (serverPlayerEntity2 != null && serverPlayerEntity != serverPlayerEntity2) {
                    beaver.setOwner(serverPlayerEntity2);
                    beaver.setTamed(true);
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
    public ActionResult interactMob(PlayerEntity player, Hand hand) {
        ItemStack itemStack = player.getStackInHand(hand);
        Item item = itemStack.getItem();
        if (this.getWorld().isClient) {
            boolean bl = this.isOwner(player) || this.isTamed() || itemStack.isOf(Items.STICK) && !this.isTamed();
            return bl ? ActionResult.CONSUME : ActionResult.PASS;
        }
        if (!player.getAbilities().creativeMode) {
            itemStack.decrement(1);
        }
        if (this.random.nextInt(3) == 0) {
            this.setOwner(player);
            this.navigation.stop();
            this.setTarget(null);
            this.setSitting(true);
            this.getWorld().sendEntityStatus(this, EntityStatuses.ADD_POSITIVE_PLAYER_REACTION_PARTICLES);
            return ActionResult.SUCCESS;
        } else {
            this.getWorld().sendEntityStatus(this, EntityStatuses.ADD_NEGATIVE_PLAYER_REACTION_PARTICLES);
        }
        return ActionResult.SUCCESS;
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

    private boolean canEat(ItemStack stack) {
        return stack.getItem().isFood() && this.getTarget() == null && this.isOnGround() && !this.isSleeping();
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
    public boolean canBreatheInWater() {
        return true;
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
        this.goalSelector.add(0, new BeaverSwimGoal());
        this.goalSelector.add(1, new EscapeDangerGoal(this, 1.0f));
        this.goalSelector.add(2, new DamGoal(0.6f, 30, 1));
        this.goalSelector.add(3, new MateGoal(1.0));
        this.goalSelector.add(3, new PickupSidewaysLogGoal(.6f));
        this.goalSelector.add(4, new BeavGoal(.6f, 16, 3));
        this.goalSelector.add(1, new FollowOwnerGoal(this, 1.0, 10.0f, 2.0f, false));
        this.goalSelector.add(7, new TemptGoal(this, .5f, BREEDING_INGREDIENT, false));
        this.goalSelector.add(8, new FollowParentGoal(this, .7f));
        this.goalSelector.add(8, new PickupItemGoal());
        this.goalSelector.add(9, new WanderAroundGoal(this, .5f));
        this.goalSelector.add(10, new LookAtEntityGoal(this, PlayerEntity.class, 6.0f));
        this.goalSelector.add(11, new LookAroundGoal(this));
        this.goalSelector.add(15, new WaterWanderGoal(1.0));

    }

    @Nullable
    @Override
    public Beaver createChild(ServerWorld world, PassiveEntity entity) {
        return ModEntities.BEAVER.create(world);
    }

    public class DamGoal extends MoveToTargetPosGoal {
        public DamGoal(double speed, int range, int maxYDifference) {
            super(Beaver.this, speed, range, maxYDifference);
        }

        @Override
        public double getDesiredDistanceToTarget() {
            return 1.5;
        }

        @Override
        public void tick() {
            super.tick();
            if (this.hasReached()) {
                ItemStack itemStack = Beaver.this.getEquippedStack(EquipmentSlot.MAINHAND);
                BlockState blockState = Beaver.this.getCarriedBlock();
                if (blockState == null) {
                    return;
                }
                World world = Beaver.this.getWorld();
                boolean blockPlaced = world.setBlockState(targetPos, blockState, Block.NOTIFY_ALL);
                if(blockPlaced){
                    world.emitGameEvent(GameEvent.BLOCK_PLACE, targetPos, GameEvent.Emitter.of(Beaver.this, blockState));
                    Beaver.this.setCarriedBlock(null);
                    if (blockState.getBlock().asItem() == itemStack.getItem()) {
                        itemStack.decrement(1);
                    }
                    this.stop();
                }
            }
        }

        @Override
        public boolean canStart() {
            ItemStack itemStack = Beaver.this.getEquippedStack(EquipmentSlot.MAINHAND);
            BlockState carriedBlock = Beaver.this.getCarriedBlock();
            if (!itemStack.isEmpty() || carriedBlock != null) {
                return super.canStart();
            }
            return false;
        }

        @Override
        protected boolean isTargetPos(WorldView world, BlockPos pos) {
            BlockState blockState = world.getBlockState(pos);

            // Early exit if the current block is not water.
            if (blockState.getFluidState().getFluid() != Fluids.WATER) {
                return false;
            }

            boolean touchingSolid = false;

            if (!world.isAir(pos.up())) {
                return false;
            }

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
        protected int timer = 0;
        @Override
        public double getDesiredDistanceToTarget() {
            return 2.0;
        }

        public PickupSidewaysLogGoal(double speed) {
            super(Beaver.this, speed, 10);
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
            if (!isLog(Beaver.this.getWorld().getBlockState(this.targetPos))) {
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
            ServerWorld world = (ServerWorld) Beaver.this.getWorld();
            if (this.hasReached()) {
                if (this.timer < EATING_TIME) {
                    Beaver.this.startEatingTree();
                    if (this.timer % 5 == 0) {
                        Vec3d blockCenter = new Vec3d(this.targetPos.getX() + 0.5, this.targetPos.getY() + 0.5, this.targetPos.getZ() + 0.5);
                        Vec3d beaverPos = new Vec3d(Beaver.this.getX(), Beaver.this.getY(), Beaver.this.getZ());

                        Vec3d direction = blockCenter.subtract(beaverPos).normalize();

                        // Adjust the spawn position to be on the side of the block facing the beaver
                        double spawnX = blockCenter.x + direction.x * 0.5;
                        double spawnY = blockCenter.y + direction.y * 0.5;
                        double spawnZ = blockCenter.z + direction.z * 0.5;

                        world.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, Beaver.this.getWorld().getBlockState(this.targetPos)),
                                spawnX, spawnY, spawnZ, 200,
                                0.0D, 0.0D, 0.0D, 2.0D);

                        Beaver.this.playSound(SoundEvents.ENTITY_GENERIC_EAT, .4f, 1.5f);
                    }
                    this.timer++;
                } else {
                    this.pickUpLog();
                    Beaver.this.stopEatingTree();
                    timer = 0; // Reset the timer for the next log
                }
            }
        }

        private void pickUpLog() {
            BlockState state = Beaver.this.getWorld().getBlockState(this.targetPos);
            if (isLog(state) && isSidewaysLog(state)) {
                World world = Beaver.this.getWorld();
                Beaver.this.equipStack(EquipmentSlot.MAINHAND, new ItemStack(state.getBlock()));
                Beaver.this.playSound(SoundEvents.BLOCK_WOOD_BREAK, 1.0f, 1.0f);
                Beaver.this.setCarriedBlock(state);
                world.breakBlock(this.targetPos, false);
            }
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
            ServerWorld world = (ServerWorld) Beaver.this.getWorld();

            // Additional check to stop the beaver if it already holds a log
            ItemStack itemStack = Beaver.this.getEquippedStack(EquipmentSlot.MAINHAND);
            if (!itemStack.isEmpty()) {
                this.stop();
                return;
            }

            if (this.hasReached()) {
                if (this.timer < EATING_TIME) {
                    Beaver.this.startEatingTree();
                    if (this.timer % 5 == 0) { // Play particle effect and sound every second
                        Vec3d blockCenter = new Vec3d(this.targetPos.getX() + 0.5, this.targetPos.getY() + 0.5, this.targetPos.getZ() + 0.5);
                        Vec3d beaverPos = new Vec3d(Beaver.this.getX(), Beaver.this.getY(), Beaver.this.getZ());

                        Vec3d direction = blockCenter.subtract(beaverPos).normalize();

                        // Adjust the spawn position to be on the side of the block facing the beaver
                        double spawnX = blockCenter.x + direction.x * 0.5;
                        double spawnY = blockCenter.y + direction.y * 0.5;
                        double spawnZ = blockCenter.z + direction.z * 0.5;

                        world.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, Beaver.this.getWorld().getBlockState(this.targetPos)),
                                spawnX, spawnY, spawnZ, 200,
                                0.0D, 0.0D, 0.0D, 2.0D);
                        Beaver.this.playSound(SoundEvents.ENTITY_GENERIC_EAT, .4f, 1.5f);
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
                // Add logs to a list starting from the targetPos and moving upward
                List<BlockPos> logs = new ArrayList<>();
                BlockPos current = this.targetPos;
                while (isLog(Beaver.this.getWorld().getBlockState(current))) {
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
                world.setBlockState(newPos, getSidewaysLogState(logState), 3);
                world.removeBlock(logPos, false);
            }
        }


        @Override
        public boolean canStart() {
            if (!super.canStart()) {
                return false; // Return false if the superclass's canStart method returns false
            }
            ItemStack itemStack = Beaver.this.getEquippedStack(EquipmentSlot.MAINHAND);
            return itemStack.isEmpty();
        }


        @Override
        public void start() {
            this.timer = 0;
            super.start();
        }
    }
}

