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
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.sandflow.smpte.mxf.helpers.OP1aHelper;
import com.sandflow.smpte.mxf.types.AUIDSet;
import com.sandflow.smpte.mxf.types.ComponentStrongReferenceVector;
import com.sandflow.smpte.mxf.types.DescriptiveMarker;
import com.sandflow.smpte.mxf.types.GenericStreamTextBasedSet;
import com.sandflow.smpte.mxf.types.IABEssenceDescriptor;
import com.sandflow.smpte.mxf.types.PHDRMetadataTrackSubDescriptor;
import com.sandflow.smpte.mxf.types.Package;
import com.sandflow.smpte.mxf.types.PictureDescriptor;
import com.sandflow.smpte.mxf.types.Sequence;
import com.sandflow.smpte.mxf.types.SoundDescriptor;
import com.sandflow.smpte.mxf.types.SourcePackage;
import com.sandflow.smpte.mxf.types.StaticTrack;
import com.sandflow.smpte.mxf.types.TextBasedFramework;
import com.sandflow.smpte.mxf.types.Track;
import com.sandflow.smpte.util.UL;
import com.sandflow.smpte.util.UUID;

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

    /* find the Picture Essence Descriptor and PHDR subdescriptor */

    GCEssenceTracks inTracks = new GCEssenceTracks(inInfo.getPreface());

    PictureDescriptor d = null;
    for (int i = 0; i < inTracks.getTrackCount(); i++) {
      if (inTracks.getTrackInfo(0).descriptor() instanceof PictureDescriptor) {
        d = (PictureDescriptor) inTracks.getTrackInfo(0).descriptor();
      }
    }

    PHDRMetadataTrackSubDescriptor phdrSD = (PHDRMetadataTrackSubDescriptor) d.SubDescriptors.stream()
        .filter(e -> e instanceof PHDRMetadataTrackSubDescriptor)
        .findFirst()
        .orElse(null);

    /* create header metadata */

    final long PHDR_METADATA_SID = phdrSD.SimplePayloadSID;
    final long IMG_BODY_SID = PHDR_METADATA_SID + 1;
    final long IMG_INDEX_SID = IMG_BODY_SID + 1;

    final byte IMG_TRACKID = 1;
    final byte PHDR_TRACKID = 3;

    OP1aHelper.TrackInfo phdrMetadataTrackInfo = new OP1aHelper.TrackInfo(
        PHDR_TRACKID,
        EssenceKeys.PHDRImageMetadataItem.asUL(),
        null,
        Labels.DataEssenceTrack,
        "PHDR Metadata Track");

    OP1aHelper.TrackInfo phdrImageTrackInfo = new OP1aHelper.TrackInfo(
        IMG_TRACKID,
        EssenceKeys.FrameWrappedJPEG2000PictureElement.asUL(),
        d,
        Labels.PictureEssenceTrack,
        "PHDR Image Track");

    OP1aHelper.EssenceContainerInfo eci = new OP1aHelper.EssenceContainerInfo(
        List.of(phdrMetadataTrackInfo, phdrImageTrackInfo),
        null,
        d.SampleRate,
        IMG_BODY_SID,
        IMG_INDEX_SID,
        null);

    OP1aHelper outHeader = new OP1aHelper(eci);

    UL phdrImageElementKey = outHeader.getElementKey(IMG_TRACKID);
    UL phdrMetadataElementKey = outHeader.getElementKey(PHDR_TRACKID);
    UL phdrGSElementKey = GenericStreamDataElementKey.make(
        GenericStreamDataElementKey.KLVType.WRAPPED,
        GenericStreamDataElementKey.ByteOrder.BIG_ENDIAN,
        GenericStreamDataElementKey.AccessUnitWrapping.NO,
        GenericStreamDataElementKey.MultiKLVWrapping.NO,
        GenericStreamDataElementKey.EssenceSync.OTHER);

    /**
     * EXCEPTION: Dolby requires the following label even though it is invalid
     * (since there is more than one track) and it is not specified in RDD 56.
     */
    outHeader.getPreface().OperationalPattern = Labels.MXFOP1aSingleItemSinglePackageUniTrackStreamInternal;

    /* create output file */

    OutputStream os = new FileOutputStream("target/test-output/VIDEO_f031aa43-88c8-4de9-856f-904a33a78505.new.mxf");

    StreamingWriter outWriter = new StreamingWriter(os, outHeader.getPreface());

    var gc = outWriter.addVBEFrameWrappedGC(IMG_BODY_SID, IMG_INDEX_SID);
    var gs = outWriter.addGenericStream(PHDR_METADATA_SID);

    byte[] phdrMetadataPayload = null;

    outWriter.start();
    outWriter.startPartition(gc);
    while (true) {

      if (!inReader.nextElement())
        break;

      UL elementKey = inReader.getElementKey().asUL();

      if (elementKey.equalsWithMask(EssenceKeys.PHDRImageMetadataItem, 0b1111_1110_1111_1010)) {
        gc.nextElement(phdrMetadataElementKey, inReader.getElementLength());
      } else if (elementKey.equalsWithMask(EssenceKeys.FrameWrappedJPEG2000PictureElement, 0b1111_1110_1111_1010)) {
        /* we index J2K elements */
        gc.nextContentPackage();
        gc.nextElement(phdrImageElementKey, inReader.getElementLength());
      } else if (phdrGSElementKey.equalsIgnoreVersion(elementKey)) {
        phdrMetadataPayload = inReader.readNBytes((int) inReader.getElementLength());
      } else {
        throw new RuntimeException();
      }

      byte[] buffer = inReader.readNBytes((int) inReader.getElementLength());
      gc.write(buffer);
    }

    /* write the PHDR metadata partition */
    if (phdrMetadataPayload != null) {
      outWriter.startPartition(gs);
      gs.nextElement(phdrGSElementKey, phdrMetadataPayload.length);
      gs.write(phdrMetadataPayload);
    }

    outWriter.finish();

    inReader.close();
  }

  @Test
  void testAUDIO_807d0b4c_69ec_44b0_be74_dfbf1a8c99d3() throws Exception {

    /* load the source file */
    InputStream is = ClassLoader.getSystemResourceAsStream("imps/imp_1/AUDIO_807d0b4c-69ec-44b0-be74-dfbf1a8c99d3.mxf");

    var sourceInfo = new StreamingFileInfo(is, null);

    StreamingReader in = new StreamingReader(is, null);

    /* get the audio descriptor */

    GCEssenceTracks inTracks = new GCEssenceTracks(sourceInfo.getPreface());

    assertEquals(1, inTracks.getTrackCount());
    assertInstanceOf(SoundDescriptor.class, inTracks.getTrackInfo(0).descriptor());
    SoundDescriptor d = (SoundDescriptor) inTracks.getTrackInfo(0).descriptor();

    long bytesPerSample = (d.QuantizationBits / 8) * d.ChannelCount;

    /* create header metadata */

    final long BODY_SID = 1;
    final long INDEX_SID = BODY_SID + 1;

    final byte SOUND_TRACKID = 1;

    /* EXCEPTION: Resolve requires the essence container duration to be set for multichannel track files */
    long ecDuration = d.EssenceLength;

    OP1aHelper.EssenceContainerInfo eci = new OP1aHelper.EssenceContainerInfo(
        Collections.singletonList(
            new OP1aHelper.TrackInfo(
                SOUND_TRACKID,
                EssenceKeys.WaveClipWrappedSoundElement.asUL(),
                d,
                Labels.SoundEssenceTrack,
                "AUDIO_807d0b4c-69ec-44b0-be74-dfbf1a8c99d3")),
        null,
        d.SampleRate,
        BODY_SID,
        INDEX_SID,
        ecDuration);

    OP1aHelper outHeader = new OP1aHelper(eci);

    UL elementKey = outHeader.getElementKey(SOUND_TRACKID);

    /* create output file */

    OutputStream os = new FileOutputStream("target/test-output/AUDIO_807d0b4c-69ec-44b0-be74-dfbf1a8c99d3.new.mxf");

    StreamingWriter out = new StreamingWriter(os, outHeader.getPreface());

    /* create a single clip wrapped generic container */

    var gc = out.addCBEClipWrappedGC(BODY_SID, INDEX_SID);

    /* start reading the clip from the source file */

    assertTrue(in.nextElement());

    /* make sure it is audio as expected */

    assertTrue(
        EssenceKeys.WaveClipWrappedSoundElement.asUL().equalsWithMask(in.getElementKey(), 0b1111_1110_1111_1010));

    assertEquals(0, in.getElementLength() % bytesPerSample);

    /* read the clip payload */

    byte[] clipPayload = in.readNBytes((int) in.getElementLength());

    in.close();

    /* write the ouput file */

    out.start();
    out.startPartition(gc);
    gc.nextClip(elementKey, bytesPerSample, clipPayload.length / bytesPerSample);
    gc.write(clipPayload);
    out.finish();

  }

  @Test
  void testIAB_dd3fabc6_4794_4bae_95ee_6bc2405716a6() throws Exception {

    final long BODY_SID = 1;
    final long INDEX_SID = BODY_SID + 1;
    final long DD_SID = INDEX_SID + 1;
    final long ADM_SID = DD_SID + 1;

    final byte IAB_TRACKID = 1;
    final byte DD_TRACKID = IAB_TRACKID + 1;
    final byte ADM_TRACKID = DD_TRACKID + 1;

    /* load the source file */
    InputStream is = ClassLoader.getSystemResourceAsStream("imps/imp_1/IAB_dd3fabc6-4794-4bae-95ee-6bc2405716a6.mxf");
    var sourceInfo = new StreamingFileInfo(is, null);
    StreamingReader in = new StreamingReader(is, null);

    /* get the audio descriptor */

    GCEssenceTracks inTracks = new GCEssenceTracks(sourceInfo.getPreface());

    assertEquals(1, inTracks.getTrackCount());
    assertInstanceOf(IABEssenceDescriptor.class, inTracks.getTrackInfo(0).descriptor());
    IABEssenceDescriptor d = (IABEssenceDescriptor) inTracks.getTrackInfo(0).descriptor();

    /* create header metadata */

    /*
     * EXCEPTION: Resolve requires the essence descriptor to specify the duration of the essence container
     */
    long ecDuration = d.EssenceLength;
    OP1aHelper.EssenceContainerInfo eci = new OP1aHelper.EssenceContainerInfo(
        Collections.singletonList(
            new OP1aHelper.TrackInfo(
                IAB_TRACKID,
                EssenceKeys.IMF_IABEssenceClipWrappedElement.asUL(),
                d,
                Labels.SoundEssenceTrack,
                "IA Bitstream")),
        java.util.Set.of(Labels.IMF_IABTrackFileLevel0),
        d.SampleRate,
        BODY_SID,
        INDEX_SID,
        ecDuration);

    OP1aHelper outHeader = new OP1aHelper(eci);

    UL elementKey = outHeader.getElementKey(IAB_TRACKID);

    /* find RP 2057 DMSs and copy them to the new file */

    SourcePackage topPackage = (SourcePackage) outHeader.getPreface().ContentStorageObject.Packages.stream()
        .filter(e -> e instanceof SourcePackage).findFirst().orElse(null);

    long sourceDDGSSID = 0;
    long sourceADMGSSID = 0;

    for (Package p : sourceInfo.getPreface().ContentStorageObject.Packages) {
      if (!(p instanceof SourcePackage))
        continue;

      for (Track t : ((SourcePackage) p).PackageTracks) {
        if (!Labels.DescriptiveMetadataTrack.asUL().equalsIgnoreVersion(t.TrackSegment.ComponentDataDefinition))
          continue;

        Sequence sq = (Sequence) t.TrackSegment;

        if (!(sq.ComponentObjects.get(0) instanceof DescriptiveMarker))
          continue;

        DescriptiveMarker dm = (DescriptiveMarker) sq.ComponentObjects.get(0);

        if (!(dm.DescriptiveFrameworkObject instanceof TextBasedFramework))
          continue;

        GenericStreamTextBasedSet gstb = (GenericStreamTextBasedSet) ((TextBasedFramework) dm.DescriptiveFrameworkObject).TextBasedObject;

        GenericStreamTextBasedSet dmT = new GenericStreamTextBasedSet();
        dmT.InstanceID = UUID.fromRandom();
        dmT.RFC5646TextLanguageCode = gstb.RFC5646TextLanguageCode;
        dmT.TextBasedMetadataPayloadSchemeID = gstb.TextBasedMetadataPayloadSchemeID;
        dmT.TextDataDescription = gstb.TextDataDescription;
        dmT.TextMIMEMediaType = gstb.TextMIMEMediaType;
        if (dmT.TextDataDescription.equals("urn:ebu:metadata-schema:ebuCore_2016")) {
          dmT.GenericStreamID = (long) ADM_SID;
          assertEquals(0, sourceADMGSSID);
          sourceADMGSSID = gstb.GenericStreamID;
        } else if (dmT.TextDataDescription.equals("http://www.dolby.com/schemas/2018/DbmdWrapper")) {
          dmT.GenericStreamID = (long) DD_SID;
          assertEquals(0, sourceDDGSSID);
          sourceDDGSSID = gstb.GenericStreamID;
        } else {
          throw new RuntimeException();
        }

        TextBasedFramework dmTBF = new TextBasedFramework();
        dmTBF.InstanceID = UUID.fromRandom();
        dmTBF.TextBasedObject = dmT;

        DescriptiveMarker dmMarker = new DescriptiveMarker();
        dmMarker.InstanceID = UUID.fromRandom();
        dmMarker.DescriptiveFrameworkObject = dmTBF;
        dmMarker.EventComment = "SMPTE RP 2057 Generic Stream Text-Based Set";

        Sequence dmSequence = new Sequence();
        dmSequence.InstanceID = UUID.fromRandom();
        dmSequence.ComponentDataDefinition = Labels.DescriptiveMetadataTrack;
        dmSequence.ComponentObjects = new ComponentStrongReferenceVector();
        dmSequence.ComponentObjects.add(dmMarker);

        StaticTrack dmTrack = new StaticTrack();
        dmTrack.InstanceID = UUID.fromRandom();
        if (gstb.TextDataDescription.equals("urn:ebu:metadata-schema:ebuCore_2016")) {
          dmTrack.TrackID = (long) ADM_TRACKID;
          dmTrack.TrackName = "Audio Definition Model Metadata Track";
        } else if (gstb.TextDataDescription.equals("http://www.dolby.com/schemas/2018/DbmdWrapper")) {
          dmTrack.TrackID = (long) DD_TRACKID;
          dmTrack.TrackName = "Dolby Audio Metadata Chunk Track";
        }
        dmTrack.TrackSegment = dmSequence;

        topPackage.PackageTracks.add(dmTrack);
      }
    }

    assertNotEquals(0, sourceADMGSSID);
    assertNotEquals(0, sourceDDGSSID);

    outHeader.getPreface().DescriptiveSchemes = new AUIDSet();
    outHeader.getPreface().DescriptiveSchemes.add(Labels.MXFTextBasedFramework);

    /* create output file */

    OutputStream os = new FileOutputStream("target/test-output/IAB_dd3fabc6-4794-4bae-95ee-6bc2405716a6.new.mxf");

    StreamingWriter out = new StreamingWriter(os, outHeader.getPreface());

    /* start reading the clip from the source file */

    assertTrue(in.nextElement());

    /* confirm the element contains IAB essence */

    assertTrue(
        EssenceKeys.IMF_IABEssenceClipWrappedElement.asUL().equalsWithMask(in.getElementKey(), 0b1111_1110_1111_1010));

    /* create a single clip wrapped generic container */

    var gc = out.addVBEClipWrappedGC(BODY_SID, INDEX_SID);

    /* create the two generic stream containers */

    var ddGS = out.addGenericStream(DD_SID);
    var admGS = out.addGenericStream(ADM_SID);

    /* start writing */

    out.start();
    out.startPartition(gc);
    gc.nextClip(elementKey, in.getElementLength());

    /* copy the access units */

    long bytesRemaining = in.getElementLength();
    while (bytesRemaining > 0) {
      gc.nextAccessUnit();

      DataInputStream dis = new DataInputStream(in);
      DataOutputStream dos = new DataOutputStream(gc);

      /* PreambleTag */
      byte preambleTag = dis.readByte();
      assertEquals(0x01, preambleTag);
      dos.writeByte(preambleTag);
      bytesRemaining--;

      /* PreambleLength */
      long preambleLength = Integer.toUnsignedLong(dis.readInt());
      dos.writeInt((int) preambleLength);
      bytesRemaining = bytesRemaining - 4;

      /* PreambleValue */
      byte[] buffer = dis.readNBytes((int) preambleLength);
      dos.write(buffer);
      bytesRemaining = bytesRemaining - preambleLength;

      /* IAFrameTag */
      byte frameTag = dis.readByte();
      assertEquals(0x02, frameTag);
      dos.writeByte(frameTag);
      bytesRemaining--;

      /* IAFrameLength */
      long frameLength = Integer.toUnsignedLong(dis.readInt());
      dos.writeInt((int) frameLength);
      bytesRemaining = bytesRemaining - 4;

      /* IAFrame */
      buffer = dis.readNBytes((int) frameLength);
      dos.write(buffer);
      bytesRemaining = bytesRemaining - frameLength;

      dos.flush();
    }

    UL gsElementKey = UL.fromURN("urn:smpte:ul:060e2b34.0101010c.0d010509.01000000");

    while (in.nextElement()) {
      assertTrue(gsElementKey.equals(in.getElementKey()));

      if (in.getSID() == sourceDDGSSID) {
        out.startPartition(ddGS);
        ddGS.nextElement(in.getElementKey().asUL(), in.getElementLength());
        ddGS.write(in.readNBytes((int) in.getElementLength()));
      } else if (in.getSID() == sourceADMGSSID) {
        out.startPartition(admGS);
        admGS.nextElement(in.getElementKey().asUL(), in.getElementLength());
        admGS.write(in.readNBytes((int) in.getElementLength()));
      } else {
        throw new RuntimeException();
      }

    }

    /* clean-up */

    out.finish();

    in.close();

  }

}
