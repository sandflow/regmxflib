package com.sandflow.smpte.mxf;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;

import org.apache.commons.numbers.fraction.Fraction;

import com.sandflow.smpte.klv.LocalTagRegister;
import com.sandflow.smpte.klv.Set;
import com.sandflow.smpte.klv.exceptions.KLVException;
import com.sandflow.smpte.mxf.types.ComponentStrongReferenceVector;
import com.sandflow.smpte.mxf.types.ContentStorage;
import com.sandflow.smpte.mxf.types.EssenceData;
import com.sandflow.smpte.mxf.types.EssenceDataStrongReferenceSet;
import com.sandflow.smpte.mxf.types.FileDescriptor;
import com.sandflow.smpte.mxf.types.IdentificationStrongReferenceVector;
import com.sandflow.smpte.mxf.types.PackageStrongReferenceSet;
import com.sandflow.smpte.mxf.types.Preface;
import com.sandflow.smpte.mxf.types.Sequence;
import com.sandflow.smpte.mxf.types.SoundDescriptor;
import com.sandflow.smpte.mxf.types.SourceClip;
import com.sandflow.smpte.mxf.types.SourcePackage;
import com.sandflow.smpte.mxf.types.TimelineTrack;
import com.sandflow.smpte.util.AUID;
import com.sandflow.smpte.util.UL;
import com.sandflow.smpte.util.UMID;
import com.sandflow.smpte.util.UUID;

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

  StreamingWriter(OutputStream os) throws IOException, KLVException {
    /* Essence Descriptor */

    SoundDescriptor desc = new SoundDescriptor();
    desc.InstanceID = UUID.fromRandom();
    desc.ChannelCount = 2L;
    desc.SampleRate = Fraction.of(48000, 1);
    desc.QuantizationBits = 16L;
    desc.ContainerFormat = Labels.MXFGCClipWrappedBroadcastWaveAudioData;

        /* File Package */

    SourcePackage sp = new SourcePackage();
    sp.InstanceID = UUID.fromRandom();
    sp.PackageID = UMID.fromUUID(sp.InstanceID);
    sp.EssenceDescription = desc;
    sp.CreationTime = LocalDateTime.of(2025, 1, 1, 1, 0);
    sp.PackageLastModified = LocalDateTime.now();

    /* Source Clip */

    SourceClip clip = new SourceClip();
    clip.InstanceID = UUID.fromRandom();
    clip.StartPosition = 0L;
    clip.SourcePackageID = sp.PackageID;

    /* Segment */

    Sequence seq = new Sequence();
    seq.InstanceID = UUID.fromRandom();
    seq.ComponentObjects = new ComponentStrongReferenceVector();
    seq.ComponentLength = 48000L;
    seq.ComponentObjects.add(clip);

    /* Track */

    TimelineTrack track = new TimelineTrack();
    track.InstanceID = UUID.fromRandom();
    track.Origin = 0L;
    track.TrackID = 1L;
    track.EditRate = Fraction.of(48000, 1);
    track.TrackSegment = seq;
    track.TrackName = "Audio Track 1";

    /* preface */
    Preface p = new Preface();
    p.InstanceID = UUID.fromRandom();
    p.IdentificationList = new IdentificationStrongReferenceVector();
    p.IdentificationList.add(IdentificationHelper.makeIdentification());

    /* TODO: return better error when InstanceID is null */

    /* Content Storage Object */
    p.ContentStorageObject = new ContentStorage();
    p.ContentStorageObject.InstanceID = UUID.fromRandom();


    p.ContentStorageObject.Packages = new PackageStrongReferenceSet();
    p.ContentStorageObject.Packages.add(sp);

    p.ContentStorageObject.EssenceDataObjects = new EssenceDataStrongReferenceSet();
    p.ContentStorageObject.EssenceDataObjects.add(new EssenceData());
    p.ContentStorageObject.EssenceDataObjects.get(0).InstanceID = UUID.fromRandom();;
    p.ContentStorageObject.EssenceDataObjects.get(0).EssenceStreamID = 1L;


    ArrayList<Set> sets = new ArrayList<>();
    LocalTagRegister reg = new LocalTagRegister(StaticLocalTags.entries());

    /* write  */
    MXFOutputContext ctx = new MXFOutputContext() {

      @Override
      public UUID getPackageInstanceID(UMID packageID) {
        return sp.InstanceID;
      }

      @Override
      public int getLocalTag(AUID auid) {
        return (int) reg.getOrMakeLocalTag(auid);
      }

      @Override
      public void putSet(Set set) {
        sets.add(set);
      }

    };

    /* serialize the header metadata */
    p.serialize(ctx);

    /* write the header metadata */
    ByteArrayOutputStream headerbos = new ByteArrayOutputStream();
    MXFOutputStream headermos = new MXFOutputStream(headerbos);
    headermos.writeTriplet(PrimerPack.createTriplet(reg));
    for (Set set : sets) {
      Set.toStreamAsLocalSet(set, reg, headermos);
    }
    headermos.close();

    /* write the partition */
    MXFOutputStream mos = new MXFOutputStream(os);

    PartitionPack pp = new PartitionPack();
    pp.setOperationalPattern(Labels.MXFOPAtom1Track1SourceClip.asUL());
    UL[] ecs = new UL[] {Labels.MXFGCFrameWrappedAES3AudioData.asUL()};
    pp.setEssenceContainers(Arrays.asList(ecs));
    pp.setHeaderByteCount(headerbos.size());
    mos.writeTriplet(PartitionPack.toTriplet(pp, PartitionPack.Kind.HEADER, PartitionPack.Status.CLOSED_COMPLETE));
    headerbos.writeTo(mos);
    mos.close();
  }


}
