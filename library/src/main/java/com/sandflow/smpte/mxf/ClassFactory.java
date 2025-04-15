package com.sandflow.smpte.mxf;

import java.util.HashMap;

import com.sandflow.smpte.util.AUID;

public class ClassFactory {
  static HashMap<AUID, Class<?>> classMap = new HashMap<>();

  public static void put(AUID auid, Class<?> clazz) {
    classMap.put(auid, clazz);
  }
}
