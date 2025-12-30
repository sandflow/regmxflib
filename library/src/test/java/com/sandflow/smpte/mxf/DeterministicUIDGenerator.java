package com.sandflow.smpte.mxf;

import java.util.HashMap;

import com.sandflow.smpte.util.UUID;

/**
 * Generates deterministic, name-based (version 5) UUIDs based on
 * object class and an incrementing counter. This ensures that for a given
 * sequence of object creations, the same UUIDs are generated every time, which
 * enables reproducible tests.
 */
class DeterministicUIDGenerator implements UIDGenerator {
  private final HashMap<String, Long> counter = new HashMap<>();

  /**
   * Generates a version 5 UUID based on the object's class name and a counter.
   * 
   * @param o The object for which to generate a UUID.
   * @return A deterministically generated UUID.
   */
  @Override
  public UUID generate(Object o) {
    String className = o.getClass().getName();
    long count = counter.getOrDefault(className, 0L) + 1;
    counter.put(className, count);

    return UUID.fromURIName(className + count);
  }
}