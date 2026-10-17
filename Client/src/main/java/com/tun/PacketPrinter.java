package com.tun;

import java.util.HexFormat;

/**
 * Formats a captured packet for the console: a hex dump of the first bytes plus a
 * decode of the IPv4 header and TCP/UDP ports.
 *
 * The byte offsets come straight from the RFC 791 header layout - this is slicing a
 * buffer, nothing more. Output is discarded once encryption takes over in the next
 * phase, so no effort is spent making it pretty.
 */
public final class PacketPrinter {

  private PacketPrinter() {
    // utility class
  }

  public static void print(byte[] buf, int n) {
    System.out.println("[" + n + " bytes] "
        + HexFormat.ofDelimiter(" ").formatHex(buf, 0, Math.min(n, 64)));

    int version = (buf[0] >> 4) & 0x0F; // high nibble of byte 0
    if (version == 4 && n >= 20) {
      int ihl = (buf[0] & 0x0F) * 4; // IHL x 4 = header length, usually 20
      int proto = buf[9] & 0xFF;
      System.out.println("    IPv4 " + ipv4(buf, 12) + " -> " + ipv4(buf, 16)
          + "  proto=" + protoName(proto));

      // TCP and UDP both open with their port numbers immediately after the IP header.
      if ((proto == 6 || proto == 17) && n >= ihl + 4) {
        int sport = ((buf[ihl] & 0xFF) << 8) | (buf[ihl + 1] & 0xFF); // network order
        int dport = ((buf[ihl + 2] & 0xFF) << 8) | (buf[ihl + 3] & 0xFF);
        System.out.println("    " + protoName(proto) + " ports " + sport + " -> " + dport);
      }
    } else if (version == 6) {
      System.out.println("    IPv6 (not decoded yet)");
    } else {
      System.out.println("    unknown version=" + version);
    }
    System.out.println();
  }

  /** Reads four bytes at {@code off} as a dotted IPv4 string. */
  private static String ipv4(byte[] b, int off) {
    // Java bytes are signed, so & 0xFF turns them back into 0-255.
    return (b[off] & 0xFF) + "." + (b[off + 1] & 0xFF) + "."
        + (b[off + 2] & 0xFF) + "." + (b[off + 3] & 0xFF);
  }

  private static String protoName(int p) {
    return switch (p) {
      case 1 -> "ICMP";
      case 6 -> "TCP";
      case 17 -> "UDP";
      default -> String.valueOf(p);
    };
  }
}
