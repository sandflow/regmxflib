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

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.IDN;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.HexFormat;

import org.apache.commons.numbers.fraction.Fraction;
import org.junit.jupiter.api.Test;

import com.sandflow.smpte.mxf.StreamingWriter.GCClipCBEWriter;
import com.sandflow.smpte.mxf.helpers.OP1aHelper;
import com.sandflow.smpte.mxf.types.AudioChannelLabelSubDescriptor;
import com.sandflow.smpte.mxf.types.IABEssenceDescriptor;
import com.sandflow.smpte.mxf.types.IABSoundfieldLabelSubDescriptor;
import com.sandflow.smpte.mxf.types.Int32Array;
import com.sandflow.smpte.mxf.types.J2KComponentSizing;
import com.sandflow.smpte.mxf.types.J2KComponentSizingArray;
import com.sandflow.smpte.mxf.types.JPEG2000SubDescriptor;
import com.sandflow.smpte.mxf.types.LayoutType;
import com.sandflow.smpte.mxf.types.RGBAComponent;
import com.sandflow.smpte.mxf.types.RGBAComponentKind;
import com.sandflow.smpte.mxf.types.RGBADescriptor;
import com.sandflow.smpte.mxf.types.RGBALayout;
import com.sandflow.smpte.mxf.types.ScanningDirectionType;
import com.sandflow.smpte.mxf.types.SoundfieldGroupLabelSubDescriptor;
import com.sandflow.smpte.mxf.types.SubDescriptorStrongReferenceVector;
import com.sandflow.smpte.mxf.types.WAVEPCMDescriptor;
import com.sandflow.smpte.util.UL;
import com.sandflow.smpte.util.UUID;

class StreamingWriterTest {

  @org.junit.jupiter.api.BeforeAll
  static void makeBuildDirectory() throws URISyntaxException {
    File dir = new File("target/test-output");
    dir.mkdirs();
  }

  @Test
  void testCBE() throws Exception {

    final int sampleCount = 48000;
    final Fraction sampleRate = Fraction.of(48000);
    final Fraction editRate = Fraction.of(48000);
    final byte trackID = 1;

    SoundfieldGroupLabelSubDescriptor sg = new SoundfieldGroupLabelSubDescriptor();
    sg.InstanceID = UUID.fromRandom();
    sg.MCALabelDictionaryID = Labels.SMPTEST20678StandardStereo;
    sg.MCALinkID = UUID.fromRandom();
    sg.MCATagSymbol = "sgST";
    sg.MCATagName = "Standard Stereo";
    sg.MCAChannelID = 1L;
    sg.RFC5646SpokenLanguage = "en-us";
    sg.MCATitle = "1s tone";
    sg.MCAAudioContentKind = "PRM";
    sg.MCAAudioElementKind = "FCMP";

    AudioChannelLabelSubDescriptor chL = new AudioChannelLabelSubDescriptor();
    chL.InstanceID = UUID.fromRandom();
    chL.MCALabelDictionaryID = Labels.LeftAudioChannel;
    chL.MCALinkID = UUID.fromRandom();
    chL.MCATagSymbol = "chL";
    chL.MCATagName = "Left";
    chL.MCAChannelID = 1L;
    chL.RFC5646SpokenLanguage = "en-us";
    chL.SoundfieldGroupLinkID = sg.MCALinkID;

    AudioChannelLabelSubDescriptor chR = new AudioChannelLabelSubDescriptor();
    chR.InstanceID = UUID.fromRandom();
    chR.MCALabelDictionaryID = Labels.RightAudioChannel;
    chR.MCALinkID = UUID.fromRandom();
    chR.MCATagSymbol = "chR";
    chR.MCATagName = "Right";
    chR.MCAChannelID = 2L;
    chR.RFC5646SpokenLanguage = "en-us";
    chR.SoundfieldGroupLinkID = sg.MCALinkID;

    WAVEPCMDescriptor d = new WAVEPCMDescriptor();
    d.InstanceID = UUID.fromRandom();
    d.ContainerFormat = Labels.MXFGCClipWrappedBroadcastWaveAudioData;
    d.SampleRate = sampleRate;
    d.AudioSampleRate = sampleRate;
    d.Locked = false;
    d.ChannelCount = 2L;
    d.QuantizationBits = 24L;
    d.BlockAlign = 6;
    d.AverageBytesPerSecond = 288000L;
    d.ChannelAssignment = Labels.SMPTEST20672ApplicationOfTheMXFMultichannelAudioFramework;
    d.SubDescriptors = new SubDescriptorStrongReferenceVector();
    d.SubDescriptors.add(chL);
    d.SubDescriptors.add(chR);
    d.SubDescriptors.add(sg);

    /* create header metadata */

    UL essenceKey = UL.fromURN("urn:smpte:ul:060e2b34.01020101.0d010301.16010200");

    OP1aHelper.TrackInfo ti = new OP1aHelper.TrackInfo(
        trackID,
        essenceKey,
        d,
        Labels.SoundEssenceTrack,
        null);

    OP1aHelper.EssenceContainerInfo eci = new OP1aHelper.EssenceContainerInfo(
        Collections.singletonList(ti),
        null,
        editRate,
        1,
        127);

    OP1aHelper header = new OP1aHelper(eci);

    /* start writing file */

    OutputStream os = new FileOutputStream("target/test-output/test-cbeclip.mch.mxf");

    StreamingWriter sw = new StreamingWriter(os, header.getPreface());

    GCClipCBEWriter ec = sw.addCBEClipWrappedGC(1, 2);

    sw.start();

    sw.startPartition(ec);

    ec.nextClip(header.getElementKey(trackID), 6, sampleCount);

    DataOutputStream dos = new DataOutputStream(ec);
    byte[] samples = new byte[6];
    for (int i = 0; i < sampleCount; i++) {
      samples[0] = (byte) ((i >> 16) & 0xFF);
      samples[1] = (byte) ((i >> 8) & 0xFF);
      samples[2] = (byte) (i & 0xFF);
      samples[3] = samples[0];
      samples[4] = samples[1];
      samples[5] = samples[2];
      dos.write(samples);
    }

    sw.finish();
  }

