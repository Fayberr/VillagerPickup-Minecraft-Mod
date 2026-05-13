package net.fayber.villagerpickup;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.TypedEntityData;
import net.minecraft.entity.ai.brain.MemoryModuleType;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.storage.NbtWriteView;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.ErrorReporter;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.village.VillagerData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class VillagerPickup implements ModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("villager_pickup");

    @Override
    public void onInitialize() {
        LOGGER.info("Villager Pickup v4.0.8 Initialized!");

        UseEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (player.isSpectator()) return ActionResult.PASS;
            if (!(entity instanceof VillagerEntity villager)) return ActionResult.PASS;
            if (hand != Hand.MAIN_HAND) return ActionResult.PASS;

            ItemStack stack = player.getStackInHand(hand);

            if (world.isClient()) {
                if (player.isSneaking()) return ActionResult.SUCCESS;
                if (!player.isSneaking() && stack.isOf(Items.VILLAGER_SPAWN_EGG)) return ActionResult.SUCCESS;
                return ActionResult.PASS;
            }

            // A) SNEAKING -> CAPTURE
            if (player.isSneaking()) {
                try {
                    ItemStack egg = Items.VILLAGER_SPAWN_EGG.getDefaultStack();
                    if (villager.isBaby()) {
                        egg.set(DataComponentTypes.CUSTOM_NAME, Text.literal("Baby Villager Spawn Egg").formatted(Formatting.WHITE).styled(s -> s.withItalic(false)));
                    }

                    NbtWriteView writeView = NbtWriteView.create(ErrorReporter.EMPTY, world.getRegistryManager());
                    villager.writeData(writeView);
                    NbtCompound nbt = writeView.getNbt();

                    nbt.remove("Pos");
                    nbt.remove("Motion");
                    nbt.remove("Rotation");
                    nbt.remove("UUID");
                    nbt.remove("Dimension");

                    egg.set(DataComponentTypes.ENTITY_DATA, TypedEntityData.create(EntityType.VILLAGER, nbt));

                    List<Text> loreLines = new ArrayList<>();
                    VillagerData vData = villager.getVillagerData();
                    String profId = vData.profession().getKey().map(key -> key.getValue().getPath()).orElse("none");
                    String profName = profId.substring(0, 1).toUpperCase() + profId.substring(1);

                    loreLines.add(Text.literal("Profession: ").formatted(Formatting.GRAY).styled(s -> s.withItalic(false))
                        .append(Text.literal(profName).formatted(Formatting.GOLD).styled(s -> s.withItalic(false))));

                    int level = vData.level();
                    String levelStr = (level >= 5) ? level + " (MAX)" : String.valueOf(level);
                    loreLines.add(Text.literal("Level: ").formatted(Formatting.YELLOW).styled(s -> s.withItalic(false))
                        .append(Text.literal(levelStr).formatted(Formatting.WHITE).styled(s -> s.withItalic(false))));

                    villager.getBrain().getOptionalRegisteredMemory(MemoryModuleType.JOB_SITE).ifPresent(pos ->
                        loreLines.add(Text.literal("Workstation: ").formatted(Formatting.GRAY).styled(s -> s.withItalic(false))
                            .append(Text.literal(pos.pos().getX() + ", " + pos.pos().getY() + ", " + pos.pos().getZ()).formatted(Formatting.WHITE).styled(s -> s.withItalic(false)))));

                    villager.getBrain().getOptionalRegisteredMemory(MemoryModuleType.HOME).ifPresent(pos ->
                        loreLines.add(Text.literal("Bed: ").formatted(Formatting.GRAY).styled(s -> s.withItalic(false))
                            .append(Text.literal(pos.pos().getX() + ", " + pos.pos().getY() + ", " + pos.pos().getZ()).formatted(Formatting.WHITE).styled(s -> s.withItalic(false)))));

                    var offers = villager.getOffers();
                    if (!offers.isEmpty()) {
                        loreLines.add(Text.empty());
                        loreLines.add(Text.literal("Trades:").formatted(Formatting.GRAY).styled(s -> s.withItalic(false)));
                        for (var offer : offers) {
                            MutableText tradeLine = Text.literal(" - ").formatted(Formatting.DARK_GRAY).styled(s -> s.withItalic(false));
                            var costA = offer.getDisplayedFirstBuyItem();
                            tradeLine.append(Text.literal(costA.getCount() + " ").formatted(Formatting.WHITE).styled(s -> s.withItalic(false)));
                            tradeLine.append(costA.getName().copy().formatted(Formatting.WHITE).styled(s -> s.withItalic(false)));
                            var costB = offer.getDisplayedSecondBuyItem();
                            if (!costB.isEmpty()) {
                                tradeLine.append(Text.literal(" + ").formatted(Formatting.GRAY).styled(s -> s.withItalic(false)));
                                tradeLine.append(Text.literal(costB.getCount() + " ").formatted(Formatting.WHITE).styled(s -> s.withItalic(false)));
                                tradeLine.append(costB.getName().copy().formatted(Formatting.WHITE).styled(s -> s.withItalic(false)));
                            }
                            tradeLine.append(Text.literal(" -> ").formatted(Formatting.YELLOW).styled(s -> s.withItalic(false)));
                            var result = offer.getSellItem();
                            if (result.isOf(Items.ENCHANTED_BOOK)) {
                                var enchants = EnchantmentHelper.getEnchantments(result);
                                if (!enchants.isEmpty()) {
                                    var entry = enchants.getEnchantments().iterator().next();
                                    tradeLine.append(Enchantment.getName(entry, enchants.getLevel(entry)).copy().formatted(Formatting.AQUA).styled(s -> s.withItalic(false)));
                                } else {
                                    tradeLine.append(result.getName().copy().formatted(Formatting.WHITE).styled(s -> s.withItalic(false)));
                                }
                            } else {
                                tradeLine.append(Text.literal(result.getCount() + " ").formatted(Formatting.WHITE).styled(s -> s.withItalic(false)));
                                tradeLine.append(result.getName().copy().formatted(Formatting.WHITE).styled(s -> s.withItalic(false)));
                            }
                            loreLines.add(tradeLine);
                        }
                    }

                    egg.set(DataComponentTypes.LORE, new LoreComponent(loreLines));
                    if (!player.getInventory().insertStack(egg)) {
                        player.dropItem(egg, false);
                    }
                    villager.discard();
                    world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_ITEM_PICKUP, SoundCategory.PLAYERS, 1.0F, 1.0F);
                    return ActionResult.SUCCESS;
                } catch (Exception e) {
                    LOGGER.error("[VillagerPickup] Capture FAILED:", e);
                }
            }

            // B) NOT SNEAKING -> RELEASE (Captured or Vanilla)
            if (!player.isSneaking() && stack.isOf(Items.VILLAGER_SPAWN_EGG)) {
                SpawnReason reason = SpawnReason.SPAWNER;
                try { reason = SpawnReason.valueOf("SPAWN_EGG"); } catch (Exception ignored) {}
                
                var spawned = EntityType.VILLAGER.spawnFromItemStack((ServerWorld) world, stack, player, villager.getBlockPos(), reason, true, false);
                if (spawned != null) {
                    if (!player.getAbilities().creativeMode) {
                        stack.decrement(1);
                    }
                    return ActionResult.SUCCESS;
                }
            }

            return ActionResult.PASS;
        });

        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            if (world.isClient()) return ActionResult.PASS;
            if (hand != Hand.MAIN_HAND) return ActionResult.PASS;
            ItemStack stack = player.getStackInHand(hand);
            if (stack.isOf(Items.VILLAGER_SPAWN_EGG) && stack.contains(DataComponentTypes.ENTITY_DATA)) {
                return ActionResult.PASS;
            }
            return ActionResult.PASS;
        });
    }
}
