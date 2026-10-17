package com.piotrek.minecartchain;

import io.github.foundationgames.splinecart.block.TrackTiesBlockEntity;
import io.github.foundationgames.splinecart.entity.TrackFollowerEntity;
import io.github.foundationgames.splinecart.util.SplineSegment;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.entity.vehicle.minecart.MinecartFurnace;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MinecartItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import org.joml.Matrix3d;
import org.joml.Vector3d;

public final class SplinecartPlacementHandler {
	private static volatile Field minecartTypeField;

	private SplinecartPlacementHandler() {
	}

	public static void register() {
		NeoForge.EVENT_BUS.addListener(SplinecartPlacementHandler::onRightClickBlock);
	}

	private static void onRightClickBlock(final PlayerInteractEvent.RightClickBlock event) {
		InteractionResult result = handleUse(event.getEntity(), event.getLevel(), event.getHand(), event.getHitVec());
		if (result.consumesAction()) {
			event.setCancellationResult(result);
			event.setCanceled(true);
		}
	}

	public static boolean isTrainOrCartItem(final ItemStack stack) {
		if (stack == null || stack.isEmpty()) {
			return false;
		}
		Item item = stack.getItem();
		return item instanceof SteamLocomotiveItem
			|| item instanceof RadioSteamLocomotiveItem
			|| item instanceof SteamLocomotiveWithTenderItem
			|| item instanceof RadioLocomotiveItem
			|| item instanceof CoalTenderItem
			|| item instanceof LongMinecartItem
			|| item instanceof MinecartItem;
	}

	public static InteractionResult handleUse(
		final Player player,
		final Level world,
		final InteractionHand hand,
		final BlockHitResult hitResult
	) {
		ItemStack stack = player.getItemInHand(hand);
		if (!isTrainOrCartItem(stack)) {
			return InteractionResult.PASS;
		}

		BlockPos clickedPos = hitResult.getBlockPos();
		BlockEntity be = world.getBlockEntity(clickedPos);
		if (!(be instanceof TrackTiesBlockEntity tie)) {
			return InteractionResult.PASS;
		}

		List<SplineSegment> candidates = getConnectedSegments(tie);
		if (candidates.isEmpty()) {
			return InteractionResult.PASS;
		}

		Vec3 hitVec = hitResult.getLocation();
		Vector3d hitPoint = new Vector3d(hitVec.x, hitVec.y, hitVec.z);
		SelectedSegment selected = findBestSegment(candidates, hitPoint, player.getYRot());
		if (selected == null) {
			return InteractionResult.PASS;
		}

		if (world.isClientSide()) {
			return InteractionResult.SUCCESS;
		}

		if (!(world instanceof ServerLevel serverLevel)) {
			return InteractionResult.PASS;
		}

		SplineSegment segment = selected.segment;
		double progress = selected.progress;
		Vector3d spawn = selected.spawn;
		Vector3d tangent = selected.tangent;
		float yaw = computeFacingYaw(tangent, player.getYRot());

		boolean placed = placeVehicle(player, serverLevel, stack, segment, progress, spawn, tangent, yaw);
		if (!placed) {
			return InteractionResult.PASS;
		}

		world.playSound(null, spawn.x(), spawn.y(), spawn.z(), SoundEvents.IRON_GOLEM_STEP, SoundSource.BLOCKS, 0.75F, 0.8F);
		if (!player.hasInfiniteMaterials()) {
			stack.shrink(1);
		}
		return InteractionResult.SUCCESS;
	}

	private static List<SplineSegment> getConnectedSegments(final TrackTiesBlockEntity tie) {
		List<SplineSegment> candidates = new ArrayList<>(2);
		TrackTiesBlockEntity next = tie.next();
		if (next != null) {
			candidates.add(new SplineSegment(tie, next));
		}
		TrackTiesBlockEntity prev = tie.prev();
		if (prev != null) {
			candidates.add(new SplineSegment(prev, tie));
		}
		return candidates;
	}

