/*
 * Copyright (c) Sandflow Consulting, LLC
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * * Redistributions of source code must retain the above copyright notice, this
 *   list of conditions and the following disclaimer.
 * * Redistributions in binary form must reproduce the above copyright notice,
 *   this list of conditions and the following disclaimer in the documentation
 *   and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */

/**
* @author Pierre-Anthony Lemieux
*/

package com.sandflow.smpte.mxf.types;

import java.io.IOException;

import org.apache.commons.numbers.fraction.Fraction;

import com.sandflow.smpte.mxf.MXFInputContext;
import com.sandflow.smpte.mxf.MXFInputStream;
import com.sandflow.smpte.mxf.MXFOutputContext;
import com.sandflow.smpte.mxf.MXFOutputStream;
import com.sandflow.smpte.mxf.adapters.RationalAdapter;

public class IndexEntry {

  public Byte TemporalOffset;
  public Byte KeyFrameOffset;
  public Byte Flags;
  public Long StreamOffset;
  public long[] SliceOffset;
  public Fraction[] PosTable;


  public static IndexEntry fromStream(MXFInputStream is, MXFInputContext ctx, short nsl, short npe)  throws IOException {
    var r = new IndexEntry();

    r.TemporalOffset = is.readByte();
    r.KeyFrameOffset = is.readByte();
    r.Flags = is.readByte();
    r.StreamOffset = is.readLong();
    if (nsl > 0) {
      r.SliceOffset = new long[nsl];
      for (int i = 0; i < r.SliceOffset.length; i++) {
        r.SliceOffset[i] = is.readUnsignedInt();
      }
    }
    if (npe > 0) {
      r.PosTable = new Fraction[npe];
      for (int i = 0; i < r.PosTable.length; i++) {
        r.PosTable[i] = RationalAdapter.fromStream(is, ctx);
      }
    }

    return r;
  }

  public IndexEntry() {
  }

  public IndexEntry(Byte temporalOffset, Byte keyFrameOffset, Byte flags, Long streamOffset) {
    TemporalOffset = temporalOffset;
    KeyFrameOffset = keyFrameOffset;
    Flags = flags;
    StreamOffset = streamOffset;
  }

  public static void toStream(IndexEntry value, MXFOutputStream os, MXFOutputContext ctx)  throws IOException {

    os.writeByte(value.TemporalOffset);
    os.writeByte(value.KeyFrameOffset);
    os.writeByte(value.Flags);
    os.writeLong(value.StreamOffset);

    if (value.SliceOffset != null) {
      for (int i = 0; i < value.SliceOffset.length; i++) {
        os.writeUnsignedInt(value.SliceOffset[i]);
      }
    }

    if (value.PosTable != null) {
      for (int i = 0; i < value.PosTable.length; i++) {
        RationalAdapter.toStream(value.PosTable[i], os, ctx);
      }
    }

  }

}