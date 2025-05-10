package com.sandflow.smpte.mxf;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.time.LocalDateTime;
import java.util.ArrayList;
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
import com.sandflow.smpte.util.CountingOutputStream;
import com.sandflow.smpte.util.UL;
import com.sandflow.smpte.util.UMID;
import com.sandflow.smpte.util.UUID;

public class StreamingWriter {

  public enum EssenceWrapping {
    CLIP, /* clip-wrapped essence */
    FRAME, /* frame-wrapped essence */
  }

  public enum ElementSize {
    CBE, /* constant */
    VBE, /* variable */
  }

  public record EssenceInfo(
      UL essenceKey,
      UL essenceContainerKey,
      FileDescriptor descriptor,
      EssenceWrapping wrapping,
      ElementSize elementSize,
      Fraction editRate,
      Integer partitionDuration) {
  }

  /* TODO: warn if clip wrapping and partition duration is not null */

  private PartitionPack makePartitionPack() {
    /* partition pack */
    PartitionPack pp = new PartitionPack();
    pp.setOperationalPattern(Labels.MXFOPAtom1Track1SourceClip.asUL());
    pp.setEssenceContainers(Arrays.asList(new UL[] { this.essenceInfo.essenceContainerKey }));
    return pp;
  }

  private final EssenceInfo essenceInfo;
  private final Preface preface;
  private final MXFOutputStream fos;

  StreamingWriter(OutputStream os, EssenceInfo essence) throws IOException, KLVException {
    this.fos = new MXFOutputStream(os);
    /* TODO: document single container */
    this.essenceStream = new MXFOutputStream(fos);
    this.essenceInfo = essence;

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
    this.preface = new Preface();
    this.preface.InstanceID = UUID.fromRandom();
    this.preface.FormatVersion = new Version(1, 3);
    this.preface.ObjectModelVersion = 1L;
    this.preface.PrimaryPackage = sp.PackageID;
    this.preface.FileLastModified = LocalDateTime.now();
    this.preface.EssenceContainers = ecs;
    this.preface.IsRIPPresent = true;
    this.preface.OperationalPattern = Labels.MXFOP1aSingleItemSinglePackageUniTrackStreamInternal;
    this.preface.IdentificationList = idList;
    this.preface.ContentStorageObject = cs;

    /* header metadata */
    byte[] headerbytes = serializeHeaderMetadata();

    /* write the header partition */

    MXFOutputStream mos = new MXFOutputStream(this.fos);

    /* write the partition */
    /* TODO: is this really open and incomplete? */
    PartitionPack pp = makePartitionPack();
    pp.setHeaderByteCount(headerbytes.length);
    mos.writeTriplet(
        PartitionPack.toTriplet(curPartition, PartitionPack.Kind.HEADER, PartitionPack.Status.OPEN_INCOMPLETE));
    mos.write(headerbytes);
    mos.flush();

    this.state = State.START;
    /* TODO: need to add 8K fill per st 2067-5 */
    /* TODO: can clip-wrapped essence be partitioned */
  }