	private static double findNearestProgress(final SplineSegment segment, final Vector3d hitPoint) {
		int samples = 20;
		double bestT = 0.0;
		double minDistanceSq = Double.MAX_VALUE;
		Vector3d tempPos = new Vector3d();
		Matrix3d dummyBasis = new Matrix3d();
		Vector3d dummyDeriv = new Vector3d();

		for (int i = 0; i <= samples; i++) {
			double t = (double) i / (double) samples;
			segment.interpolate(t, tempPos, dummyBasis, dummyDeriv);
			double dSq = tempPos.distanceSquared(hitPoint);
			if (dSq < minDistanceSq) {
				minDistanceSq = dSq;
				bestT = t;
			}
		}

		double step = 1.0 / (double) samples;
		double low = Math.max(0.0, bestT - step);
		double high = Math.min(1.0, bestT + step);
		for (int iter = 0; iter < 12; iter++) {
			double m1 = low + (high - low) / 3.0;
			double m2 = high - (high - low) / 3.0;
			segment.interpolate(m1, tempPos, dummyBasis, dummyDeriv);
			double d1 = tempPos.distanceSquared(hitPoint);
			segment.interpolate(m2, tempPos, dummyBasis, dummyDeriv);
			double d2 = tempPos.distanceSquared(hitPoint);
			if (d1 < d2) {
				high = m2;
			} else {
				low = m1;
			}
		}
		return (low + high) / 2.0;
	}

	private record SelectedSegment(SplineSegment segment, double progress, Vector3d spawn, Vector3d tangent) {
	}

	private static SelectedSegment findBestSegment(
		final List<SplineSegment> candidates,
		final Vector3d hitPoint,
		final float playerYaw
	) {
		SplineSegment bestSeg = null;
		double bestT = 0.0;
		Vector3d bestSpawn = new Vector3d();
		Vector3d bestTangent = new Vector3d();
		double minDistanceSq = Double.MAX_VALUE;

		Vector3d tempPos = new Vector3d();
		Matrix3d tempBasis = new Matrix3d();
		Vector3d tempTangent = new Vector3d();

		for (SplineSegment candidate : candidates) {
			double t = findNearestProgress(candidate, hitPoint);
			candidate.interpolate(t, tempPos, tempBasis, tempTangent);
			double dSq = tempPos.distanceSquared(hitPoint);
			if (dSq < minDistanceSq) {
				minDistanceSq = dSq;
				bestSeg = candidate;
				bestT = t;
				bestSpawn.set(tempPos);
				bestTangent.set(tempTangent);
			}
		}

		if (bestSeg == null || minDistanceSq > 9.0) {
			return null;
		}

		bestT = Math.max(0.05, Math.min(0.95, bestT));
		bestSeg.interpolate(bestT, bestSpawn, tempBasis, bestTangent);
		if (bestTangent.lengthSquared() > 1.0E-6) {
			bestTangent.normalize();
		}
		return new SelectedSegment(bestSeg, bestT, bestSpawn, bestTangent);
	}

	private static float computeFacingYaw(final Vector3d tangent, final float playerYaw) {
		float forwardYaw = (float) (Math.toDegrees(Math.atan2(tangent.z(), tangent.x())) - 90.0);
		float diff = (playerYaw - forwardYaw) % 360.0F;
		if (diff < -180.0F) diff += 360.0F;
		if (diff > 180.0F) diff -= 360.0F;
		return (Math.abs(diff) > 90.0F) ? (forwardYaw + 180.0F) : forwardYaw;
	}

	private static boolean placeVehicle(
		final Player player,
		final ServerLevel serverLevel,
		final ItemStack stack,
		final SplineSegment segment,
		final double progress,
		final Vector3d spawn,
		final Vector3d tangent,
		final float yaw
	) {
		Item item = stack.getItem();
		if (item instanceof SteamLocomotiveItem) {
			return placeSteamLocomotive(player, serverLevel, stack, segment, progress, spawn, tangent, yaw);
		} else if (item instanceof RadioSteamLocomotiveItem) {
			return placeRadioSteamLocomotive(player, serverLevel, stack, segment, progress, spawn, tangent, yaw);
		} else if (item instanceof SteamLocomotiveWithTenderItem) {
			return placeSteamLocomotiveWithTender(player, serverLevel, stack, segment, progress, spawn, tangent, yaw);
		} else if (item instanceof RadioLocomotiveItem) {
			return placeRadioFurnaceLocomotive(player, serverLevel, stack, segment, progress, spawn, tangent, yaw);
		} else if (item instanceof CoalTenderItem) {
			return placeCoalTender(player, serverLevel, stack, segment, progress, spawn, tangent, yaw);
		} else if (item instanceof LongMinecartItem) {
			return placeLongMinecart(player, serverLevel, stack, segment, progress, spawn, tangent, yaw);
		} else if (item instanceof MinecartItem) {
			return placeVanillaMinecart(player, serverLevel, stack, segment, progress, spawn, tangent, yaw);
		}
		return false;
	}

