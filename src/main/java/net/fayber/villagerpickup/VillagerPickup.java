package net.fayber.villagerpickup;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.util.ActionResult;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.Item;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.Registries;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;

import java.util.ArrayList;
import java.util.List;

public class VillagerPickup implements ModInitializer {

    @Override
    public void onInitialize() {
        UseEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (!world.isClient() && hand == Hand.MAIN_HAND && player.isSneaking() && entity instanceof VillagerEntity villager) {
                
                Item eggItem = (Item) Items.VILLAGER_SPAWN_EGG;
                ItemStack egg = new ItemStack(eggItem);
                
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
                int level = villager.getVillagerData().getLevel();
                
                loreLines.add(Text.literal("Job: " + job.substring(0, 1).toUpperCase() + job.substring(1))
                        .formatted(Formatting.GOLD));
                loreLines.add(Text.literal("Level: " + level).formatted(Formatting.YELLOW));
                
                if (nbt.contains("Brain", NbtElement.COMPOUND_TYPE)) {
                    NbtCompound brain = nbt.getCompound("Brain");
                    if (brain.contains("memories", NbtElement.COMPOUND_TYPE)) {
                        NbtCompound memories = brain.getCompound("memories");
                        if (memories.contains("minecraft:job_site")) {
                            loreLines.add(Text.literal("Has Workstation: Yes").formatted(Formatting.GRAY));
                        }
                        if (memories.contains("minecraft:home")) {
                            loreLines.add(Text.literal("Has Bed: Yes").formatted(Formatting.GRAY));
                        }
                    }
                }
                
                if (nbt.contains("Offers", NbtElement.COMPOUND_TYPE)) {
                    NbtCompound offers = nbt.getCompound("Offers");
                    if (offers.contains("Recipes", NbtElement.LIST_TYPE)) {
                        NbtList recipes = offers.getList("Recipes", NbtElement.COMPOUND_TYPE);
                        loreLines.add(Text.literal("Trades: " + recipes.size()).formatted(Formatting.GREEN));
                        
                        if (job.equals("librarian")) {
                            for (int i = 0; i < recipes.size(); i++) {
                                NbtCompound recipe = recipes.getCompound(i);
                                NbtCompound sellItem = recipe.getCompound("sell");
                                if (sellItem.getString("id").equals("minecraft:enchanted_book")) {
                                    if (sellItem.contains("components", NbtElement.COMPOUND_TYPE)) {
                                        NbtCompound components = sellItem.getCompound("components");
                                        if (components.contains("minecraft:stored_enchantments", NbtElement.COMPOUND_TYPE)) {
                                            NbtCompound enchantments = components.getCompound("minecraft:stored_enchantments");
                                            for (String key : enchantments.getKeys()) {
                                                int enchLvl = enchantments.getInt(key);
                                                String enchName = key.replace("minecraft:", "").replace("_", " ");
                                                loreLines.add(Text.literal("- " + enchName + " " + enchLvl).formatted(Formatting.AQUA));
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                
                egg.set(DataComponentTypes.LORE, new LoreComponent(loreLines));
                
                if (!player.getInventory().insertStack(egg)) {
                    player.dropItem(egg, false);
                }
                
                villager.discard();
                world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_ITEM_PICKUP, SoundCategory.PLAYERS, 1.0F, 1.0F);
                
                return ActionResult.SUCCESS;
            }
            return ActionResult.PASS;
        });
    }
}
