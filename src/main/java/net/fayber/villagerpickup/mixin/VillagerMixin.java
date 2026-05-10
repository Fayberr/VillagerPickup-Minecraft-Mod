package net.fayber.villagerpickup.mixin;

import net.fayber.villagerpickup.VillagerPickup;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerData;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.component.TypedEntityData;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.storage.TagValueOutput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Mixin(value = Villager.class, priority = 10000)
public abstract class VillagerMixin {

    @Inject(method = "mobInteract", at = @At("HEAD"), cancellable = true, remap = false)
    private void onMobInteract(Player player, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
        if (hand == InteractionHand.MAIN_HAND && player.isShiftKeyDown()) {
            Villager villager = (Villager) (Object) this;
            if (player.level().isClientSide()) {
                cir.setReturnValue(InteractionResult.SUCCESS);
                return;
            }

            VillagerPickup.LOGGER.info("[VillagerPickup] Capturing villager with dynamic naming...");

            try {
                // 1. Create Egg
                ItemStack egg = Items.VILLAGER_SPAWN_EGG.getDefaultInstance();
                
                // 2. Extract Data
                TagValueOutput out = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, player.level().registryAccess());
                villager.saveWithoutId(out);
                CompoundTag nbt = out.buildResult();
                nbt.remove("Pos");
                nbt.remove("Motion");
                nbt.remove("Rotation");
                nbt.remove("UUID");
                nbt.remove("Dimension");
                egg.set(DataComponents.ENTITY_DATA, TypedEntityData.of(EntityType.VILLAGER, nbt));

                // 3. Dynamic Naming
                VillagerData vData = villager.getVillagerData();
                String profPath = vData.profession().unwrapKey().map(key -> key.identifier().getPath()).orElse("none");
                
                if (!profPath.equals("none")) {
                    String profName = profPath.substring(0, 1).toUpperCase() + profPath.substring(1);
                    // Name format: "{Profession} Villager Spawn Egg"
                    egg.set(DataComponents.CUSTOM_NAME, Component.literal(profName + " Villager Spawn Egg").withStyle(s -> s.withItalic(false)));
                }

                // 4. Lore Generation
                List<Component> loreLines = new ArrayList<>();
                String profNameDisplay = profPath.substring(0, 1).toUpperCase() + profPath.substring(1);
                
                loreLines.add(Component.literal("Profession: ").withStyle(ChatFormatting.GRAY).withStyle(s -> s.withItalic(false))
                    .append(Component.literal(profNameDisplay).withStyle(ChatFormatting.GOLD).withStyle(s -> s.withItalic(false))));
                
                int level = vData.level();
                String levelStr = (level >= 5) ? level + " (MAX)" : String.valueOf(level);
                loreLines.add(Component.literal("Level: ").withStyle(ChatFormatting.YELLOW).withStyle(s -> s.withItalic(false))
                    .append(Component.literal(levelStr).withStyle(ChatFormatting.WHITE).withStyle(s -> s.withItalic(false))));

                villager.getBrain().getMemory(MemoryModuleType.JOB_SITE).ifPresent(pos -> {
                    String coords = pos.pos().getX() + ", " + pos.pos().getY() + ", " + pos.pos().getZ();
                    loreLines.add(Component.literal("Workstation: ").withStyle(ChatFormatting.GRAY).withStyle(s -> s.withItalic(false))
                        .append(Component.literal(coords).withStyle(ChatFormatting.WHITE).withStyle(s -> s.withItalic(false))));
                });
                
                villager.getBrain().getMemory(MemoryModuleType.HOME).ifPresent(pos -> {
                    String coords = pos.pos().getX() + ", " + pos.pos().getY() + ", " + pos.pos().getZ();
                    loreLines.add(Component.literal("Bed: ").withStyle(ChatFormatting.GRAY).withStyle(s -> s.withItalic(false))
                        .append(Component.literal(coords).withStyle(ChatFormatting.WHITE).withStyle(s -> s.withItalic(false))));
                });

                MerchantOffers offers = villager.getOffers();
                if (!offers.isEmpty()) {
                    loreLines.add(Component.empty());
                    loreLines.add(Component.literal("Trades:").withStyle(ChatFormatting.GRAY).withStyle(s -> s.withItalic(false)));
                    
                    for (MerchantOffer offer : offers) {
                        MutableComponent tradeLine = Component.literal(" - ").withStyle(ChatFormatting.DARK_GRAY).withStyle(s -> s.withItalic(false));
                        ItemCost costA = offer.getItemCostA();
                        tradeLine.append(Component.literal(costA.count() + " " + costA.itemStack().getHoverName().getString()).withStyle(ChatFormatting.WHITE).withStyle(s -> s.withItalic(false)));
                        
                        offer.getItemCostB().ifPresent(costB -> {
                            tradeLine.append(Component.literal(" + ").withStyle(ChatFormatting.GRAY).withStyle(s -> s.withItalic(false)));
                            tradeLine.append(Component.literal(costB.count() + " " + costB.itemStack().getHoverName().getString()).withStyle(ChatFormatting.WHITE).withStyle(s -> s.withItalic(false)));
                        });
                        
                        tradeLine.append(Component.literal(" -> ").withStyle(ChatFormatting.YELLOW).withStyle(s -> s.withItalic(false)));
                        
                        ItemStack result = offer.getResult();
                        if (result.is(Items.ENCHANTED_BOOK)) {
                            ItemEnchantments enchants = result.get(DataComponents.STORED_ENCHANTMENTS);
                            if (enchants != null && !enchants.isEmpty()) {
                                var entry = enchants.entrySet().iterator().next();
                                tradeLine.append(Enchantment.getFullname(entry.getKey(), entry.getIntValue()).copy().withStyle(ChatFormatting.AQUA).withStyle(s -> s.withItalic(false)));
                            } else {
                                tradeLine.append(result.getHoverName().copy().withStyle(ChatFormatting.WHITE).withStyle(s -> s.withItalic(false)));
                            }
                        } else {
                            tradeLine.append(Component.literal(result.getCount() + " " + result.getHoverName().getString()).withStyle(ChatFormatting.WHITE).withStyle(s -> s.withItalic(false)));
                        }
                        loreLines.add(tradeLine);
                    }
                }

                egg.set(DataComponents.LORE, new ItemLore(loreLines));
                if (!player.getInventory().add(egg)) player.drop(egg, false);
                villager.discard();
                player.level().playSound(null, player.blockPosition(), SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 1.0F, 1.0F);
                
                cir.setReturnValue(InteractionResult.SUCCESS);
            } catch (Exception e) {
                VillagerPickup.LOGGER.error("[VillagerPickup] CRITICAL FAILURE:", e);
            }
        }
    }
}
