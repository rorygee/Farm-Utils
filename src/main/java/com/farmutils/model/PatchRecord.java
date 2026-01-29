package com.farmutils.model;

public final class PatchRecord
{
    private final PatchState state;
    private final long updatedAtMillis;

    public PatchRecord(PatchState state, long updatedAtMillis)
    {
        this.state = state;
        this.updatedAtMillis = updatedAtMillis;
    }

    public PatchState getState()
    {
        return state;
    }

    public long getUpdatedAtMillis()
    {
        return updatedAtMillis;
    }
}
