package com.sandflow.smpte.mxf;

import com.sandflow.smpte.klv.LocalTagRegister;
import com.sandflow.smpte.klv.LocalTagResolver;
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

  protected static void add(long localTag, AUID auid) {
    StaticLocalTags.reg.add(localTag, auid);
  }

  public static LocalTagResolver register() {
    return StaticLocalTags.reg;
  }

}
