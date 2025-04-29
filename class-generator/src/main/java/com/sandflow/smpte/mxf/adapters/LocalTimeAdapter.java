/*
 * Copyright (c) 2014, Pierre-Anthony Lemieux (pal@sandflow.com)
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
import java.time.LocalDateTime;
import java.time.LocalTime;

import com.sandflow.smpte.mxf.MXFInputContext;
import com.sandflow.smpte.mxf.MXFInputStream;
import com.sandflow.smpte.mxf.MXFOutputContext;
import com.sandflow.smpte.mxf.MXFOutputStream;

public class LocalTimeAdapter {
  public static final Integer ITEM_LENGTH = 4;

  public static LocalTime fromStream(MXFInputStream is, MXFInputContext ctx) throws IOException {

    /*
     * INFO: ST 2001-1 and ST 377-1 diverge on the meaning of 'fraction'.
     * fraction is msec/4 according to 377-1
     */
    int hour = is.readUnsignedByte();
    int minute = is.readUnsignedByte();
    int second = is.readUnsignedByte();
    int fraction = is.readUnsignedByte();

    return LocalTime.of(hour, minute, second, 4 * fraction * 1000);
  }

  public static void toStream(LocalDateTime t, MXFOutputStream os, MXFOutputContext ctx) throws IOException {
    os.writeUnsignedByte((byte) t.getHour());
    os.writeUnsignedByte((byte) t.getMinute());
    os.writeUnsignedByte((byte) t.getSecond());
    os.writeUnsignedByte((byte) (t.getNano() / 4000));
  }

}
