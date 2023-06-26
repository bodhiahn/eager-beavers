package bodhi.beaver.entity;

import bodhi.beaver.entity.client.ModEntities;
import bodhi.beaver.sound.ModSounds;
import com.google.common.collect.Lists;
import net.minecraft.advancement.criterion.Criteria;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.*;
import net.minecraft.entity.ai.goal.*;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.mob.EndermanEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.passive.PassiveEntity;
import net.minecraft.entity.passive.TurtleEntity;
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
import net.minecraft.particle.ItemStackParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.recipe.Ingredient;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.stat.Stats;
import net.minecraft.tag.BlockTags;
import net.minecraft.tag.FluidTags;
import net.minecraft.tag.ItemTags;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.*;
import net.minecraft.world.event.GameEvent;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib3.core.IAnimatable;
import software.bernie.geckolib3.core.PlayState;
import software.bernie.geckolib3.core.builder.AnimationBuilder;
import software.bernie.geckolib3.core.builder.ILoopType;
import software.bernie.geckolib3.core.controller.AnimationController;
import software.bernie.geckolib3.core.event.predicate.AnimationEvent;
import software.bernie.geckolib3.core.manager.AnimationData;
import software.bernie.geckolib3.core.manager.AnimationFactory;
import software.bernie.geckolib3.util.GeckoLibUtil;

import java.util.*;
import java.util.function.Predicate;

public class Beaver extends AnimalEntity implements IAnimatable {
    private static final Ingredient BREEDING_INGREDIENT = Ingredient.ofItems(Items.OAK_WOOD, Items.BIRCH_WOOD, Items.DARK_OAK_WOOD, Items.SPRUCE_SAPLING);
    private static final TrackedData<Optional<UUID>> OWNER = DataTracker.registerData(Beaver.class, TrackedDataHandlerRegistry.OPTIONAL_UUID);
    private static final TrackedData<Optional<UUID>> OTHER_TRUSTED = DataTracker.registerData(Beaver.class, TrackedDataHandlerRegistry.OPTIONAL_UUID);
    private static final TrackedData<Optional<BlockState>> CARRIED_BLOCK = DataTracker.registerData(EndermanEntity.class, TrackedDataHandlerRegistry.OPTIONAL_BLOCK_STATE);

    static final Predicate<ItemEntity> PICKABLE_DROP_FILTER = item -> !item.cannotPickup() && item.isAlive();
    private int eatingTime;
    private final AnimationFactory factory = GeckoLibUtil.createFactory(this);
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
    public class BeaverSwimGoal extends Goal {
        private final Beaver beaver;

        public BeaverSwimGoal(Beaver beaver) {
            this.beaver = beaver;
            this.setControls(EnumSet.of(Goal.Control.JUMP));
            beaver.getNavigation().setCanSwim(true);
        }

        @Override
        public boolean canStart() {
            return beaver.isTouchingWater() && beaver.getFluidHeight(FluidTags.WATER) > (beaver.isBaby() ? 0.1D : 0.2D) || beaver.isInLava();
        }

        public void tick() {
            if (beaver.getRandom().nextFloat() < 0.8F) {
                beaver.getJumpControl().setActive();
            }

            world.addParticle(ParticleTypes.BUBBLE ,beaver.getX(), beaver.getY(), beaver.getZ(), 0.1, .3, 0.1);
        }
    }

    private <E extends IAnimatable> PlayState predicate(AnimationEvent<E> event) {
        boolean isSwimming = this.isTouchingWater();
        boolean isHoldingItem = !this.getEquippedStack(EquipmentSlot.MAINHAND).isEmpty();
        if (isSwimming) {
            event.getController().setAnimation(new AnimationBuilder().addAnimation("animation.beaver.swim", ILoopType.EDefaultLoopTypes.LOOP));
            return PlayState.CONTINUE;
        } else if (event.isMoving() && !isSwimming) {
            if (!isHoldingItem) {
                event.getController().setAnimation(new AnimationBuilder().addAnimation("animation.beaver.walk", ILoopType.EDefaultLoopTypes.LOOP));
            } else {
                event.getController().setAnimation(new AnimationBuilder().addAnimation("animation.beaver.holdwalk", ILoopType.EDefaultLoopTypes.LOOP));
            }
            return PlayState.CONTINUE;
        } else {
            if (!isHoldingItem) {
                event.getController().setAnimation(new AnimationBuilder().addAnimation("animation.beaver.idle", ILoopType.EDefaultLoopTypes.LOOP));
            } else {
                event.getController().setAnimation(new AnimationBuilder().addAnimation("animation.beaver.holdidle", ILoopType.EDefaultLoopTypes.LOOP));
            }
            return PlayState.CONTINUE;
        }
    }

