package com.tun;

import com.config.Config;

/**
 * Creates the tunnel interface and points traffic at it.
 *
 * The `ip` commands here are the same ones you would type by hand; running them from
 * Java keeps the interface setup in one place instead of spreading shell strings
 * through the main class.
 */
public final class TunSetup {

  private final Config config;

  public TunSetup(Config config) {
    this.config = config;
  }

  /**
   * Creates the interface, gives it an address, powers it on, sets its MTU and
   * optionally hijacks the default route. Returns a handle the caller must close.
   */
  public TunDevice bringUp() throws Exception {
    String name = config.get("tun.name");
    TunDevice tun = new TunDevice(name);

    // A freshly created interface is DOWN and has no address, so nothing would flow to
    // it yet. Flushing first clears leftovers from an earlier run.
    run("ip", "addr", "flush", "dev", name);
    run("ip", "addr", "add", config.get("tun.address"), "dev", name);
    run("ip", "link", "set", name, "up");

    // The default MTU of 1500 would let the kernel hand over a packet that, once wrapped
    // in our counter, ciphertext and tag, exceeds the path MTU and gets IP-fragmented -
    // slow when it works and silently dropped through NAT when it doesn't. Shrinking the
    // interface keeps every forwarded datagram inside a single frame.
    run("ip", "link", "set", name, "mtu", config.get("tun.mtu"));

    if (config.getBool("tun.hijackDefaultRoute")) {
      // Two /1 routes are more specific than the 0.0.0.0/0 default, so together they
      // catch every non-local IPv4 destination. They are bound to the interface, so they
      // disappear with it and the network recovers on exit.
      run("ip", "route", "add", "0.0.0.0/1", "dev", name);
      run("ip", "route", "add", "128.0.0.0/1", "dev", name);
    }

    return tun;
  }

  /** Runs an external command, inheriting its output so `ip` errors reach the console. */
  private static void run(String... cmd) throws Exception {
    int code = new ProcessBuilder(cmd).inheritIO().start().waitFor();
    if (code != 0) {
      throw new RuntimeException("command failed (" + code + "): " + String.join(" ", cmd));
    }
  }
}
