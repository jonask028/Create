package com.simibubi.create.content.logistics;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import com.simibubi.create.foundation.block.entity.behaviour.linked.LinkBehaviour;
import org.apache.commons.lang3.tuple.Pair;

import com.simibubi.create.Create;
import com.simibubi.create.foundation.utility.WorldHelper;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.World;
import net.minecraft.world.WorldAccess;

public class RedstoneLinkNetworkHandler {

	static final Map<WorldAccess, Map<Pair<Frequency, Frequency>, Set<LinkBehaviour>>> connections = new IdentityHashMap<>();

	public static class Frequency {
		public static final Frequency EMPTY = new Frequency(ItemStack.EMPTY);
		private static final Map<Item, Frequency> simpleFrequencies = new IdentityHashMap<>();
		private ItemStack stack;
		private Item item;
		private int color;

		public static Frequency of(ItemStack stack) {
			if (stack.isEmpty())
				return EMPTY;
			if (!stack.hasTag())
				return simpleFrequencies.computeIfAbsent(stack.getItem(), $ -> new Frequency(stack));
			return new Frequency(stack);
		}

		private Frequency(ItemStack stack) {
			this.stack = stack;
			item = stack.getItem();
			CompoundTag displayTag = stack.getSubTag("display");
			color = displayTag != null && displayTag.contains("color") ? displayTag.getInt("color") : -1;
		}

		public ItemStack getStack() {
			return stack;
		}

		@Override
		public int hashCode() {
			return item.hashCode() ^ color;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			return obj instanceof Frequency ? ((Frequency) obj).item == item && ((Frequency) obj).color == color
					: false;
		}

	}

	public void onLoadWorld(WorldAccess world) {
		connections.put(world, new HashMap<>());
		Create.logger.debug("Prepared Redstone Network Space for " + WorldHelper.getDimensionID(world));
	}

	public void onUnloadWorld(WorldAccess world) {
		connections.remove(world);
		Create.logger.debug("Removed Redstone Network Space for " + WorldHelper.getDimensionID(world));
	}

	public Set<LinkBehaviour> getNetworkOf(LinkBehaviour actor) {
		Map<Pair<Frequency, Frequency>, Set<LinkBehaviour>> networksInWorld = networksIn(actor.getWorld());
		Pair<Frequency, Frequency> key = actor.getNetworkKey();
		if (!networksInWorld.containsKey(key))
			networksInWorld.put(key, new LinkedHashSet<>());
		return networksInWorld.get(key);
	}

	public void addToNetwork(LinkBehaviour actor) {
		getNetworkOf(actor).add(actor);
		updateNetworkOf(actor);
	}

	public void removeFromNetwork(LinkBehaviour actor) {
		Set<LinkBehaviour> network = getNetworkOf(actor);
		network.remove(actor);
		if (network.isEmpty()) {
			networksIn(actor.getWorld()).remove(actor.getNetworkKey());
			return;
		}
		updateNetworkOf(actor);
	}

	public void updateNetworkOf(LinkBehaviour actor) {
		Set<LinkBehaviour> network = getNetworkOf(actor);
		int power = 0;

		for (Iterator<LinkBehaviour> iterator = network.iterator(); iterator.hasNext();) {
			LinkBehaviour other = iterator.next();
			if (other.blockEntity.isRemoved()) {
				iterator.remove();
				continue;
			}
			World world = actor.getWorld();
			if (!world.canSetBlock(other.blockEntity.getPos())) {
				iterator.remove();
				continue;
			}
			if (world.getBlockEntity(other.blockEntity.getPos()) != other.blockEntity) {
				iterator.remove();
				continue;
			}
			if (!withinRange(actor, other))
				continue;

			if (power < 15)
				power = Math.max(other.getTransmittedStrength(), power);
		}

		// fix one-to-one loading order problem
		if (actor.isListening()) {
			actor.newPosition = true;
			actor.updateReceiver(power);
		}

		for (LinkBehaviour other : network) {
			if (other != actor && other.isListening() && withinRange(actor, other))
				other.updateReceiver(power);
		}
	}

	public static boolean withinRange(LinkBehaviour from, LinkBehaviour to) {
		if (from == to)
			return true;
		return from.getPos().isWithinDistance(to.getPos(), Create.getConfig().logistics.linkRange);
	}

	public Map<Pair<Frequency, Frequency>, Set<LinkBehaviour>> networksIn(WorldAccess world) {
		if (!connections.containsKey(world)) {
			Create.logger.warn(
					"Tried to Access unprepared network space of " + WorldHelper.getDimensionID(world));
			return new HashMap<>();
		}
		return connections.get(world);
	}

}
