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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

import com.sandflow.smpte.klv.Set;
import com.sandflow.smpte.klv.Triplet;
import com.sandflow.smpte.klv.exceptions.KLVException;
import com.sandflow.smpte.mxf.PartitionPack.Status;
import com.sandflow.smpte.mxf.types.IndexTableSegment;
import com.sandflow.smpte.mxf.types.Preface;
import com.sandflow.smpte.util.RandomAccessInputSource;
import com.sandflow.smpte.util.UUID;
import com.sandflow.util.events.Event;
import com.sandflow.util.events.EventHandler;

public class RandomAccessFileInfo {

  interface ECIndex {
    long getECPosition(long editUnitIndex);

    long length();
  }

  class VBEIndex implements ECIndex {
    private ArrayList<Long> positions = new ArrayList<>();

    protected void addECPosition(long ecPosition) {
      this.positions.add(ecPosition);
    }

    @Override
    public long getECPosition(long editUnit) {
      if (editUnit >= this.positions.size()) {
        throw new IllegalArgumentException();
      }
      return (long) this.positions.get((int) editUnit);
    }

    @Override
    public long length() {
      return this.positions.size();
    }
  }

  class CBEClipIndex implements ECIndex {
    private long cbeSize;
    private long length;

    CBEClipIndex(long cbeSize, long length) {
      if (length <= 0) {
        throw new IllegalArgumentException();
      }

      if (length <= 0) {
        throw new IllegalArgumentException();
      }
      this.cbeSize = cbeSize;
      this.length = length;
    }

    @Override
    public long getECPosition(long editUnit) {
      if (editUnit >= this.length) {
        throw new IllegalArgumentException();
      }
      return this.cbeSize * editUnit;
    }

    @Override
    public long length() {
      return this.length;
    }

    public long getLength() {
      return length;
    }

    public long getCbeSize() {
      return cbeSize;
    }

  }

  /**
   * Maps Essence Container offset to file offsets
   */
  private class FilePositionMapper {
    final private TreeMap<Long, Long> ecToFileOffsets = new TreeMap<>();

    void addPartition(long ecPosition, long filePosition) {
      this.ecToFileOffsets.put(ecPosition, filePosition);
    }

    long getFilePosition(long ecPosition) {
      var entry = this.ecToFileOffsets.floorEntry(ecPosition);

      if (entry == null) {
        throw new IllegalStateException("No file position corresponds to the essence container position");
      }

      return ecPosition - entry.getKey() + entry.getValue();
    }
  }

  private final StreamingFileInfo basicInfo;
  private Long ecSID = null;
  private Long ecIndexSID = null;
  private ECIndex euToECPosition;
  private final FilePositionMapper ecToFilePositions = new FilePositionMapper();
  private final EventHandler evthandler;

  private Map<Long, FilePositionMapper> gsToFilePositions = new HashMap<>();

