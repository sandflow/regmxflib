package com.sandflow.smpte.mxf;

import java.util.HashMap;

import com.sandflow.smpte.util.UUID;

class DeterministicUIDGenerator implements StreamingWriter.UIDGenerator {
  private final HashMap<String, Long> counter = new HashMap<>();

  @Override
  public UUID generate(Object o) {
    String className = o.getClass().getName();
    long count = counter.getOrDefault(className, 0L) + 1;
    counter.put(className, count);

    return UUID.fromURIName(className + count);
  }
}