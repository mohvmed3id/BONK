package dev.svaren.bonk.client;

import com.mojang.blaze3d.platform.InputConstants;
import org.lwjgl.sdl.SDLScancode;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.phys.EntityHitResult;

import dev.svaren.bonk.net.RerollPayload;

public class BonkClient implements ClientModInitializer {
	private static KeyMapping rerollKey;

	@Override
	public void onInitializeClient() {
		KeyMapping.Category category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath("bonk", "bonk"));

		// Default key: C (can be changed in Options > Controls > Key Binds > BONK)
		rerollKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key.bonk.reroll",
				InputConstants.Type.KEYBOARD,
				SDLScancode.SDL_SCANCODE_C,
				category
		));

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (rerollKey.consumeClick()) {
				onRerollPressed(client);
			}
		});
	}

	private static void onRerollPressed(Minecraft client) {
		if (client.player == null || client.level == null) {
			return;
		}

		if (!(client.hitResult instanceof EntityHitResult entityHit) || !(entityHit.getEntity() instanceof Villager villager)) {
			client.player.sendOverlayMessage(Component.translatable("bonk.message.look_at_villager"));
			return;
		}

		if (!ClientPlayNetworking.canSend(RerollPayload.TYPE)) {
			client.player.sendOverlayMessage(Component.translatable("bonk.message.server_missing"));
			return;
		}

		ClientPlayNetworking.send(new RerollPayload(villager.getId()));
	}
}
