package com.farmutils.ui;

/**
 * Filter query case-sensitivity contract.
 *
 * Default is INSENSITIVE. SENSITIVE is a forward hook for future filter query
 * improvements (toggle or query syntax) without reworking the matching pipeline.
 */
public enum FilterCaseMode
{
    INSENSITIVE,
    SENSITIVE
}
