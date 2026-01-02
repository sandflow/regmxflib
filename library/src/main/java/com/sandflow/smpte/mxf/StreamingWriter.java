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
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import org.apache.commons.lang3.NotImplementedException;
import org.apache.commons.numbers.fraction.Fraction;

import com.sandflow.smpte.klv.LocalTagRegister;
import com.sandflow.smpte.klv.LocalTagResolver;
import com.sandflow.smpte.klv.Set;
import com.sandflow.smpte.klv.Triplet;
import com.sandflow.smpte.klv.exceptions.KLVException;
import com.sandflow.smpte.mxf.RandomIndexPack.PartitionOffset;
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
import com.sandflow.util.events.Event;
import com.sandflow.util.events.EventHandler;

/**
 * Writes an MXF file sequentially (streaming).
 */
public class StreamingWriter {

  private abstract class ContainerWriter extends OutputStream {

    enum State {
      READY,
      WRITING
    }

    private final long bodySID;
    private final long indexSID;
    private long bytesToWrite;
    private long ecOffset = 0;
    private State state = State.READY;

    ContainerWriter(long bodySID, long indexSID) {
      this.bodySID = bodySID;
      this.indexSID = indexSID;
    }

    State getState() {
      return this.state;
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

    boolean isWriting() {
      return this.state.equals(State.WRITING);
    }

    void startWriting(long bytesToWrite) {
      if (this.state != State.READY) {
        throw new IllegalStateException("ContainerWriter is not in READY state");
      }
      this.bytesToWrite = bytesToWrite;
      this.state = State.WRITING;
    }

    abstract byte[] drainIndexSegments() throws IOException, MXFException;

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
        throw new IllegalStateException("ContainerWriter is not active");
      }
      if (this.state != State.WRITING) {
        throw new IllegalStateException("ContainerWriter is not in WRITING state");
      }
      if (this.bytesToWrite - 1 < 0)
        throw new EOFException("Attempting to write more bytes than allocated to the container");
      StreamingWriter.this.fos.write(b);
      this.bytesToWrite--;
      this.ecOffset++;

      if (this.bytesToWrite == 0) {
        this.state = State.READY;
      }
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
      if (!this.isActive()) {
        throw new IllegalStateException("ContainerWriter is not active");
      }
      if (this.state != State.WRITING) {
        throw new IllegalStateException("ContainerWriter is not in WRITING state");
      }
      if (this.bytesToWrite - len < 0) {
        throw new EOFException("Attempting to write more bytes than allocated to the container");
      }

      StreamingWriter.this.fos.write(b, off, len);
      this.bytesToWrite -= len;
      this.ecOffset += len;

      if (this.bytesToWrite == 0) {
        this.state = State.READY;
      }
    }

    @Override
    public void close() throws IOException {
      /*
       * do nothing: it is the responsibility of the caller to close the
       * underlying RandomAccessInputSource
       */
    }

