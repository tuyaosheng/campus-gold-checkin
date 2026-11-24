package com.school.checkin.utils;

import org.apache.commons.codec.digest.DigestUtils;

public final class PasswordUtil {

    private PasswordUtil() {
    }

    public static String md5(String rawPassword) {
        return DigestUtils.md5Hex(rawPassword);
    }
}
