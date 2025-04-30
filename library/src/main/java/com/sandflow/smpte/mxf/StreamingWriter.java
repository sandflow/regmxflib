package com.sandflow.smpte.mxf;

import java.io.OutputStream;
import java.time.LocalDateTime;
import java.util.Arrays;

import org.apache.commons.numbers.fraction.Fraction;

import com.sandflow.smpte.klv.LocalTagRegister;
import com.sandflow.smpte.mxf.types.AES3PCMDescriptor;
import com.sandflow.smpte.mxf.types.ComponentStrongReferenceVector;
import com.sandflow.smpte.mxf.types.ContentStorage;
import com.sandflow.smpte.mxf.types.EssenceData;
import com.sandflow.smpte.mxf.types.EssenceDataStrongReferenceSet;
import com.sandflow.smpte.mxf.types.FileDescriptor;
import com.sandflow.smpte.mxf.types.PackageStrongReferenceSet;
import com.sandflow.smpte.mxf.types.Preface;
import com.sandflow.smpte.mxf.types.Segment;
import com.sandflow.smpte.mxf.types.Sequence;
import com.sandflow.smpte.mxf.types.SoundDescriptor;
import com.sandflow.smpte.mxf.types.SourceClip;
import com.sandflow.smpte.mxf.types.SourcePackage;
import com.sandflow.smpte.mxf.types.TimelineTrack;
import com.sandflow.smpte.mxf.types.WAVEPCMDescriptor;
import com.sandflow.smpte.util.UMID;

public class StreamingWriter {

  public enum EssenceWrapping {
    CLIP, FRAME;
  }

  public record EssenceContainerInfo(
      FileDescriptor[] descriptors,
      EssenceWrapping wrapping) {
  }

  public record Configuration(EssenceContainerInfo[] containers) {
  }

  StreamingWriter(OutputStream os) {
    PartitionPack pp = new PartitionPack();

    /* TODO: add static local tags */
    LocalTagRegister ltr = new LocalTagRegister();

    /* Essence Descriptor */

    SoundDescriptor desc = new SoundDescriptor();
    desc.ChannelCount = 2L;
    desc.SampleRate = Fraction.of(48000, 1);
    desc.QuantizationBits = 16L;
    desc.ContainerFormat = Labels.MXFGCClipWrappedBroadcastWaveAudioData;

        /* File Package */

    SourcePackage sp = new SourcePackage();
    sp.PackageID = UMID.usingUUID();
    sp.EssenceDescription = desc;
    sp.CreationTime = LocalDateTime.of(2025, 1, 1, 1, 0);
    sp.PackageLastModified = LocalDateTime.now();

    /* Source Clip */
    
    SourceClip clip = new SourceClip();
    clip.StartPosition = 0L;
    clip.SourcePackageID = sp.PackageID;

    /* Segment */

    Sequence seq = new Sequence();
    seq.ComponentObjects = new ComponentStrongReferenceVector();
    seq.ComponentLength = 48000L;
    seq.ComponentObjects.add(clip);

    /* Track */

    TimelineTrack track = new TimelineTrack();
    track.Origin = 0L;
    track.TrackID = 1L;
    track.EditRate = Fraction.of(48000, 1);
    track.TrackSegment = seq;
    track.TrackName = "Audio Track 1";
  
    /* preface */
    Preface p = new Preface();
    
    p.ContentStorageObject = new ContentStorage();
    p.ContentStorageObject.EssenceDataObjects = new EssenceDataStrongReferenceSet();
    p.ContentStorageObject.EssenceDataObjects.add(new EssenceData());
    p.ContentStorageObject.EssenceDataObjects.get(1).EssenceStreamID = 1L;

    p.ContentStorageObject.Packages = new PackageStrongReferenceSet();
    p.ContentStorageObject.Packages.add(sp);
  }


}
