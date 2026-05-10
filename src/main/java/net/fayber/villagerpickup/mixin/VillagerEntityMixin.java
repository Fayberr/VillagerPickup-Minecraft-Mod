package net.fayber.villagerpickup.mixin;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.Registries;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

@Mixin(value = VillagerEntity.class, priority = 9999)
public abstract class VillagerEntityMixin {

    @Inject(method = "interactMob", at = @At("HEAD"), cancellable = true)
    private void onInteractMob(PlayerEntity player, Hand hand, CallbackInfoReturnable<ActionResult> cir) {
        // ALWAYS LOG THE ATTEMPT
        System.out.println("[VillagerPickup] Interaction attempt: Hand=" + hand + ", Sneaking=" + player.isSneaking() + ", Side=" + (player.getWorld().isClient() ? "CLIENT" : "SERVER"));

        if (hand == Hand.MAIN_HAND && player.isSneaking()) {
            VillagerEntity villager = (VillagerEntity) (Object) this;
            
            System.out.println("[VillagerPickup] Conditions met! Processing pickup for: " + villager.getName().getString());
            
            if (player.getWorld().isClient()) {
                System.out.println("[VillagerPickup] Client-side: Cancelling vanilla interaction.");
                cir.setReturnValue(ActionResult.SUCCESS);
                return;
            }
            
            System.out.println("[VillagerPickup] Server-side: Creating spawn egg...");

            try {
                ItemStack egg = Items.VILLAGER_SPAWN_EGG.getDefaultStack();
                
                NbtCompound nbt = new NbtCompound();
                villager.saveNbt(nbt);
                
                nbt.remove("Pos");
                nbt.remove("Motion");
                nbt.remove("Rotation");
                nbt.remove("UUID");
                nbt.putString("id", Registries.ENTITY_TYPE.getId(EntityType.VILLAGER).toString());
                
                egg.set(DataComponentTypes.ENTITY_DATA, NbtComponent.of(nbt));
                
                // Lore Generation
                List<Text> loreLines = new ArrayList<>();
                String job = villager.getVillagerData().getProfession().id();
                loreLines.add(Text.literal("Job: " + job).formatted(Formatting.GOLD));
                egg.set(DataComponentTypes.LORE, new LoreComponent(loreLines));
                
                if (!player.getInventory().insertStack(egg)) {
                    player.dropItem(egg, false);
                }
                
                villager.discard();
                player.getWorld().playSound(null, player.getBlockPos(), SoundEvents.ENTITY_ITEM_PICKUP, SoundCategory.PLAYERS, 1.0F, 1.0F);
                
                System.out.println("[VillagerPickup] Server-side: Pickup COMPLETE.");
                cir.setReturnValue(ActionResult.SUCCESS);
            } catch (Exception e) {
                System.err.println("[VillagerPickup] CRITICAL ERROR during pickup:");
                e.printStackTrace();
            }
        }
    }
}
