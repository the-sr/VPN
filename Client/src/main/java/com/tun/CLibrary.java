package com.tun;

import com.sun.jna.LastErrorException;
import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Pointer;

/**
 * Bridges Java to the libc functions that create and drive a TUN interface.
 *
 * The JDK has no ioctl, so Java cannot call C directly - JNA declares each C
 * function as a Java signature and dispatches the call at runtime. These
 * signatures are C's, not Java's, and nothing validates them against the real
 * headers, so they must be kept in sync by hand.
 */
public interface CLibrary extends Library {

  /**
   * Loads libc and returns a proxy that looks like this interface; without it
   * the methods below have no C library to dispatch to. This is the single line
   * that calls dlopen() on libc.
   */
  CLibrary INSTANCE = Native.load("c", CLibrary.class);

  /**
   * int open(const char *pathname, int flags);
   * Opens a file or device and returns a file descriptor as a plain int rather
   * than a Java stream. For us it opens /dev/net/tun, the gateway to creating a
   * TUN interface.
   */
  int open(String pathname, int flags) throws LastErrorException;

  /**
   * int ioctl(int fd, unsigned long request, ...);
   * Sends a control command to a device, where `request` selects the command and
   * `arg` points at its argument data. It is the generic escape hatch for device
   * control; here it is called with TUNSETIFF, the command that creates tun0.
   */
  int ioctl(int fd, long request, Pointer arg) throws LastErrorException;

  /**
   * ssize_t read(int fd, void *buf, size_t count);
   * Pulls bytes out of the device into `buffer`. On a TUN fd it blocks until a
   * packet is available and returns exactly one whole IP packet per call, so the
   * caller is an infinite loop rather than a parser over partial data.
   */
  int read(int fd, byte[] buffer, int count) throws LastErrorException;

  /**
   * ssize_t write(int fd, const void *buf, size_t count);
   * Pushes bytes into the device as if a packet had just arrived from the network
   * - the reverse direction, and how replies will eventually be delivered back to
   * the OS.
   */
  int write(int fd, byte[] buffer, int count) throws LastErrorException;

  /**
   * int close(int fd);
   * Releases the file descriptor, and on a TUN fd also destroys the interface, so
   * tun0 disappears on close and Ctrl-C leaves nothing behind.
   */
  int close(int fd);
}
