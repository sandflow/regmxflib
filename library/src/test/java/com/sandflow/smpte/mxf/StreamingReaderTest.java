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
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.File;
import java.io.FileWriter;
import java.io.InputStream;
import java.io.Writer;
import java.net.URISyntaxException;

import org.apache.commons.numbers.fraction.Fraction;
import org.junit.jupiter.api.Test;

import com.sandflow.smpte.util.UL;

class StreamingReaderTest {

  @org.junit.jupiter.api.BeforeAll
  static void makeBuildDirectory() throws URISyntaxException {
    File dir = new File("target/test-output");
    dir.mkdirs();
  }

  @Test
  void testGetUnitTrackInfo() throws Exception {
    InputStream is = ClassLoader.getSystemResourceAsStream("mxf-files/audio.mxf");
    assertNotNull(is);

    StreamingReader sr = new StreamingReader(is, null);

    sr.nextUnit();

    assertEquals(288000, sr.getUnitPayloadLength());

    assert (UL.fromURN("urn:smpte:ul:060e2b34.04010101.0d010301.02060200")
        .equalsIgnoreVersion(sr.getUnitTrackInfo().descriptor().ContainerFormat));
  }

  @Test
  void testNextUnit() throws Exception {
    InputStream is = ClassLoader.getSystemResourceAsStream("mxf-files/video.mxf");
    assertNotNull(is);

    StreamingReader sr = new StreamingReader(is, null);

    Fraction frameTime = sr.getTrack(0).descriptor().SampleRate.reciprocal();

    int i = 0;
    while (sr.nextUnit()) {
      assertEquals(frameTime.multiply(i), sr.getUnitOffset());
      i++;
    }

    assertEquals(24, i);
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