  private byte[] serializeHeaderMetadata() throws IOException {
    /* write */
    LinkedList<Set> sets = new LinkedList<>();
    LocalTagRegister reg = new LocalTagRegister(StaticLocalTags.entries());
    MXFOutputContext ctx = new MXFOutputContext() {

      @Override
      public UUID getPackageInstanceID(UMID packageID) {
        for (var p : preface.ContentStorageObject.Packages) {
          if (packageID.equals(p.PackageID))
            return p.InstanceID;
        }
        return null;
      }

      @Override
      public int getLocalTag(AUID auid) {
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

    /* serialize the header metadata sets */
    this.preface.serialize(ctx);

    /* serialize the header */
    MXFOutputStream mos = new MXFOutputStream(new ByteArrayOutputStream());
    mos.writeTriplet(PrimerPack.createTriplet(reg));
    for (Set set : sets) {
      Set.toStreamAsLocalSet(set, reg, mos);
    }

    return ((ByteArrayOutputStream) mos.stream()).toByteArray();
  }

  enum State {
    START,
    RUNNING,
    DONE
  }

  /**
   * temportal offset (in Edit Units) of the current unit
   */
  long unitOffset = 0;
  long nextUnitOffset = 0;
  long unitSize;
  long essencePosition = 0;
  long nextEssencePosition = 0;
  State state = State.START;
  PartitionPack curPartition;
  MXFOutputStream essenceStream;
  MXFOutputStream partitionStream;
  ArrayList<Long> unitOffsets;

  public EssenceInfo getEssenceInfo() {
    return essenceInfo;
  }

  public long getUnitOffset() {
    return unitOffset;
  }

  /*
   * TODO: clip wrapped essence containers can be partitioned, but probably should
   * be tested
   */

  private void startBodyPartition() throws IOException, KLVException {
    var pp = makePartitionPack();
    pp.setThisPartition(this.fos.written());
    pp.setBodyOffset(this.essenceStream.written());

    if (this.curPartition != null) {
      pp.setPreviousPartition(this.curPartition.getThisPartition());
    }

    this.curPartition = pp;
    this.fos.writeTriplet(PartitionPack.toTriplet(pp, PartitionPack.Kind.BODY, PartitionPack.Status.CLOSED_COMPLETE));

    /* initialize the index table */
    if (this.essenceInfo.elementSize() == ElementSize.VBE) {
      this.unitOffsets = new ArrayList<>();
    }
  }

  private void writeIndexPartition() throws IOException, KLVException {

  }

  /*
   * For clip-wrapped essence, a new partition is created for every call
   */
  public OutputStream nextUnits(int unitCount, long unitSize) throws IOException, KLVException {
    if (this.state == State.DONE) {
      /* TODO: improve exception */
      throw new RuntimeException("DONE");
    }

    if (this.essenceInfo.elementSize() == ElementSize.CBE) {
      if (this.nextUnitOffset == 0) {
        this.unitSize = unitSize;
      } else if (this.unitSize != unitSize) {
        throw new RuntimeException("Unit size mismatch");
      }
    }

    if (this.essenceInfo.wrapping() == EssenceWrapping.FRAME && unitCount != 1) {
      throw new RuntimeException("Only one Edit Unit can be written at a time");
    }

    /* do we need to start a new body partition */
    if (this.nextUnitOffset % this.essenceInfo.partitionDuration() == 0
        || this.essenceInfo.wrapping() == EssenceWrapping.CLIP) {

      if (this.nextUnitOffset != 0) {
        /* do not create an index partition on the initial call */
        this.writeIndexPartition();
      }

      this.startBodyPartition();

      /* start the essence element if clip-wrapping */
      if (this.essenceInfo.wrapping() == EssenceWrapping.CLIP) {
        this.essenceStream.writeUL(this.essenceInfo.essenceKey);
        this.essenceStream.writeBERLength(unitSize * unitCount);
      }
    }

    this.unitOffset = this.nextUnitOffset;
    this.nextUnitOffset += unitCount;

    /* add an entry to the index table if we have VBE essence */
    if (this.essenceInfo.elementSize() == ElementSize.VBE) {
      this.unitOffsets.add(this.essenceStream.written());
    }

    /* start the essence element if frame-wrapping */
    if (this.essenceInfo.wrapping() == EssenceWrapping.FRAME) {
      this.essenceStream.writeUL(this.essenceInfo.essenceKey);
      this.essenceStream.writeBERLength(unitSize * unitCount);
    }

    return this.essenceStream;
  }

  public boolean isDone() {
    return this.state == State.DONE;
  }

  public void finish() throws IOException, KLVException {
    if (this.state == State.DONE) {
      /* TODO: improve exception */
      throw new RuntimeException("DONE");
    }

    if (this.state == State.START) {
      /* TODO: improve exception */
      throw new RuntimeException("START");
    }

    this.writeIndexPartition();

    /* update header metadata */

    /* header metadata */
    byte[] headerbytes = serializeHeaderMetadata();

    /* write the footer partition */
    MXFOutputStream mos = new MXFOutputStream(this.fos);
    PartitionPack pp = makePartitionPack();
    pp.setHeaderByteCount(headerbytes.length);
    mos.writeTriplet(
        PartitionPack.toTriplet(curPartition, PartitionPack.Kind.HEADER, PartitionPack.Status.CLOSED_COMPLETE));
    mos.write(headerbytes);
    mos.flush();

    this.state = State.DONE;
  }
}
