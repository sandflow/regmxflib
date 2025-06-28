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

package com.sandflow.smpte.mxf;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;

import com.sandflow.smpte.klv.Set;
import com.sandflow.smpte.klv.Triplet;
import com.sandflow.smpte.klv.exceptions.KLVException;
import com.sandflow.smpte.mxf.PartitionPack.Status;
import com.sandflow.smpte.mxf.StreamingReader.TrackInfo;
import com.sandflow.smpte.mxf.StreamingReader.TrackState;
import com.sandflow.smpte.mxf.types.IndexTableSegment;
import com.sandflow.smpte.mxf.types.MaterialPackage;
import com.sandflow.smpte.mxf.types.Preface;
import com.sandflow.smpte.util.CountingInputStream;
import com.sandflow.smpte.util.UUID;
import com.sandflow.util.events.EventHandler;

public class RandomAccessReader {

  interface Index {
    long getPos(int editUnit);

    long length();

    void applyECOffsets(SortedMap<Long, Long> ecToFileOffsets);
  }

  static class CBECLipIndex implements Index {
    private long cbeSize;
    private long startPos;
    private long length;

    CBECLipIndex(long cbeSize, long length) {
      if (length <= 0) {
        throw new IllegalArgumentException();
      }
      if (startPos <= 0) {
        throw new IllegalArgumentException();
      }
      if (length <= 0) {
        throw new IllegalArgumentException();
      }
      this.cbeSize = cbeSize;
      this.length = length;
    }

    @Override
    public long getPos(int editUnit) {
      if (editUnit >= this.length) {
        throw new IllegalArgumentException();
      }
      return this.cbeSize * editUnit + this.startPos;
    }

    @Override
    public long length() {
      return this.length;
    }

    @Override
    public void applyECOffsets(SortedMap<Long, Long> ecToFileOffsets) {
      if (ecToFileOffsets.size() != 1) {
        throw new RuntimeException();
      }
      this.startPos = ecToFileOffsets.entrySet().iterator().next().getValue();
    }
  }

  class VBEIndex implements Index {
    private ArrayList<Long> positions = new ArrayList<>();

    protected void add(long pos) {
      this.positions.add(pos);
    }

    @Override
    public long getPos(int editUnit) {
      if (editUnit >= this.positions.size()) {
        throw new IllegalArgumentException();
      }
      return (long) this.positions.get(editUnit);
    }

    @Override
    public long length() {
      return this.positions.size();
    }

    @Override
    public void applyECOffsets(SortedMap<Long, Long> ecToFileOffsets) {
      if (ecToFileOffsets.size() == 0) {
        throw new RuntimeException();
      }
      var nextECOffset = ecToFileOffsets.entrySet().iterator();
      long ecOffset = nextECOffset.next().
      for (int i = 0; i < this.positions.size(); i++) {
        long pos = this.positions.get(i);
        if (pos >= ms) {
          ecOffset = ms;
        }
        this.positions.set(i, pos + ecOffset);
      }

      this.startPos = ecToFileOffsets.entrySet().iterator().next().getValue();
    }
  }

  public static abstract class RandomAccessInputSource extends InputStream {
    /**
     * Set the position (in bytes) from the beginning of the source.
     *
     * @param pos Position in bytes
     * @throws IOException
     */
    public void position(long pos) throws IOException {
      throw new UnsupportedOperationException();
    }

    /**
     * Returns the size (in bytes) of the source.
     * 
     * @return Size in bytes
     * @throws IOException
     */
    public long size() throws IOException {
      throw new UnsupportedOperationException();
    }

    /**
     * Returns the position (in bytes) from the beginning of the source.
     * 
     * @return Position in bytes
     * @throws IOException
     */
    public long position() throws IOException {
      throw new UnsupportedOperationException();
    }
  }

  enum State {
    FRAME_PAYLOAD,
    CLIP_PAYLOAD,
    READY
  }

  private State state;
  private Index index;
  private final RandomAccessInputSource fis;
  private final Preface preface;
  private final List<StreamingReader.TrackState> tracks;
  private final Long streamID;

