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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.File;
import java.io.FileWriter;
import java.io.InputStream;
import java.io.Writer;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.commons.numbers.fraction.Fraction;
import org.junit.jupiter.api.Test;

import com.sandflow.smpte.mxf.types.RGBADescriptor;
import com.sandflow.smpte.mxf.types.WAVEPCMDescriptor;

class StreamingReaderTest {

  @org.junit.jupiter.api.BeforeAll
  static void makeBuildDirectory() throws URISyntaxException {
    File dir = new File("target/test-output");
    dir.mkdirs();
  }

  @Test
  void testVBE() throws Exception {
    List<Long> actualSizes = Arrays.asList(1964L, 1296L, 1965L, 2023L, 1698L, 1966L, 2063L, 1618L, 2100L, 2068L,
        2520L, 1846L, 2475L, 2540L, 2214L, 2486L, 2615L, 2150L, 2628L, 2614L, 3058L, 2472L, 2992L, 3093L);

    InputStream is = ClassLoader.getSystemResourceAsStream("mxf-files/video.mxf");
    assertNotNull(is);

    StreamingReader sr = new StreamingReader(is, null);

    assertEquals(1, sr.getTrackCount());

    RGBADescriptor d = (RGBADescriptor) sr.getTrack(0).descriptor();
    assertEquals(640L, d.StoredWidth);

    ArrayList<Long> measuredSizes = new ArrayList<>();
    while (sr.nextElement()) {
      measuredSizes.add(sr.getElementLength());
    }

    assertIterableEquals(actualSizes, measuredSizes);
  }

  @Test
  void testCBE() throws Exception {
    InputStream is = ClassLoader.getSystemResourceAsStream("mxf-files/audio.mxf");

    StreamingReader sr = new StreamingReader(is, null);
    assertEquals(1, sr.getTrackCount());

    int i = 0;
    while (sr.nextElement()) {
      WAVEPCMDescriptor d = (WAVEPCMDescriptor) sr.getElementTrackInfo().descriptor();
      assertEquals(d.AverageBytesPerSecond, 288000);
      i++;
      assertEquals(288000, sr.getElementLength());
    }

    assertEquals(1, i);

    sr.close();

  }

  @Test
  void testPreface() throws Exception {
    InputStream is = ClassLoader.getSystemResourceAsStream("mxf-files/audio.mxf");

    StreamingReader sr = new StreamingReader(is, null);

    Writer w = new FileWriter("target/test-output/out.json");
    JSONSerializer.serialize(sr.getPreface(), w);
    w.close();

  }
}