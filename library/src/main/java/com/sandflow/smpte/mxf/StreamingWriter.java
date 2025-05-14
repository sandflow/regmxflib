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
import com.sandflow.smpte.mxf.types.SourcePackage;
import com.sandflow.smpte.mxf.types.Version;
import com.sandflow.smpte.util.AUID;
import com.sandflow.smpte.util.UL;
import com.sandflow.smpte.util.UMID;
import com.sandflow.smpte.util.UUID;

public class StreamingWriter {

  private static final long BODY_SID = 1L;
  private static final long INDEX_SID = 1L;
  private static final byte elementCount = 1;
  private static final byte elementId = 1;

  private enum UnitWrapping {
    CLIP, /* clip-wrapped essence */
    FRAME, /* frame-wrapped essence */
  }

  private enum UnitSizing {
    CBE, /* constant */
    VBE, /* variable */
  }

  private enum State {
    START,
    CONTAINER,
    DONE
  }

  UnitWrapping unitWrapping;
  UnitSizing unitSizing;

  /**
   * size (in bytes) of CBE units (only valid for clip-wrapped CBE essence)
   */
  private long cbeUnitSize;


  public enum EssenceKind {
    VIDEO,
    AUDIO
  }

  public record EssenceInfo(
      UL essenceKey,
      UL essenceContainerKey,
      EssenceKind essenceKind,
      FileDescriptor descriptor,
      Fraction editRate,
      Integer partitionDuration,
      java.util.Set<AUID> conformsToSpecifications) {
  }

  private final EssenceInfo essenceInfo;
  private final Preface preface;
  private final MXFOutputStream fos;
  private final LocalTagRegister reg;

  /**
   * Essence key for a single item essence element
   */
  private final UL elementKey;

  /**
   * Temporal position (in Edit Units) of the current unit
   */
  private long tPos = 0;

  /**
   * Temporal position (in Edit Units) of the next unit
   */
  private long nextTPos = 0;

  /**
   * Expected position (in bytes) of the next unit in the Essence Container
   */
  private long nextBPos = 0;


  private State state;
  private MXFOutputStream essenceStream;
  private RandomIndexPack rip = new RandomIndexPack();

  /**
   * size in bytes of the VBE units within the current partition
   */
  private ArrayList<Long> vbeBytePositions;

  /**
   * current partition
   */
  private PartitionPack curPartition;

  /**
   * temporal position (in Edit Units) of the first unit in this partition
   */
  private long indexStartPosition;