  RandomAccessReader(RandomAccessInputSource raip, EventHandler evthandler)
      throws IOException, KLVException, MXFException {
    this.fis = raip;
    MXFInputStream mis = new MXFInputStream(this.fis);

    /* load the RIP */
    this.fis.position(this.fis.size() - 4);
    long ripSize = mis.readUnsignedInt();
    this.fis.position(this.fis.size() - ripSize);

    Triplet t = mis.readTriplet();
    if (t == null) {
      /* no Triplet where the RIP should start */
      throw new RuntimeException();
    }

    RandomIndexPack rip = RandomIndexPack.fromTriplet(t);
    if (rip == null) {
      /* no RIP where it should be */
      throw new RuntimeException();
    }

    /*
     * process all the partitions, extracting:
     * - index information
     * - essence container locations
     * - header metadata information
     */

    /* content package offsets within the essence container */
    ArrayList<Long> offsets = new ArrayList<>();
    /* essence container to file offsets */
    TreeMap<Long, Long> ecToFileOffsets = new TreeMap<>();

    PartitionPack headerMetadataPartition = null;

    for (int i = 0; i < rip.getOffsets().size(); i++) {

      /* seek to and read partition */
      this.fis.position(rip.getOffsets().get(i).getOffset());
      t = mis.readTriplet();
      if (t == null) {
        /* no triplet where it should be */
        throw new RuntimeException();
      }

      PartitionPack pp = PartitionPack.fromTriplet(t);
      if (pp == null) {
        /* no partition pack where expected */
        throw new RuntimeException();
      }

      /* skip generic stream partition */

      if (pp.getStatus() == Status.STREAM) {
        continue;
      }

      /* look for header metadata */
      if ((i == 0 || i == rip.getOffsets().size() - 1) &&
          (pp.getStatus() == PartitionPack.Status.CLOSED_COMPLETE
              || pp.getStatus() == PartitionPack.Status.CLOSED_INCOMPLETE)
          &&
          pp.getHeaderByteCount() > 0) {
        headerMetadataPartition = pp;
      }

      /*
       * skip further processing unless the partition contains index table
       * segements or essence container bytes
       */

      if (pp.getIndexSID() == 0 && pp.getBodySID() == 0) {
        continue;
      }

      /* skip the fill item that follows the partition pack, if any */
      long pos = this.fis.position();
      t = mis.readTriplet();
      FillItem fi = FillItem.fromTriplet(t);
      if (fi != null) {
        /*
         * so we have skipped a Fill Item and are either at the start of the
         * header metadata or index table
         */
        this.fis.position(this.fis.position() + pp.getHeaderByteCount());
      } else {
        /*
         * no fill item, so we are either at the start of the header metadata or
         * index table
         */
        this.fis.position(pos + pp.getHeaderByteCount());
      }

      CountingInputStream cis = new CountingInputStream(this.fis);
      /* Reset MXF Input stream */
      /* TODO: include counting in MXFInputStream */
      mis = new MXFInputStream(cis);

      /* read Index Segments until the IndexByteCount is exceeded */
      while (cis.getCount() < pp.getIndexByteCount()) {
        IndexTableSegment its = IndexTableSegment.fromSet(
            Set.fromLocalSet(mis.readTriplet(), StaticLocalTags.register()),
            new MXFInputContext() {
              @Override
              public Set getSet(UUID uuid) {
                throw new UnsupportedOperationException("Unimplemented method 'getSet'");
              }
            });

        if (its == null) {
          continue;
        }

        if (its.EditUnitByteCount != null && its.EditUnitByteCount > 0) {
          /* we have a CBE index table */

          /*
           * there can only be one CBE table per IndexSID, so if we already have
           * a CBE index table, we ignore its
           */
          if (this.index != null) {
            /* report error */
            continue;
          }

          if (its.EditUnitByteCount != null && its.EditUnitByteCount > 0) {
            /* we have a CBE index table */

            /*
             * there can only be one CBE table per IndexSID
             */
            if (this.index != null) {
              throw new RuntimeException();
            }

            this.index = new CBECLipIndex(its.EditUnitByteCount, its.IndexDuration);
          } else {
            VBEIndex vbeIndex;

            if (this.index == null) {
              vbeIndex = new VBEIndex();
              this.index = vbeIndex;
            } else if (this.index instanceof VBEIndex) {
              vbeIndex = (VBEIndex) this.index;
            } else {
              /* report error */
              continue;
            }

            for (var e : its.IndexEntryArray) {
              if (e.StreamOffset == null) {
                throw new RuntimeException();
              }
              vbeIndex.add(e.StreamOffset);
            }
          }
        }

      }

      /* save the essence container offset */
      if (pp.getBodySID() != 0) {
        ecToFileOffsets.put(pp.getBodyOffset(), this.fis.position());
      }

    }

    /*  */

    /* Load header metadata */

    this.fis.position(headerMetadataPartition.getThisPartition());
    mis.readTriplet();
    this.preface = StreamingReader.readHeaderMetadataFrom(mis, headerMetadataPartition.getHeaderByteCount(),
        evthandler);

    /* TODO: handle NULL preface */

    /* we can only handle a single essence container at this point */
    if (this.preface.ContentStorageObject.EssenceDataObjects.size() != 1) {
      throw new RuntimeException("Only one essence container supported");
    }

    /* we can only handle one material package at this point */
    if (this.preface.ContentStorageObject.Packages.stream().filter(e -> e instanceof MaterialPackage).count() != 1) {
      throw new RuntimeException("Only one material package supported");
    }

    this.streamID = this.preface.ContentStorageObject.EssenceDataObjects.get(0).EssenceStreamID;

    this.tracks = StreamingReader.extractTracks(this.preface);

    this.seek(0);
  }

