package com.crypto;

import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.GeneralSecurityException;
import java.util.Arrays;

/**
 * AES-256-GCM around one packet at a time.
 *
 * GCM is an AEAD, so a single doFinal both encrypts and produces a 16-byte tag; the
 * same call in reverse verifies that tag before returning plaintext. Wire format:
 *
 *   [ 8-byte counter ][ ciphertext ][ 16-byte tag ]
 *
 * NOT thread-safe. javax.crypto.Cipher keeps per-call state, so one instance belongs to
 * one thread - which is fine here, since the client capture loop and the server receive
 * loop are each single-threaded. Sharing one of these would corrupt packets silently.
 */
public final class PacketCipher {

  private static final String ALGORITHM = "AES/GCM/NoPadding";
  private static final int COUNTER_LEN = 8;
  private static final int NONCE_LEN = 12;
  private static final int TAG_LEN = 16;
  private static final int TAG_BITS = TAG_LEN * 8;

  private final SecretKey key;
  private final Cipher cipher;

  /** Sender-side packet counter. The receiver never increments this. */
  private long counter = 0;

  public PacketCipher(byte[] rawKey) {
    this.key = new SecretKeySpec(rawKey, "AES");
    try {
      this.cipher = Cipher.getInstance(ALGORITHM);
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("AES-GCM is not available in this JVM", e);
    }
  }

  /**
   * Encrypts one packet, consuming the next counter value.
   *
   * @param plain the raw IP packet.
   * @param len how many bytes of plain to encrypt.
   * @return the wire form: counter, then ciphertext, then tag.
   */
  public byte[] encrypt(byte[] plain, int len) {
    counter++;
    byte[] counterBytes = counterBytes(counter);

    byte[] encrypted;
    try {
      cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, nonce(counter)));
      cipher.updateAAD(counterBytes); // authenticated, but left in the clear
      encrypted = cipher.doFinal(plain, 0, len);
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("encryption failed", e);
    }

    byte[] wire = new byte[COUNTER_LEN + encrypted.length];
    System.arraycopy(counterBytes, 0, wire, 0, COUNTER_LEN);
    System.arraycopy(encrypted, 0, wire, COUNTER_LEN, encrypted.length);
    return wire;
  }

  /**
   * Verifies and decrypts one wire packet.
   *
   * @return the plaintext packet, or null if the tag did not verify or the input was
   *         too short. Callers discard silently: telling an attacker which part failed
   *         would help them.
   */
  public byte[] decrypt(byte[] wire, int len) {
    if (len < COUNTER_LEN + TAG_LEN) return null;

    byte[] counterBytes = Arrays.copyOfRange(wire, 0, COUNTER_LEN);
    byte[] nonce = nonce(counter(counterBytes));

    try {
      cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, nonce));
      cipher.updateAAD(counterBytes);
      // GCM expects the tag to be part of the input and strips it itself, so the slice
      // runs to the end of the packet rather than stopping at the ciphertext.
      return cipher.doFinal(wire, COUNTER_LEN, len - COUNTER_LEN);
    } catch (AEADBadTagException e) {
      return null; // tampered, replayed, or the wrong key
    } catch (GeneralSecurityException e) {
      return null;
    }
  }

  /**
   * Reads the counter without decrypting. Useful for the replay window, but only after
   * decrypt() has succeeded - a counter that has not proven possession of the key must
   * never be trusted.
   */
  public static long counterOf(byte[] wire) {
    return counter(Arrays.copyOfRange(wire, 0, COUNTER_LEN));
  }

  /** Big-endian, so the wire form reads the same in a hex dump as the number. */
  private static byte[] counterBytes(long value) {
    byte[] out = new byte[COUNTER_LEN];
    for (int i = COUNTER_LEN - 1; i >= 0; i--) {
      out[i] = (byte) value;
      value >>>= 8;
    }
    return out;
  }

  private static long counter(byte[] bytes) {
    long value = 0;
    for (byte b : bytes) {
      value = (value << 8) | (b & 0xFF);
    }
    return value;
  }

  /**
   * 12-byte nonce: the counter in the last eight bytes, first four zero. GCM is broken
   * outright if a nonce repeats under the same key, so uniqueness has to be guaranteed
   * by construction - which the counter does - rather than left to random chance.
   */
  private static byte[] nonce(long counter) {
    byte[] out = new byte[NONCE_LEN];
    System.arraycopy(counterBytes(counter), 0, out, NONCE_LEN - COUNTER_LEN, COUNTER_LEN);
    return out;
  }
}