	private static boolean placeSteamLocomotive(
		final Player player,
		final ServerLevel serverLevel,
		final ItemStack stack,
		final SplineSegment segment,
		final double progress,
		final Vector3d spawn,
		final Vector3d tangent,
		final float yaw
	) {
		SteamLocomotiveEntity loco = (SteamLocomotiveEntity) AbstractMinecart.createMinecart(
			serverLevel,
			spawn.x(), spawn.y(), spawn.z(),
			MinecartChainMod.STEAM_LOCOMOTIVE_ENTITY.get(),
			EntitySpawnReason.SPAWN_ITEM_USE,
			stack,
			player
		);
		if (loco == null) {
			return false;
		}

		loco.setFacingYaw(yaw);
		loco.setYRot(yaw);
		loco.yRotO = yaw;
		loco.setYHeadRot(yaw);
		loco.setYBodyRot(yaw);
		MinecartChainAccess locoData = (MinecartChainAccess) loco;
		locoData.minecartChain$setLocomotiveYaw(yaw);
		locoData.minecartChain$setHasLocomotiveYaw(true);

		TrackFollowerEntity follower = TrackFollowerEntity.createPlaced(
			serverLevel,
			segment.start().getBlockPos(),
			progress
		);
		if (follower == null) {
			return false;
		}

		serverLevel.addFreshEntity(follower);
		serverLevel.addFreshEntity(loco);
		loco.startRiding(follower, true, false);
		return true;
	}

	private static boolean placeRadioSteamLocomotive(
		final Player player,
		final ServerLevel serverLevel,
		final ItemStack stack,
		final SplineSegment segment,
		final double progress,
		final Vector3d spawn,
		final Vector3d tangent,
		final float yaw
	) {
		SteamLocomotiveEntity loco = (SteamLocomotiveEntity) AbstractMinecart.createMinecart(
			serverLevel,
			spawn.x(), spawn.y(), spawn.z(),
			MinecartChainMod.STEAM_LOCOMOTIVE_ENTITY.get(),
			EntitySpawnReason.SPAWN_ITEM_USE,
			stack,
			player
		);
		if (loco == null) {
			return false;
		}

		MinecartChainAccess data = (MinecartChainAccess) loco;
		data.minecartChain$setRadioLocomotive(true);
		data.minecartChain$setEngineLever(true);
		data.minecartChain$setEngineActive(false);
		data.minecartChain$setFullThrottle(false);
		data.minecartChain$setReversed(false);
		data.minecartChain$setReversePending(false);
		data.minecartChain$setThrottleChannel(0);
		data.minecartChain$setReverseChannel(1);
		data.minecartChain$setArmed(false);

		loco.setFacingYaw(yaw);
		loco.setYRot(yaw);
		loco.yRotO = yaw;
		loco.setYHeadRot(yaw);
		loco.setYBodyRot(yaw);
		data.minecartChain$setLocomotiveYaw(yaw);
		data.minecartChain$setHasLocomotiveYaw(true);

		TrackFollowerEntity follower = TrackFollowerEntity.createPlaced(
			serverLevel,
			segment.start().getBlockPos(),
			progress
		);
		if (follower == null) {
			return false;
		}

		serverLevel.addFreshEntity(follower);
		serverLevel.addFreshEntity(loco);
		loco.startRiding(follower, true, false);
		return true;
	}

