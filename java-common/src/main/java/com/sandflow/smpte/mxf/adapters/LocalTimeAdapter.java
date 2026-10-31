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
import java.time.DateTimeException;
import java.time.LocalTime;

import com.sandflow.smpte.mxf.MXFInputContext;
import com.sandflow.smpte.mxf.MXFDataInput;
import com.sandflow.smpte.mxf.MXFEvent;
import com.sandflow.smpte.mxf.MXFException;
import com.sandflow.smpte.mxf.MXFOutputContext;
import com.sandflow.smpte.mxf.MXFDataOutput;

public class LocalTimeAdapter {
  public static final Integer ITEM_LENGTH = 4;
  private static final int NANOS_PER_FRACTION = 4_000_000;
  private static final int MAX_FRACTION = 249;

  public static LocalTime fromStream(MXFDataInput is, MXFInputContext ctx) throws IOException {

    /*
     * INFO: ST 2001-1 and ST 377-1 diverge on the meaning of 'fraction'.
     * fraction is msec/4 according to 377-1
     */
    int hour = is.readUnsignedByte();
    int minute = is.readUnsignedByte();
    int second = is.readUnsignedByte();
    int fraction = capFraction(is.readUnsignedByte(), ctx);

    try {
      return LocalTime.of(hour, minute, second, fraction * NANOS_PER_FRACTION);
    } catch (DateTimeException e) {
      throw new IOException("Invalid time", e);
    }
  }

  static int capFraction(int fraction, MXFInputContext ctx) throws IOException {
    if (fraction <= MAX_FRACTION) {
      return fraction;
    }

    try {
      ctx.handleEvent(new MXFEvent(
          MXFEvent.EventCodes.INVALID_TIME_FRACTION,
          "Time fraction " + fraction + " exceeds " + MAX_FRACTION));
    } catch (MXFException e) {
      throw new IOException(e);
    }

    return MAX_FRACTION;
  }

  public static void toStream(LocalTime t, MXFDataOutput os, MXFOutputContext ctx) throws IOException {
    os.writeUnsignedByte((byte) t.getHour());
    os.writeUnsignedByte((byte) t.getMinute());
    os.writeUnsignedByte((byte) t.getSecond());
    os.writeUnsignedByte((byte) (t.getNano() / NANOS_PER_FRACTION));
  }

  public static LocalTime copyOf(LocalTime value) {
    return value;
  }
}
