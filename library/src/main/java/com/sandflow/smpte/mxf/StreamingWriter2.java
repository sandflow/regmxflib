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
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Optional;
import java.util.function.Consumer;

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
import com.sandflow.smpte.mxf.types.IndexTableSegment;
import com.sandflow.smpte.mxf.types.MultipleDescriptor;
import com.sandflow.smpte.mxf.types.Package;
import com.sandflow.smpte.mxf.types.Preface;
import com.sandflow.smpte.mxf.types.SourcePackage;
import com.sandflow.smpte.mxf.types.TimelineTrack;
import com.sandflow.smpte.mxf.types.Track;
import com.sandflow.smpte.util.AUID;
import com.sandflow.smpte.util.UL;
import com.sandflow.smpte.util.UMID;
import com.sandflow.smpte.util.UUID;

public class StreamingWriter2 {

  private abstract class ContainerWriter extends OutputStream {

    private final long bodySID;
    private final long indexSID;
    private long bytesToWrite;

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
      return StreamingWriter2.this.currentContainer == this;
    }

    long getBytesToWrite() {
      return bytesToWrite;
    }

    void setBytesToWrite(long bytesToWrite) {
      this.bytesToWrite = bytesToWrite;
    }

    abstract byte[] drainIndexSegments() throws IOException;

    abstract long getECOffset();

    abstract long getDuration();

    @Override
    public void write(int b) throws IOException {
      if (!this.isActive()) {
        throw new RuntimeException();
      }
      if (this.bytesToWrite - 1 < 0)
        throw new RuntimeException();
      StreamingWriter2.this.fos.write(b);
      this.bytesToWrite--;
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
      if (!this.isActive()) {
        throw new RuntimeException();
      }
      if (this.bytesToWrite - len < 0) {
        throw new RuntimeException();
      }

      StreamingWriter2.this.fos.write(b, off, len);
      this.bytesToWrite -= len;
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

    GCClipCBEWriter(long bodySID, long indexSID) {
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

      StreamingWriter2.this.fos.writeUL(elementKey);
      StreamingWriter2.this.fos.writeBERLength(clipSize);
      this.setBytesToWrite(clipSize);

      this.accessUnitCount = accessUnitCount;
      this.accessUnitSize = accessUnitSize;

      this.state = State.WRITTEN;
    }

    @Override
    long getECOffset() {
      return 0L;
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
      its.IndexEditRate = StreamingWriter2.this.getECEditRate(this.getBodySID());
      its.IndexStartPosition = 0L;
      its.IndexDuration = this.accessUnitCount;
      its.IndexStreamID = this.getIndexSID();
      its.EssenceStreamID = this.getBodySID();
      its.EditUnitByteCount = this.accessUnitSize;

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
  private RandomIndexPack rip = new RandomIndexPack();
  private final java.util.Set<Long> sids = new HashSet<>();
  private final java.util.Map<Long, ContainerWriter> ecs = new HashMap<>();
  private ContainerWriter currentContainer;
  private final Preface preface;

  /**
   * current partition
   */
  private PartitionPack curPartition;

  public StreamingWriter2(OutputStream os, Preface preface) throws IOException, KLVException {
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
    byte[] hmb = serializeHeaderMetadata(this.preface);

    /* write the header partition */
    startPartition(0, 0, hmb.length, 0, 0L, PartitionPack.Kind.HEADER, PartitionPack.Status.OPEN_INCOMPLETE);
    this.fos.write(hmb);

    this.state = State.START;
  }

  public ContainerWriter startPartition(long sid) throws IOException, KLVException {
    this.closeCurrentPartition();

    ContainerWriter cw = this.ecs.get(sid);
    if (cw == null) {
      throw new RuntimeException();
    }

    /* start a new partition */
    startPartition(sid, cw.getIndexSID(), 0, 0, cw.getECOffset(), PartitionPack.Kind.BODY,
        PartitionPack.Status.CLOSED_COMPLETE);

    this.currentContainer = cw;

    return cw;
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
   * @param unitCount Number of units in the essence container
   * @param unitSize  Size in bytes of each element
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
      }
    }

    /* header metadata */
    byte[] headerbytes = serializeHeaderMetadata(this.preface);

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

  private byte[] serializeHeaderMetadata(Preface preface) throws IOException {
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
    startPartition(
        this.currentContainer.getBodySID(),
        this.currentContainer.getIndexSID(),
        0L,
        (long) itsBytes.length,
        this.currentContainer.getECOffset(),
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
