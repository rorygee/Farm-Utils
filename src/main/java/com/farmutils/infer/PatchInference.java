package com.farmutils.infer;

import com.farmutils.model.PatchId;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Objects;

/** Immutable output for a single PatchId. */
public final class PatchInference
{
    private final PatchId patchId;
    private final InferredStage stage;
    private final Instant lastObservedAt;
    private final Instant earliestReadyAt;
    private final Instant latestReadyAt;
    private final float confidence;
    private final EnumSet<ReasonCode> reasons;

    public PatchInference(
            PatchId patchId,
            InferredStage stage,
            Instant lastObservedAt,
            Instant earliestReadyAt,
            Instant latestReadyAt,
            float confidence,
            EnumSet<ReasonCode> reasons)
    {
        this.patchId = Objects.requireNonNull(patchId, "patchId");
        this.stage = Objects.requireNonNull(stage, "stage");
        this.lastObservedAt = lastObservedAt;
        this.earliestReadyAt = earliestReadyAt;
        this.latestReadyAt = latestReadyAt;
        this.confidence = confidence;
        this.reasons = reasons == null ? EnumSet.noneOf(ReasonCode.class) : EnumSet.copyOf(reasons);
    }

    public PatchId getPatchId()
    {
        return patchId;
    }

    public InferredStage getStage()
    {
        return stage;
    }

    public Instant getLastObservedAt()
    {
        return lastObservedAt;
    }

    public Instant getEarliestReadyAt()
    {
        return earliestReadyAt;
    }

    public Instant getLatestReadyAt()
    {
        return latestReadyAt;
    }

    public float getConfidence()
    {
        return confidence;
    }

    public EnumSet<ReasonCode> getReasons()
    {
        return EnumSet.copyOf(reasons);
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o)
        {
            return true;
        }
        if (!(o instanceof PatchInference))
        {
            return false;
        }

        PatchInference that = (PatchInference) o;
        return Float.compare(that.confidence, confidence) == 0
                && patchId.equals(that.patchId)
                && stage == that.stage
                && Objects.equals(lastObservedAt, that.lastObservedAt)
                && Objects.equals(earliestReadyAt, that.earliestReadyAt)
                && Objects.equals(latestReadyAt, that.latestReadyAt)
                && reasons.equals(that.reasons);
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(patchId, stage, lastObservedAt, earliestReadyAt, latestReadyAt, confidence, reasons);
    }

    @Override
    public String toString()
    {
        return "PatchInference{" +
                "patchId=" + patchId +
                ", stage=" + stage +
                ", lastObservedAt=" + lastObservedAt +
                ", earliestReadyAt=" + earliestReadyAt +
                ", latestReadyAt=" + latestReadyAt +
                ", confidence=" + confidence +
                ", reasons=" + reasons +
                '}';
    }
}
