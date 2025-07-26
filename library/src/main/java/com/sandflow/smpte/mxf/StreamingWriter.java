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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import org.apache.commons.lang3.NotImplementedException;
import org.apache.commons.numbers.fraction.Fraction;

import com.sandflow.smpte.klv.LocalTagRegister;
import com.sandflow.smpte.klv.LocalTagResolver;
import com.sandflow.smpte.klv.Set;
import com.sandflow.smpte.klv.Triplet;
import com.sandflow.smpte.klv.exceptions.KLVException;
import com.sandflow.smpte.mxf.RandomIndexPack.PartitionOffset;
import com.sandflow.smpte.mxf.helpers.IndexSegmentHelper;
import com.sandflow.smpte.mxf.types.EssenceData;
import com.sandflow.smpte.mxf.types.FileDescriptor;
import com.sandflow.smpte.mxf.types.IndexEntry;
import com.sandflow.smpte.mxf.types.IndexEntryArray;
import com.sandflow.smpte.mxf.types.IndexTableSegment;
import com.sandflow.smpte.mxf.types.MaterialPackage;
import com.sandflow.smpte.mxf.types.MultipleDescriptor;
import com.sandflow.smpte.mxf.types.Package;
import com.sandflow.smpte.mxf.types.Preface;
import com.sandflow.smpte.mxf.types.Sequence;
import com.sandflow.smpte.mxf.types.SourceClip;
import com.sandflow.smpte.mxf.types.SourcePackage;
import com.sandflow.smpte.mxf.types.TimelineTrack;
import com.sandflow.smpte.mxf.types.Track;
import com.sandflow.smpte.util.AUID;
import com.sandflow.smpte.util.UL;
import com.sandflow.smpte.util.UMID;
import com.sandflow.smpte.util.UUID;

public class StreamingWriter {

  private abstract class ContainerWriter extends OutputStream {

    private final long bodySID;
    private final long indexSID;
    private long bytesToWrite;
    private long ecOffset = 0;

    /* TODO: warn if the body and index SIDs are not in the preface */

    ContainerWriter(long bodySID, long indexSID) {
      this.bodySID = bodySID;
      this.indexSID = indexSID;
    }

    long getIndexSID() {
      return this.indexSID;
    }

    long getBodySID() {
      return this.bodySID;
    }

    boolean isActive() {
      return StreamingWriter.this.currentContainer == this;
    }

    long getBytesToWrite() {
      return bytesToWrite;
    }

    /*
     * TODO: this is super ugly as it interferes with the ability of the GC to write
     * stuff to itself
     */
    void setBytesToWrite(long bytesToWrite) {
      this.bytesToWrite = bytesToWrite;
    }

    abstract byte[] drainIndexSegments() throws IOException;

    long getPosition() {
      return this.ecOffset;
    }

    void setPosition(long p) {
      this.ecOffset = p;
    }

    abstract long getDuration();

    abstract PartitionPack.Kind getPartitionKind();

    abstract PartitionPack.Status getPartitionStatus();

    @Override
    public void write(int b) throws IOException {
      if (!this.isActive()) {
        throw new RuntimeException();
      }
      if (this.bytesToWrite - 1 < 0)
        throw new RuntimeException();
      StreamingWriter.this.fos.write(b);
      this.bytesToWrite--;
      this.ecOffset++;
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
      if (!this.isActive()) {
        throw new RuntimeException();
      }
      if (this.bytesToWrite - len < 0) {
        throw new RuntimeException();
      }

      StreamingWriter.this.fos.write(b, off, len);
      this.bytesToWrite -= len;
      this.ecOffset += len;
    }

    @Override
    public void close() throws IOException {
      /*
       * do nothing: it is the responsibility of the caller to close the
       * underlying RandomAccessInputSource
       */
    }
  }

  class GCClipCBEWriter extends ContainerWriter {

    enum State {
      READY,
      WRITTEN,
      DRAINED
    }

    private long accessUnitSize;
    private long accessUnitCount;
    private State state = State.READY;

    public GCClipCBEWriter(long bodySID, long indexSID) {
      super(bodySID, indexSID);
    }

