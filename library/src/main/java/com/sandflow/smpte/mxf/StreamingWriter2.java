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
import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;

import javax.management.RuntimeErrorException;

import org.apache.commons.numbers.fraction.Fraction;

import com.sandflow.smpte.klv.LocalTagRegister;
import com.sandflow.smpte.klv.LocalTagResolver;
import com.sandflow.smpte.klv.Set;
import com.sandflow.smpte.klv.exceptions.KLVException;
import com.sandflow.smpte.mxf.RandomIndexPack.PartitionOffset;
import com.sandflow.smpte.mxf.helpers.IdentificationHelper;
import com.sandflow.smpte.mxf.helpers.IndexSegmentHelper;
import com.sandflow.smpte.mxf.helpers.OP1aHelper.EssenceContainerInfo;
import com.sandflow.smpte.mxf.helpers.PackageHelper;
import com.sandflow.smpte.mxf.types.AUIDSet;
import com.sandflow.smpte.mxf.types.ContentStorage;
import com.sandflow.smpte.mxf.types.EssenceData;
import com.sandflow.smpte.mxf.types.EssenceDataStrongReferenceSet;
import com.sandflow.smpte.mxf.types.EssenceDescriptor;
import com.sandflow.smpte.mxf.types.FileDescriptor;
import com.sandflow.smpte.mxf.types.IdentificationStrongReferenceVector;
import com.sandflow.smpte.mxf.types.IndexEntry;
import com.sandflow.smpte.mxf.types.IndexEntryArray;
import com.sandflow.smpte.mxf.types.IndexTableSegment;
import com.sandflow.smpte.mxf.types.MaterialPackage;
import com.sandflow.smpte.mxf.types.MultipleDescriptor;
import com.sandflow.smpte.mxf.types.PackageStrongReferenceSet;
import com.sandflow.smpte.mxf.types.Preface;
import com.sandflow.smpte.mxf.types.Sequence;
import com.sandflow.smpte.mxf.types.SourcePackage;
import com.sandflow.smpte.mxf.types.TimelineTrack;
import com.sandflow.smpte.mxf.types.Track;
import com.sandflow.smpte.mxf.types.Package;
import com.sandflow.smpte.mxf.types.Version;
import com.sandflow.smpte.util.AUID;
import com.sandflow.smpte.util.UL;
import com.sandflow.smpte.util.UMID;
import com.sandflow.smpte.util.UUID;

public class StreamingWriter2 extends OutputStream {

  private abstract class EssenceContainerWriter {

    private final long bodySID;
    private final long indexSID;

    EssenceContainerWriter(long bodySID, long indexSID) {
      this.bodySID = bodySID;
      this.indexSID = indexSID;
    }

    long getIndexSID() {
      return this.indexSID;
    }

    long getBodySID() {
      return this.bodySID;
    }

    abstract byte[] drainIndexSegments() throws IOException;

    abstract long getBodyOffset();

    abstract void addIndexEntry();

    abstract void nextElement(UL elementKey, long elementLength) throws IOException;
  }

  class ClipCBEWriter extends EssenceContainerWriter {

    private CBEClipIndex index;
    private final long accessUnitSize;

    ClipCBEWriter(long bodySID, long indexSID, long accessUnitSize) {
      super(bodySID, indexSID);
      this.accessUnitSize = accessUnitSize;
    }

    @Override
    void addIndexEntry() {
      /* does nothing */
    }

    @Override
    void nextElement(UL elementKey, long elementLength) throws IOException {
      if (elementLength % this.accessUnitSize != 0) {
        throw new RuntimeException();
      }

      StreamingWriter2.this.essenceStream.writeUL(elementKey);
      StreamingWriter2.this.essenceStream.writeBERLength(elementLength);
      StreamingWriter2.this.remainingElementBytes = elementLength;

      this.index = new CBEClipIndex(this.accessUnitSize, elementLength / this.accessUnitSize);
    }


    @Override
    long getBodyOffset() {
      return 0L;
    }

