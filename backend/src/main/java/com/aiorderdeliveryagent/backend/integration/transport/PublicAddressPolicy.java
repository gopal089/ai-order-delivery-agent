package com.aiorderdeliveryagent.backend.integration.transport;

import java.net.InetAddress;

/** Conservative public-unicast policy; special-use transition/documentation ranges are denied. */
final class PublicAddressPolicy {
	static boolean permits(InetAddress address) {
		byte[] b = address.getAddress();
		if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
				|| address.isSiteLocalAddress() || address.isMulticastAddress()) return false;
		if (b.length == 4) {
			int a = b[0] & 255, c = b[1] & 255, d = b[2] & 255;
			return !(a == 0 || a == 10 || a == 127 || a >= 224
				|| (a == 100 && c >= 64 && c <= 127)
				|| (a == 169 && c == 254) || (a == 172 && c >= 16 && c <= 31)
				|| (a == 192 && (c == 168 || (c == 0 && (d == 0 || d == 2)) || (c == 88 && d == 99)))
				|| (a == 198 && (c == 18 || c == 19 || (c == 51 && d == 100)))
				|| (a == 203 && c == 0 && d == 113));
		}
		if (b.length != 16 || (b[0] & 0xe0) != 0x20) return false;
		// Protocol assignments, Teredo, benchmarking, ORCHID, documentation, 6to4.
		return !((b[0] == 0x20 && b[1] == 0x01 && (b[2] & 0xfe) == 0)
			|| (b[0] == 0x20 && b[1] == 0x01 && (b[2] & 255) == 0x0d && (b[3] & 255) == 0xb8)
			|| (b[0] == 0x20 && b[1] == 0x02)
			|| ((b[0] & 255) == 0x3f && (b[1] & 255) == 0xff && (b[2] & 0xf0) == 0));
	}
	private PublicAddressPolicy() {}
}
