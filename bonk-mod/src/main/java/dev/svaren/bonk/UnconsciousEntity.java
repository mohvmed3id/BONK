package dev.svaren.bonk;

/** An entity that can be knocked unconscious. Implemented on villagers by {@link dev.svaren.bonk.mixin.VillagerMixin}. */
public interface UnconsciousEntity {
	int bonk$getUnconsciousTime();

	void bonk$setUnconsciousTime(int ticks);
}
