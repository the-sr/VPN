package com.tun;

import com.sun.jna.Memory;

/**
 * Represents one TUN interface: a virtual network card with no hardware behind it.
 *
 * The OS treats tun0 as a real NIC and will happily route packets to it, but the
 * other end of the interface is a file descriptor we hold. Packets sent to tun0
 * therefore never reach the network - they emerge from read() instead, which is
 * what makes interception possible.
 *
 * Opening /dev/net/tun only yields a handle; the interface appears when ioctl is
 * called with TUNSETIFF and vanishes again when the fd is closed, so it lives for
 * exactly as long as this object does.
 */
public class TunDevice implements AutoCloseable {

  // The TUN subsystem's device file. It is opened to obtain a handle but never read
  // or written like a normal file - the interface itself is created via ioctl below.
  private static final String DEV_NET_TUN = "/dev/net/tun";

  // O_RDWR (2, from fcntl.h) rather than O_RDONLY, because packets are both captured
  // out of the interface and injected back into it.
  private static final int O_RDWR = 2;

  // The ioctl command that attaches a descriptor to an interface. The value comes from
  // the kernel's _IOW('T', 202, int) macro, precomputed here to 0x400454ca.
  private static final long TUNSETIFF = 0x400454caL;

  // Modes from <linux/if_tun.h>. IFF_TUN gives Layer 3 (raw IP packets) rather than
  // IFF_TAP's Layer 2 Ethernet frames, which is what a VPN carries. IFF_NO_PI drops the
  // 4-byte packet info prefix so read() hands back a bare IP packet.
  private static final short IFF_TUN = 0x0001;
  private static final short IFF_NO_PI = 0x1000;

  // The file descriptor is the whole handle to the interface.
  private final int fd;

  /**
   * Creates the interface and holds it open for this object's lifetime.
   *
   * @param name interface name, e.g. "tun0". Creating one requires CAP_NET_ADMIN, so
   *             the process must run as root.
   */
  public TunDevice(String name) {
    // Opening only yields a descriptor; no interface exists yet. On failure JNA throws
    // LastErrorException carrying errno.
    this.fd = CLibrary.INSTANCE.open(DEV_NET_TUN, O_RDWR);

    // ioctl expects a struct ifreq: 40 bytes on 64-bit Linux, holding the interface
    // name as a 16-byte NUL-padded string at offset 0 and a union whose first two bytes
    // at offset 16 are ifr_flags. JNA's Memory is raw native memory, so the struct is
    // laid out byte by byte.
    Memory ifr = new Memory(40);
    ifr.clear();                                     // native memory isn't guaranteed to start zeroed
    ifr.setString(0, name);                          // ifr_name
    ifr.setShort(16, (short) (IFF_TUN | IFF_NO_PI)); // ifr_flags

    // On success `name` becomes a real network interface, though still DOWN and with no
    // address - configuring it is TunMain's responsibility.
    CLibrary.INSTANCE.ioctl(fd, TUNSETIFF, ifr);
  }

  /**
   * Blocks until the kernel routes a packet into the interface and returns it whole.
   * One call yields exactly one packet, so the caller simply loops.
   *
   * @return the packet length in bytes, or -1 on error or if the interface is closed.
   */
  public int read(byte[] buf) {
    return CLibrary.INSTANCE.read(fd, buf, buf.length);
  }

  /**
   * Injects a packet into the interface as though it had arrived from the network, so
   * the OS routes it onward to the waiting application. This is the direction replies
   * will travel once we have a server to receive them from.
   *
   * @param buf the raw packet bytes.
   * @param len how many bytes of buf to write.
   */
  public int write(byte[] buf, int len) {
    return CLibrary.INSTANCE.write(fd, buf, len);
  }

  /**
   * Invoked by try-with-resources. Closing the descriptor makes the kernel drop the
   * interface, so tun0 disappears instead of lingering in a DOWN state.
   */
  @Override
  public void close() {
    CLibrary.INSTANCE.close(fd);
  }
}
