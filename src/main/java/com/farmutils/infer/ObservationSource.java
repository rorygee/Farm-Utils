package com.farmutils.infer;

/**
 * Where an observation came from.
 *
 * v0: only {@link #MANUAL_DEBUG} is used.
 */
public enum ObservationSource
{
    MANUAL_DEBUG,

    // Reserved for future sources.
    VARBIT,
    CHAT_MESSAGE,
    ITEM_SPAWN
}
