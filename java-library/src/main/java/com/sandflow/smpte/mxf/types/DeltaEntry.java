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

import com.sandflow.smpte.mxf.MXFInputContext;
import com.sandflow.smpte.mxf.MXFDataInput;
import com.sandflow.smpte.mxf.MXFOutputContext;
import com.sandflow.smpte.mxf.MXFDataOutput;

public class DeltaEntry {
  public static final int ITEM_LENGTH = 16;

  public Byte PosTableIndex;
  public Short Slice;
  public Long ElementDelta;

  public static DeltaEntry fromStream(MXFDataInput is, MXFInputContext ctx) throws IOException {
    var r = new DeltaEntry();

    r.PosTableIndex = is.readByte();
    r.Slice = (short) is.readUnsignedByte();
    r.ElementDelta = is.readUnsignedInt();

    return r;
  }

  public DeltaEntry() {
  }

  public DeltaEntry(Byte posTableIndex, Short slice, Long elementDelta) {
    PosTableIndex = posTableIndex;
    Slice = slice;
    ElementDelta = elementDelta;
  }

  public static void toStream(DeltaEntry value, MXFDataOutput os, MXFOutputContext ctx) throws IOException {

    os.writeByte(value.PosTableIndex);
    os.writeUnsignedByte(value.Slice);
    os.writeUnsignedInt(value.ElementDelta);

  }

}