    @Override
    public void registerControllers(AnimationData animationData) {
        animationData.addAnimationController(new AnimationController(this, "controller",
                0, this::predicate));
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
        if (!this.world.isClient && this.isAlive() && this.canMoveVoluntarily()) {
            ++this.eatingTime;
            ItemStack itemStack = this.getEquippedStack(EquipmentSlot.MAINHAND);
            if (this.canEat(itemStack)) {
                if (this.eatingTime > 600) {
                    ItemStack itemStack2 = itemStack.finishUsing(this.world, this);
                    if (!itemStack2.isEmpty()) {
                        this.equipStack(EquipmentSlot.MAINHAND, itemStack2);
                    }
                    this.eatingTime = 0;
                } else if (this.eatingTime > 560 && this.random.nextFloat() < 0.1f) {
                    this.playSound(this.getEatSound(itemStack), 1.0f, 1.0f);
                    this.world.sendEntityStatus(this, EntityStatuses.CREATE_EATING_PARTICLES);
                }
            }
        }
        if (this.isTouchingWater()) {
            ItemStack itemStack = this.getEquippedStack(EquipmentSlot.MAINHAND);
            BlockState blockState3 = this.getCarriedBlock();
            if (blockState3 != null) {
                world.setBlockState(this.getBlockPos(), blockState3, Block.NOTIFY_NEIGHBORS);
                //world.emitGameEvent(GameEvent.BLOCK_PLACE, this.getBlockPos(), GameEvent.Emitter.of(this, blockState3));
                this.setCarriedBlock(null);
                itemStack.decrement(1);
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
            this.triggerItemPickedUpByEntityCriteria(item);
            this.equipStack(EquipmentSlot.MAINHAND, itemStack.split(1));
            this.updateDropChances(EquipmentSlot.MAINHAND);
            this.sendPickup(item, itemStack.getCount());
            if (itemStack.getItem() instanceof BlockItem blockItem) {
                if (blockItem.getBlock().getDefaultState().isIn(BlockTags.LOGS)) {
                    setCarriedBlock(blockItem.getBlock().getDefaultState());
                }
            }
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
        if (random.nextFloat() < 0.2f) {
            float f = random.nextFloat();
            ItemStack itemStack = new ItemStack(Items.OAK_LOG);
            this.equipStack(EquipmentSlot.MAINHAND, itemStack);
            Beaver.this.setCarriedBlock(Blocks.OAK_LOG.getDefaultState());
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
    public AnimationFactory getFactory() {
        return factory;
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

    class PickupItemGoal
            extends Goal {
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
            List<ItemEntity> list = Beaver.this.world.getEntitiesByClass(ItemEntity.class, Beaver.this.getBoundingBox().expand(8.0, 8.0, 8.0), PICKABLE_DROP_FILTER);
            return !list.isEmpty() && Beaver.this.getEquippedStack(EquipmentSlot.MAINHAND).isEmpty();
        }

        @Override
        public void tick() {
            List<ItemEntity> list = Beaver.this.world.getEntitiesByClass(ItemEntity.class, Beaver.this.getBoundingBox().expand(8.0, 8.0, 8.0), PICKABLE_DROP_FILTER);
            ItemStack itemStack = Beaver.this.getEquippedStack(EquipmentSlot.MAINHAND);
            if (itemStack.isEmpty() && !list.isEmpty()) {
                Beaver.this.getNavigation().startMovingTo(list.get(0), .6f);
            }
        }

        @Override
        public void start() {
            List<ItemEntity> list = Beaver.this.world.getEntitiesByClass(ItemEntity.class, Beaver.this.getBoundingBox().expand(8.0, 8.0, 8.0), PICKABLE_DROP_FILTER);
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
    public void playAmbientSound() {
        if (isBaby()) {
            this.playSound(ModSounds.BEAVER_AMBIENT, 0.3f, getSoundPitch());
            return;
        }
        this.playSound(ModSounds.BEAVER_AMBIENT, 0.10f, getSoundPitch());
    }

    @Override
    protected void initGoals() {
        this.goalSelector.add(0, new BeaverSwimGoal(this));
        this.goalSelector.add(1, new EscapeDangerGoal(this, 1.25f));
        this.goalSelector.add(11, new PickupItemGoal());
        this.goalSelector.add(3, new MateGoal(1.0));
        this.goalSelector.add(5, new Beaver.DamGoal(this, 0.6f, 30, 1));
        this.goalSelector.add(6, new Beaver.BeavGoal(.6f, 15, 1));
        this.goalSelector.add(4, new TemptGoal(this, .5f, BREEDING_INGREDIENT, false));
        this.goalSelector.add(5, new FollowParentGoal(this, .7f));
        this.goalSelector.add(7, new WanderAroundGoal(this, .5f));
        this.goalSelector.add(8, new LookAtEntityGoal(this, PlayerEntity.class, 6.0f));
        this.goalSelector.add(9, new LookAroundGoal(this));
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
                BlockState blockState3 = this.beaver.getCarriedBlock();
                if (blockState3 == null) {
                    return;
                }
                world.setBlockState(targetPos, blockState3, Block.NOTIFY_ALL);
                world.emitGameEvent(GameEvent.BLOCK_PLACE, targetPos, GameEvent.Emitter.of(this.beaver, blockState3));
                this.beaver.setCarriedBlock(null);
                itemStack.decrement(1);
            }
        }

        @Override
        public boolean canStart() {
            ItemStack itemStack = Beaver.this.getEquippedStack(EquipmentSlot.MAINHAND);
            if (!itemStack.isEmpty()) {
                return super.canStart();

            }
            return false;
        }

        @Override
        protected boolean isTargetPos(WorldView world, BlockPos pos) {
            BlockState blockState = world.getBlockState(pos);
            return (blockState.getFluidState().getFluid() == Fluids.WATER);
        }
    }


    public class BeavGoal
            extends MoveToTargetPosGoal {
        private static final int EATING_TIME = 20;
        protected int timer;

        public BeavGoal(double speed, int range, int maxYDifference) {
            super(Beaver.this, speed, range, maxYDifference);
        }

        @Override
        public double getDesiredDistanceToTarget() {
            return 2.0;
        }

        @Override
        public boolean shouldResetPath() {
            return this.tryingTime % 100 == 0;
        }

        @Override
        protected boolean isTargetPos(WorldView world, BlockPos pos) {
            BlockState blockState = world.getBlockState(pos);
            if (blockState.isOf(Blocks.OAK_LOG)) {
                if (!(world.getBlockState(pos.east()).getFluidState().isIn(FluidTags.WATER) || world.getBlockState(pos.north()).getFluidState().isIn(FluidTags.WATER) || world.getBlockState(pos.south()).getFluidState().isIn(FluidTags.WATER))) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public void tick() {
            if (this.hasReached()) {
                if (this.timer >= 10) {
                    Beaver.this.playSound(SoundEvents.ENTITY_GENERIC_EAT, 1.0f, 1.0f);
                    this.eatWood();
                } else {
                    ++this.timer;
                }
            }
            super.tick();
        }

        protected void eatWood() {
            if (!Beaver.this.world.getGameRules().getBoolean(GameRules.DO_MOB_GRIEFING)) {
                return;
            }
            BlockState blockState = Beaver.this.world.getBlockState(this.targetPos);
            if (blockState.isOf(Blocks.OAK_LOG)) {
                this.eatLog(blockState);
            }
        }

        private void eatLog(BlockState state) {
            ItemStack itemStack = Beaver.this.getEquippedStack(EquipmentSlot.MAINHAND);
            if (itemStack.isEmpty()) {
                Beaver.this.equipStack(EquipmentSlot.MAINHAND, new ItemStack(Beaver.this.world.getBlockState(this.targetPos).getBlock()));
            }
            Beaver.this.playSound(SoundEvents.BLOCK_WOOD_BREAK, 1.0f, 1.0f);
            Beaver.this.setCarriedBlock(Beaver.this.world.getBlockState(this.targetPos));
            Beaver.this.world.breakBlock(this.targetPos, false);
        }

        @Override
        public boolean canStart() {
            super.canStart();
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

