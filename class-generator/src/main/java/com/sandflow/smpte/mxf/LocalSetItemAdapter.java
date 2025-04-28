package com.sandflow.smpte.mxf;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;

import com.sandflow.smpte.util.AUID;

@FunctionalInterface
public interface LocalSetItemAdapter<T> {
  void apply(T v, MXFOutputStream os, MXFOutputContext ctx) throws IOException;

  public static <T> void toStream(T itemValue, AUID itemKey, LocalSetItemAdapter<T> a, OutputStream os,
      MXFOutputContext ctx)
      throws IOException {
    if (itemValue == null)
      return;
    ByteArrayOutputStream ibos = new ByteArrayOutputStream();
    MXFOutputStream imos = new MXFOutputStream(ibos);
    a.apply(itemValue, imos, ctx);
    imos.flush();

    MXFOutputStream mos = new MXFOutputStream(os);
    mos.writeUnsignedShort((int) ctx.getLocalTag(itemKey));
    mos.writeBERLength(ibos.size());
    ibos.writeTo(mos);
  }
}
