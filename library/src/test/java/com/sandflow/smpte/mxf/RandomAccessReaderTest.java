/*
 * Copyright (c) Pierre-Anthony Lemieux <pal@sandflow.com>
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
package com.sandflow.smpte.mxf;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.InputStream;
import java.io.RandomAccessFile;

import org.junit.jupiter.api.Test;

import com.sandflow.smpte.mxf.types.RGBADescriptor;

class RandomAccessReaderTest {

  @Test
  void testVBE() throws Exception {
    File f = new File(ClassLoader.getSystemResource("mxf-files/video.mxf").toURI());
    RandomAccessReader rar = new RandomAccessReader(new FileRandomAccessInputSource(new RandomAccessFile(f, "r")),
        null);

    assertEquals(1, rar.getTrackCount());

    RGBADescriptor d = (RGBADescriptor) rar.getTrack(0).descriptor();
    assertEquals(640L, d.StoredWidth);

    assertEquals(24, rar.size());

    for (int i = 0; i < rar.size(); i++) {
      rar.seek(i);
      assertTrue(rar.nextElement());
    }

  }

  @Test
  void testCBE() throws Exception {
    File f = new File(ClassLoader.getSystemResource("mxf-files/audio.mxf").toURI());
    RandomAccessReader rar = new RandomAccessReader(new FileRandomAccessInputSource(new RandomAccessFile(f, "r")),
        null);

    assertEquals(1, rar.getTrackCount());

    assertEquals(48000, rar.size());

    rar.seek(24000);

    InputStream is = rar.getClipPayload();

    byte[] buffer = new byte[144000];

    is.read(buffer);
  }

}