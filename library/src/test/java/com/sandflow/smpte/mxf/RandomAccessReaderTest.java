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

package com.sandflow.smpte.mxf;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.RandomAccessFile;

import org.junit.jupiter.api.Test;

import com.sandflow.smpte.mxf.types.IABEssenceDescriptor;
import com.sandflow.smpte.mxf.types.RGBADescriptor;
import com.sandflow.smpte.util.FileRandomAccessInputSource;

class RandomAccessReaderTest {

  @Test
  void testFrameVBE() throws Exception {
    File f = new File(ClassLoader.getSystemResource("mxf-files/video.mxf").toURI());
    FileRandomAccessInputSource rais = new FileRandomAccessInputSource(new RandomAccessFile(f, "r"));

    RandomAccessFileInfo fi = new RandomAccessFileInfo(rais, null);
    GCEssenceTracks tracks = new GCEssenceTracks(fi.getPreface());
    assertEquals(1, tracks.getTrackCount());
    RGBADescriptor d = (RGBADescriptor) tracks.getTrackInfo(0).descriptor();
    assertEquals(640L, d.StoredWidth);
    assertEquals(24, fi.getEUCount());

    FrameReader fr = new FrameReader(fi, rais);
    for (int i = 0; i < fi.getEUCount(); i++) {
      fr.seek(i);
      assertTrue(fr.nextElement());
    }
    fr.close();
  }

  @Test
  void testFrameVBEAndGS() throws Exception {
    File f = new File(ClassLoader.getSystemResource("imps/imp_1/IAB_dd3fabc6-4794-4bae-95ee-6bc2405716a6.mxf").toURI());
    FileRandomAccessInputSource rais = new FileRandomAccessInputSource(new RandomAccessFile(f, "r"));

    RandomAccessFileInfo fi = new RandomAccessFileInfo(rais, null);
    GCEssenceTracks tracks = new GCEssenceTracks(fi.getPreface());
    assertEquals(1, tracks.getTrackCount());
    assertInstanceOf(IABEssenceDescriptor.class, tracks.getTrackInfo(0).descriptor());

    final byte[] iaFrameMagic = { 01, 00, 00, 06, 67, 17, 01, 14 };
    byte[] buffer = new byte[iaFrameMagic.length];

    ClipReader fr = new ClipReader(fi, rais);
    for (int i = 0; i < fi.getEUCount(); i++) {
      fr.seek(i);
      fr.read(buffer);  
      assertArrayEquals(buffer, iaFrameMagic);
    }
    fr.close();

    GenericStreamReader gsr = new GenericStreamReader(fi, rais);
    for (var sid : fi.getGenericStreams()) {
      gsr.seek(sid);
      assertTrue(gsr.getElementLength() > 0);
    }
    gsr.close();
  }

  @Test
  void testClipCBE() throws Exception {
    File f = new File(ClassLoader.getSystemResource("mxf-files/audio.mxf").toURI());
    FileRandomAccessInputSource rais = new FileRandomAccessInputSource(new RandomAccessFile(f, "r"));

    RandomAccessFileInfo fi = new RandomAccessFileInfo(rais, null);
    GCEssenceTracks tracks = new GCEssenceTracks(fi.getPreface());
    assertEquals(1, tracks.getTrackCount());
    assertEquals(48000, fi.getEUCount());

    ClipReader fr = new ClipReader(fi, rais);
    fr.seek(24000);
    byte[] buffer = new byte[144000];
    fr.read(buffer);
    fr.close();
  }

  @Test
  void testClipVBE() throws Exception {
    File f = new File(ClassLoader.getSystemResource("mxf-files/iab.mxf").toURI());
    FileRandomAccessInputSource rais = new FileRandomAccessInputSource(new RandomAccessFile(f, "r"));

    RandomAccessFileInfo fi = new RandomAccessFileInfo(rais, null);
    GCEssenceTracks tracks = new GCEssenceTracks(fi.getPreface());
    assertEquals(1, tracks.getTrackCount());
    assertEquals(2, fi.getEUCount());

    final byte[] iaFrameMagic = { 01, 00, 00, 00, 00, 02, 00, 00 };
    byte[] buffer = new byte[iaFrameMagic.length];

    ClipReader fr = new ClipReader(fi, rais);
    for (int i = 0; i < fr.getEUCount(); i++) {
      fr.seek(i);
      fr.read(buffer);
      assertArrayEquals(buffer, iaFrameMagic);
    }
    fr.close();
  }

}