  /*
   * <r0:IABEssenceDescriptor>
   * <r1:InstanceID>urn:uuid:8b34fae4-33d1-430f-9b78-8b58c2b698cd</r1:InstanceID>
   * <r1:SubDescriptors>
   * <r0:IABSoundfieldLabelSubDescriptor>
   * <r1:InstanceID>urn:uuid:359dea8e-868c-4768-a750-a83620fd165e</r1:InstanceID>
   * <r1:MCALabelDictionaryID>urn:smpte:ul:060e2b34.0401010d.03020221.00000000<!--
   * IABSoundfield--></r1:MCALabelDictionaryID>
   * <r1:MCALinkID>urn:uuid:6370bd8b-49cd-4e72-a151-cbbb90039d04</r1:MCALinkID>
   * <r1:MCATagSymbol>IAB</r1:MCATagSymbol>
   * <r1:MCATagName>IAB</r1:MCATagName>
   * <r1:RFC5646SpokenLanguage>en</r1:RFC5646SpokenLanguage>
   * </r0:IABSoundfieldLabelSubDescriptor>
   * </r1:SubDescriptors>
   * <r1:LinkedTrackID>2</r1:LinkedTrackID>
   * <r1:SampleRate>24/1</r1:SampleRate>
   * <r1:EssenceLength>2</r1:EssenceLength>
   * <r1:ContainerFormat>urn:smpte:ul:060e2b34.0401010d.0d010301.021d0101<!--
   * IMF_IABEssenceClipWrappedContainer--></r1:ContainerFormat>
   * <r1:AudioSampleRate>48000/1</r1:AudioSampleRate>
   * <r1:Locked>False</r1:Locked>
   * <r1:ChannelCount>0</r1:ChannelCount>
   * <r1:QuantizationBits>24</r1:QuantizationBits>
   * <r1:SoundCompression>urn:smpte:ul:060e2b34.04010105.0e090604.00000000<!--
   * ImmersiveAudioCoding--></r1:SoundCompression>
   * </r0:IABEssenceDescriptor>
   */

