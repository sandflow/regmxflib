package com.sandflow.smpte.mxf;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.commons.numbers.fraction.Fraction;

import com.sandflow.smpte.klv.LocalTagRegister;
import com.sandflow.smpte.klv.Set;
import com.sandflow.smpte.klv.exceptions.KLVException;
import com.sandflow.smpte.mxf.RandomIndexPack.PartitionOffset;
import com.sandflow.smpte.mxf.types.AUIDSet;
import com.sandflow.smpte.mxf.types.ContentStorage;
import com.sandflow.smpte.mxf.types.EssenceData;
import com.sandflow.smpte.mxf.types.EssenceDataStrongReferenceSet;
import com.sandflow.smpte.mxf.types.FileDescriptor;
import com.sandflow.smpte.mxf.types.IdentificationStrongReferenceVector;
import com.sandflow.smpte.mxf.types.IndexEntry;
import com.sandflow.smpte.mxf.types.IndexEntryArray;
import com.sandflow.smpte.mxf.types.IndexTableSegment;
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

  private static final long BODY_SID = 1L;
  private static final long INDEX_SID = 1L;

  public enum EssenceWrapping {
    CLIP, /* clip-wrapped essence */
    FRAME, /* frame-wrapped essence */
  }

  public enum ElementSize {
    CBE, /* constant */
    VBE, /* variable */
  }

  private enum State {
    RUNNING,
    DONE
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

  private final EssenceInfo essenceInfo;
  private final Preface preface;
  private final MXFOutputStream fos;
  private final LocalTagRegister reg;

  /**
   * temporal position (in Edit Units) of the current unit
   */
  long tPos = 0;
  /**
   * temporal position (in Edit Units) of the next unit
   */
  long nextTPos = 0;

  /**
   * size (in bytes) of CBE units (only valid for clip-wrapped CBE essence)
   */
  long cbeSize;

  State state;
  MXFOutputStream essenceStream;
  MXFOutputStream partitionStream;
  RandomIndexPack rip = new RandomIndexPack();

  /**
   * size in bytes of the VBE units within the current partition
   */
  ArrayList<Long> vbeBytePositions;

  /**
   * current partition
   */
  PartitionPack curPartition;

  /**
   * temporal position (in Edit Units) of the first unit in this partition
   */
  long indexStartPosition;


  StreamingWriter(OutputStream os, EssenceInfo essence) throws IOException, KLVException {
    /* TOOD: check for null */
    this.fos = new MXFOutputStream(os);
    /* TODO: document single container */
    this.essenceStream = new MXFOutputStream(fos);
    /* TODO: check for null */
    this.essenceInfo = essence;

    if (this.essenceInfo.wrapping() == EssenceWrapping.FRAME && this.essenceInfo.elementSize() == ElementSize.CBE) {
      throw new RuntimeException("Frame wrapping must use VBE");
    }

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
    edo.IndexStreamID = 1L;

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

    /* local tag register */

    this.reg = new LocalTagRegister(StaticLocalTags.entries());

    /* header metadata */
    byte[] headerbytes = serializeHeaderMetadata();

    /* write the partition */
    /* TODO: is this really open and incomplete? */
    startPartition(0, 0, headerbytes.length, 0, PartitionPack.Kind.HEADER, PartitionPack.Status.OPEN_INCOMPLETE);

    this.fos.write(headerbytes);

    this.state = State.RUNNING;
    /* TODO: need to add 8K fill per st 2067-5 */
    /* TODO: can clip-wrapped essence be partitioned */
  }

  private byte[] serializeHeaderMetadata() throws IOException {
    /* write */
    LinkedList<Set> sets = new LinkedList<>();
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

  /**
   * Partition utilities
   */

  /* TODO: warn if clip wrapping and partition duration is not null */

  private void startPartition(long bodySID, long indexSID, long headerSize, long indexSize, PartitionPack.Kind kind,  PartitionPack.Status status) throws IOException, KLVException {
    PartitionPack pp = new PartitionPack();
    pp.setBodySID(bodySID);
    pp.setIndexSID(indexSID);
    pp.setIndexByteCount(indexSize);
    pp.setHeaderByteCount(headerSize);
    pp.setOperationalPattern(Labels.MXFOPAtom1Track1SourceClip.asUL());
    pp.setEssenceContainers(Arrays.asList(new UL[] { this.essenceInfo.essenceContainerKey }));
    pp.setThisPartition(this.fos.written());
    pp.setBodyOffset(this.essenceStream.written());
    if (this.curPartition != null) {
      pp.setPreviousPartition(this.curPartition.getThisPartition());
    }
    this.fos.writeTriplet(PartitionPack.toTriplet(pp, kind, status));

    this.rip.addOffset(new PartitionOffset(bodySID, pp.getThisPartition()));

    this.curPartition = pp;
  }


  /*
   * TODO: clip wrapped essence containers can be partitioned, but probably should
   * be tested
   */

  private void startEssencePartition() throws IOException, KLVException {
    startPartition(BODY_SID, 0, 0, 0, PartitionPack.Kind.BODY, PartitionPack.Status.CLOSED_COMPLETE);

    /* initialize the index table */
    this.indexStartPosition = this.nextTPos;
    if (this.essenceInfo.elementSize() == ElementSize.VBE) {
      this.vbeBytePositions = new ArrayList<>();
    } else {
      this.vbeBytePositions = null;
    }
  }

  private void writeIndexPartition() throws IOException, KLVException {
    /* TODO: handle multile index table segments */

    var its = new IndexTableSegment();
    its.InstanceID = UUID.fromRandom();
    its.IndexEditRate = this.essenceInfo.editRate();
    its.IndexStartPosition = this.indexStartPosition;
    its.IndexDuration = this.nextTPos - its.IndexStartPosition;
    its.EditUnitByteCount = this.essenceInfo.elementSize() == ElementSize.CBE ? this.cbeSize : 0L;
    its.IndexStreamID = INDEX_SID;
    its.EssenceStreamID = BODY_SID;

    if (this.essenceInfo.elementSize() == ElementSize.VBE) {
      its.IndexEntryArray = new IndexEntryArray();
      for(var offset: this.vbeBytePositions) {
        var e = new IndexEntry();
        e.TemporalOffset = 0;
        e.Flags = (byte) 0x80;
        e.StreamOffset = offset;
        its.IndexEntryArray.add(e);
      }
      /* its.VBEByteCount = this.vbeBytePositions.get(this.vbeBytePositions.size() - 1);*/
    }

    /* serialize the index table segment */
    /* the AtomicReference is necessary since the variable is initialized in the
    inline MXFOutputContext */
    AtomicReference<Set> ars = new AtomicReference<>();
    MXFOutputContext ctx = new MXFOutputContext() {

      @Override
      public UUID getPackageInstanceID(UMID packageID) {
        throw new UnsupportedOperationException();
      }

      @Override
      public int getLocalTag(AUID auid) {
        return (int) reg.getOrMakeLocalTag(auid);
      }

      @Override
      public void putSet(Set set) {
        if (ars.get() != null) {
          throw new RuntimeException("Index Table Segment already serialized");
        }
        ars.set(set);
      }

    };

    its.serialize(ctx);

    if (ars.get() == null) {
      throw new RuntimeException("Index Table Segment not serialized");
    }

    /* serialize the header */
    MXFOutputStream mos = new MXFOutputStream(new ByteArrayOutputStream());
    Set.toStreamAsLocalSet(ars.get(), reg, mos);

    byte[] itsBytes = ((ByteArrayOutputStream) mos.stream()).toByteArray();

    startPartition(0, INDEX_SID, 0, itsBytes.length, PartitionPack.Kind.BODY, PartitionPack.Status.CLOSED_COMPLETE);
    fos.write(itsBytes);
  }

  /**
   * Client API
   */

  /*
   * For clip-wrapped essence, a new partition is created for every call
   */
  public OutputStream nextUnits(int unitCount, long unitSize) throws IOException, KLVException {
    if (this.state == State.DONE) {
      /* TODO: improve exception */
      throw new RuntimeException("DONE");
    }

    if (this.essenceInfo.elementSize() == ElementSize.CBE) {
      if (this.nextTPos == 0) {
        this.cbeSize = unitSize;
      } else if (this.cbeSize != unitSize) {
        throw new RuntimeException("CBE edit unit size mismatch");
      }
    }

    if (this.essenceInfo.wrapping() == EssenceWrapping.FRAME && unitCount != 1) {
      throw new RuntimeException("Only one Edit Unit can be written at a time");
    }

    /* do we need to start a new essence partition */
    if (this.nextTPos == 0 ||
        this.nextTPos % this.essenceInfo.partitionDuration() == 0 ||
        this.essenceInfo.wrapping() == EssenceWrapping.CLIP) {

      if (this.nextTPos != 0) {
        /* do not create an index partition on the initial call */
        this.writeIndexPartition();
      }

      this.startEssencePartition();

      /* start the essence element if clip-wrapping */
      if (this.essenceInfo.wrapping() == EssenceWrapping.CLIP) {
        this.essenceStream.writeUL(this.essenceInfo.essenceKey);
        this.essenceStream.writeBERLength(unitSize * unitCount);
      }
    }

    /* add an entry to the index table if we have VBE essence */
    if (this.essenceInfo.elementSize() == ElementSize.VBE) {
      this.vbeBytePositions.add(this.essenceStream.written());
    }

    /* start the essence element if frame-wrapping */
    if (this.essenceInfo.wrapping() == EssenceWrapping.FRAME) {
      this.essenceStream.writeUL(this.essenceInfo.essenceKey);
      this.essenceStream.writeBERLength(unitSize * unitCount);
    }

    this.tPos = this.nextTPos;
    this.nextTPos += unitCount;

    return this.essenceStream;
  }

  public void finish() throws IOException, KLVException {
    if (this.state == State.DONE) {
      /* TODO: improve exception */
      throw new RuntimeException("DONE");
    }

    /* have we written anything in the file? */
    if (this.nextTPos == 0) {
      /* TODO: improve exception */
      throw new RuntimeException("START");
    }

    this.writeIndexPartition();

    /* update header metadata */

    /* header metadata */
    byte[] headerbytes = serializeHeaderMetadata();

    /* write the footer partition */
    startPartition(0, 0, headerbytes.length, 0, PartitionPack.Kind.FOOTER, PartitionPack.Status.CLOSED_COMPLETE);
    fos.write(headerbytes);

    /* write the RIP */
    this.rip.toStream(fos);

    this.state = State.DONE;
  }

  /**
   * GETTERS/SETTERS
   */

  public EssenceInfo getEssenceInfo() {
    return essenceInfo;
  }

  public long gettPos() {
    return tPos;
  }

  public boolean isDone() {
    return this.state == State.DONE;
  }

}
