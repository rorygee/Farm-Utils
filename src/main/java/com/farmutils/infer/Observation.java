package com.farmutils.infer;

import com.farmutils.model.PatchId;
import com.farmutils.model.PatchState;
import java.time.Instant;
import java.util.Objects;

/** A single observation about a patch at a specific moment. */
public final class Observation
{
    public enum Kind
    {
        PATCH_STATE_SET,
        PATCH_STATE_CLEARED,

        // v3 anchor events (debug-fed / test-fed)
        PLANTED,
        HARVESTED,
        DISEASED_SET,
        DISEASED_CLEARED,
        DEAD_SET
    }

    private final Kind kind;
    private final PatchId patchId;
    private final PatchState patchStateOrNull;
    private final Instant observedAt;
    private final ObservationSource source;

    private Observation(
            Kind kind,
            PatchId patchId,
            PatchState patchStateOrNull,
            Instant observedAt,
            ObservationSource source)
    {
        this.kind = Objects.requireNonNull(kind, "kind");
        this.patchId = Objects.requireNonNull(patchId, "patchId");
        this.patchStateOrNull = patchStateOrNull;
        this.observedAt = Objects.requireNonNull(observedAt, "observedAt");
        this.source = Objects.requireNonNull(source, "source");
    }

    public static Observation patchStateSet(PatchId patchId, PatchState patchState, Instant observedAt, ObservationSource source)
    {
        return new Observation(Kind.PATCH_STATE_SET, patchId, Objects.requireNonNull(patchState, "patchState"), observedAt, source);
    }

    public static Observation patchStateCleared(PatchId patchId, Instant observedAt, ObservationSource source)
    {
        return new Observation(Kind.PATCH_STATE_CLEARED, patchId, null, observedAt, source);
    }

    public static Observation planted(PatchId patchId, Instant observedAt, ObservationSource source)
    {
        return new Observation(Kind.PLANTED, patchId, null, observedAt, source);
    }

    public static Observation harvested(PatchId patchId, Instant observedAt, ObservationSource source)
    {
        return new Observation(Kind.HARVESTED, patchId, null, observedAt, source);
    }

    public static Observation diseasedSet(PatchId patchId, Instant observedAt, ObservationSource source)
    {
        return new Observation(Kind.DISEASED_SET, patchId, null, observedAt, source);
    }

    public static Observation diseasedCleared(PatchId patchId, Instant observedAt, ObservationSource source)
    {
        return new Observation(Kind.DISEASED_CLEARED, patchId, null, observedAt, source);
    }

    public static Observation deadSet(PatchId patchId, Instant observedAt, ObservationSource source)
    {
        return new Observation(Kind.DEAD_SET, patchId, null, observedAt, source);
    }

    public Kind getKind()
    {
        return kind;
    }

    public PatchId getPatchId()
    {
        return patchId;
    }

    public PatchState getPatchStateOrNull()
    {
        return patchStateOrNull;
    }

    public Instant getObservedAt()
    {
        return observedAt;
    }

    public ObservationSource getSource()
    {
        return source;
    }
}
