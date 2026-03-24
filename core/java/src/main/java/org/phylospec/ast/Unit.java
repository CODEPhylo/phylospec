package org.phylospec.ast;

import java.util.Map;

public enum Unit {
    DAYS,
    YEARS,
    THOUSAND_YEARS,
    MILLION_YEARS;

    static Map<String, Unit> mapping = Map.of(
            "d", DAYS,
            "yr", YEARS,
            "kyr", THOUSAND_YEARS,
            "ka", THOUSAND_YEARS,
            "Myr", MILLION_YEARS,
            "Ma", MILLION_YEARS
    );

    public static boolean isValidUnit(String string) {
        return mapping.containsKey(string);
    }

    public static Unit toUnit(String string) {
        return mapping.get(string);
    }
}
