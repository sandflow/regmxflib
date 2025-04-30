package com.sandflow.smpte.mxf;

import java.util.HashMap;

import com.sandflow.smpte.util.UL;

public class ClassFactory {
  static protected final HashMap<UL, Class<?>> classMap = new HashMap<>();

  static {
    try {
      Class.forName("com.sandflow.smpte.mxf.ClassFactoryInitializer");
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private ClassFactory() {
  }

  public static Class<?> getClass(UL ul) {
    return classMap.get(makeNormalizedUL(ul));
  }

  protected static void putClass(UL ul, Class<?> clazz) {
    classMap.put(makeNormalizedUL(ul), clazz);
  }

  private static UL makeNormalizedUL(UL ul) {
    byte[] value = ul.getValue().clone();
    /* set version to 0 */
    value[7] = 0;
    if (ul.isGroup()) {
      /* set byte 6 to 0x7f */
      value[5] = 0x7f;
    }
    return new UL(value);
  }
}