    public void nextClip(UL elementKey, long accessUnitSize, long accessUnitCount) throws IOException {
      /* TODO check parameter validity */

      if (!this.isActive()) {
        throw new RuntimeException();
      }

      if (this.state != State.READY) {
        throw new RuntimeException();
      }

      long clipSize = accessUnitCount * accessUnitSize;

      StreamingWriter.this.fos.writeUL(elementKey);
      StreamingWriter.this.fos.writeBERLength(clipSize);
      this.setBytesToWrite(clipSize);

      this.accessUnitCount = accessUnitCount;
      this.accessUnitSize = accessUnitSize;

      this.state = State.WRITTEN;
    }

    @Override
    long getDuration() {
      return this.accessUnitCount;
    }

    @Override
    byte[] drainIndexSegments() throws IOException {
      if (this.state != State.WRITTEN) {
        return null;
      }
      this.state = State.DRAINED;

      var its = new IndexTableSegment();
      its.InstanceID = UUID.fromRandom();
      its.IndexEditRate = StreamingWriter.this.getECEditRate(this.getBodySID());
      its.IndexStartPosition = 0L;
      its.IndexDuration = this.accessUnitCount;
      its.IndexStreamID = this.getIndexSID();
      its.EssenceStreamID = this.getBodySID();
      its.EditUnitByteCount = this.accessUnitSize;

      return IndexSegmentHelper.toBytes(its);
    }

    @Override
    PartitionPack.Kind getPartitionKind() {
      return PartitionPack.Kind.BODY;
    }

    @Override
    PartitionPack.Status getPartitionStatus() {
      return PartitionPack.Status.CLOSED_COMPLETE;
    }

  }

  class GSWriter extends ContainerWriter {

    public GSWriter(long bodySID) {
      super(bodySID, 0);
    }

    public void nextElement(UL elementKey, long elementLength) throws IOException {
      if (!this.isActive()) {
        throw new RuntimeException();
      }
      StreamingWriter.this.fos.writeUL(elementKey);
      StreamingWriter.this.fos.writeBERLength(elementLength);
      this.setBytesToWrite(elementLength);
    }

    @Override
    long getDuration() {
      throw new NotImplementedException();
    }

    @Override
    byte[] drainIndexSegments() throws IOException {
      return null;
    }

    @Override
    PartitionPack.Kind getPartitionKind() {
      return PartitionPack.Kind.BODY;
    }

    @Override
    PartitionPack.Status getPartitionStatus() {
      return PartitionPack.Status.STREAM;
    }

  }

  public class GCClipVBEWriter extends ContainerWriter {

    enum State {
      READY,
      WRITTEN,
      DRAINED
    }

    private State state = State.READY;

    private long clipSize;

    /**
     * offset in bytes of the VBE units within the essence container
     */
    private List<Long> auOffsets = new ArrayList<>();

    GCClipVBEWriter(long bodySID, long indexSID) {
      super(bodySID, indexSID);
    }

    public void nextClip(UL elementKey, long clipSize) throws IOException {
      /* TODO check parameter validity */

      if (!this.isActive()) {
        throw new RuntimeException();
      }

      if (this.state != State.READY) {
        throw new RuntimeException();
      }

      if (StreamingWriter.this.preface.EssenceContainers != null
          && StreamingWriter.this.preface.EssenceContainers.contains(Labels.IMF_IABEssenceClipWrappedContainer)) {
        /**
         * EXCPTION: ASDCPLib incorrectly includes the Clip KL in the essence container
         * offset for IAB files
         */
        this.setBytesToWrite(50);
        MXFOutputStream mos = new MXFOutputStream(this);
        mos.writeUL(elementKey);
        mos.writeBERLength(clipSize);
        mos.flush();
        mos.close();
      } else {
        StreamingWriter.this.fos.writeUL(elementKey);
        StreamingWriter.this.fos.writeBERLength(clipSize);
      }

      this.setBytesToWrite(clipSize);

      this.state = State.WRITTEN;
    }

    public void nextAccessUnit() {
      auOffsets.add(this.getPosition());
    }

    @Override
    long getDuration() {
      return this.auOffsets.size();
    }

    @Override
    byte[] drainIndexSegments() throws IOException {
      if (this.state != State.WRITTEN) {
        return null;
      }
      this.state = State.DRAINED;

      var its = new IndexTableSegment();
      its.InstanceID = UUID.fromRandom();
      its.IndexEditRate = StreamingWriter.this.getECEditRate(this.getBodySID());
      its.IndexStartPosition = 0L;
      its.IndexDuration = this.getDuration();
      its.IndexStreamID = this.getIndexSID();
      its.EssenceStreamID = this.getBodySID();
      its.VBEByteCount = clipSize - this.auOffsets.get(this.auOffsets.size() - 1);

      its.IndexEntryArray = new IndexEntryArray();
      for (var auOffset : this.auOffsets) {
        var e = new IndexEntry();
        e.TemporalOffset = 0;
        e.Flags = (byte) 0x80;
        e.StreamOffset = auOffset;
        e.KeyFrameOffset = 0;
        e.TemporalOffset = 0;

        its.IndexEntryArray.add(e);
      }

      return IndexSegmentHelper.toBytes(its);
    }

