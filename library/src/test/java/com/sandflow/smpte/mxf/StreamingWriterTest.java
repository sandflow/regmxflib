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

import java.io.DataOutputStream;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.HexFormat;

import org.apache.commons.numbers.fraction.Fraction;
import org.junit.jupiter.api.Test;

import com.sandflow.smpte.mxf.StreamingWriter.ElementSize;
import com.sandflow.smpte.mxf.StreamingWriter.EssenceWrapping;
import com.sandflow.smpte.mxf.types.AudioChannelLabelSubDescriptor;
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
import com.sandflow.smpte.mxf.types.SoundDescriptor;
import com.sandflow.smpte.mxf.types.SoundfieldGroupLabelSubDescriptor;
import com.sandflow.smpte.mxf.types.SubDescriptorStrongReferenceVector;
import com.sandflow.smpte.mxf.types.WAVEPCMDescriptor;
import com.sandflow.smpte.util.UL;
import com.sandflow.smpte.util.UUID;

class StreamingWriterTest {

  @Test
  void testSmoke() throws Exception {
    OutputStream os = new FileOutputStream("hello.mxf");
    SoundDescriptor descriptor = new SoundDescriptor();
    StreamingWriter.EssenceInfo ei = new StreamingWriter.EssenceInfo(
      Labels.AAFAIFFAIFCAudioContainer.asUL(),
      Labels.MXFGCClipWrappedAES3AudioData.asUL(),
      descriptor,
      EssenceWrapping.CLIP,
      ElementSize.CBE,
      Fraction.of(48000),
      null
      );
    StreamingWriter sw = new StreamingWriter(os, ei);
  }

