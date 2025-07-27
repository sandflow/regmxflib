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
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URISyntaxException;

import org.apache.commons.numbers.fraction.Fraction;
import org.junit.jupiter.api.Test;

import com.sandflow.smpte.mxf.types.AudioChannelLabelSubDescriptor;
import com.sandflow.smpte.mxf.types.SoundfieldGroupLabelSubDescriptor;
import com.sandflow.smpte.mxf.types.SubDescriptorStrongReferenceVector;
import com.sandflow.smpte.mxf.types.WAVEPCMDescriptor;
import com.sandflow.smpte.tools.RegMXFDump;
import com.sandflow.smpte.util.UUID;

public class ClassGenerationTest {

  @Test
  void testCloning() {
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
    d.SampleRate = Fraction.of(48000);
    d.AudioSampleRate = Fraction.of(48000);
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

    WAVEPCMDescriptor dClone = new WAVEPCMDescriptor(d);

    assertEquals(d.ChannelCount, dClone.ChannelCount);
    assertEquals(d.SubDescriptors.get(0).InstanceID, dClone.SubDescriptors.get(0).InstanceID);

    dClone.SubDescriptors.get(0).InstanceID = UUID.fromRandom();

    assertNotEquals(d.SubDescriptors.get(0).InstanceID, dClone.SubDescriptors.get(0).InstanceID);
  }
}
