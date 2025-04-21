package com.sandflow.smpte.mxf;

import java.util.HashMap;

import com.sandflow.smpte.util.AUID;

public class ClassFactory {
  static protected final HashMap<AUID, Class<?>> classMap = new HashMap<>();

  private ClassFactory() {}
  public static Class<?> getClass(AUID auid) {
    return classMap.get(auid);
  }
}