    @Override
    PartitionPack.Kind getPartitionKind() {
      return PartitionPack.Kind.BODY;
    }

    @Override
    PartitionPack.Status getPartitionStatus() {
      return PartitionPack.Status.CLOSED_COMPLETE;
    }

  }

  public class GCFrameVBEWriter extends ContainerWriter {

    /*
     * position of content packages within the essence container since the last
     * index table was drained
     */
    private final List<Long> cpPositions = new ArrayList<>();

    /*
     * duration of the generic container
     */
    private long duration;

    /*
     * index in edit unit of the first content package within the essence
     * container since the last index table was drained
     */
    private long cpFirstEditUnit = 0;

    GCFrameVBEWriter(long bodySID, long indexSID) {
      super(bodySID, indexSID);
    }

    public void nextContentPackage() {
      cpPositions.add(this.getPosition());
      duration++;
    }

    public void nextElement(UL elementKey, long elementSize) throws IOException {
      /* TODO check parameter validity */

      if (!this.isActive()) {
        throw new RuntimeException();
      }

      MXFOutputStream mos = new MXFOutputStream(StreamingWriter.this.fos);
      mos.writeUL(elementKey);
      mos.writeBERLength(elementSize);
      this.setPosition(this.getPosition() + mos.getWrittenCount());
      mos.close();

      this.setBytesToWrite(elementSize);
    }

    @Override
    long getDuration() {
      return duration;
    }

    @Override
    byte[] drainIndexSegments() throws IOException {
      if (this.cpPositions.size() == 0) {
        return null;
      }

      var its = new IndexTableSegment();
      its.InstanceID = UUID.fromRandom();
      its.IndexEditRate = StreamingWriter.this.getECEditRate(this.getBodySID());
      its.IndexStartPosition = cpFirstEditUnit;
      its.IndexDuration = (long) this.cpPositions.size();
      its.IndexStreamID = this.getIndexSID();
      its.EssenceStreamID = this.getBodySID();
      its.VBEByteCount = this.getPosition() - this.cpPositions.get(this.cpPositions.size() - 1);

      its.IndexEntryArray = new IndexEntryArray();
      for (var position : this.cpPositions) {
        var e = new IndexEntry();
        e.TemporalOffset = 0;
        e.Flags = (byte) 0x80;
        e.StreamOffset = position;
        e.KeyFrameOffset = 0;
        e.TemporalOffset = 0;

        its.IndexEntryArray.add(e);
      }

      this.cpFirstEditUnit = this.duration;
      this.cpPositions.clear();

      return IndexSegmentHelper.toBytes(its);
    }

    @Override
    PartitionPack.Kind getPartitionKind() {
      return PartitionPack.Kind.BODY;
    }

    @Override
    PartitionPack.Status getPartitionStatus() {
      return PartitionPack.Status.CLOSED_COMPLETE;
    }

  }

  /* TODO: clean-up states */
  private enum State {
    INIT,
    START,
    CONTAINER,
    ELEMENT,
    DONE
  }

  private final MXFOutputStream fos;

  private State state = State.INIT;
  private RandomIndexPack rip = new RandomIndexPack();
  private final java.util.Set<Long> sids = new HashSet<>();
  private final java.util.Map<Long, ContainerWriter> ecs = new HashMap<>();
  private ContainerWriter currentContainer;
  private final Preface preface;

  /**
   * current partition
   */
  private PartitionPack curPartition;

  public StreamingWriter(OutputStream os, Preface preface) throws IOException, KLVException {
    if (os == null) {
      throw new IllegalArgumentException("Output stream must not be null");
    }
    this.fos = new MXFOutputStream(os);

    if (preface == null) {
      throw new RuntimeException();
    }

    this.preface = preface;
  }

  private UL getOP() {
    if (!this.preface.OperationalPattern.isUL()) {
      throw new RuntimeException();
    }
    return this.preface.OperationalPattern.asUL();
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

    if (!t.isPresent())
      return null;

    return ((TimelineTrack) t.get()).EditRate;
  }

