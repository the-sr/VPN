package com.config;

import java.io.IOException;
import java.io.InputStream;
import java.util.Base64;
import java.util.Properties;

/**
 * Loads app.properties from the classpath and hands out typed values.
 *
 * Reading from the classpath rather than a file path means the same code works whether
 * it runs from a build directory or from inside the jar. A missing property or a key of
 * the wrong length throws at startup, because a bad config should fail here instead of
 * surfacing as a confusing error once packets are already flowing.
 */
public final class Config {

  private final Properties props = new Properties();

  private Config(InputStream in) {
    try {
      props.load(in);
    } catch (IOException e) {
      throw new IllegalStateException("could not read app.properties", e);
    }
  }

  public static Config load() {
    InputStream in = Config.class.getResourceAsStream("/app.properties");
    if (in == null) {
      throw new IllegalStateException("app.properties not found on the classpath");
    }
    try (in) {
      return new Config(in);
    } catch (IOException e) {
      throw new IllegalStateException("could not close app.properties", e);
    }
  }

  public String get(String key) {
    String value = props.getProperty(key);
    if (value == null || value.isBlank()) {
      throw new IllegalStateException("missing property: " + key);
    }
    return value.trim();
  }

  public int getInt(String key) {
    return Integer.parseInt(get(key));
  }

  public boolean getBool(String key) {
    return Boolean.parseBoolean(get(key));
  }

  /**
   * The 32-byte pre-shared key. AES-256 needs exactly 32 bytes, so a wrong length is
   * rejected here rather than becoming a cipher error later.
   */
  public byte[] cryptoKey() {
    String base64 = get("crypto.key.base64");
    if (base64.equals("REPLACE_ME")) {
      throw new IllegalStateException(
          "crypto.key.base64 is unset - generate one with: openssl rand -base64 32");
    }
    byte[] key = Base64.getDecoder().decode(base64);
    if (key.length != 32) {
      throw new IllegalStateException(
          "crypto.key.base64 must decode to 32 bytes, got " + key.length);
    }
    return key;
  }

  public String serverHost() {
    return get("server.host");
  }

  public int serverPort() {
    return getInt("server.port");
  }

  public int progressInterval() {
    return getInt("progress.interval");
  }
}
