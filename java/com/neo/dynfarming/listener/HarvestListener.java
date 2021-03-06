package com.neo.dynfarming.listener;

import com.neo.dynfarming.condition.Condition;
import com.neo.dynfarming.condition.ConditionFactory;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.loot.LootContext;
import org.bukkit.loot.LootTable;
import org.bukkit.loot.Lootable;

import java.util.Collection;
import java.util.Iterator;
import java.util.Random;

public class HarvestListener implements Listener {
	@EventHandler(ignoreCancelled = true)
	public void onEntityDeath(EntityDeathEvent event) {
		LivingEntity victim = event.getEntity();
		if(victim.getLastDamageCause() instanceof EntityDamageByEntityEvent) {
			EntityDamageByEntityEvent damageEvent = (EntityDamageByEntityEvent) victim.getLastDamageCause();
			if(damageEvent.getDamager() instanceof Player && !(victim instanceof Player)) {
				// get drop multiplier
				Condition condition = ConditionFactory.getCondition(victim);
				if(condition == null || !(victim instanceof Lootable)) {
					// ignore invalid entities
					return;
				}
				
				// multiply loot table
				double multiplier = condition.getDropMultiplier();
				
				int lootingModifier = getLootingModifier(multiplier);
				float luck = getLuck(multiplier);
				
				Random random = new Random(System.currentTimeMillis());
				LootContext.Builder builder = new LootContext.Builder(victim.getLocation())
						                              .killer((HumanEntity) damageEvent.getDamager())
						                              .lootedEntity(victim);
				
				if(lootingModifier > 0) {
					builder = builder.lootingModifier(lootingModifier);
				}
				builder = builder.luck(luck);
				
				LootTable table = ((Lootable) victim).getLootTable();
				Collection<ItemStack> drops = table.populateLoot(random, builder.build());
				Iterator<ItemStack> iter = drops.iterator();
				while(iter.hasNext()) {
					ItemStack i = iter.next();
					double amount = i.getAmount();
					amount *= multiplier;
					if(amount < 1) {
						if(random.nextDouble() < amount) {
							amount = 1;
						} else {
							iter.remove();
							continue;
						}
					}
					i.setAmount((int) amount);
				}
				
				// overwrite loot drops
				event.getDrops().clear();
				event.getDrops().addAll(drops);
			}
		}
	}
	
	@EventHandler(ignoreCancelled = true)
	public void onBlockBreak(BlockBreakEvent event) {
		Block block = event.getBlock();
		Condition condition = ConditionFactory.getCondition(block);
		if(condition == null) {
			// ignore invalid blocks
			return;
		}
		
		double multiplier = condition.getDropMultiplier();
		
		Player player = event.getPlayer();
		if(player != null) {
			Random random = new Random(System.currentTimeMillis());
			Collection<ItemStack> drops = block.getDrops();
			Iterator<ItemStack> iter = drops.iterator();
			
			event.setCancelled(true);
			block.setType(Material.AIR);
			
			while(iter.hasNext()) {
				ItemStack i = iter.next();
				double amount = i.getAmount();
				if(!i.getType().name().endsWith("SEEDS")) {
					amount *= multiplier;
					int truncated = (int) amount;
					double chance = amount - truncated;
					if(random.nextDouble() < chance) {
						truncated += 1;
					} else if(truncated == 0) {
						iter.remove();
						continue;
					}
					i.setAmount(truncated);
				}
				block.getWorld().dropItemNaturally(block.getLocation(), i.clone());
			}
		}
	}
	
	private static int getLootingModifier(double multiplier) {
		if(multiplier < 1) {
			return 0;
		} else if(multiplier >= 5) {
			return 20;
		}
		return (int) (5 * multiplier - 5);
	}
	
	private static float getLuck(double multiplier) {
		if(multiplier < 0) {
			return -1.25f;
		} else if(multiplier >= 5) {
			return 5;
		}
		return (float) (1.25 * multiplier - 1.25);
	}
}
