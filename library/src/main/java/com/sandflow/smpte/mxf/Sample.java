package com.sandflow.smpte.mxf;

import java.io.IOException;

import com.sandflow.smpte.klv.Set;
import com.sandflow.smpte.mxf.types.InterchangeObject;
import com.sandflow.smpte.util.AUID;
import com.sandflow.smpte.util.UL;

public class Sample extends InterchangeObject {
  private static final UL KEY = UL.fromURN("urn:smpte:ul:060e2b34.027f0101.0d010101.01017800");
  private static final AUID AACChannelConfiguration_AUID = AUID
      .fromURN("urn:smpte:ul:060e2b34.0101010e.04020403.01030000");
  Short AACChannelConfiguration;

  private void writeToSet(Set s, MXFOutputContext ctx) throws IOException {
    /* super.toSet(s, ctx); */

    LocalSetItemAdapter.toSetItem(this.AACChannelConfiguration, AACChannelConfiguration_AUID,
        com.sandflow.smpte.mxf.adapters.UInt8Adapter::toStream, s, ctx);

  }

  public void serialize(MXFOutputContext ctx) throws IOException {
    Set s = new Set(KEY);
    this.writeToSet(s, ctx);
    ctx.putSet(s);
  }

  public static void toStream(Sample value, MXFOutputStream mos, MXFOutputContext ctx) throws IOException {
    mos.writeUUID(value.InstanceID);
    value.serialize(ctx);
  }
}