  @Test
  void testCBE() throws Exception {

    final int sampleCount = 48000;
    final Fraction sampleRate = Fraction.of(48000);
    final Fraction editRate = Fraction.of(48000);

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

    /* start writing file */

    OutputStream os = new FileOutputStream("hello.mxf");

    UL essenceKey = UL.fromURN("urn:smpte:ul:060e2b34.01020101.0d010301.16010200");

    StreamingWriter.EssenceInfo ei = new StreamingWriter.EssenceInfo(
      essenceKey,
      Labels.MXFGCClipWrappedBroadcastWaveAudioData.asUL(),
      d,
      EssenceWrapping.CLIP,
      ElementSize.CBE,
      editRate,
      null
      );
    StreamingWriter sw = new StreamingWriter(os, ei);

    DataOutputStream dos = new DataOutputStream(sw.nextUnits(sampleCount, 6));
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
  <r0:RGBADescriptor>
      <r1:InstanceID>urn:uuid:888c7510-06d5-4ccf-976c-627199e435d6</r1:InstanceID>
      <r1:SubDescriptors>
        <r0:JPEG2000SubDescriptor>
            <r1:InstanceID>urn:uuid:a08c380b-b8ad-4662-9435-deb72176b11f</r1:InstanceID>
            <r1:Rsiz>1798</r1:Rsiz>
            <r1:Xsiz>640</r1:Xsiz>
            <r1:Ysiz>360</r1:Ysiz>
            <r1:XOsiz>0</r1:XOsiz>
            <r1:YOsiz>0</r1:YOsiz>
            <r1:XTsiz>640</r1:XTsiz>
            <r1:YTsiz>360</r1:YTsiz>
            <r1:XTOsiz>0</r1:XTOsiz>
            <r1:YTOsiz>0</r1:YTOsiz>
            <r1:Csiz>3</r1:Csiz>
            <r1:PictureComponentSizing>
              <r2:J2KComponentSizing>
                  <r2:Ssiz>15</r2:Ssiz>
                  <r2:XRSiz>1</r2:XRSiz>
                  <r2:YRSiz>1</r2:YRSiz>
              </r2:J2KComponentSizing>
              <r2:J2KComponentSizing>
                  <r2:Ssiz>15</r2:Ssiz>
                  <r2:XRSiz>1</r2:XRSiz>
                  <r2:YRSiz>1</r2:YRSiz>
              </r2:J2KComponentSizing>
              <r2:J2KComponentSizing>
                  <r2:Ssiz>15</r2:Ssiz>
                  <r2:XRSiz>1</r2:XRSiz>
                  <r2:YRSiz>1</r2:YRSiz>
              </r2:J2KComponentSizing>
            </r1:PictureComponentSizing>
            <r1:CodingStyleDefault>01040001010503030001778888888888</r1:CodingStyleDefault>
            <r1:QuantizationDefault>20909898a09898a09898a0989898909098</r1:QuantizationDefault>
            <r1:J2CLayout>
              <r2:RGBAComponent>
                  <r2:Code>CompRed</r2:Code>
                  <r2:ComponentSize>16</r2:ComponentSize>
              </r2:RGBAComponent>
              <r2:RGBAComponent>
                  <r2:Code>CompGreen</r2:Code>
                  <r2:ComponentSize>16</r2:ComponentSize>
              </r2:RGBAComponent>
              <r2:RGBAComponent>
                  <r2:Code>CompBlue</r2:Code>
                  <r2:ComponentSize>16</r2:ComponentSize>
              </r2:RGBAComponent>
              <r2:RGBAComponent>
                  <r2:Code>CompNull</r2:Code>
                  <r2:ComponentSize>0</r2:ComponentSize>
              </r2:RGBAComponent>
              <r2:RGBAComponent>
                  <r2:Code>CompNull</r2:Code>
                  <r2:ComponentSize>0</r2:ComponentSize>
              </r2:RGBAComponent>
              <r2:RGBAComponent>
                  <r2:Code>CompNull</r2:Code>
                  <r2:ComponentSize>0</r2:ComponentSize>
              </r2:RGBAComponent>
              <r2:RGBAComponent>
                  <r2:Code>CompNull</r2:Code>
                  <r2:ComponentSize>0</r2:ComponentSize>
              </r2:RGBAComponent>
              <r2:RGBAComponent>
                  <r2:Code>CompNull</r2:Code>
                  <r2:ComponentSize>0</r2:ComponentSize>
              </r2:RGBAComponent>
            </r1:J2CLayout>
        </r0:JPEG2000SubDescriptor>
      </r1:SubDescriptors>
      <r1:LinkedTrackID>2</r1:LinkedTrackID>
      <r1:SampleRate>24/1</r1:SampleRate>
      <r1:EssenceLength>24</r1:EssenceLength>
      <r1:ContainerFormat>urn:smpte:ul:060e2b34.0401010d.0d010301.020c0600<!--MXFGCP1FrameWrappedPictureElement--></r1:ContainerFormat>
      <r1:FrameLayout>FullFrame</r1:FrameLayout>
      <r1:StoredWidth>640</r1:StoredWidth>
      <r1:StoredHeight>360</r1:StoredHeight>
      <r1:DisplayF2Offset>0</r1:DisplayF2Offset>
      <r1:ImageAspectRatio>640/360</r1:ImageAspectRatio>
      <r1:TransferCharacteristic>urn:smpte:ul:060e2b34.04010101.04010101.01020000<!--TransferCharacteristic_ITU709--></r1:TransferCharacteristic>
      <r1:PictureCompression>urn:smpte:ul:060e2b34.0401010d.04010202.0301050f<!--J2K_2KIMF_SingleMultiTileReversibleProfile_M6S0--></r1:PictureCompression>
      <r1:ColorPrimaries>urn:smpte:ul:060e2b34.04010106.04010101.03030000<!--ColorPrimaries_ITU709--></r1:ColorPrimaries>
      <r1:VideoLineMap>
        <r2:Int32>0</r2:Int32>
        <r2:Int32>0</r2:Int32>
      </r1:VideoLineMap>
      <r1:ComponentMaxRef>65535</r1:ComponentMaxRef>
      <r1:ComponentMinRef>0</r1:ComponentMinRef>
      <r1:ScanningDirection>ScanningDirection_LeftToRightTopToBottom</r1:ScanningDirection>
      <r1:PixelLayout>
        <r2:RGBAComponent>
            <r2:Code>CompRed</r2:Code>
            <r2:ComponentSize>16</r2:ComponentSize>
        </r2:RGBAComponent>
        <r2:RGBAComponent>
            <r2:Code>CompGreen</r2:Code>
            <r2:ComponentSize>16</r2:ComponentSize>
        </r2:RGBAComponent>
        <r2:RGBAComponent>
            <r2:Code>CompBlue</r2:Code>
            <r2:ComponentSize>16</r2:ComponentSize>
        </r2:RGBAComponent>
        <r2:RGBAComponent>
            <r2:Code>CompNull</r2:Code>
            <r2:ComponentSize>0</r2:ComponentSize>
        </r2:RGBAComponent>
        <r2:RGBAComponent>
            <r2:Code>CompNull</r2:Code>
            <r2:ComponentSize>0</r2:ComponentSize>
        </r2:RGBAComponent>
        <r2:RGBAComponent>
            <r2:Code>CompNull</r2:Code>
            <r2:ComponentSize>0</r2:ComponentSize>
        </r2:RGBAComponent>
        <r2:RGBAComponent>
            <r2:Code>CompNull</r2:Code>
            <r2:ComponentSize>0</r2:ComponentSize>
        </r2:RGBAComponent>
        <r2:RGBAComponent>
            <r2:Code>CompNull</r2:Code>
            <r2:ComponentSize>0</r2:ComponentSize>
        </r2:RGBAComponent>
      </r1:PixelLayout>
  </r0:RGBADescriptor>
   */

  @Test
  void testVBE() throws Exception {

    final int frameCount = 3;
    final int frameSize = 1000;
    final Fraction sampleRate = Fraction.of(24);
    final Fraction editRate = Fraction.of(24);

    RGBADescriptor d = new RGBADescriptor();
    d.InstanceID = UUID.fromRandom();
    d.SampleRate = sampleRate;
    d.FrameLayout = LayoutType.FullFrame;
    d.StoredWidth = 640L;
    d.StoredHeight = 360L;
    d.DisplayF2Offset = 0;
    d.ImageAspectRatio = Fraction.of(640, 360);
    d.TransferCharacteristic = Labels.TransferCharacteristic_ITU709.asUL();
    d.PictureCompression = Labels.J2K_2KIMF_SingleMultiTileReversibleProfile_M6S0;
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
    /* start writing file */

    OutputStream os = new FileOutputStream("video-test.mxf");

    UL essenceKey = UL.fromURN("urn:smpte:ul:060e2b34.01020101.0d010301.16010200");

    StreamingWriter.EssenceInfo ei = new StreamingWriter.EssenceInfo(
      essenceKey,
      Labels.MXFGCP1FrameWrappedPictureElement.asUL(),
      d,
      EssenceWrapping.FRAME,
      ElementSize.VBE,
      editRate,
      null
      );
    StreamingWriter sw = new StreamingWriter(os, ei);

    for (int i = 0; i < frameCount; i++) {
      OutputStream frameOS = sw.nextUnits(1, frameSize);
      for (int j = 0; j < frameSize; j++) {
        frameOS.write(j);
      }
    }
    sw.finish();

  }

}