  @Test
  void testClipVBE() throws Exception {

    final int frameCount = 480;
    final Fraction sampleRate = Fraction.of(48000);
    final Fraction editRate = Fraction.of(24000, 1001);
    final byte trackID = 1;
    final long BODY_SID = 1;
    final long INDEX_SID = 127;

    /* read IA frame */

    InputStream is = ClassLoader.getSystemResourceAsStream("ia-frames/0.iab");
    byte[] iaFrame = is.readAllBytes();
    is.close();

    /* create descriptors */

    IABSoundfieldLabelSubDescriptor sd = new IABSoundfieldLabelSubDescriptor();
    sd.InstanceID = UUID.fromRandom();
    sd.MCALabelDictionaryID = Labels.IABSoundfield;
    sd.MCALinkID = UUID.fromRandom();
    sd.MCATagSymbol = "IAB";
    sd.MCATagName = "IAB";
    sd.RFC5646SpokenLanguage = "en-us";

    IABEssenceDescriptor d = new IABEssenceDescriptor();
    d.InstanceID = UUID.fromRandom();
    d.SampleRate = editRate;
    d.AudioSampleRate = sampleRate;
    d.Locked = false;
    d.ChannelCount = 0L;
    d.QuantizationBits = 24L;
    d.SoundCompression = Labels.ImmersiveAudioCoding;
    d.SubDescriptors = new SubDescriptorStrongReferenceVector();
    d.SubDescriptors.add(sd);
    d.ContainerFormat = Labels.IMF_IABEssenceClipWrappedContainer;

    /* create header metadata */

    OP1aHelper.TrackInfo ti = new OP1aHelper.TrackInfo(
        trackID,
        EssenceKeys.IMF_IABEssenceClipWrappedElement.asUL(),
        d,
        Labels.SoundEssenceTrack,
        null);

    OP1aHelper.EssenceContainerInfo eci = new OP1aHelper.EssenceContainerInfo(
        Collections.singletonList(ti),
        java.util.Set.of(Labels.IMF_IABTrackFileLevel0),
        editRate,
        BODY_SID,
        INDEX_SID);

    OP1aHelper header = new OP1aHelper(eci);

    /* Initialize the streaming writer */
    OutputStream os = new FileOutputStream("target/test-output/clipvbe-test.iab.mxf");
    StreamingWriter sw = new StreamingWriter(os, header.getPreface());

    /* configure the clip-wrapped generic container */

    var gc = sw.addVBEClipWrappedGC(BODY_SID, INDEX_SID);

    /* start writing file */

    sw.start();

    /* write @frameCount copies of the same IA Frame */

    sw.startPartition(gc);
    gc.nextClip(header.getElementKey(trackID), iaFrame.length * frameCount);
    for (int i = 0; i < frameCount; i++) {
      gc.nextAccessUnit();
      gc.write(iaFrame);
    }

    /* complete the file */
    sw.finish();
    os.close();
  }

