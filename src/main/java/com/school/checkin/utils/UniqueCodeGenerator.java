package com.school.checkin.utils;

import java.util.UUID;

public final class UniqueCodeGenerator {

    private UniqueCodeGenerator() {
    }

    public static String generate() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
    }
}
