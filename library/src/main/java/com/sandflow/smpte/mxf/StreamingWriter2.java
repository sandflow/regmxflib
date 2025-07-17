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
import com.sandflow.smpte.klv.LocalTagResolver;
import com.sandflow.smpte.klv.Set;
import com.sandflow.smpte.klv.exceptions.KLVException;
import com.sandflow.smpte.mxf.RandomIndexPack.PartitionOffset;
import com.sandflow.smpte.mxf.helpers.IdentificationHelper;
import com.sandflow.smpte.mxf.helpers.PackageHelper;
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
import com.sandflow.smpte.mxf.types.Sequence;
import com.sandflow.smpte.mxf.types.SourcePackage;
import com.sandflow.smpte.mxf.types.Version;
import com.sandflow.smpte.util.AUID;
import com.sandflow.smpte.util.UL;
import com.sandflow.smpte.util.UMID;
import com.sandflow.smpte.util.UUID;

public class StreamingWriter2 {

  interface EssenceContainerWriter {
    boolean nextContentPackage();

    boolean nextElement(UL essenceKey, long elementLength);

    void partition();

    void end();
  }

  class ClipCBEWriter implements EssenceContainerWriter {

    private long cbeUnitSize;
    private long sid;

    @Override
    public boolean nextContentPackage() {
      throw new UnsupportedOperationException("Unimplemented method 'nextContentPackage'");
    }

    @Override
    public boolean nextElement(UL essenceKey, long elementLength) {
      throw new UnsupportedOperationException("Unimplemented method 'nextElement'");
    }

    @Override
    public void partition() {
      throw new UnsupportedOperationException("Unimplemented method 'partition'");
    }

    @Override
    public void end() {
      throw new UnsupportedOperationException("Unimplemented method 'end'");
    }

  }

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

  /* TODO: document single container */

  StreamingWriter2(OutputStream os, EssenceContainerInfo essence) throws IOException, KLVException {
    if (os == null) {
      throw new IllegalArgumentException("Output stream must not be null");
    }
    this.fos = new MXFOutputStream(os);
    this.essenceStream = new MXFOutputStream(fos);

    if (essence == null) {
      throw new IllegalArgumentException("Essence info must not be null");
    }
    this.essenceInfo = essence;

    AUID dataDefinition = essence.essenceKind == EssenceKind.AUDIO ? Labels.SoundEssenceTrack
        : Labels.PictureEssenceTrack;

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
    PackageHelper.initSingleTrackPackage(sp, essence.editRate(), /* 24L */ null, UMID.NULL_UMID, trackNum, null,
        dataDefinition);

    /* Material Package */
    var mp = new MaterialPackage();
    mp.PackageName = "Material Package";
    PackageHelper.initSingleTrackPackage(mp, essence.editRate(), /* 24L */ null, sp.PackageID, null, 1L,
        dataDefinition);

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
    startPartition(0, 0, headerbytes.length, 0, PartitionPack.Kind.HEADER, PartitionPack.Status.OPEN_INCOMPLETE);

    this.fos.write(headerbytes);

    this.state = State.START;
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
      public void putSet(Set set) {
        /* makes the preface set the first in the list as required by ST 377-1 */
        if (Preface.getKey().equalsIgnoreVersionAndGroupCoding(set.getKey())) {
          sets.addFirst(set);
        } else {
          sets.add(set);
        }
      }

    };

    /* collect the header metadata sets */
    this.preface.toSet(ctx);

    /* serialize the header */
    LocalTagResolver tags = new LocalTagResolver() {

      long nextDynamicTag = 0x8000L;

      @Override
      public Long getLocalTag(AUID auid) {
        Long localTag = reg.getLocalTag(auid);

        if (localTag == null) {
          localTag = StaticLocalTags.register().getLocalTag(auid);
        }

        if (localTag == null) {
          localTag = nextDynamicTag++;
        }

        reg.add(localTag, auid);

        return localTag;
      }

      @Override
      public AUID getAUID(long localtag) {
        throw new UnsupportedOperationException("Unimplemented method 'getAUID'");
      }

    };

    ByteArrayOutputStream bos = new ByteArrayOutputStream();
    MXFOutputStream mos = new MXFOutputStream(bos);
    mos.writeTriplet(PrimerPack.createTriplet(reg));
    for (Set set : sets) {
      Set.toStreamAsLocalSet(set, tags, mos);
    }

    /* required 8 KB fill item per ST 2067-5 */
    FillItem.toStream(mos, (short) 8192);

    mos.flush();

    return bos.toByteArray();
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
    pp.setThisPartition(this.fos.getWrittenCount());
    if (kind == PartitionPack.Kind.FOOTER) {
      pp.setFooterPartition(pp.getThisPartition());
    }
    pp.setBodyOffset(this.essenceStream.getWrittenCount());
    if (this.curPartition != null) {
      pp.setPreviousPartition(this.curPartition.getThisPartition());
    }
    this.fos.writeBER4Triplet(PartitionPack.toTriplet(pp, kind, status));

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
      its.VBEByteCount = this.nextBPos - this.vbeBytePositions.get(this.vbeBytePositions.size() - 1);
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
      public void putSet(Set set) {
        if (ars.get() != null) {
          throw new RuntimeException("Index Table Segment already serialized");
        }
        ars.set(set);
      }

    };

    its.toSet(ctx);

    if (ars.get() == null) {
      throw new RuntimeException("Index Table Segment not serialized");
    }

    /* serialize the header */
    LocalTagResolver tags = new LocalTagResolver() {
      @Override
      public Long getLocalTag(AUID auid) {
        Long localTag = StaticLocalTags.register().getLocalTag(auid);
        if (localTag == null) {
          throw new RuntimeException();
        }
        return localTag;
      }

      @Override
      public AUID getAUID(long localtag) {
        throw new UnsupportedOperationException("Unimplemented method 'getAUID'");
      }

    };
    MXFOutputStream mos = new MXFOutputStream(new ByteArrayOutputStream());
    Set.toStreamAsLocalSet(ars.get(), tags, mos);

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
   * @param unitSize  Size in bytes of each element
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
    this.essenceStream.writeBERLength(unitSize * unitCount);

    return this.essenceStream;
  }

  /**
   * Starts a frame-wrapped essence container
   * 
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
    this.essenceStream.writeBERLength(totalSize);
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
    if (this.nextTPos != 0 && this.essenceStream.getWrittenCount() != this.nextBPos) {
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
    this.vbeBytePositions.add(this.essenceStream.getWrittenCount());

    /* start the essence element if frame-wrapping */
    if (this.unitWrapping == UnitWrapping.FRAME) {
      this.essenceStream.writeUL(this.elementKey);
      this.essenceStream.writeBERLength(unitSize);
    }

    /* update the expected position of the next Unit */
    this.nextBPos = this.essenceStream.getWrittenCount() + unitSize;

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
        Sequence s = (Sequence) t.TrackSegment;
        s.ComponentLength = this.tPos;
        for (var c : s.ComponentObjects) {
          c.ComponentLength = this.tPos;
        }
      }

      if (p instanceof SourcePackage) {
        SourcePackage sp = (SourcePackage) p;
        if (sp.EssenceDescription instanceof FileDescriptor) {
          FileDescriptor fd = (FileDescriptor) sp.EssenceDescription;
          fd.EssenceLength = this.tPos;
        }
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

  public EssenceContainerInfo getEssenceInfo() {
    return essenceInfo;
  }

  public long gettPos() {
    return tPos;
  }

  public boolean isDone() {
    return this.state == State.DONE;
  }

}
