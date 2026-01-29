package com.farmutils.storage;

import com.farmutils.model.PatchId;
import com.farmutils.model.PatchRecord;
import com.farmutils.model.PatchState;
import java.util.Optional;
import javax.inject.Inject;
import net.runelite.client.config.ConfigManager;
import javax.inject.Singleton;

@Singleton
public class PatchStore
{
    private static final String GROUP = "farmutils";
    private static final String KEY_PREFIX = "patch.";

    @Inject
    private ConfigManager configManager;

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
