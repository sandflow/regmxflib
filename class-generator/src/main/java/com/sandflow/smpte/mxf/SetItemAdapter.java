package com.sandflow.smpte.mxf;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import com.sandflow.smpte.klv.MemoryTriplet;
import com.sandflow.smpte.klv.Set;
import com.sandflow.smpte.util.AUID;

@FunctionalInterface
public interface SetItemAdapter<T> {
  void apply(T v, MXFOutputStream os, MXFOutputContext ctx) throws IOException;

  public static <T> void toSetItem(T itemValue, AUID itemKey, SetItemAdapter<T> adapter, Set s, MXFOutputContext ctx)
      throws IOException {
    if (itemValue == null)
      return;
    ByteArrayOutputStream ibos = new ByteArrayOutputStream();
    MXFOutputStream imos = new MXFOutputStream(ibos);
    adapter.apply(itemValue, imos, ctx);

    s.addItem(new MemoryTriplet(itemKey, ibos.toByteArray()));
  }
}
