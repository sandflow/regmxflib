package com.sandflow.smpte.mxf;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import com.sandflow.smpte.klv.MemoryGroup;
import com.sandflow.smpte.mxf.types.InterchangeObject;
import com.sandflow.smpte.util.AUID;
import com.sandflow.smpte.util.UL;

public class Sample extends InterchangeObject {
  private static final UL KEY = UL.fromURN("urn:smpte:ul:060e2b34.027f0101.0d010101.01017800");
  private static final AUID AACChannelConfiguration_AUID = AUID
      .fromURN("urn:smpte:ul:060e2b34.0101010e.04020403.01030000");
  Short AACChannelConfiguration;

  public static void toGroup(MXFOutputContext ctx) throws IOException {


  }

  void toStream(MXFOutputStream mos, MXFOutputContext ctx) throws IOException {
    mos.writeUUID(InstanceID);

    ByteArrayOutputStream vbos = new ByteArrayOutputStream();

    LocalSetItemAdapter.toStream(this.AACChannelConfiguration, AACChannelConfiguration_AUID,
        com.sandflow.smpte.mxf.adapters.UInt8Adapter::toStream, vbos, ctx);

    vbos.flush();

    mos.writeLocalSetKeyWithBERLength(KEY);
    mos.writeBERLength(vbos.size());
    vbos.writeTo(mos);
  }
}