  private Package getPackageByID(UMID id) {
    return this.preface.ContentStorageObject.Packages.stream()
        .filter(p -> id == p.PackageID)
        .findFirst()
        .orElse(null);
  }

  private SourcePackage getPackageBySID(long sid) {
    return (SourcePackage) this.preface.ContentStorageObject.EssenceDataObjects.stream()
        .filter(e -> e.EssenceStreamID == sid)
        .map(e -> getPackageByID(e.LinkedPackageID))
        .findFirst()
        .orElse(null);
  }

  private List<MaterialPackage> getMaterialPackages() {
    return this.preface.ContentStorageObject.Packages.stream()
        .filter(p -> p instanceof MaterialPackage).map(e -> (MaterialPackage) e)
        .toList();
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
   * @throws IOException
   * @throws KLVException
   */
  public void start() throws IOException, KLVException {
    if (this.state != State.INIT) {
      throw new RuntimeException();
    }

    /* TODO: fill in ecLabels */

    /* serialize the header metadata */
    byte[] hmb = serializePreface(this.preface);

    /* write the header partition */
    startPartition(0, 0, hmb.length, 0, 0L, PartitionPack.Kind.HEADER, PartitionPack.Status.OPEN_INCOMPLETE);
    this.fos.write(hmb);

    this.state = State.START;
  }

  void startPartition(ContainerWriter cw) throws IOException, KLVException {
    if (cw == null) {
      throw new RuntimeException();
    }

    this.closeCurrentPartition();

    /* start a new partition */
    startPartition(cw.getBodySID(), 0, 0, 0, cw.getPosition(), cw.getPartitionKind(),
        cw.getPartitionStatus());

    this.currentContainer = cw;
  }

  private void closeCurrentPartition() throws IOException, KLVException {
    if (this.currentContainer == null) {
      return;
    }

    /* are we done with the current partition? */
    /* TODO: replace with state check */
    if (this.currentContainer.getBytesToWrite() != 0) {
      throw new RuntimeException();
    }
    /* do we need to create an index partition for the current essence container */
    if (this.currentContainer.getIndexSID() != 0) {
      this.writeIndexPartition();
    }
  }

  /**
   * creates a clip-wrapped essence container with constant size units
   * 
   */
  public GCClipCBEWriter addCBEClipWrappedGC(long bodySID, long indexSID)
      throws IOException, KLVException {
    /* TODO: check for valid SIDs */

    if (this.sids.contains(bodySID)) {
      throw new RuntimeException();
    }

    if (this.sids.contains(indexSID)) {
      throw new RuntimeException();
    }

    /* TODO: check for valid information in the preface */

    this.sids.add(bodySID);
    this.sids.add(indexSID);

    GCClipCBEWriter w = new GCClipCBEWriter(bodySID, indexSID);

    this.ecs.put(bodySID, w);

    return w;
  }

  /**
   * creates a clip-wrapped essence container with variable size access units
   * 
   */
  public GCClipVBEWriter addVBEClipWrappedGC(long bodySID, long indexSID)
      throws IOException, KLVException {
    /* TODO: check for valid SIDs */

    if (this.sids.contains(bodySID)) {
      throw new RuntimeException();
    }

    if (this.sids.contains(indexSID)) {
      throw new RuntimeException();
    }

    /* TODO: check for valid information in the preface */

    this.sids.add(bodySID);
    this.sids.add(indexSID);

    GCClipVBEWriter w = new GCClipVBEWriter(bodySID, indexSID);

    this.ecs.put(bodySID, w);

    return w;
  }

  /**
   * creates a framed-wrapped essence container with variable size access units
   * 
   */
  public GCFrameVBEWriter addVBEFrameWrappedGC(long bodySID, long indexSID)
      throws IOException, KLVException {
    /* TODO: check for valid SIDs */

    if (this.sids.contains(bodySID)) {
      throw new RuntimeException();
    }

    if (this.sids.contains(indexSID)) {
      throw new RuntimeException();
    }

    /* TODO: check for valid information in the preface */

    this.sids.add(bodySID);
    this.sids.add(indexSID);

    GCFrameVBEWriter w = new GCFrameVBEWriter(bodySID, indexSID);

    this.ecs.put(bodySID, w);

    return w;
  }

  /**
   * creates a generic stream container
   * 
   */
  public GSWriter addGenericStream(long bodySID)
      throws IOException, KLVException {
    /* TODO: check for valid SIDs */

    if (this.sids.contains(bodySID)) {
      throw new RuntimeException();
    }

    this.sids.add(bodySID);

    GSWriter w = new GSWriter(bodySID);

    this.ecs.put(bodySID, w);

    return w;
  }

  /**
   * Finish the file. No further writing is possible afterwards.
   * 
   * @throws IOException
   * @throws KLVException
   */
  public void finish() throws IOException, KLVException {
    this.closeCurrentPartition();

    /* update header metadata */
    for (ContainerWriter cw : this.ecs.values()) {
      SourcePackage sp = getPackageBySID(cw.getBodySID());

      if (sp == null) {
        continue;
      }

      FileDescriptor fd = (FileDescriptor) sp.EssenceDescription;

      fd.EssenceLength = cw.getDuration();

      if (fd instanceof MultipleDescriptor) {
        for (FileDescriptor cfd : ((MultipleDescriptor) fd).FileDescriptors) {
          cfd.EssenceLength = cw.getDuration();
        }
      }

      for (var t : sp.PackageTracks) {
        t.TrackSegment.ComponentLength = cw.getDuration();

        if (!(t.TrackSegment instanceof Sequence))
          continue;

        Sequence sq = (Sequence) t.TrackSegment;

        for (var co : sq.ComponentObjects) {
          co.ComponentLength = cw.getDuration();
        }
      }

      /* look for material package tracks that reference the source package */

      for (var mp : getMaterialPackages()) {
        for (var t : mp.PackageTracks) {
          if (!(t instanceof TimelineTrack))
            continue;

          TimelineTrack tt = (TimelineTrack) t;

          if (!(tt.TrackSegment instanceof Sequence))
            continue;

          tt.TrackSegment.ComponentLength = cw.getDuration();

          Sequence sq = (Sequence) tt.TrackSegment;

          for (var co : sq.ComponentObjects) {
            if (!(co instanceof SourceClip))
              continue;

            SourceClip sc = (SourceClip) co;

            if (sc.SourcePackageID.equals(sp.PackageID)) {
              sc.ComponentLength = cw.getDuration();
            }
          }
        }
      }
    }

    /* header metadata */
    byte[] headerbytes = serializePreface(this.preface);

    /* write the footer partition */
    startPartition(0, 0, headerbytes.length, 0, 0, PartitionPack.Kind.FOOTER, PartitionPack.Status.CLOSED_COMPLETE);
    fos.write(headerbytes);

    /* write the RIP */
    this.rip.toStream(fos);

    fos.flush();

    this.state = State.DONE;
  }

  /**
   * PRIVATE API
   */

  private byte[] serializePreface(Preface preface) throws IOException {
    /* write */
    LocalTagRegister reg = new LocalTagRegister();
    LinkedList<Set> sets = new LinkedList<>();
    MXFOutputContext ctx = new MXFOutputContext() {

      long nextDynamicTag = 0x8000L;

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

        /* allocate dynamic tags */

        for (Triplet t : set.getItems()) {
          Long localTag = reg.getLocalTag(t.getKey());

          if (localTag == null) {
            localTag = StaticLocalTags.register().getLocalTag(t.getKey());

            if (localTag == null) {
              localTag = nextDynamicTag++;
            }

            reg.add(localTag, t.getKey());
          }
        }

      }

    };

    /* collect the header metadata sets */
    this.preface.toSet(ctx);

    /* serialize the header */
    LocalTagResolver tags = new LocalTagResolver() {

      @Override
      public Long getLocalTag(AUID auid) {
        Long localTag = reg.getLocalTag(auid);

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
    pp.setOperationalPattern(this.getOP());
    pp.setEssenceContainers(this.getECLabels());
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

  private void writeIndexPartition() throws IOException, KLVException {
    byte[] itsBytes = this.currentContainer.drainIndexSegments();
    if (itsBytes == null) {
      return;
    }

    startPartition(
        0,
        this.currentContainer.getIndexSID(),
        0L,
        (long) itsBytes.length,
        0,
        PartitionPack.Kind.BODY,
        PartitionPack.Status.CLOSED_COMPLETE);
    fos.write(itsBytes);
  }

  /**
   * GETTERS/SETTERS
   */

  public boolean isDone() {
    return this.state == State.DONE;
  }

}
