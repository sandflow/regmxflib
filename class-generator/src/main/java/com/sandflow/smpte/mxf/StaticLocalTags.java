package com.sandflow.smpte.mxf;

import com.sandflow.smpte.klv.LocalTagRegister;
import com.sandflow.smpte.util.AUID;

public class StaticLocalTags {

  private static final LocalTagRegister reg = new LocalTagRegister();

  static {
    try {
      Class.forName("com.sandflow.smpte.mxf.StaticLocalTagsInitializer");
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private StaticLocalTags() {
  }

  /**
   * Returns the Local Tag corresponding to a AUID
   *
   * @param auid AUID
   * @return Local tag, or null if no Local Tag is associated with the AUID
   */
  public static Long getLocalTag(AUID auid) {
    return StaticLocalTags.reg.getLocalTag(auid);
  }

  protected static void add(long localTag, AUID auid) {
    StaticLocalTags.reg.add(localTag, auid);
  }

}
