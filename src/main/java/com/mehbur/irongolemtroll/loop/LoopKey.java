package com.mehbur.irongolemtroll.loop;

import java.util.UUID;

/** Identifies one player/golem pair, so every pair runs its own independent loop. */
public record LoopKey(UUID player, UUID golem) {
}
