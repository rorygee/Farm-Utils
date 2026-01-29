package com.farmutils.model;

import java.util.Optional;

public final class PatchView
{
    private final Optional<PatchRecord> record;
    private final boolean stale;
    private final PatchSource source;

    public PatchView(Optional<PatchRecord> record, boolean stale, PatchSource source)
    {
        this.record = record;
        this.stale = stale;
        this.source = source;
    }

    public Optional<PatchRecord> getRecord()
    {
        return record;
    }

    public boolean isStale()
    {
        return stale;
    }

    public PatchSource getSource()
    {
        return source;
    }
}