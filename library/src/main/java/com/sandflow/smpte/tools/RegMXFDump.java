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

import java.io.EOFException;
import java.io.FileInputStream;
import java.io.OutputStreamWriter;
import java.util.HashMap;
import java.util.logging.Logger;

import com.sandflow.smpte.klv.LocalTagRegister;
import com.sandflow.smpte.klv.MemoryTriplet;
import com.sandflow.smpte.klv.Set;
import com.sandflow.smpte.klv.Triplet;
import com.sandflow.smpte.mxf.FillItem;
import com.sandflow.smpte.mxf.HeaderMetadataSet;
import com.sandflow.smpte.mxf.MXFInputContext;
import com.sandflow.smpte.mxf.MXFInputStream;
import com.sandflow.smpte.mxf.PartitionPack;
import com.sandflow.smpte.mxf.PrimerPack;
import com.sandflow.smpte.mxf.RandomIndexPack;
import com.sandflow.smpte.mxf.StaticLocalTags;
import com.sandflow.smpte.mxf.types.IndexTableSegment;
import com.sandflow.smpte.mxf.types.Preface;
import com.sandflow.smpte.util.AUID;
import com.sandflow.smpte.util.UUID;
import com.sandflow.util.JSONSerializer;
import com.sandflow.util.events.Event;
import com.sandflow.util.events.EventHandler;

public class RegMXFDump {
  private final static Logger LOG = Logger.getLogger(RegMXFDump.class.getName());

  static class ElementStat {
    long totalCount;
    long totalLength;
  }

  public static void main(String[] args) throws Exception {
    EventHandler evthandler = new EventHandler() {

      @Override
      public boolean handle(Event evt) {
        String msg = evt.getCode().getClass().getCanonicalName() + "::" + evt.getCode().toString() + " "
            + evt.getMessage();

        switch (evt.getSeverity()) {
          case ERROR:
          case FATAL:
            LOG.severe(msg);
            break;
          case INFO:
            LOG.info(msg);
            break;
          case WARN:
            LOG.warning(msg);
            break;
        }
        return true;
      }
    };

    OutputStreamWriter osw = new OutputStreamWriter(System.out);
    osw.write("[\n");

    FileInputStream f = new FileInputStream(args[0]);

    MXFInputStream mis = new MXFInputStream(f);

    try {
      while (true) {
        AUID elementKey = mis.readAUID();
        long elementLength = mis.readBERLength();

        if (RandomIndexPack.getKey().equalsIgnoreVersion(elementKey)) {
          byte[] value = new byte[(int) elementLength];
          mis.readFully(value);
          JSONSerializer.serialize(RandomIndexPack.fromTriplet(new MemoryTriplet(elementKey, value)), osw);
          osw.flush();
          osw.write(",\n");
          continue;
        } else if (!PartitionPack.isInstance(elementKey)) {
          /* we have reached something other than a partition */
          osw.write(String.format("{\"key\": \"%s\", \"length\": %d},\n", elementKey, elementLength));
          osw.flush();
          mis.skipFully(elementLength);
          continue;
        }

        /* found a partition */
        /* partition pack is fixed length so that cast is ok */
        byte[] value = new byte[(int) elementLength];
        mis.readFully(value);
        PartitionPack pp = PartitionPack.fromTriplet(new MemoryTriplet(elementKey, value));

        JSONSerializer.serialize(pp, osw);
        osw.flush();
        osw.write(",\n");

        mis.resetCount();

        /* read header metadata */
        if (pp.getHeaderByteCount() > 0) {

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

          while (mis.getReadCount() < pp.getHeaderByteCount()) {

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
              JSONSerializer.serialize(Preface.fromSet(s, mic), osw);
              osw.flush();
              osw.write(",\n");
            }
          }
        }

        /* read indexes */
        if (pp.getIndexByteCount() > 0) {
          Triplet t;
          for (; (t = mis.readTriplet()) != null; mis.resetCount()) {
            /* skip fill items, if any */
            if (!FillItem.getKey().equalsIgnoreVersion(t.getKey())) {
              break;
            }
          }

          while (true) {
            IndexTableSegment its = IndexTableSegment.fromSet(
                Set.fromLocalSet(t, StaticLocalTags.register()),
                new MXFInputContext() {
                  @Override
                  public Set getSet(UUID uuid) {
                    throw new UnsupportedOperationException("Unimplemented method 'getSet'");
                  }
                });

            if (its != null) {
              JSONSerializer.serialize(its, osw);
              osw.write(",\n");
              osw.flush();
            }

            if (mis.getReadCount() >= pp.getIndexByteCount())
              break;

            t = mis.readTriplet();
          }

        }
      }

    } catch (EOFException e) {
    }

    osw.write("]\n");
    osw.flush();
  }
}