  StreamingWriter(OutputStream os, EssenceInfo essence) throws IOException, KLVException {
    /* TOOD: check for null */
    this.fos = new MXFOutputStream(os);
    /* TODO: document single container */
    this.essenceStream = new MXFOutputStream(fos);
    /* TODO: check for null */
    this.essenceInfo = essence;

    AUID dataDefinition = essence.essenceKind == EssenceKind.AUDIO ? Labels.SoundEssenceTrack : Labels.PictureEssenceTrack;

    this.elementKey = MXFFiles.makeEssenceElementKey(this.essenceInfo.essenceKey, elementCount, elementId);

    /* Essence Descriptor */
    /* TODO: need to allow cloning */
    FileDescriptor desc = essence.descriptor();
    desc.ContainerFormat = new AUID(essence.essenceContainerKey());
    desc.EssenceLength = /* 24L */ 0L;
    desc.LinkedTrackID = null;

    /* File Package */
    SourcePackage sp = new SourcePackage();
    sp.PackageName = "Top-level File Package";
    sp.EssenceDescription = desc;
    long trackNum = MXFFiles.getTrackNumber(this.elementKey);
    PackageHelper.initSingleTrackPackage(sp, essence.editRate(), /* 24L */ null, UMID.NULL_UMID, trackNum, null, dataDefinition);

    /* Material Package */
    var mp = new MaterialPackage();
    mp.PackageName = "Material Package";
    PackageHelper.initSingleTrackPackage(mp, essence.editRate(), /* 24L */ null, sp.PackageID, null, 1L, dataDefinition);

    /* TODO: return better error when InstanceID is null */
    /* EssenceDataObject */
    var edo = new EssenceData();
    edo.InstanceID = UUID.fromRandom();
    edo.EssenceStreamID = BODY_SID;
    edo.IndexStreamID = INDEX_SID;
    edo.LinkedPackageID = sp.PackageID;

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
    ecs.add(new AUID(this.essenceInfo.essenceContainerKey()));

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
    if (this.essenceInfo.conformsToSpecifications != null) {
      this.preface.ConformsToSpecifications = new AUIDSet();
      this.preface.ConformsToSpecifications.addAll(this.essenceInfo.conformsToSpecifications);
    }

    /* local tag register */

    this.reg = new LocalTagRegister();

    /* header metadata */
    byte[] headerbytes = serializeHeaderMetadata();

    /* write the partition */
    /* TODO: is this really open and incomplete? */
    startPartition(0, 0, headerbytes.length, 0, PartitionPack.Kind.HEADER, PartitionPack.Status.OPEN_INCOMPLETE);

    this.fos.write(headerbytes);

    this.state = State.START;
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
        Long localTag = reg.getLocalTag(auid);

        if (localTag == null) {
          localTag = StaticLocalTags.getLocalTag(auid);
        }

        if (localTag == null) {
          localTag = reg.getOrMakeLocalTag(auid);
        } else {
          reg.add(localTag, auid);
        }

        return (int) (localTag & 0xFFFFFFFF);
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

  private void startPartition(long bodySID, long indexSID, long headerSize, long indexSize, PartitionPack.Kind kind,
      PartitionPack.Status status) throws IOException, KLVException {
    PartitionPack pp = new PartitionPack();
    pp.setKagSize(1L);
    pp.setBodySID(bodySID);
    pp.setIndexSID(indexSID);
    pp.setIndexByteCount(indexSize);
    pp.setHeaderByteCount(headerSize);
    pp.setOperationalPattern(Labels.MXFOP1aSingleItemSinglePackageUniTrackStreamInternal.asUL());
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
    if (this.unitSizing == UnitSizing.VBE) {
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
    its.IndexStreamID = INDEX_SID;
    its.EssenceStreamID = BODY_SID;

    if (this.unitSizing == UnitSizing.VBE) {
      its.EditUnitByteCount = 0L;
      its.IndexEntryArray = new IndexEntryArray();
      for (var offset : this.vbeBytePositions) {
        var e = new IndexEntry();
        e.TemporalOffset = 0;
        e.Flags = (byte) 0x80;
        e.StreamOffset = offset;
        e.KeyFrameOffset = 0;
        e.TemporalOffset = 0;
        its.IndexEntryArray.add(e);
      }
      its.VBEByteCount = this.nextBPos - this.vbeBytePositions.get( this.vbeBytePositions.size() - 1);
      /*
       * TODO: VBEByteCount
       */
    } else {
      its.EditUnitByteCount = this.cbeUnitSize;
    }

    /* serialize the index table segment */
    /*
     * the AtomicReference is necessary since the variable is initialized in the
     * inline MXFOutputContext
     */
    AtomicReference<Set> ars = new AtomicReference<>();
    MXFOutputContext ctx = new MXFOutputContext() {

      @Override
      public UUID getPackageInstanceID(UMID packageID) {
        throw new UnsupportedOperationException();
      }

      @Override
      public int getLocalTag(AUID auid) {
        return (int) (StaticLocalTags.getLocalTag(auid).longValue() & 0xFFFFFFF);
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



  /**
   * creates a clip-wrapped essence container with constant size units
   * 
   * @param unitCount Number of units in the essence container
   * @param unitSize Size in bytes of each element
   */
  public OutputStream createClipWrappedCBE(long unitCount, long unitSize) throws IOException, KLVException {
    if (this.state != State.START) {
      /* TODO: improve exception */
      throw new RuntimeException("START");
    }
    this.unitWrapping = UnitWrapping.CLIP;
    this.unitSizing = UnitSizing.CBE;
    this.cbeUnitSize = unitSize;
    this.state = State.CONTAINER;

    this.startEssencePartition();
    this.essenceStream.writeUL(this.elementKey);
    this.essenceStream.writeBER4Length(unitSize * unitCount);

    return this.essenceStream;
  }

  /**
   * Starts a frame-wrapped essence container
   * @throws KLVException 
   * @throws IOException 
   */
  public void startVBEFrameWrapped() throws IOException, KLVException {
    if (this.state != State.START) {
      /* TODO: improve exception */
      throw new RuntimeException("START");
    }
    this.unitWrapping = UnitWrapping.FRAME;
    this.unitSizing = UnitSizing.VBE;
    this.state = State.CONTAINER;

    this.startEssencePartition();
  }

  /**
   * Starts a clip-wrapped essence container with variable size elements
   * 
   * @param totalSize Size in bytes of the essence container
   */
  public void startVBEClipWrapped(long totalSize) throws IOException, KLVException {
    if (this.state != State.START) {
      /* TODO: improve exception */
      throw new RuntimeException("START");
    }
    this.unitWrapping = UnitWrapping.CLIP;
    this.unitSizing = UnitSizing.VBE;
    this.state = State.CONTAINER;

    this.startEssencePartition();

    this.essenceStream.writeUL(this.elementKey);
    this.essenceStream.writeBER4Length(totalSize);
  }

  /*
   * Adds a single unit to a clip- and frame-wrapped VBE essence container
   */
  public OutputStream nextUnit(long unitSize) throws IOException, KLVException {
    if (this.state != State.CONTAINER) {
      /* TODO: improve exception */
      throw new RuntimeException("DONE");
    }

    if (this.unitSizing == UnitSizing.CBE) {
      throw new RuntimeException("Cannot be called for CBE units");
    }

    /* check if the previous unit landed where it was expected */
    if (this.nextTPos != 0 && this.essenceStream.written() != this.nextBPos) {
      throw new RuntimeException("Number of bytes written does not match the size of the unit.");
    }

    /* do we need to start a new essence partition */
    /*
     * TODO: this should allow the partition to go slightly beyond its requested
     * duration
     */
    if (this.unitWrapping == UnitWrapping.FRAME && this.nextTPos != 0 &&
        (this.essenceInfo.partitionDuration() != null && this.nextTPos % this.essenceInfo.partitionDuration() == 0)) {

      this.writeIndexPartition();
      this.startEssencePartition();

    }

    /* add an entry to the index table if we have VBE essence */
    this.vbeBytePositions.add(this.essenceStream.written());


    /* start the essence element if frame-wrapping */
    if (this.unitWrapping == UnitWrapping.FRAME) {
      this.essenceStream.writeUL(this.elementKey);
      this.essenceStream.writeBER4Length(unitSize);
    }

    /* update the expected position of the next Unit */
    this.nextBPos = this.essenceStream.written() + unitSize;

    /* update the temporal positions */
    this.tPos = this.nextTPos;
    this.nextTPos += 1;

    return this.essenceStream;
  }

  public void finish() throws IOException, KLVException {
    if (this.state != State.CONTAINER) {
      /* TODO: improve exception */
      throw new RuntimeException("DONE");
    }

    this.writeIndexPartition();

    /* update header metadata */

    for (var p : this.preface.ContentStorageObject.Packages) {
      for (var t : p.PackageTracks) {
        t.TrackSegment.ComponentLength = this.tPos;
      }
    }

    /* header metadata */
    byte[] headerbytes = serializeHeaderMetadata();

    /* write the footer partition */
    startPartition(0, 0, headerbytes.length, 0, PartitionPack.Kind.FOOTER, PartitionPack.Status.CLOSED_COMPLETE);
    fos.write(headerbytes);

    /* write the RIP */
    this.rip.toStream(fos);

    fos.flush();

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
