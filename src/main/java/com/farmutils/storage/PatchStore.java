package com.farmutils.storage;

import com.farmutils.model.PatchId;
import com.farmutils.model.PatchRecord;
import com.farmutils.model.PatchState;
import com.farmutils.model.PatchSource;
import com.farmutils.FarmutilsConfig;
import java.util.Optional;
import javax.inject.Inject;
import net.runelite.client.config.ConfigManager;
import com.farmutils.model.PatchView;
import java.time.Duration;
import javax.inject.Singleton;

@Singleton
public class PatchStore
{
    private static final String GROUP = "farmutils";
    private static final String KEY_PREFIX = "patch.";

    @Inject
    private FarmutilsConfig config;

    @Inject
    private ConfigManager configManager;

    private static final long STALE_MILLIS = Duration.ofDays(7).toMillis();

    private boolean isStale(PatchRecord record, long nowMillis)
    {
        int days = config.staleDays();
        if (days <= 0)
        {
            return false; // treat as "never stale"
        }
        long threshold = Duration.ofDays(days).toMillis();
        return nowMillis - record.getUpdatedAtMillis() > threshold;
    }

    public Optional<PatchRecord> load(PatchId id)
    {
        String raw = configManager.getConfiguration(GROUP, KEY_PREFIX + id.storageKey());
        if (raw == null || raw.isEmpty())
        {
            return Optional.empty(); // Unknown
        }

        String[] parts = raw.split("\\|", 2);
        if (parts.length != 2)
        {
            return Optional.empty();
        }

        try
        {
            PatchState state = PatchState.valueOf(parts[0]);
            long ts = Long.parseLong(parts[1]);
            return Optional.of(new PatchRecord(state, ts));
        }
        catch (Exception e)
        {
            return Optional.empty();
        }
    }

    public PatchView view(PatchId id)
    {
        long now = System.currentTimeMillis();
        Optional<PatchRecord> record = load(id);

        boolean stale = record.isPresent() && isStale(record.get(), now);

        PatchSource source = record.isPresent()
                ? PatchSource.MANUAL
                : PatchSource.UNKNOWN;

        return new PatchView(record, stale, source);
    }

    public void save(PatchId id, PatchState state)
    {
        long now = System.currentTimeMillis();
        String raw = state.name() + "|" + now;
        configManager.setConfiguration(GROUP, KEY_PREFIX + id.storageKey(), raw);
    }

    public void clear(PatchId id)
    {
        configManager.unsetConfiguration(GROUP, KEY_PREFIX + id.storageKey());
    }
}
