package net.fayber.villagerpickup.mixin;

import net.fayber.villagerpickup.VillagerPickup;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.component.TypedEntityData;
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

            VillagerPickup.LOGGER.info("[VillagerPickup] Interaction intercepted!");

            try {
                ItemStack egg = Items.VILLAGER_SPAWN_EGG.getDefaultInstance();
                
                TagValueOutput out = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, player.level().registryAccess());
                villager.saveWithoutId(out);
                CompoundTag nbt = out.buildResult();
                
                egg.set(DataComponents.ENTITY_DATA, TypedEntityData.of(EntityType.VILLAGER, nbt));
                
                List<Component> loreLines = new ArrayList<>();
                String job = villager.getVillagerData().profession().toString();
                loreLines.add(Component.literal("Job: " + job).withStyle(ChatFormatting.GOLD));
                loreLines.add(Component.literal("Level: " + villager.getVillagerData().level()).withStyle(ChatFormatting.YELLOW));
                
                egg.set(DataComponents.LORE, new ItemLore(loreLines));
                
                if (!player.getInventory().add(egg)) {
                    player.drop(egg, false);
                }
                
                villager.discard();
                player.level().playSound(null, player.blockPosition(), SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 1.0F, 1.0F);
                
                VillagerPickup.LOGGER.info("[VillagerPickup] SUCCESS.");
                cir.setReturnValue(InteractionResult.SUCCESS);
            } catch (Exception e) {
                VillagerPickup.LOGGER.error("[VillagerPickup] FAILED:", e);
            }
        }
    }
}