  /*
   * <r0:RGBADescriptor>
   * <r1:InstanceID>urn:uuid:888c7510-06d5-4ccf-976c-627199e435d6</r1:InstanceID>
   * <r1:SubDescriptors>
   * <r0:JPEG2000SubDescriptor>
   * <r1:InstanceID>urn:uuid:a08c380b-b8ad-4662-9435-deb72176b11f</r1:InstanceID>
   * <r1:Rsiz>1798</r1:Rsiz>
   * <r1:Xsiz>640</r1:Xsiz>
   * <r1:Ysiz>360</r1:Ysiz>
   * <r1:XOsiz>0</r1:XOsiz>
   * <r1:YOsiz>0</r1:YOsiz>
   * <r1:XTsiz>640</r1:XTsiz>
   * <r1:YTsiz>360</r1:YTsiz>
   * <r1:XTOsiz>0</r1:XTOsiz>
   * <r1:YTOsiz>0</r1:YTOsiz>
   * <r1:Csiz>3</r1:Csiz>
   * <r1:PictureComponentSizing>
   * <r2:J2KComponentSizing>
   * <r2:Ssiz>15</r2:Ssiz>
   * <r2:XRSiz>1</r2:XRSiz>
   * <r2:YRSiz>1</r2:YRSiz>
   * </r2:J2KComponentSizing>
   * <r2:J2KComponentSizing>
   * <r2:Ssiz>15</r2:Ssiz>
   * <r2:XRSiz>1</r2:XRSiz>
   * <r2:YRSiz>1</r2:YRSiz>
   * </r2:J2KComponentSizing>
   * <r2:J2KComponentSizing>
   * <r2:Ssiz>15</r2:Ssiz>
   * <r2:XRSiz>1</r2:XRSiz>
   * <r2:YRSiz>1</r2:YRSiz>
   * </r2:J2KComponentSizing>
   * </r1:PictureComponentSizing>
   * <r1:CodingStyleDefault>01040001010503030001778888888888</r1:
   * CodingStyleDefault>
   * <r1:QuantizationDefault>20909898a09898a09898a0989898909098</r1:
   * QuantizationDefault>
   * <r1:J2CLayout>
   * <r2:RGBAComponent>
   * <r2:Code>CompRed</r2:Code>
   * <r2:ComponentSize>16</r2:ComponentSize>
   * </r2:RGBAComponent>
   * <r2:RGBAComponent>
   * <r2:Code>CompGreen</r2:Code>
   * <r2:ComponentSize>16</r2:ComponentSize>
   * </r2:RGBAComponent>
   * <r2:RGBAComponent>
   * <r2:Code>CompBlue</r2:Code>
   * <r2:ComponentSize>16</r2:ComponentSize>
   * </r2:RGBAComponent>
   * <r2:RGBAComponent>
   * <r2:Code>CompNull</r2:Code>
   * <r2:ComponentSize>0</r2:ComponentSize>
   * </r2:RGBAComponent>
   * <r2:RGBAComponent>
   * <r2:Code>CompNull</r2:Code>
   * <r2:ComponentSize>0</r2:ComponentSize>
   * </r2:RGBAComponent>
   * <r2:RGBAComponent>
   * <r2:Code>CompNull</r2:Code>
   * <r2:ComponentSize>0</r2:ComponentSize>
   * </r2:RGBAComponent>
   * <r2:RGBAComponent>
   * <r2:Code>CompNull</r2:Code>
   * <r2:ComponentSize>0</r2:ComponentSize>
   * </r2:RGBAComponent>
   * <r2:RGBAComponent>
   * <r2:Code>CompNull</r2:Code>
   * <r2:ComponentSize>0</r2:ComponentSize>
   * </r2:RGBAComponent>
   * </r1:J2CLayout>
   * </r0:JPEG2000SubDescriptor>
   * </r1:SubDescriptors>
   * <r1:LinkedTrackID>2</r1:LinkedTrackID>
   * <r1:SampleRate>24/1</r1:SampleRate>
   * <r1:EssenceLength>24</r1:EssenceLength>
   * <r1:ContainerFormat>urn:smpte:ul:060e2b34.0401010d.0d010301.020c0600<!--
   * MXFGCP1FrameWrappedPictureElement--></r1:ContainerFormat>
   * <r1:FrameLayout>FullFrame</r1:FrameLayout>
   * <r1:StoredWidth>640</r1:StoredWidth>
   * <r1:StoredHeight>360</r1:StoredHeight>
   * <r1:DisplayF2Offset>0</r1:DisplayF2Offset>
   * <r1:ImageAspectRatio>640/360</r1:ImageAspectRatio>
   * <r1:TransferCharacteristic>urn:smpte:ul:060e2b34.04010101.04010101.01020000<!
   * --TransferCharacteristic_ITU709--></r1:TransferCharacteristic>
   * <r1:PictureCompression>urn:smpte:ul:060e2b34.0401010d.04010202.0301050f<!--
   * J2K_2KIMF_SingleMultiTileReversibleProfile_M6S0--></r1:PictureCompression>
   * <r1:ColorPrimaries>urn:smpte:ul:060e2b34.04010106.04010101.03030000<!--
   * ColorPrimaries_ITU709--></r1:ColorPrimaries>
   * <r1:VideoLineMap>
   * <r2:Int32>0</r2:Int32>
   * <r2:Int32>0</r2:Int32>
   * </r1:VideoLineMap>
   * <r1:ComponentMaxRef>65535</r1:ComponentMaxRef>
   * <r1:ComponentMinRef>0</r1:ComponentMinRef>
   * <r1:ScanningDirection>ScanningDirection_LeftToRightTopToBottom</r1:
   * ScanningDirection>
   * <r1:PixelLayout>
   * <r2:RGBAComponent>
   * <r2:Code>CompRed</r2:Code>
   * <r2:ComponentSize>16</r2:ComponentSize>
   * </r2:RGBAComponent>
   * <r2:RGBAComponent>
   * <r2:Code>CompGreen</r2:Code>
   * <r2:ComponentSize>16</r2:ComponentSize>
   * </r2:RGBAComponent>
   * <r2:RGBAComponent>
   * <r2:Code>CompBlue</r2:Code>
   * <r2:ComponentSize>16</r2:ComponentSize>
   * </r2:RGBAComponent>
   * <r2:RGBAComponent>
   * <r2:Code>CompNull</r2:Code>
   * <r2:ComponentSize>0</r2:ComponentSize>
   * </r2:RGBAComponent>
   * <r2:RGBAComponent>
   * <r2:Code>CompNull</r2:Code>
   * <r2:ComponentSize>0</r2:ComponentSize>
   * </r2:RGBAComponent>
   * <r2:RGBAComponent>
   * <r2:Code>CompNull</r2:Code>
   * <r2:ComponentSize>0</r2:ComponentSize>
   * </r2:RGBAComponent>
   * <r2:RGBAComponent>
   * <r2:Code>CompNull</r2:Code>
   * <r2:ComponentSize>0</r2:ComponentSize>
   * </r2:RGBAComponent>
   * <r2:RGBAComponent>
   * <r2:Code>CompNull</r2:Code>
   * <r2:ComponentSize>0</r2:ComponentSize>
   * </r2:RGBAComponent>
   * </r1:PixelLayout>
   * </r0:RGBADescriptor>
   */