    static byte[] serializeIndexTableSegment(IndexTableSegment its, EventHandler evthandler)
        throws IOException, MXFException {
      /* serialize the index table segment */

      /*
       * The AtomicReference is necessary since the variable is initialized in the
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
            throw new RuntimeException("Serializing an Index Table Segment should not require more than one Set");
          }
          ars.set(set);
        }

        @Override
        public void handleEvent(Event evt) throws MXFException {
          MXFException.handle(evthandler, evt);
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
            throw new ArrayIndexOutOfBoundsException("No local tag found for AUID " + auid);
          }
          return localTag;
        }

        @Override
        public AUID getAUID(long localtag) {
          throw new UnsupportedOperationException(
              "Serializing an Index Table Segment should not require resolving a local tag to an AUID");
        }

      };

      ByteArrayOutputStream bos = new ByteArrayOutputStream();
      MXFDataOutput mos = new MXFDataOutput(bos);
      Set.toStreamAsLocalSet(ars.get(), tags, mos);
      mos.flush();

      return bos.toByteArray();
    }
  }

  /**
   * Writer for CBE clip-wrapped essence.
   */
  class GCClipCBEWriter extends ContainerWriter {

    private long accessUnitSize;
    private long accessUnitCount;
    private boolean indexTableFilled = false;

    public GCClipCBEWriter(long bodySID, long indexSID) {
      super(bodySID, indexSID);
    }

    /**
     * Writes the next clip.
     * 
     * @param elementKey      Key of the element.
     * @param accessUnitSize  Size of each access unit.
     * @param accessUnitCount Number of access units.
     * @throws IOException If an I/O error occurs.
     */
    public void nextClip(UL elementKey, long accessUnitSize, long accessUnitCount) throws IOException {
      if (elementKey == null) {
        throw new IllegalArgumentException("Element Key cannot be null");
      }

      if (accessUnitCount < 0) {
        throw new IllegalArgumentException("Access Unit Count cannot be negative");
      }

      if (accessUnitSize <= 0) {
        throw new IllegalArgumentException("Access Unit Size must be greater than 0");
      }

      if (!this.isActive()) {
        throw new IllegalStateException("ContainerWriter is not active");
      }

      if (this.getState() != State.READY) {
        throw new IllegalStateException("ContainerWriter is not ready for the next clip");
      }

      long clipSize = accessUnitCount * accessUnitSize;

      StreamingWriter.this.fos.writeUL(elementKey);
      StreamingWriter.this.fos.writeBERLength(clipSize);
      this.startWriting(clipSize);

      this.accessUnitCount = accessUnitCount;
      this.accessUnitSize = accessUnitSize;
      this.indexTableFilled = true;
    }

    @Override
    long getDuration() {
      return this.accessUnitCount;
    }

    @Override
    byte[] drainIndexSegments() throws IOException, MXFException {
      if (!this.indexTableFilled) {
        return null;
      }
      this.indexTableFilled = false;

      var its = new IndexTableSegment();
      its.InstanceID = StreamingWriter.this.uidGenerator.generate(this);
      its.IndexEditRate = StreamingWriter.this.getECEditRate(this.getBodySID());
      its.IndexStartPosition = 0L;
      its.IndexDuration = this.accessUnitCount;
      its.IndexStreamID = this.getIndexSID();
      its.EssenceStreamID = this.getBodySID();
      its.EditUnitByteCount = this.accessUnitSize;

      return serializeIndexTableSegment(its, StreamingWriter.this.evthandler);
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

  /**
   * Writer for Generic Stream.
   */
  class GSWriter extends ContainerWriter {

    public GSWriter(long bodySID) {
      super(bodySID, 0);
    }

    /**
     * Writes the next element.
     * 
     * @param elementKey    Key of the element.
     * @param elementLength Length of the element.
     * @throws IOException If an I/O error occurs.
     */
    public void nextElement(UL elementKey, long elementLength) throws IOException {
      if (!this.isActive()) {
        throw new IllegalStateException("ContainerWriter is not active");
      }
      StreamingWriter.this.fos.writeUL(elementKey);
      StreamingWriter.this.fos.writeBERLength(elementLength);
      this.startWriting(elementLength);
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

  /**
   * Writer for VBE clip-wrapped essence.
   */
  public class GCClipVBEWriter extends ContainerWriter {

    enum State {
      READY,
      WRITTEN,
      DRAINED
    }

    private State state = State.READY;

    /**
     * offset in bytes of the VBE units within the essence container
     */
    private List<Long> auOffsets = new ArrayList<>();

    GCClipVBEWriter(long bodySID, long indexSID) {
      super(bodySID, indexSID);
    }

    /**
     * Writes the next clip.
     * 
     * @param elementKey Key of the element.
     * @param clipSize   Size of the clip.
     * @throws IOException If an I/O error occurs.
     */
    public void nextClip(UL elementKey, long clipSize) throws IOException {
      Objects.requireNonNull(elementKey, "Element Key cannot be null");

      if (clipSize < 0) {
        throw new IllegalArgumentException("Clip size cannot be negative");
      }

      if (!this.isActive()) {
        throw new IllegalStateException("ContainerWriter is not active");
      }

      if (this.state != State.READY) {
        throw new IllegalStateException("ContainerWriter is not ready for the next clip");
      }

      if (StreamingWriter.this.preface.EssenceContainers != null
          && StreamingWriter.this.preface.EssenceContainers.contains(Labels.IMF_IABEssenceClipWrappedContainer)) {
        /**
         * EXCEPTION: ASDCPLib incorrectly includes the Clip KL in the essence container
         * offset for IAB files
         */
        long curPos = StreamingWriter.this.fos.getWrittenCount();
        StreamingWriter.this.fos.writeUL(elementKey);
        StreamingWriter.this.fos.writeBERLength(clipSize);
        this.setPosition(this.getPosition() + StreamingWriter.this.fos.getWrittenCount() - curPos);
      } else {
        StreamingWriter.this.fos.writeUL(elementKey);
        StreamingWriter.this.fos.writeBERLength(clipSize);
      }

      this.startWriting(clipSize);

      this.state = State.WRITTEN;
    }

    /**
     * Marks the start of the next access unit.
     */
    public void nextAccessUnit() {
      auOffsets.add(this.getPosition());
    }

    @Override
    long getDuration() {
      return this.auOffsets.size();
    }

    /**
     * Ensure that each index segment does not contain more than MAX_INDEX_ENTRIES
     * entries so that its size is less than 65kB
     */
    private final static int MAX_INDEX_ENTRIES = 5000;

    @Override
    byte[] drainIndexSegments() throws IOException, MXFException {
      if (this.state != State.WRITTEN) {
        return null;
      }
      this.state = State.DRAINED;

      ByteArrayOutputStream bos = new ByteArrayOutputStream();

      /* each index table segment contains at most MAX_INDEX_ENTRIES entries */
      int numSegments = (this.auOffsets.size() + MAX_INDEX_ENTRIES - 1) / MAX_INDEX_ENTRIES;

      for (int segIndex = 0; segIndex < numSegments; segIndex++) {
        int startIndex = segIndex * MAX_INDEX_ENTRIES;
        int endIndex = Math.min((segIndex + 1) * MAX_INDEX_ENTRIES, this.auOffsets.size());

        var its = new IndexTableSegment();
        its.InstanceID = StreamingWriter.this.uidGenerator.generate(this);
        its.IndexEditRate = StreamingWriter.this.getECEditRate(this.getBodySID());
        its.IndexStartPosition = (long) startIndex;
        its.IndexDuration = (long) (endIndex - startIndex);
        its.IndexStreamID = this.getIndexSID();
        its.EssenceStreamID = this.getBodySID();
        its.VBEByteCount = segIndex + 1 == numSegments
            ? this.getPosition() - this.auOffsets.get(this.auOffsets.size() - 1)
            : this.auOffsets.get(endIndex) - this.auOffsets.get(endIndex - 1);

        its.IndexEntryArray = new IndexEntryArray();
        for (int i = startIndex; i < endIndex; i++) {
          var e = new IndexEntry();
          e.TemporalOffset = 0;
          e.Flags = (byte) 0x80;
          e.StreamOffset = this.auOffsets.get(i);
          e.KeyFrameOffset = 0;
          e.TemporalOffset = 0;

          its.IndexEntryArray.add(e);
        }

        bos.write(serializeIndexTableSegment(its, StreamingWriter.this.evthandler));
      }

      return bos.toByteArray();
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

  /**
   * Writer for VBE frame-wrapped essence.
   */
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

    /**
     * Marks the start of the next content package.
     */
    public void nextContentPackage() {
      cpPositions.add(this.getPosition());
      duration++;
    }

    /**
     * Writes the next element.
     * 
     * @param elementKey  Key of the element.
     * @param elementSize Size of the element.
     * @throws IOException If an I/O error occurs.
     */
    public void nextElement(UL elementKey, long elementSize) throws IOException {
      Objects.requireNonNull(elementKey, "Element Key cannot be null");

      if (elementSize < 0) {
        throw new IllegalArgumentException("Element size cannot be negative");
      }

      if (!this.isActive()) {
        throw new IllegalStateException("ContainerWriter is not active");
      }

      long curPos = StreamingWriter.this.fos.getWrittenCount();
      StreamingWriter.this.fos.writeUL(elementKey);
      StreamingWriter.this.fos.writeBERLength(elementSize);
      this.setPosition(this.getPosition() + StreamingWriter.this.fos.getWrittenCount() - curPos);

      this.startWriting(elementSize);
    }

    @Override
    long getDuration() {
      return duration;
    }

    /**
     * Ensure that each index segment does not contain more than MAX_INDEX_ENTRIES
     * entries so that its size is less than 65kB
     */
    private final static int MAX_INDEX_ENTRIES = 5000;

    @Override
    byte[] drainIndexSegments() throws IOException, MXFException {
      if (this.cpPositions.size() == 0) {
        return null;
      }

      ByteArrayOutputStream bos = new ByteArrayOutputStream();

      /* each index table segment contains at most MAX_INDEX_ENTRIES entries */
      int numSegments = (this.cpPositions.size() + MAX_INDEX_ENTRIES - 1) / MAX_INDEX_ENTRIES;

      for (int segIndex = 0; segIndex < numSegments; segIndex++) {
        int startIndex = segIndex * MAX_INDEX_ENTRIES;
        int endIndex = Math.min((segIndex + 1) * MAX_INDEX_ENTRIES, this.cpPositions.size());

        var its = new IndexTableSegment();
        its.InstanceID = StreamingWriter.this.uidGenerator.generate(this);
        its.IndexEditRate = StreamingWriter.this.getECEditRate(this.getBodySID());
        its.IndexStartPosition = cpFirstEditUnit + startIndex;
        its.IndexDuration = (long) (endIndex - startIndex);
        its.IndexStreamID = this.getIndexSID();
        its.EssenceStreamID = this.getBodySID();
        its.VBEByteCount = segIndex + 1 == numSegments ? this.getPosition() - this.cpPositions.get(endIndex - 1)
            : this.cpPositions.get(endIndex) - this.cpPositions.get(endIndex - 1);

        its.IndexEntryArray = new IndexEntryArray();
        for (int i = startIndex; i < endIndex; i++) {
          var e = new IndexEntry();
          e.TemporalOffset = 0;
          e.Flags = (byte) 0x80;
          e.StreamOffset = this.cpPositions.get(i);
          e.KeyFrameOffset = 0;
          e.TemporalOffset = 0;

          its.IndexEntryArray.add(e);
        }

        bos.write(serializeIndexTableSegment(its, StreamingWriter.this.evthandler));
      }

      this.cpFirstEditUnit = this.duration;
      this.cpPositions.clear();

      return bos.toByteArray();
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

  private enum State {
    INIT,
    STARTED,
    DONE
  }

  private final MXFDataOutput fos;

  private State state = State.INIT;
  private RandomIndexPack rip = new RandomIndexPack();
  private final java.util.Set<Long> sids = new HashSet<>();
  private final java.util.Map<Long, ContainerWriter> ecs = new HashMap<>();
  private ContainerWriter currentContainer;
  private final Preface preface;
  private final EventHandler evthandler;
  private java.util.Set<UL> ecLabels;

  /**
   * current partition
   */
  private PartitionPack curPartition;

  /**
   * IntanceID generator
   */
  private final UIDGenerator uidGenerator;

  /**
   * Instantiates a StreamingWriter. The StreamingWriter makes a copy of the
   * provided preface.
   * 
   * @param os         Output stream to write the MXF file to.
   * @param preface    Preface set of the MXF file.
   * @param evthandler Handler for events generated during writing.
   * @throws IOException  If an I/O error occurs.
   * @throws KLVException If a KLV error occurs.
   * @throws MXFException If an MXF error occurs.
   */
  public StreamingWriter(OutputStream os, Preface preface, EventHandler evthandler)
      throws IOException, KLVException, MXFException {
    this(os, preface, evthandler, new Class4UIDGenerator());
  }

  /**
   * Instantiates a StreamingWriter with a custom UID generator. The
   * StreamingWriter makes a copy of the provided preface.
   * 
   * @param os         Output stream to write the MXF file to.
   * @param preface    Preface set of the MXF file.
   * @param evthandler Handler for events generated during writing.
   * @param uidg       Generator for InstanceIDs.
   * @throws IOException  If an I/O error occurs.
   * @throws KLVException If a KLV error occurs.
   * @throws MXFException If an MXF error occurs.
   */
  public StreamingWriter(OutputStream os, Preface preface, EventHandler evthandler, UIDGenerator uidg)
      throws IOException, KLVException, MXFException {
    if (os == null) {
      throw new IllegalArgumentException("Output stream must not be null");
    }
    this.fos = new MXFDataOutput(os);

    if (preface == null) {
      throw new IllegalArgumentException("Preface must not be null");
    }

    if (!preface.OperationalPattern.isUL()) {
      throw new MXFException("The Operational Pattern label found in the Preface is not a UL");
    }

    if (uidg == null) {
      throw new IllegalArgumentException("UID generator must not be null");
    }
    this.uidGenerator = uidg;

    this.preface = preface.copyOf();

    this.evthandler = evthandler;
  }

  private UL getOP() {
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
  /**
   * Writes the header partition.
   *
   * @throws IOException  If an I/O error occurs.
   * @throws KLVException If a KLV error occurs.
   * @throws MXFException If an MXF error occurs.
   */
  public void start() throws IOException, KLVException, MXFException {
    if (this.state != State.INIT) {
      throw new IllegalStateException("StreamingWriter has already been started");
    }

    this.ecLabels = getECLabels();

    /* serialize the header metadata */
    byte[] hmb = serializePreface(this.preface);

    /* write the header partition */
    startPartition(0, 0, hmb.length, 0, 0L, PartitionPack.Kind.HEADER, PartitionPack.Status.OPEN_INCOMPLETE);
    this.fos.write(hmb);

    this.state = State.STARTED;
  }

  void startPartition(ContainerWriter cw) throws IOException, KLVException, MXFException {
    if (cw == null) {
      throw new IllegalArgumentException("ContainerWriter cannot be null");
    }
    if (this.state != State.STARTED) {
      throw new IllegalStateException("StreamingWriter has not been started");
    }

    this.closeCurrentPartition();

    /* start a new partition */
    startPartition(cw.getBodySID(), 0, 0, 0, cw.getPosition(), cw.getPartitionKind(),
        cw.getPartitionStatus());

    this.currentContainer = cw;
  }

  private void closeCurrentPartition() throws IOException, KLVException, MXFException {
    if (this.currentContainer == null) {
      return;
    }

    /* are we done with the current partition? */
    if (this.currentContainer.isWriting()) {
      throw new IllegalStateException("The current partition cannot be closed because it is still writing");
    }
    /* do we need to create an index partition for the current essence container */
    if (this.currentContainer.getIndexSID() != 0) {
      this.writeIndexPartition();
    }
  }

  private void addGC(long bodySID, long indexSID, ContainerWriter cw) throws MXFException {
    if (this.state != State.INIT) {
      throw new IllegalStateException("StreamingWriter has already been started");
    }
    if (bodySID <= 0 || indexSID <= 0) {
      throw new IllegalArgumentException("bodySID and indexSID must be larger than 0");
    }

    if (this.sids.contains(bodySID)) {
      throw new RuntimeException(String.format("BodySID %d is already registered.", bodySID));
    }

    if (this.sids.contains(indexSID)) {
      throw new RuntimeException(String.format("IndexSID %d is already registered.", indexSID));
    }

    List<EssenceData> gcs = this.preface.ContentStorageObject.EssenceDataObjects.stream()
        .filter(e -> e.EssenceStreamID == bodySID).toList();

    if (gcs.size() != 1) {
      MXFException.handle(evthandler, new MXFEvent(
          MXFEvent.EventCodes.INCONSISTENT_HEADER,
          String.format("Header metadata does not specify exactly one generic container with BodySID = %d",
              bodySID)));
    }

    if (gcs.get(0).IndexStreamID != indexSID) {
      MXFException.handle(evthandler, new MXFEvent(
          MXFEvent.EventCodes.INCONSISTENT_HEADER,
          String.format(
              "Trying to add a generic container with BodySID=%d and IndexSID=%d but the header metadata specifies an IndexSID=%d",
              bodySID, indexSID, gcs.get(0).IndexStreamID)));
    }

    this.sids.add(bodySID);
    this.sids.add(indexSID);

    this.ecs.put(bodySID, cw);
  }

  /**
   * Adds a clip-wrapped essence container with Constant Byte per Element (CBE)
   * indexing.
   * 
   * @param bodySID  Body SID of the essence container.
   * @param indexSID Index SID of the essence container.
   * @return A writer for the essence container.
   * @throws IOException  If an I/O error occurs.
   * @throws KLVException If a KLV error occurs.
   * @throws MXFException If an MXF error occurs.
   */
  public GCClipCBEWriter addCBEClipWrappedGC(long bodySID, long indexSID)
      throws IOException, KLVException, MXFException {

    GCClipCBEWriter w = new GCClipCBEWriter(bodySID, indexSID);

    this.addGC(bodySID, indexSID, w);

    return w;
  }

  /**
   * Adds a clip-wrapped essence container with Variable Byte per Element (VBE)
   * indexing.
   * 
   * @param bodySID  Body SID of the essence container.
   * @param indexSID Index SID of the essence container.
   * @return A writer for the essence container.
   * @throws IOException  If an I/O error occurs.
   * @throws KLVException If a KLV error occurs.
   * @throws MXFException If an MXF error occurs.
   */
  public GCClipVBEWriter addVBEClipWrappedGC(long bodySID, long indexSID)
      throws IOException, KLVException, MXFException {

    GCClipVBEWriter w = new GCClipVBEWriter(bodySID, indexSID);

    this.addGC(bodySID, indexSID, w);

    return w;
  }

  /**
   * Adds a frame-wrapped essence container with Variable Byte per Element (VBE)
   * indexing.
   * 
   * @param bodySID  Body SID of the essence container.
   * @param indexSID Index SID of the essence container.
   * @return A writer for the essence container.
   * @throws IOException  If an I/O error occurs.
   * @throws KLVException If a KLV error occurs.
   * @throws MXFException If an MXF error occurs.
   */
  public GCFrameVBEWriter addVBEFrameWrappedGC(long bodySID, long indexSID)
      throws IOException, KLVException, MXFException {

    GCFrameVBEWriter w = new GCFrameVBEWriter(bodySID, indexSID);

    this.addGC(bodySID, indexSID, w);

    return w;
  }

  /**
   * Adds a Generic Stream.
   * 
   * @param bodySID Body SID of the generic stream.
   * @return A writer for the generic stream.
   * @throws IOException  If an I/O error occurs.
   * @throws KLVException If a KLV error occurs.
   */
  public GSWriter addGenericStream(long bodySID)
      throws IOException, KLVException {
    if (this.state != State.INIT) {
      throw new IllegalStateException("StreamingWriter has already been started");
    }
    if (bodySID <= 0) {
      throw new IllegalArgumentException("bodySID and indexSID must be larger than 0");
    }

    if (this.sids.contains(bodySID)) {
      throw new RuntimeException(String.format("BodySID %d is already registered.", bodySID));
    }

    GSWriter w = new GSWriter(bodySID);

    this.sids.add(bodySID);
    this.ecs.put(bodySID, w);

    return w;
  }

  /**
   * Completes the file writing (Footer partition, RIP).
   * 
   * @throws IOException  If an I/O error occurs.
   * @throws KLVException If a KLV error occurs.
   * @throws MXFException If an MXF error occurs.
   */
  public void finish() throws IOException, KLVException, MXFException {
    if (this.state != State.STARTED) {
      throw new IllegalStateException("StreamingWriter has not been started or is already finished");
    }
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
    this.rip.toStream(fos.stream());

    fos.flush();

    this.state = State.DONE;
  }

  /**
   * PRIVATE API
   */

  private byte[] serializePreface(Preface preface) throws IOException, MXFException {
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

      @Override
      public void handleEvent(Event evt) throws MXFException {
        StreamingWriter.this.evthandler.handle(evt);
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
          throw new ArrayIndexOutOfBoundsException("No local tag found for AUID " + auid);
        }

        return localTag;
      }

      @Override
      public AUID getAUID(long localtag) {
        throw new UnsupportedOperationException("Unimplemented method 'getAUID'");
      }

    };

    ByteArrayOutputStream bos = new ByteArrayOutputStream();
    MXFDataOutput mos = new MXFDataOutput(bos);
    mos.writeTriplet(PrimerPack.createTriplet(reg));
    for (Set set : sets) {
      Set.toStreamAsLocalSet(set, tags, mos);
    }

    /* required 8 KB fill item per ST 2067-5 */
    FillItem.toStream(mos.stream(), (short) 8192);

    mos.flush();

    return bos.toByteArray();
  }

  /**
   * Partition utilities
   */

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

  private void writeIndexPartition() throws IOException, KLVException, MXFException {
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

  /**
   * Checks if writing is finished.
   * 
   * @return True if writing is finished.
   */
  public boolean isDone() {
    return this.state == State.DONE;
  }

}
