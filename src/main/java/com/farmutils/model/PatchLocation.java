package com.farmutils.model;

public final class PatchLocation
{
    private final String key;   // stable, e.g. "falador"
    private final String name;  // display, e.g. "Falador"

    public PatchLocation(String key, String name)
    {
        this.key = key;
        this.name = name;
    }

    public String getKey()
    {
        return key;
    }

    public String getName()
    {
        return name;
    }
}
