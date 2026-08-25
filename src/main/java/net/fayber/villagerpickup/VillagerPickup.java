package net.fayber.villagerpickup;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.component.TypedEntityData;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.phys.BlockHitResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class VillagerPickup implements ModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("villager_pickup");

    private static boolean isTrapped(ItemStack stack) {
        if (stack.getItem() != Items.VILLAGER_SPAWN_EGG) return false;
        TypedEntityData<?> data = stack.get(DataComponents.ENTITY_DATA);
        if (data == null) return false;
        return data.contains("VillagerPickupMarker");
    }

    private static EntitySpawnReason spawnReason() {
        EntitySpawnReason reason = EntitySpawnReason.SPAWNER;
        try {
            reason = EntitySpawnReason.valueOf("SPAWN_ITEM_USE");
        } catch (Exception ignored) {
            // keep SPAWNER fallback
        }
        return reason;
    }

    /** 26.2 removed the EntityType.* constants; look the villager type up. */
    @SuppressWarnings("unchecked")
    private static EntityType<Villager> villagerType() {
        return (EntityType<Villager>) BuiltInRegistries.ENTITY_TYPE.getValue(
                Identifier.fromNamespaceAndPath("minecraft", "villager"));
    }

    @Override
    public void onInitialize() {
        String version = FabricLoader.getInstance()
                .getModContainer("villager_pickup")
                .map(c -> c.getMetadata().getVersion().getFriendlyString())
                .orElse("unknown");
        LOGGER.info("Villager Pickup v{} Initialized!", version);

        UseEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (player.isSpectator()) return InteractionResult.PASS;
            if (!(entity instanceof Villager villager)) return InteractionResult.PASS;
            if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;

            ItemStack stack = player.getItemInHand(hand);
            boolean trapped = isTrapped(stack);

            if (world.isClientSide()) {
                if (player.isShiftKeyDown()) return InteractionResult.SUCCESS;
                if (!player.isShiftKeyDown() && trapped) return InteractionResult.SUCCESS;
                return InteractionResult.PASS;
            }

            // A) SNEAKING -> CAPTURE
            if (player.isShiftKeyDown()) {
                try {
                    ItemStack egg = Items.VILLAGER_SPAWN_EGG.getDefaultInstance();

                    if (villager.isBaby()) {
                        egg.set(DataComponents.CUSTOM_NAME, Component.literal("Baby Villager Spawn Egg")
                            .withStyle(ChatFormatting.WHITE).withStyle(s -> s.withItalic(false)));
                    }

                    TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, world.registryAccess());
                    villager.saveWithoutId(output);
                    CompoundTag nbt = output.buildResult();

                    nbt.remove("Pos");
                    nbt.remove("Motion");
                    nbt.remove("Rotation");
                    nbt.remove("UUID");
                    nbt.remove("Dimension");
                    nbt.putBoolean("VillagerPickupMarker", true);

                    egg.set(DataComponents.ENTITY_DATA, TypedEntityData.of(villagerType(), nbt));

                    List<Component> loreLines = new ArrayList<>();
                    VillagerData vData = villager.getVillagerData();
                    String profId = vData.profession().unwrapKey().map(key -> key.identifier().getPath()).orElse("none");
                    String profName = profId.substring(0, 1).toUpperCase() + profId.substring(1);

                    loreLines.add(Component.literal("Profession: ").withStyle(ChatFormatting.GRAY).withStyle(s -> s.withItalic(false))
                        .append(Component.literal(profName).withStyle(ChatFormatting.GOLD).withStyle(s -> s.withItalic(false))));

                    int level = vData.level();
                    String levelStr = (level >= 5) ? level + " (MAX)" : String.valueOf(level);
                    loreLines.add(Component.literal("Level: ").withStyle(ChatFormatting.YELLOW).withStyle(s -> s.withItalic(false))
                        .append(Component.literal(levelStr).withStyle(ChatFormatting.WHITE).withStyle(s -> s.withItalic(false))));

                    villager.getBrain().getMemory(MemoryModuleType.JOB_SITE).ifPresent(pos ->
                        loreLines.add(Component.literal("Workstation: ").withStyle(ChatFormatting.GRAY).withStyle(s -> s.withItalic(false))
                            .append(Component.literal(pos.pos().getX() + ", " + pos.pos().getY() + ", " + pos.pos().getZ()).withStyle(ChatFormatting.WHITE).withStyle(s -> s.withItalic(false)))));

                    villager.getBrain().getMemory(MemoryModuleType.HOME).ifPresent(pos ->
                        loreLines.add(Component.literal("Bed: ").withStyle(ChatFormatting.GRAY).withStyle(s -> s.withItalic(false))
                            .append(Component.literal(pos.pos().getX() + ", " + pos.pos().getY() + ", " + pos.pos().getZ()).withStyle(ChatFormatting.WHITE).withStyle(s -> s.withItalic(false)))));

                    MerchantOffers offers = villager.getOffers();
                    if (!offers.isEmpty()) {
                        loreLines.add(Component.empty());
                        loreLines.add(Component.literal("Trades:").withStyle(ChatFormatting.GRAY).withStyle(s -> s.withItalic(false)));
                        for (MerchantOffer offer : offers) {
                            MutableComponent tradeLine = Component.literal(" - ").withStyle(ChatFormatting.DARK_GRAY).withStyle(s -> s.withItalic(false));
                            ItemStack costA = offer.getCostA();
                            tradeLine.append(Component.literal(costA.getCount() + " ").withStyle(ChatFormatting.WHITE).withStyle(s -> s.withItalic(false)));
                            tradeLine.append(costA.getHoverName().copy().withStyle(ChatFormatting.WHITE).withStyle(s -> s.withItalic(false)));
                            ItemStack costB = offer.getCostB();
                            if (!costB.isEmpty()) {
                                tradeLine.append(Component.literal(" + ").withStyle(ChatFormatting.GRAY).withStyle(s -> s.withItalic(false)));
                                tradeLine.append(Component.literal(costB.getCount() + " ").withStyle(ChatFormatting.WHITE).withStyle(s -> s.withItalic(false)));
                                tradeLine.append(costB.getHoverName().copy().withStyle(ChatFormatting.WHITE).withStyle(s -> s.withItalic(false)));
                            }
                            tradeLine.append(Component.literal(" -> ").withStyle(ChatFormatting.YELLOW).withStyle(s -> s.withItalic(false)));
                            ItemStack result = offer.getResult();
                            if (result.getItem() == Items.ENCHANTED_BOOK) {
                                ItemEnchantments enchants = EnchantmentHelper.getEnchantmentsForCrafting(result);
                                if (!enchants.isEmpty()) {
                                    Holder<Enchantment> entry = enchants.keySet().iterator().next();
                                    tradeLine.append(Enchantment.getFullname(entry, enchants.getLevel(entry)).copy().withStyle(ChatFormatting.AQUA).withStyle(s -> s.withItalic(false)));
                                } else {
                                    tradeLine.append(result.getHoverName().copy().withStyle(ChatFormatting.WHITE).withStyle(s -> s.withItalic(false)));
                                }
                            } else {
                                tradeLine.append(Component.literal(result.getCount() + " ").withStyle(ChatFormatting.WHITE).withStyle(s -> s.withItalic(false)));
                                tradeLine.append(result.getHoverName().copy().withStyle(ChatFormatting.WHITE).withStyle(s -> s.withItalic(false)));
                            }
                            loreLines.add(tradeLine);
                        }
                    }

                    egg.set(DataComponents.LORE, new ItemLore(loreLines));

                    if (!player.getInventory().add(egg)) {
                        player.drop(egg, false, false);
                    }

                    villager.discard();
                    world.playSound(null, player.blockPosition(), SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 1.0F, 1.0F);

                    return InteractionResult.SUCCESS;
                } catch (Exception e) {
                    LOGGER.error("[VillagerPickup] Capture FAILED:", e);
                }
            }

            // B) NOT SNEAKING -> RELEASE (TRAPPED ONLY)
            if (!player.isShiftKeyDown() && trapped) {
                ItemStack stackToSpawn = stack.copy();
                stackToSpawn.remove(DataComponents.CUSTOM_NAME);

                Villager spawned = villagerType().spawn((ServerLevel) world, stackToSpawn, player, villager.blockPosition(), spawnReason(), true, false);
                if (spawned != null) {
                    if (!player.getAbilities().instabuild) {
                        stack.shrink(1);
                    }
                    return InteractionResult.SUCCESS;
                }
            }

            return InteractionResult.PASS;
        });

        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            if (world.isClientSide()) return InteractionResult.PASS;
            if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;

            ItemStack stack = player.getItemInHand(hand);
            if (isTrapped(stack)) {
                if (world instanceof ServerLevel serverWorld) {
                    ItemStack stackToSpawn = stack.copy();
                    stackToSpawn.remove(DataComponents.CUSTOM_NAME);

                    BlockPos spawnPos = ((BlockHitResult) hitResult).getBlockPos().relative(((BlockHitResult) hitResult).getDirection());
                    Villager spawned = villagerType().spawn(serverWorld, stackToSpawn, player, spawnPos, spawnReason(), true, false);
                    if (spawned != null) {
                        if (!player.getAbilities().instabuild) {
                            stack.shrink(1);
                        }
                        return InteractionResult.SUCCESS;
                    }
                }
            }

            return InteractionResult.PASS;
        });
    }
}