    @Override
    byte[] drainIndexSegments() throws IOException {
      if (this.index == null) {
        return null;
      }

      var its = new IndexTableSegment();
      its.InstanceID = UUID.fromRandom();
      its.IndexEditRate = StreamingWriter2.this.getECEditRate(this.getBodySID());
      its.IndexStartPosition = 0L;
      its.IndexDuration = this.index.getLength();
      its.IndexStreamID = this.getIndexSID();
      its.EssenceStreamID = this.getBodySID();
      its.EditUnitByteCount = this.accessUnitSize;

      this.index = null;

      return IndexSegmentHelper.toBytes(its);
    }

  }

  private enum State {
    INIT,
    START,
    CONTAINER,
    ELEMENT,
    DONE
  }

  private final MXFOutputStream fos;

  private State state = State.INIT;
  private MXFOutputStream essenceStream;
  private long remainingElementBytes;
  private RandomIndexPack rip = new RandomIndexPack();
  private final java.util.Set<Long> sids = new HashSet<>();
  private final java.util.Map<Long, EssenceContainerWriter> ecs = new HashMap<>();
  private EssenceContainerWriter currentEC;
  java.util.Set<UL> ecLabels = new HashSet<>();
  private Preface preface;

  /**
   * current partition
   */
  private PartitionPack curPartition;

  StreamingWriter2(OutputStream os) throws IOException, KLVException {
    if (os == null) {
      throw new IllegalArgumentException("Output stream must not be null");
    }
    this.fos = new MXFOutputStream(os);
    this.essenceStream = new MXFOutputStream(fos);
  }

  private AUID getOP() {
    return this.preface.OperationalPattern;
  }

  private Fraction getECEditRate(long sid) {

    Optional<EssenceData> ed = this.preface.ContentStorageObject.EssenceDataObjects.stream()
      .filter(e -> e.EssenceStreamID == sid)
      .findAny();

    if (!ed.isPresent())
      return null;

    Optional<Package> p = this.preface.ContentStorageObject.Packages.stream()
      .filter(e -> e.PackageID.equals(ed.get().LinkedPackageID))
      .findFirst();

    if (!p.isPresent())
      return null;

    Optional<Track> t = p.get().PackageTracks.stream()
      .filter(e -> e instanceof TimelineTrack)
      .findFirst();

    if (! t.isPresent())
      return null;

    return ((TimelineTrack) t.get()).EditRate;
  }

  private java.util.Set<UL> getECLabels() {
    java.util.Set<UL> labels = new HashSet<>();

    Consumer<FileDescriptor> collectLabels = new Consumer<>() {
      @Override
      public void accept(FileDescriptor fd) {
        if (fd == null)
          return;
        if (fd.ContainerFormat != null && fd.ContainerFormat.isUL())
          labels.add(fd.ContainerFormat.asUL());

        if (!(fd instanceof MultipleDescriptor))
          return;
        MultipleDescriptor md = (MultipleDescriptor) fd;

        if (md.FileDescriptors == null)
          return;

        for (FileDescriptor cfd : md.FileDescriptors) {
          this.accept(cfd);
        }
      }
    };

    for (Package p : this.preface.ContentStorageObject.Packages) {
      if (!(p instanceof SourcePackage))
        continue;
      SourcePackage sp = (SourcePackage) p;
      if (sp.EssenceDescription == null || !(sp.EssenceDescription instanceof FileDescriptor))
        continue;
      collectLabels.accept((FileDescriptor) sp.EssenceDescription);
    }

    return labels;
  }

  /**
   * Client API
   */

  /**
   * Write the header partition
   *
   * @param headerMetadata Header metadata
   * @throws IOException
   * @throws KLVException
   */
  public void start(Preface headerMetadata) throws IOException, KLVException {
    if (this.state != State.INIT) {
      throw new RuntimeException();
    }

    if (headerMetadata == null) {
      throw new RuntimeException();
    }

    this.preface = headerMetadata;

    /* TODO: fill in ecLabels */

    /* serialize the header metadata */
    byte[] hmb = serializeHeaderMetadata(headerMetadata);

    /* write the header parition */
    startPartition(0, 0, hmb.length, 0, 0L, PartitionPack.Kind.HEADER, PartitionPack.Status.OPEN_INCOMPLETE);
    this.fos.write(hmb);

    this.state = State.START;
  }

