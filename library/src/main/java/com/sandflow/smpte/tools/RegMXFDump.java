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
import java.util.Map;
import java.util.logging.Logger;

import com.sandflow.smpte.mxf.ECTracks;
import com.sandflow.smpte.mxf.StreamingFileInfo;
import com.sandflow.smpte.mxf.StreamingReader;
import com.sandflow.smpte.util.AUID;
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

    FileInputStream f = new FileInputStream(args[0]);
    StreamingFileInfo sfi = new StreamingFileInfo(f, evthandler);
    ECTracks tracks = new ECTracks(sfi.getPreface());

    for (int i = 0; i < tracks.getTrackCount(); i++) {
      JSONSerializer.serialize(tracks.getTrackInfo(i).descriptor(), osw);
      osw.write("\n");
    }
    osw.flush();

    StreamingReader sr = new StreamingReader(f, evthandler);

    Map<AUID, ElementStat> stats = new HashMap<>();

    while (sr.nextElement()) {
      AUID key = sr.getElementKey();
      ElementStat stat = stats.computeIfAbsent(key, k -> new ElementStat());
      stat.totalCount++;
      stat.totalLength += sr.getElementLength();
    }

    for (Map.Entry<AUID, ElementStat> entry : stats.entrySet()) {
      System.out.println(entry.getKey() + " => " + entry.getValue().totalCount + ", "
          + (entry.getValue().totalLength / entry.getValue().totalCount));
    }
  }
}
