/*
 * Copyright (c) Sandflow Consulting LLC
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

package com.sandflow.smpte.mxf.adapters;

import java.io.IOException;

import com.sandflow.smpte.klv.Triplet;
import com.sandflow.smpte.mxf.MXFInputContext;
import com.sandflow.smpte.mxf.MXFDataInput;
import com.sandflow.smpte.mxf.MXFOutputContext;
import com.sandflow.smpte.mxf.MXFDataOutput;
import com.sandflow.smpte.util.AUID;
import com.sandflow.smpte.util.UMID;
import com.sandflow.smpte.util.UUID;

public class PrimaryPackageAdapter {
  public static final Integer ITEM_LENGTH = 16;

  private static final AUID PACKAGEID_AUID = AUID.fromURN("urn:smpte:ul:060e2b34.01010101.01011510.00000000");

  public static UMID fromStream(MXFDataInput is, MXFInputContext ctx) throws IOException {
    UUID uuid = is.readUUID();
    var s = ctx.getSet(uuid);
    if (s == null) {
      throw new RuntimeException();
    }
    Triplet t = s.getItem(PACKAGEID_AUID);
    MXFDataInput mis = new MXFDataInput(t.getValueAsStream());
    return mis.readUMID();
  }

  public static void toStream(UMID packageID, MXFDataOutput os, MXFOutputContext ctx) throws IOException {
    UUID instanceID = ctx.getPackageInstanceID(packageID);
    if (instanceID == null) {
      throw new RuntimeException();
    }
    os.writeUUID(instanceID);
  }

  public static UMID copyOf(UMID value) {
    return value;
  }

}