	private static boolean placeSteamLocomotiveWithTender(
		final Player player,
		final ServerLevel serverLevel,
		final ItemStack stack,
		final SplineSegment segment,
		final double progress,
		final Vector3d spawn,
		final Vector3d tangent,
		final float yaw
	) {
		SteamLocomotiveEntity loco = (SteamLocomotiveEntity) AbstractMinecart.createMinecart(
			serverLevel,
			spawn.x(), spawn.y(), spawn.z(),
			MinecartChainMod.STEAM_LOCOMOTIVE_ENTITY.get(),
			EntitySpawnReason.SPAWN_ITEM_USE,
			stack,
			player
		);
		if (loco == null) {
			return false;
		}

		loco.setFacingYaw(yaw);
		loco.setYRot(yaw);
		loco.yRotO = yaw;
		loco.setYHeadRot(yaw);
		loco.setYBodyRot(yaw);
		MinecartChainAccess locoData = (MinecartChainAccess) loco;
		locoData.minecartChain$setLocomotiveYaw(yaw);
		locoData.minecartChain$setHasLocomotiveYaw(true);

		TrackFollowerEntity locoFollower = TrackFollowerEntity.createPlaced(
			serverLevel,
			segment.start().getBlockPos(),
			progress
		);
		if (locoFollower == null) {
			return false;
		}

		SplineSegment tenderSegment = segment;
		double tenderProg = progress;
		Vector3d tenderSpawn = new Vector3d(spawn);
		Vector3d previousSample = new Vector3d(spawn);
		double walked = 0.0D;
		boolean room = false;
		for (int sample = 0; sample < 16000; sample++) {
			if (tenderProg <= 0.0D) {
				TrackTiesBlockEntity prevTie = tenderSegment.start().prev();
				if (prevTie == null) {
					return false;
				}
				tenderSegment = new SplineSegment(prevTie, tenderSegment.start());
				tenderProg = 1.0D;
			}
			tenderProg = Math.max(0.0D, tenderProg - 0.002D);
			tenderSegment.interpolate(tenderProg, tenderSpawn, new Matrix3d(), new Vector3d());
			walked += tenderSpawn.distance(previousSample);
			previousSample.set(tenderSpawn);
			double dx = tenderSpawn.x() - spawn.x();
			double dz = tenderSpawn.z() - spawn.z();
			if (walked >= SteamLocomotiveEntity.HINGE_DISTANCE
				&& Math.hypot(dx, dz) >= SteamLocomotiveEntity.HINGE_DISTANCE
				&& dx * tangent.x() + dz * tangent.z() < 0.0D) {
				room = true;
				break;
			}
		}
		if (!room) {
			return false;
		}

		CoalTenderEntity tender = (CoalTenderEntity) AbstractMinecart.createMinecart(
			serverLevel,
			tenderSpawn.x(), tenderSpawn.y(), tenderSpawn.z(),
			MinecartChainMod.COAL_TENDER_ENTITY.get(),
			EntitySpawnReason.SPAWN_ITEM_USE,
			ItemStack.EMPTY,
			player
		);
		if (tender == null) {
			return false;
		}

		tender.setYRot(yaw);
		tender.yRotO = yaw;
		tender.setYHeadRot(yaw);
		tender.setYBodyRot(yaw);

		TrackFollowerEntity tenderFollower = TrackFollowerEntity.createPlaced(
			serverLevel,
			tenderSegment.start().getBlockPos(),
			tenderProg
		);
		if (tenderFollower == null) {
			return false;
		}

		loco.setPermanentTender(tender.getUUID());
		tender.setPermanentLocomotive(loco.getUUID());
		((MinecartChainAccess) loco).minecartChain$addLink(tender.getUUID());
		((MinecartChainAccess) tender).minecartChain$addLink(loco.getUUID());

		serverLevel.addFreshEntity(locoFollower);
		serverLevel.addFreshEntity(loco);
		loco.startRiding(locoFollower, true, false);

		serverLevel.addFreshEntity(tenderFollower);
		serverLevel.addFreshEntity(tender);
		tender.startRiding(tenderFollower, true, false);
		return true;
	}

	private static boolean placeRadioFurnaceLocomotive(
		final Player player,
		final ServerLevel serverLevel,
		final ItemStack stack,
		final SplineSegment segment,
		final double progress,
		final Vector3d spawn,
		final Vector3d tangent,
		final float yaw
	) {
		MinecartFurnace furnace = (MinecartFurnace) AbstractMinecart.createMinecart(
			serverLevel,
			spawn.x(), spawn.y(), spawn.z(),
			EntityTypes.FURNACE_MINECART,
			EntitySpawnReason.SPAWN_ITEM_USE,
			stack,
			player
		);
		if (furnace == null) {
			return false;
		}

		MinecartChainAccess data = (MinecartChainAccess) furnace;
		data.minecartChain$setRadioLocomotive(true);
		data.minecartChain$setEngineLever(true);
		data.minecartChain$setEngineActive(false);
		data.minecartChain$setFullThrottle(false);
		data.minecartChain$setReversed(false);
		data.minecartChain$setReversePending(false);
		data.minecartChain$setThrottleChannel(0);
		data.minecartChain$setReverseChannel(1);
		data.minecartChain$setArmed(false);

		furnace.setYRot(yaw);
		furnace.yRotO = yaw;
		data.minecartChain$setLocomotiveYaw(yaw);
		data.minecartChain$setHasLocomotiveYaw(true);

		TrackFollowerEntity follower = TrackFollowerEntity.createPlaced(
			serverLevel,
			segment.start().getBlockPos(),
			progress
		);
		if (follower == null) {
			return false;
		}

		serverLevel.addFreshEntity(follower);
		serverLevel.addFreshEntity(furnace);
		furnace.startRiding(follower, true, false);
		return true;
	}

