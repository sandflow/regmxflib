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

package com.sandflow.smpte.tools;

import java.io.FileInputStream;
import java.io.OutputStreamWriter;
import java.util.HashMap;
import java.util.logging.Logger;

import com.sandflow.smpte.klv.LocalTagRegister;
import com.sandflow.smpte.klv.Set;
import com.sandflow.smpte.klv.Triplet;
import com.sandflow.smpte.mxf.FillItem;
import com.sandflow.smpte.mxf.HeaderMetadataSet;
import com.sandflow.smpte.mxf.MXFInputContext;
import com.sandflow.smpte.mxf.MXFInputStream;
import com.sandflow.smpte.mxf.PartitionPack;
import com.sandflow.smpte.mxf.PrimerPack;
import com.sandflow.smpte.mxf.types.Preface;
import com.sandflow.smpte.util.UUID;
import com.sandflow.util.JSONSerializer;

public class DumpHeader {
  private final static Logger LOG = Logger.getLogger(RegMXFDump.class.getName());

  public static void main(String[] args) throws Exception {

    MXFInputStream mis = new MXFInputStream(new FileInputStream(args[0]));

    PartitionPack pp = null;
    for (Triplet t; (t = mis.readTriplet()) != null;) {
      if ((pp = PartitionPack.fromTriplet(t)) != null) {
        break;
      }
    }

    long headerByteCount = pp.getHeaderByteCount();

    /* look for the primer pack */
    LocalTagRegister localreg = null;
    for (Triplet t; (t = mis.readTriplet()) != null; mis.resetCount()) {

      /* skip fill items, if any */
      if (!FillItem.getKey().equalsIgnoreVersion(t.getKey())) {
        localreg = PrimerPack.createLocalTagRegister(t);
        break;
      }
    }

    /*
     * capture all local sets within the header metadata
     */
    HashMap<UUID, Set> setresolver = new HashMap<>();

    while (mis.getReadCount() < headerByteCount) {

      Triplet t = mis.readTriplet();

      /* skip fill items */
      if (FillItem.isInstance(t.getKey())) {
        continue;
      }

      Set s = Set.fromLocalSet(t, localreg);

      if (s != null) {

        UUID instanceID = HeaderMetadataSet.getInstanceID(s);
        if (instanceID != null) {
          setresolver.put(instanceID, s);
        }

      }

    }

    MXFInputContext mic = new MXFInputContext() {
      @Override
      public Set getSet(UUID uuid) {
        return setresolver.get(uuid);
      }
    };

    for (Set s : setresolver.values()) {
      if (Preface.getKey().equalsIgnoreVersionAndGroupCoding(s.getKey())) {
        OutputStreamWriter osw = new OutputStreamWriter(System.out);
        JSONSerializer.serialize(Preface.fromSet(s, mic), osw);
        osw.flush();
      }
    }

  }
}
