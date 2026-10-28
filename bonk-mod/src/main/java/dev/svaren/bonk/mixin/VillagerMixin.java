package dev.svaren.bonk.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import dev.svaren.bonk.UnconsciousEntity;

@Mixin(Villager.class)
public abstract class VillagerMixin extends AbstractVillager implements UnconsciousEntity {
	@Unique
	private int bonk$unconsciousTime = 0;

	protected VillagerMixin(EntityType<? extends AbstractVillager> entityType, Level level) {
		super(entityType, level);
	}

	@Override
	public int bonk$getUnconsciousTime() {
		return bonk$unconsciousTime;
	}

	@Override
	public void bonk$setUnconsciousTime(int ticks) {
		bonk$unconsciousTime = ticks;
	}

	/** Keep the villager "asleep" while it is unconscious (unless it ends up in water). */
	@Inject(method = "stopSleeping", at = @At("HEAD"), cancellable = true)
	private void bonk$onStopSleeping(CallbackInfo ci) {
		if (bonk$unconsciousTime > 0 && !this.getInBlockState().is(Blocks.WATER)) {
			bonk$unconsciousTime -= 1;
			ci.cancel();
		} else {
			this.setNoAi(false);
			bonk$unconsciousTime = 0;
		}
	}

	/** Knock the villager down when it has just been blammed. */
	@Inject(method = "customServerAiStep", at = @At("HEAD"))
	private void bonk$onServerAiStep(ServerLevel level, CallbackInfo ci) {
		if (!this.isSleeping() && bonk$unconsciousTime > 0) {
			this.setNoAi(true);

			Vec3 pos = this.position();
			this.startSleeping(this.blockPosition());
			this.setPos(pos.x, pos.y, pos.z);
		}
	}
}
