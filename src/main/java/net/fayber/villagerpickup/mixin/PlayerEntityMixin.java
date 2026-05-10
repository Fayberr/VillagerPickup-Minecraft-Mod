package net.fayber.villagerpickup.mixin;

import net.fayber.villagerpickup.VillagerPickup;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.entity.Entity;
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

@Mixin(value = PlayerEntity.class, priority = 10000)
public abstract class PlayerEntityMixin {

    /*
     * We use remap = false because the runtime (26.1.2) appears to be unobfuscated.
     * Targeting both the standard name "interact" and any common aliases.
     */
    @Inject(method = "interact", at = @At("HEAD"), cancellable = true, remap = false)
    private void onInteract(Entity entity, Hand hand, CallbackInfoReturnable<ActionResult> cir) {
        if (entity instanceof VillagerEntity villager && hand == Hand.MAIN_HAND) {
            PlayerEntity player = (PlayerEntity) (Object) this;
            
            if (player.isSneaking()) {
                if (player.getWorld().isClient()) {
                    cir.setReturnValue(ActionResult.SUCCESS);
                    return;
                }

                VillagerPickup.LOGGER.info("[VillagerPickup] Interaction intercepted with remap=false!");

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
                    
                    List<Text> loreLines = new ArrayList<>();
                    String job = villager.getVillagerData().getProfession().id();
                    loreLines.add(Text.literal("Job: " + job).formatted(Formatting.GOLD));
                    egg.set(DataComponentTypes.LORE, new LoreComponent(loreLines));
                    
                    if (!player.getInventory().insertStack(egg)) {
                        player.dropItem(egg, false);
                    }
                    
                    villager.discard();
                    player.getWorld().playSound(null, player.getBlockPos(), SoundEvents.ENTITY_ITEM_PICKUP, SoundCategory.PLAYERS, 1.0F, 1.0F);
                    
                    VillagerPickup.LOGGER.info("[VillagerPickup] SUCCESS.");
                    cir.setReturnValue(ActionResult.SUCCESS);
                } catch (Exception e) {
                    VillagerPickup.LOGGER.error("[VillagerPickup] FAILED:", e);
                }
            }
        }
    }
}