  private int elementTrackIndex;
  private BodyReader bodyReader;

  /**
   * Returns the temporal offset of the current unit.
   *
   * @return Offset in number of track edit units.
   */
  public long getElementPosition() {
    if (this.state != State.FRAME_PAYLOAD) {
      throw new RuntimeException();
    }
    return this.tracks.get(this.elementTrackIndex).position;
  }

  /**
   * Returns metadata about the current essence unit's track.
   *
   * @return TrackInfo object associated with the current unit.
   */
  public TrackInfo getElementTrackInfo() {
    if (this.state != State.FRAME_PAYLOAD) {
      throw new RuntimeException();
    }
    return this.tracks.get(this.elementTrackIndex).info;
  }

  public long getElementLength() {
    if (this.state != State.FRAME_PAYLOAD) {
      throw new RuntimeException();
    }
    return this.bodyReader.elementength();
  }

  public InputStream getElementPayload() {
    if (this.state != State.FRAME_PAYLOAD) {
      throw new RuntimeException();
    }
    return this.bodyReader.elementPayload();
  }

  public TrackInfo getTrack(int i) {
    return this.tracks.get(i).info;
  }

  public int getTrackCount() {
    return this.tracks.size();
  }

  public Preface getPreface() {
    return this.preface;
  }

  /**
   * Advances the stream to the next essence unit.
   *
   * @return true if a new unit is available; false if end of stream.
   * @throws IOException  if an I/O error occurs.
   * @throws KLVException if a KLV reading error occurs.
   */
  public boolean nextElement() throws KLVException, IOException {
    if (this.state == State.CLIP_PAYLOAD) {
      throw new RuntimeException();
    } else if (this.state == State.READY) {
      this.bodyReader = new BodyReader(fis);
    }
    this.state = State.FRAME_PAYLOAD;

    if (!this.bodyReader.nextElement()) {
      return false;
    }

    /* we have reached an essence element */
    long trackNum = MXFFiles.getTrackNumber(this.bodyReader.essenceKey().asUL());

    /* find track info */
    this.elementTrackIndex = -1;
    for (int i = 0; i < this.tracks.size(); i++) {
      TrackState ts = this.tracks.get(i);
      if (ts.info.container().EssenceStreamID == this.streamID &&
          ts.info.track().EssenceTrackNumber == trackNum) {
        this.elementTrackIndex = i;
        break;
      }
    }

    /* TODO: error if no track info found */

    this.tracks.get(this.elementTrackIndex).position++;

    return true;
  }

  public InputStream getClipPayload() {
    if (this.state != State.READY) {
      throw new RuntimeException();
    }
    this.state = State.CLIP_PAYLOAD;
    return this.fis;
  }

  public void seek(int position) throws IOException {
    this.state = State.READY;
    /* TODO: check for bad position */
    this.fis.position(this.index.getPos(position));
  }
}
