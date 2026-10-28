package dev.svaren.bonk;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import dev.svaren.bonk.net.RerollPayload;

public class Bonk implements ModInitializer {
	public static final String MOD_ID = "bonk";
	public static final Logger LOGGER = LoggerFactory.getLogger("BONK");

	/** How much durability one bonk / blam costs. */
	public static final int DURABILITY_COST = 1;

	/** Max distance (blocks) between player and villager when using the reroll key. */
	private static final double KEY_MAX_DISTANCE = 6.0;

	/** Minimum ticks between two key rerolls by the same player (anti-spam). */
	private static final long KEY_COOLDOWN_TICKS = 5;

	private static final Map<UUID, Long> LAST_KEY_USE = new HashMap<>();

	@Override
	public void onInitialize() {
		// Hitting a villager with a shovel / mace (works even without the client mod).
		AttackEntityCallback.EVENT.register((player, level, hand, entity, hitResult) -> {
			if (!(level instanceof ServerLevel serverLevel) || !(entity instanceof Villager villager)) {
				return InteractionResult.PASS;
			}

			ItemStack stack = player.getItemInHand(hand);
			if (!isBonkTool(stack)) {
				return InteractionResult.PASS;
			}

			useTool(serverLevel, player, hand, villager);
			// Cancel the actual hit so the villager is not hurt.
			return InteractionResult.FAIL;
		});

		// A knocked-out villager wakes up when it takes damage.
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
			if (entity instanceof UnconsciousEntity unconscious) {
				unconscious.bonk$setUnconsciousTime(0);
			}
			return true;
		});

		// Reroll key (C by default) sent from the client mod.
		PayloadTypeRegistry.serverboundPlay().register(RerollPayload.TYPE, RerollPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(RerollPayload.TYPE, (payload, context) -> handleRerollKey(context.player(), payload.entityId()));

		LOGGER.info("Initialized!");
	}

	public static boolean isBonkTool(ItemStack stack) {
		return stack.is(ItemTags.SHOVELS) || stack.is(Items.MACE);
	}

	private static void handleRerollKey(ServerPlayer player, int entityId) {
		if (player.isSpectator() || !(player.level() instanceof ServerLevel level)) {
			return;
		}

		long now = level.getGameTime();
		Long last = LAST_KEY_USE.get(player.getUUID());
		if (last != null && now - last < KEY_COOLDOWN_TICKS && now >= last) {
			return;
		}
		LAST_KEY_USE.put(player.getUUID(), now);

		Entity entity = level.getEntity(entityId);
		if (!(entity instanceof Villager villager) || !villager.isAlive()
				|| player.distanceToSqr(villager) > KEY_MAX_DISTANCE * KEY_MAX_DISTANCE) {
			return;
		}

		InteractionHand hand;
		if (isBonkTool(player.getItemInHand(InteractionHand.MAIN_HAND))) {
			hand = InteractionHand.MAIN_HAND;
		} else if (isBonkTool(player.getItemInHand(InteractionHand.OFF_HAND))) {
			hand = InteractionHand.OFF_HAND;
		} else {
			player.sendOverlayMessage(Component.translatable("bonk.message.need_tool"));
			return;
		}

		useTool(level, player, hand, villager);
	}

	/** Bonk (shovel) or blam (mace) the villager with the item in the given hand. */
	public static void useTool(ServerLevel level, Player player, InteractionHand hand, Villager villager) {
		ItemStack stack = player.getItemInHand(hand);
		boolean success;

		if (stack.is(Items.MACE)) {
			blamVillager(level, villager);
			success = true;
		} else if (stack.is(ItemTags.SHOVELS)) {
			success = bonkVillager(level, villager);
		} else {
			return;
		}

		if (success) {
			// Costs durability (does nothing in creative mode).
			EquipmentSlot slot = hand == InteractionHand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND;
			stack.hurtAndBreak(DURABILITY_COST, player, slot);
		}
	}

	/**
	 * Attempt to bonk a villager. Fails if the villager has no profession or has already been traded with.
	 * @return true if the bonk succeeded.
	 */
	private static boolean bonkVillager(ServerLevel level, Villager villager) {
		boolean canBeBonked = !villager.getVillagerData().profession().is(VillagerProfession.NONE)
				&& villager.getVillagerXp() == 0;

		if (!canBeBonked) {
			failBonk(level, villager);
			return false;
		}

		spawnBonkParticles(level, villager);
		playBonkSounds(level, villager);
		resetOffers(villager);
		return true;
	}

	private static void failBonk(ServerLevel level, Villager villager) {
		level.sendParticles(ParticleTypes.ANGRY_VILLAGER, villager.getX(), villager.getY() + 1.5, villager.getZ(), 1, 0.0, 0.0, 0.0, 0.01);
		level.playSound(null, villager, SoundEvents.NOTE_BLOCK_COW_BELL.value(), SoundSource.NEUTRAL, 1f, 0f);
	}

	/** A BLAM always succeeds, resets everything and knocks the villager out for a short time. */
	private static void blamVillager(ServerLevel level, Villager villager) {
		((UnconsciousEntity) villager).bonk$setUnconsciousTime(60);

		spawnBonkParticles(level, villager);
		level.sendParticles(ParticleTypes.ELECTRIC_SPARK, villager.getX(), villager.getY() + 1.5, villager.getZ(), 20, 0.0, 0.0, 0.0, 0.8);
		playBonkSounds(level, villager);
		level.playSound(null, villager, SoundEvents.MACE_SMASH_AIR, SoundSource.NEUTRAL, 0.5f, 1.0f);

		resetOffers(villager);
		villager.getGossips().clear();
	}

	private static void spawnBonkParticles(ServerLevel level, Entity entity) {
		level.sendParticles(ParticleTypes.POOF, entity.getX(), entity.getY() + 1.5, entity.getZ(), 8, 0.2, 0.2, 0.2, 0.01);
	}

	private static void playBonkSounds(ServerLevel level, Entity entity) {
		level.playSound(null, entity, SoundEvents.NOTE_BLOCK_COW_BELL.value(), SoundSource.NEUTRAL, 1f, 0.7f);
	}

	/** Reset trade offers and profession progress. */
	public static void resetOffers(Villager villager) {
		villager.setOffers(null);
		villager.setVillagerXp(0);
		villager.setVillagerData(villager.getVillagerData().withLevel(1));
	}
}