	private static boolean placeCoalTender(
		final Player player,
		final ServerLevel serverLevel,
		final ItemStack stack,
		final SplineSegment segment,
		final double progress,
		final Vector3d spawn,
		final Vector3d tangent,
		final float yaw
	) {
		CoalTenderEntity tender = (CoalTenderEntity) AbstractMinecart.createMinecart(
			serverLevel,
			spawn.x(), spawn.y(), spawn.z(),
			MinecartChainMod.COAL_TENDER_ENTITY.get(),
			EntitySpawnReason.SPAWN_ITEM_USE,
			stack,
			player
		);
		if (tender == null) {
			return false;
		}

		tender.setYRot(yaw);
		tender.yRotO = yaw;
		tender.setYHeadRot(yaw);
		tender.setYBodyRot(yaw);

		TrackFollowerEntity follower = TrackFollowerEntity.createPlaced(
			serverLevel,
			segment.start().getBlockPos(),
			progress
		);
		if (follower == null) {
			return false;
		}

		serverLevel.addFreshEntity(follower);
		serverLevel.addFreshEntity(tender);
		tender.startRiding(follower, true, false);
		return true;
	}

	private static boolean placeLongMinecart(
		final Player player,
		final ServerLevel serverLevel,
		final ItemStack stack,
		final SplineSegment segment,
		final double progress,
		final Vector3d spawn,
		final Vector3d tangent,
		final float yaw
	) {
		LongMinecartItem item = (LongMinecartItem) stack.getItem();
		EntityType<? extends AbstractMinecart> cartType = item.cartType();
		AbstractMinecart cart = AbstractMinecart.createMinecart(
			serverLevel,
			spawn.x(), spawn.y(), spawn.z(),
			cartType,
			EntitySpawnReason.SPAWN_ITEM_USE,
			stack,
			player
		);
		if (cart == null) {
			return false;
		}

		cart.setYRot(yaw);
		cart.yRotO = yaw;

		TrackFollowerEntity follower = TrackFollowerEntity.createPlaced(
			serverLevel,
			segment.start().getBlockPos(),
			progress
		);
		if (follower == null) {
			return false;
		}

		serverLevel.addFreshEntity(follower);
		serverLevel.addFreshEntity(cart);
		cart.startRiding(follower, true, false);
		return true;
	}

	private static boolean placeVanillaMinecart(
		final Player player,
		final ServerLevel serverLevel,
		final ItemStack stack,
		final SplineSegment segment,
		final double progress,
		final Vector3d spawn,
		final Vector3d tangent,
		final float yaw
	) {
		EntityType<? extends AbstractMinecart> cartType = getMinecartType(stack.getItem());
		if (cartType == null) {
			return false;
		}

		AbstractMinecart cart = AbstractMinecart.createMinecart(
			serverLevel,
			spawn.x(), spawn.y(), spawn.z(),
			cartType,
			EntitySpawnReason.SPAWN_ITEM_USE,
			stack,
			player
		);
		if (cart == null) {
			return false;
		}

		cart.setYRot(yaw);
		cart.yRotO = yaw;

		TrackFollowerEntity follower = TrackFollowerEntity.createPlaced(
			serverLevel,
			segment.start().getBlockPos(),
			progress
		);
		if (follower == null) {
			return false;
		}

		serverLevel.addFreshEntity(follower);
		serverLevel.addFreshEntity(cart);
		cart.startRiding(follower, true, false);
		return true;
	}

	private static EntityType<? extends AbstractMinecart> getMinecartType(final Item item) {
		if (item == Items.MINECART) {
			return EntityTypes.MINECART;
		}
		if (item == Items.CHEST_MINECART) {
			return EntityTypes.CHEST_MINECART;
		}
		if (item == Items.FURNACE_MINECART) {
			return EntityTypes.FURNACE_MINECART;
		}
		if (item == Items.TNT_MINECART) {
			return EntityTypes.TNT_MINECART;
		}
		if (item == Items.HOPPER_MINECART) {
			return EntityTypes.HOPPER_MINECART;
		}
		if (item == Items.COMMAND_BLOCK_MINECART) {
			return EntityTypes.COMMAND_BLOCK_MINECART;
		}
		if (item instanceof MinecartItem) {
			if (minecartTypeField == null) {
				try {
					for (Field f : MinecartItem.class.getDeclaredFields()) {
						if (EntityType.class.isAssignableFrom(f.getType())) {
							f.setAccessible(true);
							minecartTypeField = f;
							break;
						}
					}
				} catch (Exception ignored) {
				}
			}
			if (minecartTypeField != null) {
				try {
					return (EntityType<? extends AbstractMinecart>) minecartTypeField.get(item);
				} catch (Exception ignored) {
				}
			}
		}
		return EntityTypes.MINECART;
	}
}
