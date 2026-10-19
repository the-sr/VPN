package com.crypto;

/**
 * Slides a fixed window over the packet counter, remembering which of the most recent
 * counters have already been accepted.
 *
 * Only one long is needed: `highest` tracks the best counter seen so far and `bitmap`
 * marks the ones below it. A 64-bit window therefore costs no allocation and no
 * collection lookups on the hot path.
 */
public final class ReplayWindow {

  private final long window;

  /** Best counter accepted so far; its bit is bit 0 of `bitmap`. */
  private long highest = 0;

  /** Bit i marks whether counter (highest - i) has already been seen. */
  private long bitmap = 0;

  public ReplayWindow(int window) {
    if (window < 1 || window > 64) {
      throw new IllegalArgumentException("replay window must be between 1 and 64");
    }
    this.window = window;
  }

  /**
   * Records a counter and reports whether it is fresh.
   *
   * Call this only after the packet's tag has been verified. Letting an unauthenticated
   * counter move the window would let an attacker who can reach the port poison it with
   * a high value, after which every legitimate in-flight packet would look like a replay.
   *
   * @return false if the counter was already seen, too old to remember, or the reserved
   *         zero.
   */
  public boolean accept(long counter) {
    if (counter == 0) return false; // 0 is never issued, so it is always a replay

    if (counter > highest) {
      // New high water mark: shift the remembered bits up by the gap, or clear them all
      // if the packet jumped further back than the window can reach.
      long shift = counter - highest;
      bitmap = shift >= window ? 0L : (bitmap << shift);
      bitmap |= 1; // the new high water mark counts as seen, or it would be accepted twice
      highest = counter;
      return true;
    }

    long diff = highest - counter;
    if (diff >= window) return false; // older than the window remembers
    long bit = 1L << diff;
    if ((bitmap & bit) != 0) return false; // already accepted
    bitmap |= bit;
    return true;
  }
}
