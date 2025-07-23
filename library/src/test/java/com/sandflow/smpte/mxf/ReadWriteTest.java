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

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.sandflow.smpte.mxf.helpers.OP1aHelper;
import com.sandflow.smpte.mxf.types.PictureDescriptor;
import com.sandflow.smpte.mxf.types.RGBADescriptor;
import com.sandflow.smpte.tools.RegMXFDump;
import com.sandflow.smpte.util.UL;

public class ReadWriteTest {

  @org.junit.jupiter.api.BeforeAll
  static void makeBuildDirectory() throws URISyntaxException {
    File dir = new File("target/test-output");
    dir.mkdirs();
  }

  @Test
  void testVIDEO_f031aa43_88c8_4de9_856f_904a33a78505() throws Exception {
    /* load the source file */
    InputStream is = ClassLoader.getSystemResourceAsStream("imps/imp_1/VIDEO_f031aa43-88c8-4de9-856f-904a33a78505.mxf");

    var inInfo = new StreamingFileInfo(is, null);

    StreamingReader inReader = new StreamingReader(is, null);

    /* find the Picture Essence Descriptor */

    GCEssenceTracks inTracks = new GCEssenceTracks(inInfo.getPreface());

    PictureDescriptor d = null;
    for (int i = 0; i < inTracks.getTrackCount(); i++) {
      if (inTracks.getTrackInfo(0).descriptor() instanceof PictureDescriptor) {
        d = (PictureDescriptor) inTracks.getTrackInfo(0).descriptor();
      }
    }

    /* create header metadata */

    final byte IMG_TRACKID = 1;
    final byte PHDR_TRACKID = 3;

    OP1aHelper.TrackInfo phdrTrackInfo = new OP1aHelper.TrackInfo(
        PHDR_TRACKID,
        EssenceKeys.PHDRImageMetadataItem.asUL(),
        null,
        Labels.DataEssenceTrack);

    OP1aHelper.TrackInfo imageTrackInfo = new OP1aHelper.TrackInfo(
        IMG_TRACKID,
        EssenceKeys.FrameWrappedJPEG2000PictureElement.asUL(),
        d,
        Labels.PictureEssenceTrack);

    OP1aHelper.EssenceContainerInfo eci = new OP1aHelper.EssenceContainerInfo(
        List.of(phdrTrackInfo, imageTrackInfo),
        null,
        d.SampleRate);

    OP1aHelper outHeader = new OP1aHelper(eci);

    UL imgElementKey = outHeader.getElementKey(IMG_TRACKID);
    UL phdrElementKey = outHeader.getElementKey(PHDR_TRACKID);

    /* create output file */

    OutputStream os = new FileOutputStream("target/test-output/VIDEO_f031aa43-88c8-4de9-856f-904a33a78505.new.mxf");

    StreamingWriter outWriter = new StreamingWriter(os, outHeader.getPreface());

    var gc = outWriter.addVBEFrameWrappedGC(1, 2);

    outWriter.start();
    outWriter.startPartition(gc);
    while (true) {
      gc.nextContentPackage();

      if (! inReader.nextElement()) break;

      UL elementKey = inReader.getElementKey().asUL();

      if (elementKey.equalsWithMask(EssenceKeys.PHDRImageMetadataItem, 0b1111_1111_1111_1010)) {
        gc.nextElement(phdrElementKey, inReader.getElementLength());
      } else if (elementKey.equalsWithMask(EssenceKeys.FrameWrappedJPEG2000PictureElement, 0b1111_1111_1111_1010)) {
        gc.nextElement(imgElementKey, inReader.getElementLength());
      } else {
        break;
      }

      byte[] buffer = new byte[(int) inReader.getElementLength()];
      inReader.read(buffer);
      gc.write(buffer);

    }
    outWriter.finish();
  }
}
