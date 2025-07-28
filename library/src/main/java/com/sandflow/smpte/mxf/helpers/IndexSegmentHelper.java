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

package com.sandflow.smpte.mxf.helpers;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;

import com.sandflow.smpte.klv.LocalTagResolver;
import com.sandflow.smpte.klv.Set;
import com.sandflow.smpte.mxf.MXFDataOutput;
import com.sandflow.smpte.mxf.MXFException;
import com.sandflow.smpte.mxf.MXFOutputContext;
import com.sandflow.smpte.mxf.StaticLocalTags;
import com.sandflow.smpte.mxf.types.IndexTableSegment;
import com.sandflow.smpte.util.AUID;
import com.sandflow.smpte.util.UMID;
import com.sandflow.smpte.util.UUID;
import com.sandflow.util.events.Event;
import com.sandflow.util.events.EventHandler;

public class IndexSegmentHelper {

  public static byte[] toBytes(IndexTableSegment its, EventHandler evthandler) throws IOException {
    /* serialize the index table segment */
    /*
     * TODO: the AtomicReference is necessary since the variable is initialized in
     * the
     * inline MXFOutputContext
     */
    AtomicReference<Set> ars = new AtomicReference<>();
    MXFOutputContext ctx = new MXFOutputContext() {

      @Override
      public UUID getPackageInstanceID(UMID packageID) {
        throw new UnsupportedOperationException();
      }

      @Override
      public void putSet(Set set) {
        if (ars.get() != null) {
          throw new RuntimeException("Serializing an Index Table Segment should not require more than one Set");
        }
        ars.set(set);
      }

      @Override
      public void handleEvent(Event evt) throws MXFException {
        MXFException.handle(evthandler, evt);
      }

    };

    its.toSet(ctx);

    if (ars.get() == null) {
      throw new RuntimeException("Index Table Segment not serialized");
    }

    /* serialize the header */
    LocalTagResolver tags = new LocalTagResolver() {
      @Override
      public Long getLocalTag(AUID auid) {
        Long localTag = StaticLocalTags.register().getLocalTag(auid);
        if (localTag == null) {
          throw new RuntimeException();
        }
        return localTag;
      }

      @Override
      public AUID getAUID(long localtag) {
        throw new UnsupportedOperationException(
            "Serializing an Index Table Segment should not require resolving a local tag to an AUID");
      }

    };

    ByteArrayOutputStream bos = new ByteArrayOutputStream();
    MXFDataOutput mos = new MXFDataOutput(bos);
    Set.toStreamAsLocalSet(ars.get(), tags, mos);
    mos.flush();

    return bos.toByteArray();
  }
}
