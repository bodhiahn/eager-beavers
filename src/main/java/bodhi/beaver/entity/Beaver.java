package bodhi.beaver.entity;

import bodhi.beaver.BeaverMod;
import bodhi.beaver.entity.client.ModEntities;
import com.mojang.serialization.Dynamic;
import net.minecraft.advancement.criterion.Criteria;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.PillarBlock;
import net.minecraft.entity.*;
import net.minecraft.entity.ai.brain.Brain;
import net.minecraft.entity.ai.goal.*;
import net.minecraft.entity.ai.pathing.AmphibiousSwimNavigation;
import net.minecraft.entity.ai.pathing.EntityNavigation;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.passive.PassiveEntity;
import net.minecraft.entity.passive.SnifferBrain;
import net.minecraft.entity.passive.SnifferEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.*;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ItemStackParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.recipe.Ingredient;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.server.network.DebugInfoSender;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.stat.Stats;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
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

    final List<BlockPos> storedTreeLogs = new ArrayList<>();

    protected static final RawAnimation HOLD_IDLE_ANIM = RawAnimation.begin().thenLoop("animation.beaver.holdidle");
    protected static final RawAnimation HOLD_WALK_ANIM = RawAnimation.begin().thenLoop("animation.beaver.holdwalk");
    protected static final RawAnimation WALK_ANIM = RawAnimation.begin().thenLoop("animation.beaver.walk");
    protected static final RawAnimation IDLE_ANIM = RawAnimation.begin().thenLoop("animation.beaver.idle");
    protected static final RawAnimation SWIM_ANIM = RawAnimation.begin().thenLoop("animation.beaver.swim");
    private boolean isHat = false;

    public Beaver(EntityType<? extends AnimalEntity> entityType, World world) {
        super(entityType, world);
        this.setCanPickUpLoot(true);
        this.getNavigation().setCanSwim(true); // Allow swimming navigation
        //this.moveControl = new AquaticMoveControl(this, 85, 10, 0.02f, 0.1f, true);
    }

    @Override
    public Brain<Beaver> getBrain() {
        return (Brain<Beaver>) super.getBrain();
    }

    @Override
    protected void sendAiDebugData() {
        super.sendAiDebugData();
        DebugInfoSender.sendBrainDebugData(this);
    }

    @Override
    protected Brain<?> deserializeBrain(Dynamic<?> dynamic) {
        return BeaverBrain.create(this.createBrainProfile().deserialize(dynamic));
    }

    protected Brain.Profile<Beaver> createBrainProfile() {
        return Brain.createProfile(BeaverBrain.MEMORY_MODULES, BeaverBrain.SENSORS);
    }

    @Override
    protected void mobTick() {
        this.getWorld().getProfiler().push("beaverBrain");
        Brain<Beaver> beaverBrain = this.getBrain(); // Get a properly typed brain
        beaverBrain.tick((ServerWorld) this.getWorld(), this); // Pass the correct type to tick()
        this.getWorld().getProfiler().swap("beaverActivityUpdate");
        BeaverBrain.updateActivities(this);
        this.getWorld().getProfiler().pop();
        super.mobTick();
    }

    @Override
    protected EntityNavigation createNavigation(World world) {
        return new AmphibiousSwimNavigation(this, world);
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

    @Override
    public ActionResult interactMob(PlayerEntity player, Hand hand) {
        ItemStack itemStack = player.getStackInHand(hand);
        boolean bl = this.isBreedingItem(itemStack);
        ActionResult actionResult = super.interactMob(player, hand);
        if (actionResult.isAccepted() && bl) {
            this.getWorld().playSoundFromEntity(null, this, this.getEatSound(itemStack), SoundCategory.NEUTRAL, 1.0f, MathHelper.nextBetween(this.getWorld().random, 0.8f, 1.2f));
        }
        return actionResult;
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

    private boolean canEat(ItemStack stack) {
        return stack.getItem().isFood() && this.getTarget() == null && this.isOnGround() && !this.isSleeping();
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

    @Nullable
    @Override
    public Beaver createChild(ServerWorld world, PassiveEntity entity) {
        return ModEntities.BEAVER.create(world);
    }
}