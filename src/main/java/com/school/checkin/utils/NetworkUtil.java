package com.school.checkin.utils;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public final class NetworkUtil {

    private NetworkUtil() {
    }

    public static String resolveLocalIpv4() {
        try {
            List<Inet4Address> candidates = new ArrayList<>();
            for (NetworkInterface networkInterface : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!networkInterface.isUp() || networkInterface.isLoopback() || networkInterface.isVirtual() || shouldSkip(networkInterface)) {
                    continue;
                }
                for (InetAddress address : Collections.list(networkInterface.getInetAddresses())) {
                    if (address instanceof Inet4Address && !address.isLoopbackAddress() && !address.isLinkLocalAddress()) {
                        candidates.add((Inet4Address) address);
                    }
                }
            }
            if (!candidates.isEmpty()) {
                candidates.sort(Comparator.comparing((Inet4Address address) -> !address.isSiteLocalAddress()));
                return candidates.get(0).getHostAddress();
            }
            return InetAddress.getLocalHost().getHostAddress();
        } catch (Exception ex) {
            return "127.0.0.1";
        }
    }

    private static boolean shouldSkip(NetworkInterface networkInterface) {
        String name = networkInterface.getName() == null ? "" : networkInterface.getName().toLowerCase(Locale.ROOT);
        String displayName = networkInterface.getDisplayName() == null ? "" : networkInterface.getDisplayName().toLowerCase(Locale.ROOT);
        String combined = name + " " + displayName;
        return combined.contains("virtual")
                || combined.contains("vmware")
                || combined.contains("hyper-v")
                || combined.contains("docker")
                || combined.contains("teredo")
                || combined.contains("loopback");
    }
}
