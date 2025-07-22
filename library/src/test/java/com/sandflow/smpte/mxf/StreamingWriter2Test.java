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
import java.net.URISyntaxException;
import java.util.Collections;

import org.apache.commons.numbers.fraction.Fraction;
import org.junit.jupiter.api.Test;

import com.sandflow.smpte.mxf.StreamingWriter2.GCClipCBEWriter;
import com.sandflow.smpte.mxf.helpers.OP1aHelper;
import com.sandflow.smpte.mxf.types.AudioChannelLabelSubDescriptor;
import com.sandflow.smpte.mxf.types.IABEssenceDescriptor;
import com.sandflow.smpte.mxf.types.IABSoundfieldLabelSubDescriptor;
import com.sandflow.smpte.mxf.types.SoundfieldGroupLabelSubDescriptor;
import com.sandflow.smpte.mxf.types.SubDescriptorStrongReferenceVector;
import com.sandflow.smpte.mxf.types.WAVEPCMDescriptor;
import com.sandflow.smpte.util.UL;
import com.sandflow.smpte.util.UUID;

class StreamingWriter2Test {

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
        Labels.SoundEssenceTrack);

    OP1aHelper.EssenceContainerInfo eci = new OP1aHelper.EssenceContainerInfo(
        Collections.singletonList(ti),
        null,
        editRate);

    OP1aHelper header = new OP1aHelper(eci);

    /* start writing file */

    OutputStream os = new FileOutputStream("target/test-output/test-cbeclip.mch.mxf");

    StreamingWriter2 sw = new StreamingWriter2(os, header.getPreface());

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
        Labels.SoundEssenceTrack);

    OP1aHelper.EssenceContainerInfo eci = new OP1aHelper.EssenceContainerInfo(
        Collections.singletonList(ti),
        java.util.Set.of(Labels.IMF_IABTrackFileLevel0),
        editRate);

    OP1aHelper header = new OP1aHelper(eci);


    /* Initialize the streaming writer */
    OutputStream os = new FileOutputStream("target/test-output/clipvbe-test.iab.mxf");
    StreamingWriter2 sw = new StreamingWriter2(os, header.getPreface());

    /* configure the clip-wrapped generic container */

    var gc = sw.addVBEClipWrappedGC(1L, 2L);

    /* start writing file */

    sw.start();

    /* write @frameCount copies of the same IA Frame */

    sw.startPartition(gc);
    gc.nextClip(header.getElementKey(trackID), iaFrame.length * frameCount);
    for (int i = 0; i < frameCount; i++) {
      gc.nextAccessUnit(iaFrame.length);
      gc.write(iaFrame);
    }

    /* complete the file */
    sw.finish();
    os.close();
  }

}