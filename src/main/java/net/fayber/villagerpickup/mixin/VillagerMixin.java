package net.fayber.villagerpickup.mixin;

import net.minecraft.entity.passive.VillagerEntity;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(value = VillagerEntity.class, priority = 10000)
public abstract class VillagerMixin {
}