  @Test
  void testPHDR() throws Exception {

    final int frameCount = 24;
    final Fraction sampleRate = Fraction.of(24);
    final Fraction editRate = Fraction.of(24);
    final byte trackID = 1;
    final long BODY_SID = 1;
    final long INDEX_SID = 127;

    /* read J2C frame */

    InputStream is = ClassLoader.getSystemResourceAsStream("j2c-frames/counter-00006.j2c");
    byte[] j2cFrame = is.readAllBytes();
    is.close();

    /* create descriptors */

    RGBADescriptor d = new RGBADescriptor();
    d.InstanceID = UUID.fromRandom();
    d.SampleRate = sampleRate;
    d.FrameLayout = LayoutType.FullFrame;
    d.StoredWidth = 640L;
    d.StoredHeight = 360L;
    d.DisplayF2Offset = 0;
    d.ImageAspectRatio = Fraction.of(640, 360);
    d.TransferCharacteristic = Labels.TransferCharacteristic_ITU709.asUL();
    d.PictureCompression = Labels.JPEG2000BroadcastContributionSingleTileProfileLevel5;
    d.ColorPrimaries = Labels.ColorPrimaries_ITU709.asUL();
    d.VideoLineMap = new Int32Array();
    d.VideoLineMap.add(0);
    d.VideoLineMap.add(0);
    d.ComponentMaxRef = 65535L;
    d.ComponentMinRef = 0L;
    d.ScanningDirection = ScanningDirectionType.ScanningDirection_LeftToRightTopToBottom;
    d.PixelLayout = new RGBALayout();
    d.PixelLayout.add(new RGBAComponent(RGBAComponentKind.CompRed, (short) 16));
    d.PixelLayout.add(new RGBAComponent(RGBAComponentKind.CompGreen, (short) 16));
    d.PixelLayout.add(new RGBAComponent(RGBAComponentKind.CompBlue, (short) 16));
    d.PixelLayout.add(new RGBAComponent(RGBAComponentKind.CompNull, (short) 0));
    d.PixelLayout.add(new RGBAComponent(RGBAComponentKind.CompNull, (short) 0));
    d.PixelLayout.add(new RGBAComponent(RGBAComponentKind.CompNull, (short) 0));
    d.PixelLayout.add(new RGBAComponent(RGBAComponentKind.CompNull, (short) 0));
    d.PixelLayout.add(new RGBAComponent(RGBAComponentKind.CompNull, (short) 0));
    d.SubDescriptors = new SubDescriptorStrongReferenceVector();
    d.ContainerFormat = Labels.MXFGCP1FrameWrappedPictureElement;

    JPEG2000SubDescriptor sd = new JPEG2000SubDescriptor();
    sd.InstanceID = UUID.fromRandom();
    sd.Rsiz = 1798;
    sd.Xsiz = 640L;
    sd.Ysiz = 360L;
    sd.XOsiz = 0L;
    sd.YOsiz = 0L;
    sd.XTsiz = 640L;
    sd.YTsiz = 360L;
    sd.XTOsiz = 0L;
    sd.YTOsiz = 0L;
    sd.Csiz = 3;
    sd.PictureComponentSizing = new J2KComponentSizingArray();
    sd.PictureComponentSizing.add(new J2KComponentSizing((short) 15, (short) 1, (short) 1));
    sd.PictureComponentSizing.add(new J2KComponentSizing((short) 15, (short) 1, (short) 1));
    sd.PictureComponentSizing.add(new J2KComponentSizing((short) 15, (short) 1, (short) 1));
    sd.CodingStyleDefault = HexFormat.of().parseHex("01040001010503030001778888888888");
    sd.QuantizationDefault = HexFormat.of().parseHex("20909898a09898a09898a0989898909098");
    sd.J2CLayout = d.PixelLayout;

    d.SubDescriptors.add(sd);

    /* create header metadata */

    OP1aHelper.TrackInfo ti = new OP1aHelper.TrackInfo(
        trackID,
        EssenceKeys.FrameWrappedJPEG2000PictureElement.asUL(),
        d,
        Labels.PictureEssenceTrack,
        null);

    OP1aHelper.EssenceContainerInfo eci = new OP1aHelper.EssenceContainerInfo(
        Collections.singletonList(ti),
        null,
        editRate,
        BODY_SID,
        INDEX_SID);

    OP1aHelper header = new OP1aHelper(eci);

    UL j2kElementKey = header.getElementKey(trackID);

    /* start writing file */

    OutputStream os = new FileOutputStream("target/test-output/test-vbeframe.j2k.mxf");

    StreamingWriter sw = new StreamingWriter(os, header.getPreface());

    var cw = sw.addVBEFrameWrappedGC(BODY_SID, INDEX_SID);
    sw.start();
    for (int i = 0; i < frameCount; i++) {
      if (i % 4 == 0) {
        sw.startPartition(cw);
      }
      cw.nextContentPackage();
      cw.nextElement(j2kElementKey, j2cFrame.length);
      cw.write(j2cFrame);
    }
    sw.finish();

  }

