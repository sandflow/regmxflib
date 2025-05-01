package com.sandflow.smpte.mxf;

import java.util.ArrayList;
import java.util.List;

import com.sandflow.smpte.klv.LocalTagRegister.Entry;
import com.sandflow.smpte.util.AUID;

public class StaticLocalTags {
  static protected final ArrayList<Entry> entries = new ArrayList<Entry>();

  static {
    try {
      Class.forName("com.sandflow.smpte.mxf.StaticLocalTagsInitializer");
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  public static List<Entry> entries() {
    return entries;
  }

  protected static void add(AUID auid, int localTag) {
    entries.add(new Entry((long) localTag, auid));
  }

}
