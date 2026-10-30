package com.kaarislabs.chatbot.geo;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Optional;
import java.util.regex.Pattern;

/** Strict parsing of client IP strings; nothing that is not a plain IPv4/IPv6 literal ever reaches a URL. */
public final class IpAddresses {

    private static final int MAX_LENGTH = 45;
    private static final String OCTET = "(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)";
    private static final Pattern IPV4 = Pattern.compile("^" + OCTET + "(\\." + OCTET + "){3}$");
    // Hex digits, colons and (for IPv4-mapped forms) dots only: no zone ids, brackets, ports or names.
    private static final Pattern IPV6_CHARS = Pattern.compile("^[0-9A-Fa-f:.]+$");

    private IpAddresses() {
    }

    /**
     * @return the normalised address if {@code value} is a valid IPv4 or IPv6 literal of a public host; empty for
     *         missing, malformed, loopback, private, link-local, multicast and other special-use addresses
     */
    static Optional<String> publicAddress(String value) {
        return parse(value).filter(IpAddresses::isPublic).map(InetAddress::getHostAddress);
    }

    /** The normalised form of any valid IPv4/IPv6 literal (public or not); empty for anything else. */
    public static Optional<String> normalized(String value) {
        return parse(value).map(InetAddress::getHostAddress);
    }

    static Optional<InetAddress> parse(String value) {
        if (value == null) {
            return Optional.empty();
        }
        String ip = value.trim();
        if (ip.isEmpty() || ip.length() > MAX_LENGTH) {
            return Optional.empty();
        }
        boolean v4 = IPV4.matcher(ip).matches();
        boolean v6 = !v4 && ip.indexOf(':') >= 0 && IPV6_CHARS.matcher(ip).matches();
        if (!v4 && !v6) {
            return Optional.empty();
        }
        try {
            // A literal is parsed locally; with the checks above no DNS lookup can happen.
            return Optional.of(InetAddress.getByName(ip));
        } catch (UnknownHostException e) {
            return Optional.empty();
        }
    }

    static boolean isPublic(InetAddress address) {
        if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
                || address.isSiteLocalAddress() || address.isMulticastAddress()) {
            return false;
        }
        byte[] b = address.getAddress();
        if (address instanceof Inet4Address) {
            int first = b[0] & 0xff;
            int second = b[1] & 0xff;
            boolean thisNetwork = first == 0;
            boolean carrierNat = first == 100 && (second & 0xc0) == 64; // 100.64.0.0/10
            boolean reserved = first >= 240; // 240.0.0.0/4 and broadcast
            return !thisNetwork && !carrierNat && !reserved;
        }
        if (address instanceof Inet6Address) {
            boolean uniqueLocal = (b[0] & 0xfe) == 0xfc; // fc00::/7
            boolean documentation = (b[0] & 0xff) == 0x20 && (b[1] & 0xff) == 0x01 && (b[2] & 0xff) == 0x0d
                    && (b[3] & 0xff) == 0xb8; // 2001:db8::/32
            return !uniqueLocal && !documentation;
        }
        return false;
    }
}
