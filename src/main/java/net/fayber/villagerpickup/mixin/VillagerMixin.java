package net.fayber.villagerpickup.mixin;

import net.fayber.villagerpickup.VillagerPickup;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
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
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.storage.TagValueOutput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

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

            VillagerPickup.LOGGER.info("[VillagerPickup] Capturing villager with proper lines...");

            try {
                // 1. Create Egg
                ItemStack egg = Items.VILLAGER_SPAWN_EGG.getDefaultInstance();
                
                // 2. Extract Data
                TagValueOutput out = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, player.level().registryAccess());
                villager.saveWithoutId(out);
                CompoundTag nbt = out.buildResult();
                
                egg.set(DataComponents.ENTITY_DATA, TypedEntityData.of(EntityType.VILLAGER, nbt));
                
                // 3. Lore Generation
                List<Component> loreLines = new ArrayList<>();
                VillagerData vData = villager.getVillagerData();
                
                // Get professional name reliably from ResourceKey/Identifier
                String profPath = vData.profession().unwrapKey()
                    .map(key -> key.identifier().getPath())
                    .orElse("none");
                
                String profName = profPath.substring(0, 1).toUpperCase() + profPath.substring(1);
                
                loreLines.add(Component.literal("Job: ").withStyle(ChatFormatting.GOLD)
                    .append(Component.literal(profName).withStyle(ChatFormatting.WHITE)));
                
                loreLines.add(Component.literal("Level: ").withStyle(ChatFormatting.YELLOW)
                    .append(Component.literal(String.valueOf(vData.level())).withStyle(ChatFormatting.WHITE)));
                
                // Station/Bed info
                villager.getBrain().getMemory(MemoryModuleType.JOB_SITE).ifPresent(pos -> 
                    loreLines.add(Component.literal("Has Workstation: Yes").withStyle(ChatFormatting.GRAY)));
                villager.getBrain().getMemory(MemoryModuleType.HOME).ifPresent(pos -> 
                    loreLines.add(Component.literal("Has Bed: Yes").withStyle(ChatFormatting.GRAY)));
                
                // Trades info
                MerchantOffers offers = villager.getOffers();
                loreLines.add(Component.literal("Trades: ").withStyle(ChatFormatting.GREEN)
                    .append(Component.literal(String.valueOf(offers.size())).withStyle(ChatFormatting.WHITE)));
                
                if (profPath.equals("librarian")) {
                    for (MerchantOffer offer : offers) {
                        ItemStack result = offer.getResult();
                        if (result.is(Items.ENCHANTED_BOOK)) {
                            ItemEnchantments enchants = result.get(DataComponents.STORED_ENCHANTMENTS);
                            if (enchants != null) {
                                for (var entry : enchants.entrySet()) {
                                    loreLines.add(Component.literal(" - ")
                                        .append(Enchantment.getFullname(entry.getKey(), entry.getIntValue()))
                                        .withStyle(ChatFormatting.AQUA));
                                }
                            }
                        }
                    }
                }
                
                // Set ItemLore with distinct lines
                egg.set(DataComponents.LORE, new ItemLore(loreLines));
                
                // 4. Give Item
                if (!player.getInventory().add(egg)) {
                    player.drop(egg, false);
                }
                
                // 5. Cleanup
                villager.discard();
                player.level().playSound(null, player.blockPosition(), SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 1.0F, 1.0F);
                
                VillagerPickup.LOGGER.info("[VillagerPickup] Pickup COMPLETE.");
                cir.setReturnValue(InteractionResult.SUCCESS);
            } catch (Exception e) {
                VillagerPickup.LOGGER.error("[VillagerPickup] CRITICAL FAILURE:", e);
            }
        }
    }
}
