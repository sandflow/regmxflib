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
import java.util.ArrayList;
import java.util.TreeMap;

import com.sandflow.smpte.klv.Set;
import com.sandflow.smpte.klv.Triplet;
import com.sandflow.smpte.klv.exceptions.KLVException;
import com.sandflow.smpte.mxf.PartitionPack.Status;
import com.sandflow.smpte.mxf.types.IndexTableSegment;
import com.sandflow.smpte.mxf.types.Preface;
import com.sandflow.smpte.util.RandomAccessInputSource;
import com.sandflow.smpte.util.UL;
import com.sandflow.smpte.util.UUID;
import com.sandflow.util.events.EventHandler;

public class RandomAccessFileInfo implements HeaderInfo {

  interface Index {
    long getECPosition(long editUnitIndex);

    long length();
  }

  static class CBECLipIndex implements Index {
    private long cbeSize;
    private long length;

    CBECLipIndex(long cbeSize, long length) {
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
  }

  static class VBEIndex implements Index {
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

  /**
   * Maps Essence Container offset to file offsets
   */
  private class ECToFilePositionMapper {
    final private TreeMap<Long, Long> ecToFileOffsets = new TreeMap<>();

    void addPartition(long ecPosition, long filePosition) {
      this.ecToFileOffsets.put(ecPosition, filePosition);
    }

    long getFilePosition(long ecPosition) {
      if (this.ecToFileOffsets.size() == 0) {
        throw new RuntimeException();
      }
      var entry = this.ecToFileOffsets.floorEntry(ecPosition);

      if (entry == null) {
        throw new RuntimeException();
      }

      return ecPosition - entry.getKey() + entry.getValue();
    }
  }

  private final HeaderInfo basicInfo;
  private Long bodySID = null;
  private Long indexSID = null;
  private Index euToECPosition;
  private final ECToFilePositionMapper ecToFilePositions = new ECToFilePositionMapper();

  RandomAccessFileInfo(RandomAccessInputSource raip, EventHandler evthandler)
      throws IOException, KLVException, MXFException {
    MXFInputStream mis = new MXFInputStream(raip);

    /* load the RIP */
    raip.position(raip.size() - 4);
    long ripSize = mis.readUnsignedInt();
    raip.position(raip.size() - ripSize);

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

    PartitionPack headerMetadataPartition = null;

    for (int i = 0; i < rip.getOffsets().size(); i++) {

      /* seek to and read partition */
      raip.position(rip.getOffsets().get(i).getOffset());
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
        if (this.indexSID == null) {
          this.indexSID = pp.getIndexSID();
        } else if (this.indexSID != pp.getIndexSID()) {
          throw new RuntimeException();
        }

        /* Reset MXF Input stream */
        mis = new MXFInputStream(raip);

        /* read Index Segments until the IndexByteCount is exceeded */
        while (mis.getReadCount() < pp.getIndexByteCount()) {
          IndexTableSegment its = IndexTableSegment.fromSet(
              Set.fromLocalSet(mis.readTriplet(), StaticLocalTags.register()),
              new MXFInputContext() {
                @Override
                public Set getSet(UUID uuid) {
                  throw new UnsupportedOperationException("Unimplemented method 'getSet'");
                }
              });

          if (its == null) {
            throw new RuntimeException();
          }

          if (its.EditUnitByteCount != null && its.EditUnitByteCount > 0) {
            /* we have a CBE index table */

            /*
             * there can only be one CBE table per IndexSID, so if we already have
             * a CBE index table, we ignore its
             */
            if (this.euToECPosition != null) {
              throw new RuntimeException("Only one VBE permitted.");
            }

            this.euToECPosition = new CBECLipIndex(its.EditUnitByteCount, its.IndexDuration);
          } else {
            /* we have a VBE index table */
            VBEIndex vbeIndex;

            if (this.euToECPosition == null) {
              vbeIndex = new VBEIndex();
              this.euToECPosition = vbeIndex;
            } else if (this.euToECPosition instanceof VBEIndex) {
              vbeIndex = (VBEIndex) this.euToECPosition;
            } else {
              /* report error */
              continue;
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
        if (this.bodySID == null) {
          this.bodySID = pp.getBodySID();
        } else if (this.bodySID != pp.getBodySID()) {
          throw new RuntimeException();
        }

        this.ecToFilePositions.addPartition(pp.getBodyOffset(), raip.position());
      }

    }

    /* Load header metadata */

    raip.position(headerMetadataPartition.getThisPartition());
    this.basicInfo = new StreamingFileInfo(raip, evthandler);

    /* TODO: check for consistent bodySID */
    // this.bodySID =
    // this.preface.ContentStorageObject.EssenceDataObjects.get(0).EssenceStreamID;

  }

  @Override
  public TrackInfo getTrack(int i) {
    return this.basicInfo.getTrack(i);
  }

  @Override
  public int getTrackCount() {
    return this.basicInfo.getTrackCount();
  }

  @Override
  public Preface getPreface() {
    return this.basicInfo.getPreface();
  }

  public long ecFromEUPosition(long position) {
    return this.euToECPosition.getECPosition(position);
  }

  public long fileFromECPosition(long position) {
    return this.ecToFilePositions.getFilePosition(position);
  }

  public long getSize() {
    return this.euToECPosition.length();
  }

  @Override
  public TrackInfo getTrackInfo(UL elementKey) {
    return this.basicInfo.getTrackInfo(elementKey);
  }

}
