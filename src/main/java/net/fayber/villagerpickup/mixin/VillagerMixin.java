package net.fayber.villagerpickup.mixin;

import net.fayber.villagerpickup.VillagerPickup;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.TypedEntityData;
import net.minecraft.entity.ai.brain.MemoryModuleType;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
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
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

@Mixin(value = VillagerEntity.class, priority = 10000)
public abstract class VillagerMixin {

    @Inject(method = "interactMob", at = @At("HEAD"), cancellable = true)
    private void onInteractMob(PlayerEntity player, Hand hand, CallbackInfoReturnable<ActionResult> cir) {
        ItemStack stackInHand = player.getStackInHand(hand);
        
        // Prevent baby spawning if using a custom spawn egg
        if (stackInHand.isOf(Items.VILLAGER_SPAWN_EGG) && stackInHand.contains(DataComponentTypes.ENTITY_DATA)) {
            // Let it pass so it can be used on the block behind the villager or nothing happens
            cir.setReturnValue(ActionResult.PASS);
            return;
        }

        if (hand == Hand.MAIN_HAND && player.isSneaking()) {
            VillagerEntity villager = (VillagerEntity) (Object) this;
            
            if (player.getEntityWorld().isClient()) {
                cir.setReturnValue(ActionResult.SUCCESS);
                return;
            }

            VillagerPickup.LOGGER.info("[VillagerPickup] Capturing villager (1.21.10 Port)...");

            try {
                // 1. Create Egg
                ItemStack egg = Items.VILLAGER_SPAWN_EGG.getDefaultStack();
                
                // 2. Extract Data using NbtWriteView
                NbtWriteView writeView = NbtWriteView.create(ErrorReporter.EMPTY, player.getEntityWorld().getRegistryManager());
                villager.writeData(writeView);
                NbtCompound nbt = writeView.getNbt();
                
                nbt.remove("Pos");
                nbt.remove("Motion");
                nbt.remove("Rotation");
                nbt.remove("UUID");
                nbt.remove("Dimension");
                
                egg.set(DataComponentTypes.ENTITY_DATA, TypedEntityData.create(EntityType.VILLAGER, nbt));
                
                // 3. Lore Generation
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

                if (villager.isBaby()) {
                    egg.set(DataComponentTypes.CUSTOM_NAME, Text.literal("Baby Villager Spawn Egg").styled(s -> s.withItalic(false)));
                }
                
                if (!player.getInventory().insertStack(egg)) {
                    player.dropItem(egg, false);
                }
                
                villager.discard();
                player.getEntityWorld().playSound(null, player.getBlockPos(), SoundEvents.ENTITY_ITEM_PICKUP, SoundCategory.PLAYERS, 1.0F, 1.0F);
                
                cir.setReturnValue(ActionResult.SUCCESS);
            } catch (Exception e) {
                VillagerPickup.LOGGER.error("[VillagerPickup] FAILED:", e);
            }
        }
    }
}
