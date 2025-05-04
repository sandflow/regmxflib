package com.sandflow.smpte.mxf;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.LinkedList;

import org.apache.commons.numbers.fraction.Fraction;

import com.sandflow.smpte.klv.LocalTagRegister;
import com.sandflow.smpte.klv.Set;
import com.sandflow.smpte.klv.exceptions.KLVException;
import com.sandflow.smpte.mxf.types.AUIDSet;
import com.sandflow.smpte.mxf.types.ContentStorage;
import com.sandflow.smpte.mxf.types.EssenceData;
import com.sandflow.smpte.mxf.types.EssenceDataStrongReferenceSet;
import com.sandflow.smpte.mxf.types.FileDescriptor;
import com.sandflow.smpte.mxf.types.IdentificationStrongReferenceVector;
import com.sandflow.smpte.mxf.types.MaterialPackage;
import com.sandflow.smpte.mxf.types.PackageStrongReferenceSet;
import com.sandflow.smpte.mxf.types.Preface;
import com.sandflow.smpte.mxf.types.SoundDescriptor;
import com.sandflow.smpte.mxf.types.SourcePackage;
import com.sandflow.smpte.mxf.types.Version;
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
    sp.EssenceDescription = desc;
    PackageHelper.initSingleTrackPackage(sp, Fraction.of(48000, 1), null, UMID.NULL_UMID, 1L);


    /* Material Package */

    var mp = new MaterialPackage();
    PackageHelper.initSingleTrackPackage(mp, Fraction.of(48000, 1), null, sp.PackageID, null);

    /* TODO: return better error when InstanceID is null */

    /* EssenceDataObject */
    var edo = new EssenceData();
    edo.InstanceID = UUID.fromRandom();
    edo.EssenceStreamID = 1L;

    /* Content Storage Object */
    var cs = new ContentStorage();
    cs.InstanceID = UUID.fromRandom();
    cs.Packages = new PackageStrongReferenceSet();
    cs.Packages.add(mp);
    cs.Packages.add(sp);
    cs.EssenceDataObjects = new EssenceDataStrongReferenceSet();
    cs.EssenceDataObjects.add(edo);

    /* EssenceContainers */
    var ecs = new AUIDSet();
    ecs.add(Labels.MXFGCClipWrappedBroadcastWaveAudioData);

    /* Identification */
    var idList = new IdentificationStrongReferenceVector();
    idList.add(IdentificationHelper.makeIdentification());

     /* preface */
    Preface p = new Preface();
    p.InstanceID = UUID.fromRandom();
    p.FormatVersion = new Version(1, 3);
    p.ObjectModelVersion = 1L;
    p.PrimaryPackage = sp.PackageID;
    p.FileLastModified = LocalDateTime.now();
    p.EssenceContainers = ecs;
    p.IsRIPPresent = true;
    p.OperationalPattern = Labels.MXFOP1aSingleItemSinglePackageUniTrackStreamInternal;
    p.IdentificationList = idList;
    p.ContentStorageObject = cs;

    /* write  */
    LinkedList<Set> sets = new LinkedList<>();
    LocalTagRegister reg = new LocalTagRegister(StaticLocalTags.entries());
    MXFOutputContext ctx = new MXFOutputContext() {

      @Override
      public UUID getPackageInstanceID(UMID packageID) {
        return sp.InstanceID;
      }

      @Override
      public int getLocalTag(AUID auid) {
        /* TODO: not used */
        return (int) reg.getOrMakeLocalTag(auid);
      }

      @Override
      public void putSet(Set set) {
        /* makes the preface set the first in the list as required by ST 377-1 */
        if (Preface.getKey().equalsIgnoreVersionAndGroupCoding(set.getKey())) {
          sets.addFirst(set);
        } else {
          sets.add(set);
        }
      }

    };

    /* serialize the header metadata */
    p.serialize(ctx);

    /* write the header metadata */
    ByteArrayOutputStream headerbos = new ByteArrayOutputStream();
    MXFOutputStream headermos = new MXFOutputStream(headerbos);
    for (Set set : sets) {
      Set.toStreamAsLocalSet(set, reg, headermos);
    }
    headermos.close();

    /* 
     * write primer pack
     * the primer pack is written after the header metadata since dynamically
     * allocated local tags are assigned then the header metadata is written
     */
    ByteArrayOutputStream ppbos = new ByteArrayOutputStream();
    MXFOutputStream ppmos = new MXFOutputStream(ppbos);
    ppmos.writeTriplet(PrimerPack.createTriplet(reg));
    ppmos.close();

    /* partition pack */
    PartitionPack pp = new PartitionPack();
    pp.setOperationalPattern(Labels.MXFOPAtom1Track1SourceClip.asUL());
    pp.setEssenceContainers(Arrays.asList(new UL[] {Labels.MXFGCFrameWrappedAES3AudioData.asUL()}));
    pp.setHeaderByteCount(headerbos.size() + ppbos.size());

    /* write the partition */
    /* TODO: is this really open and incomplete? */
    MXFOutputStream mos = new MXFOutputStream(os);
    mos.writeTriplet(PartitionPack.toTriplet(pp, PartitionPack.Kind.HEADER, PartitionPack.Status.OPEN_INCOMPLETE));
    ppbos.writeTo(mos);
    headerbos.writeTo(mos);
    mos.close();
  }


}
