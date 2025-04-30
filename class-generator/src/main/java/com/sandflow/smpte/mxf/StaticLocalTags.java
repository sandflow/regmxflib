package com.sandflow.smpte.mxf;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import com.sandflow.smpte.util.AUID;

public class StaticLocalTags {
  static protected final HashMap<AUID, Integer> auidToLocalTag = new HashMap<>();
  static protected final HashMap<Integer, AUID> localTagToAUID = new HashMap<>();

  static {
    try {
      Class.forName("com.sandflow.smpte.mxf.StaticLocalTagsInitializer");
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  public static int getLocalTag(AUID auid) {
    return auidToLocalTag.get(auid.makeVersionNormalized());
  }

  public static AUID getAUID(int localTag) {
    return localTagToAUID.get(localTag);
  }

  public static Set<Map.Entry<AUID, Integer>> entrySet() {
    return auidToLocalTag.entrySet();
  }

  protected static void put(AUID auid, int localTag) {
    auidToLocalTag.put(auid.makeVersionNormalized(), localTag);
    localTagToAUID.put(localTag, auid.makeVersionNormalized());
  }

}