  @Test
  void testVBE() throws Exception {

    final int frameCount = 24;
    final Fraction sampleRate = Fraction.of(24);
    final Fraction editRate = Fraction.of(24);
    final byte trackID = 1;
    final long BODY_SID = 1;
    final long INDEX_SID = 127;

    /* read J2C frame */

    InputStream is = ClassLoader.getSystemResourceAsStream("j2c-frames/counter-00006.j2c");
    byte[] j2cFrame = is.readAllBytes();
    is.close();

    /* create descriptors */

    RGBADescriptor d = new RGBADescriptor();
    d.InstanceID = UUID.fromRandom();
    d.SampleRate = sampleRate;
    d.FrameLayout = LayoutType.FullFrame;
    d.StoredWidth = 640L;
    d.StoredHeight = 360L;
    d.DisplayF2Offset = 0;
    d.ImageAspectRatio = Fraction.of(640, 360);
    d.TransferCharacteristic = Labels.TransferCharacteristic_ITU709.asUL();
    d.PictureCompression = Labels.JPEG2000BroadcastContributionSingleTileProfileLevel5;
    d.ColorPrimaries = Labels.ColorPrimaries_ITU709.asUL();
    d.VideoLineMap = new Int32Array();
    d.VideoLineMap.add(0);
    d.VideoLineMap.add(0);
    d.ComponentMaxRef = 65535L;
    d.ComponentMinRef = 0L;
    d.ScanningDirection = ScanningDirectionType.ScanningDirection_LeftToRightTopToBottom;
    d.PixelLayout = new RGBALayout();
    d.PixelLayout.add(new RGBAComponent(RGBAComponentKind.CompRed, (short) 16));
    d.PixelLayout.add(new RGBAComponent(RGBAComponentKind.CompGreen, (short) 16));
    d.PixelLayout.add(new RGBAComponent(RGBAComponentKind.CompBlue, (short) 16));
    d.PixelLayout.add(new RGBAComponent(RGBAComponentKind.CompNull, (short) 0));
    d.PixelLayout.add(new RGBAComponent(RGBAComponentKind.CompNull, (short) 0));
    d.PixelLayout.add(new RGBAComponent(RGBAComponentKind.CompNull, (short) 0));
    d.PixelLayout.add(new RGBAComponent(RGBAComponentKind.CompNull, (short) 0));
    d.PixelLayout.add(new RGBAComponent(RGBAComponentKind.CompNull, (short) 0));
    d.SubDescriptors = new SubDescriptorStrongReferenceVector();
    d.ContainerFormat = Labels.MXFGCP1FrameWrappedPictureElement;

    JPEG2000SubDescriptor sd = new JPEG2000SubDescriptor();
    sd.InstanceID = UUID.fromRandom();
    sd.Rsiz = 1798;
    sd.Xsiz = 640L;
    sd.Ysiz = 360L;
    sd.XOsiz = 0L;
    sd.YOsiz = 0L;
    sd.XTsiz = 640L;
    sd.YTsiz = 360L;
    sd.XTOsiz = 0L;
    sd.YTOsiz = 0L;
    sd.Csiz = 3;
    sd.PictureComponentSizing = new J2KComponentSizingArray();
    sd.PictureComponentSizing.add(new J2KComponentSizing((short) 15, (short) 1, (short) 1));
    sd.PictureComponentSizing.add(new J2KComponentSizing((short) 15, (short) 1, (short) 1));
    sd.PictureComponentSizing.add(new J2KComponentSizing((short) 15, (short) 1, (short) 1));
    sd.CodingStyleDefault = HexFormat.of().parseHex("01040001010503030001778888888888");
    sd.QuantizationDefault = HexFormat.of().parseHex("20909898a09898a09898a0989898909098");
    sd.J2CLayout = d.PixelLayout;

    d.SubDescriptors.add(sd);

    /* create header metadata */

    OP1aHelper.TrackInfo ti = new OP1aHelper.TrackInfo(
        trackID,
        EssenceKeys.FrameWrappedJPEG2000PictureElement.asUL(),
        d,
        Labels.PictureEssenceTrack,
        null);

    OP1aHelper.EssenceContainerInfo eci = new OP1aHelper.EssenceContainerInfo(
        Collections.singletonList(ti),
        null,
        editRate,
        BODY_SID,
        INDEX_SID);

    OP1aHelper header = new OP1aHelper(eci);

    UL j2kElementKey = header.getElementKey(trackID);

    /* start writing file */

    OutputStream os = new FileOutputStream("target/test-output/test-vbeframe.j2k.mxf");

    StreamingWriter sw = new StreamingWriter(os, header.getPreface());

    var cw = sw.addVBEFrameWrappedGC(BODY_SID, INDEX_SID);
    sw.start();
    for (int i = 0; i < frameCount; i++) {
      if (i % 50 == 0) {
        sw.startPartition(cw);
      }
      cw.nextContentPackage();
      cw.nextElement(j2kElementKey, j2cFrame.length);
      cw.write(j2cFrame);
    }
    sw.finish();

  }

}