  RandomAccessFileInfo(RandomAccessInputSource raip, EventHandler evthandler)
      throws IOException, KLVException, MXFException {
    if (raip == null) {
      throw new IllegalArgumentException("No input source provided");
    }

    MXFDataInput mis = new MXFDataInput(raip);

    this.evthandler = evthandler;

    /* load the RIP */
    raip.position(raip.size() - 4);
    long ripSize = mis.readUnsignedInt();
    raip.position(raip.size() - ripSize);

    RandomIndexPack rip = null;
    Triplet t = mis.readTriplet();
    if (t != null) {
      rip = RandomIndexPack.fromTriplet(t);
    }
    if (rip == null) {
      /* no RIP where it should be */
      MXFException.handle(evthandler, new RegMXFEvent(
          RegMXFEvent.EventCodes.RIP_NOTFOUND,
          "No RIP found"));
    }

    /*
     * process all the partitions, extracting:
     * - index information
     * - essence container locations
     * - header metadata information
     */

    PartitionPack headerMetadataPartition = null;

    for (int i = 0; i < rip.getOffsets().size(); i++) {

      /* seek to and read partition */
      raip.position(rip.getOffsets().get(i).getOffset());
      PartitionPack pp = null;
      t = mis.readTriplet();
      if (t != null) {
        pp = PartitionPack.fromTriplet(t);
      }
      if (pp == null) {
        /* no partition pack where expected */
        MXFException.handle(evthandler, new RegMXFEvent(
            RegMXFEvent.EventCodes.MISSING_PARTITION_PACK,
            String.format("No partition found at RIP entry %d", i)));
      }

      /* process generic stream partition */
      if (pp.getStatus() == Status.STREAM) {
        if (pp.getBodySID() == 0) {
          MXFException.handle(evthandler, new RegMXFEvent(
              RegMXFEvent.EventCodes.BAD_GS_PARTITION,
              String.format("Generic stream partition at RIP entry %d has a zero body SID", i)));
          continue;
        }

        FilePositionMapper gs = this.gsToFilePositions.get(pp.getBodySID());

        if (gs == null || pp.getBodyOffset() == 0) {
          gs = new FilePositionMapper();
          this.gsToFilePositions.put(pp.getBodySID(), gs);
        }

        /* skip over the optional fill item and map the generic stream position */
        long pos = raip.position();
        t = mis.readTriplet();
        FillItem fi = FillItem.fromTriplet(t);
        if (fi == null) {
          gs.addPartition(pp.getBodyOffset(), pos);
        } else {
          gs.addPartition(pp.getBodyOffset(), raip.position());
        }

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

      /*
       * skip the header metadata, including the optional fill item that follows
       * the partition pack
       */
      long pos = raip.position();
      t = mis.readTriplet();
      FillItem fi = FillItem.fromTriplet(t);
      if (fi != null) {
        /*
         * so we have skipped a Fill Item and are either at the start of the
         * header metadata or index table
         */
        raip.position(raip.position() + pp.getHeaderByteCount());
      } else {
        /*
         * no fill item, so we are either at the start of the header metadata or
         * index table
         */
        raip.position(pos + pp.getHeaderByteCount());
      }

      if (pp.getIndexSID() != 0) {
        /* read the one or more index segments */

        /* we only support indexing a single EC */
        /* TODO: confirm that indexSID and bodySID are consistent */
        if (this.ecIndexSID == null) {
          this.ecIndexSID = pp.getIndexSID();
        } else if (this.ecIndexSID != pp.getIndexSID()) {
          MXFException.handle(evthandler, new RegMXFEvent(
              RegMXFEvent.EventCodes.TOO_MANY_ECS,
              "Index tables for more than one essence container are present"));
        }

        /* Reset MXF Input stream */
        mis = new MXFDataInput(raip);

        /* read Index Segments until the IndexByteCount is exceeded */
        while (mis.getReadCount() < pp.getIndexByteCount()) {
          IndexTableSegment its = IndexTableSegment.fromSet(
              Set.fromLocalSet(mis.readTriplet(), StaticLocalTags.register()),
              new MXFInputContext() {
                @Override
                public Set getSet(UUID uuid) {
                  throw new UnsupportedOperationException("Unimplemented method 'getSet'");
                }

                @Override
                public void handleEvent(Event evt) throws MXFException {
                  evthandler.handle(evt);
                }
              });

          if (its == null) {
            MXFException.handle(evthandler, new RegMXFEvent(
                RegMXFEvent.EventCodes.BAD_INDEX_SEGMENT,
                "Index table segment not found in partition at file offset: " + pp.getThisPartition()));
          }

          if (its.EditUnitByteCount != null && its.EditUnitByteCount > 0) {
            /* we have a CBE index table */

            /*
             * there can only be one CBE table per IndexSID, so if we already have
             * a CBE index table, we ignore its
             */
            if (this.euToECPosition != null) {
              MXFException.handle(evthandler, new RegMXFEvent(
                  RegMXFEvent.EventCodes.BAD_INDEX_SEGMENT,
                  "A constant-byte-per-element essence container cannot contain more than one index table segment"));
            }

            this.euToECPosition = new CBEClipIndex(its.EditUnitByteCount, its.IndexDuration);
          } else {
            /* we have a VBE index table */
            VBEIndex vbeIndex = null;

            if (this.euToECPosition == null) {
              vbeIndex = new VBEIndex();
              this.euToECPosition = vbeIndex;
            } else if (this.euToECPosition instanceof VBEIndex) {
              vbeIndex = (VBEIndex) this.euToECPosition;
            } else {
              MXFException.handle(evthandler, new RegMXFEvent(
                  RegMXFEvent.EventCodes.BAD_INDEX_SEGMENT,
                  "A constant-byte-per-element index table segment was already found for the essence container"));
            }

            for (var e : its.IndexEntryArray) {
              if (e.StreamOffset == null) {
                throw new RuntimeException();
              }
              vbeIndex.addECPosition(e.StreamOffset);
            }
          }

        }
      }

      /* save the essence container offset */
      if (pp.getBodySID() != 0) {
        if (this.ecSID == null) {
          this.ecSID = pp.getBodySID();
        } else if (this.ecSID != pp.getBodySID()) {
          throw new RuntimeException();
        }

        this.ecToFilePositions.addPartition(pp.getBodyOffset(), raip.position());
      }

    }

    /* Load header metadata */

    raip.position(headerMetadataPartition.getThisPartition());
    this.basicInfo = new StreamingFileInfo(raip, evthandler);

  }

  public Preface getPreface() {
    return this.basicInfo.getPreface();
  }

  public Collection<Long> getGenericStreams() {
    return Collections.unmodifiableSet(this.gsToFilePositions.keySet());
  }

  public long gsToFilePosition(long gsSID, long position) {
    /* check for no generic stream */
    return this.gsToFilePositions.get(gsSID).getFilePosition(position);
  }

  public long euToECPosition(long position) {
    /* check for no index */
    return this.euToECPosition.getECPosition(position);
  }

  public long ecToFilePositions(long position) {
    /* check for no ec */
    return this.ecToFilePositions.getFilePosition(position);
  }

  public long getEUCount() {
    /* check for no index */
    return this.euToECPosition.length();
  }

}