  public void startPartition(long sid) throws IOException, KLVException {
    /* TODO: check that we are not in the middle of an element */

    EssenceContainerWriter ec = this.ecs.get(sid);

    /* TODO: check that we found an ec */

    /* do we need to create an index partition for the current essence container */
    if (this.currentEC != null && this.currentEC.getBodySID() != 0) {
      this.writeIndexPartition();
    }

    /* close the current partition */

    /* start a new partition */
    startPartition(sid, ec.getIndexSID(), 0, 0, ec.getBodyOffset(), PartitionPack.Kind.BODY,
        PartitionPack.Status.CLOSED_COMPLETE);
  }

  /**
   * creates a clip-wrapped essence container with constant size units
   * 
   * @param unitCount Number of units in the essence container
   * @param unitSize  Size in bytes of each element
   */
  public void addCBEClipWrappedEC(long bodySID, long indexSID, long unitCount, long unitSize)
      throws IOException, KLVException {
    /* TODO: check for valid SIDs */

    if (this.sids.contains(bodySID)) {
      throw new RuntimeException();
    }

    if (this.sids.contains(indexSID)) {
      throw new RuntimeException();
    }

    this.sids.add(bodySID);
    this.sids.add(indexSID);

    this.ecs.put(bodySID, new ClipCBEWriter(bodySID, indexSID, unitSize));
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

  public void finish(Preface headerMetadata) throws IOException, KLVException {
    if (this.state != State.CONTAINER) {
      /* TODO: improve exception */
      throw new RuntimeException("DONE");
    }

    this.writeIndexPartition();

    /* update header metadata */

    for (var p : headerMetadata.ContentStorageObject.Packages) {
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
    byte[] headerbytes = serializeHeaderMetadata(headerMetadata);

    /* write the footer partition */
    startPartition(0, 0, headerbytes.length, 0, PartitionPack.Kind.FOOTER, PartitionPack.Status.CLOSED_COMPLETE);
    fos.write(headerbytes);

    /* write the RIP */
    this.rip.toStream(fos);

    fos.flush();

    this.state = State.DONE;
  }

  @Override
  public void write(int b) throws IOException {
    if (this.state != State.ELEMENT) {
      throw new RuntimeException();
    }
    if (this.remainingElementBytes - 1 < 0)
      throw new RuntimeException();
    this.fos.write(b);
    this.remainingElementBytes--;
  }

  @Override
  public void write(byte[] b, int off, int len) throws IOException {
    if (this.state != State.ELEMENT) {
      throw new RuntimeException();
    }

    if (this.remainingElementBytes - len < 0) {
      throw new RuntimeException();
    }

    this.fos.write(b, off, len);
    this.remainingElementBytes -= len;
  }

  @Override
  public void close() throws IOException {
    /*
     * do nothing: it is the responsibility of the caller to close the
     * underlying RandomAccessInputSource
     */
  }

  /**
   * PRIVATE API
   */

  private static byte[] serializeHeaderMetadata(Preface preface) throws IOException {
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
    preface.toSet(ctx);

    /* serialize the header */
    LocalTagRegister reg = new LocalTagRegister();
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

  private void startPartition(long bodySID, long indexSID, long headerSize, long indexSize, long bodyOffset,
      PartitionPack.Kind kind,
      PartitionPack.Status status) throws IOException, KLVException {
    PartitionPack pp = new PartitionPack();
    pp.setKagSize(1L);
    pp.setBodySID(bodySID);
    pp.setIndexSID(indexSID);
    pp.setIndexByteCount(indexSize);
    pp.setHeaderByteCount(headerSize);
    pp.setOperationalPattern(this.operationalPattern);
    pp.setEssenceContainers(this.ecLabels);
    pp.setThisPartition(this.fos.getWrittenCount());
    if (kind == PartitionPack.Kind.FOOTER) {
      pp.setFooterPartition(pp.getThisPartition());
    }
    pp.setBodyOffset(bodyOffset);
    if (this.curPartition != null) {
      pp.setPreviousPartition(this.curPartition.getThisPartition());
    }

    /* write the partition pack */
    this.fos.writeBER4Triplet(PartitionPack.toTriplet(pp, kind, status));

    /* add the partition to the RIP */
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
    its.IndexEditRate = this.currentEC.getEditRate();
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
