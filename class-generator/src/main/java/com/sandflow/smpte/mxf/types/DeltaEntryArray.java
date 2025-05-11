package com.sandflow.smpte.mxf.types;
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

import java.io.IOException;
import java.util.ArrayList;

import com.sandflow.smpte.mxf.MXFInputStream;
import com.sandflow.smpte.mxf.MXFInputContext;
import com.sandflow.smpte.mxf.MXFOutputStream;
import com.sandflow.smpte.mxf.MXFOutputContext;


public class DeltaEntryArray extends ArrayList<DeltaEntry> {

  public DeltaEntryArray() {
    super();
  }

  public static DeltaEntryArray fromStream(MXFInputStream is, MXFInputContext ctx)  throws IOException {
    int itemcount = (int) (is.readInt() & 0xfffffffL);
    @SuppressWarnings("unused")
    int itemlength = (int) (is.readInt() & 0xfffffffL);

    var items = new DeltaEntryArray();

    for (int i = 0; i < itemcount; i++) {
      items.add(DeltaEntry.fromStream(is, ctx));
    }

    return items;
  }

  public static void toStream(DeltaEntryArray v, MXFOutputStream os, MXFOutputContext ctx)  throws IOException {
    os.writeUnsignedInt(v.size());
    os.writeUnsignedInt(DeltaEntry.ITEM_LENGTH);

    for (int i = 0; i < v.size(); i++) {
      DeltaEntry.toStream(v.get(i), os, ctx);
    }
  }

 }
