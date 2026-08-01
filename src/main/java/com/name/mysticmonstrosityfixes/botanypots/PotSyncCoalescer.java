package com.name.mysticmonstrosityfixes.botanypots;

/**
 * Implemented on BlockEntityBotanyPot so the static pot ticker can pick up sync requests that were raised by instance
 * code during the tick.
 */
public interface PotSyncCoalescer {

    /**
     * @return true if a client sync was requested since the last call, clearing the request.
     */
    boolean mmf$takeQueuedSync();
}
