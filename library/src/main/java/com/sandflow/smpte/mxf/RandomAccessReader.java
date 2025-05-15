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
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.TreeMap;

import org.apache.commons.numbers.fraction.Fraction;

import com.sandflow.smpte.klv.KLVInputStream;
import com.sandflow.smpte.klv.Triplet;
import com.sandflow.smpte.klv.exceptions.KLVException;
import com.sandflow.smpte.mxf.RandomIndexPack.PartitionOffset;

public class RandomAccessReader extends StreamingReader {

  interface Index {
    long getPos(int editUnit);

    long length();
  }

  class CBECLipIndex implements Index {
    private long cbeSize;
    private long startPos;
    private long length;

    CBECLipIndex(long cbeSize, long startPos, long length) {
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
      this.startPos = startPos;
      this.length = length;
    }

    @Override
    public long getPos(int editUnit) {
      if (editUnit >= this.length) {
        throw new IllegalArgumentException();
      }
      return this.startPos + this.cbeSize * editUnit;
    }

    @Override
    public long length() {
      return this.length;
    }
  }

  class VBEIndex implements Index {
    private ArrayList<Long> pos = new ArrayList<>();

    void add(long pos) {
      this.pos.add(pos);
    }

    @Override
    public long getPos(int editUnit) {
      if (editUnit >= this.pos.size()) {
        throw new IllegalArgumentException();
      }
      return (long) this.pos.get(editUnit);
    }

    @Override
    public long length() {
      return this.pos.size();
    }
  }

  public abstract class RandomAccessInputSource extends InputStream {
    /**
     * Set the position (in bytes) from the beginning of the source.
     *
     * @param pos Position in bytes
     */
    public void position(long pos) {
      throw new UnsupportedOperationException();
    }

    /**
     * Returns the size (in bytes) of the source.
     * 
     * @return Size in bytes
     */
    public long size() {
      throw new UnsupportedOperationException();
    }

    /**
     * Returns the position (in bytes) from the beginning of the source.
     * 
     * @return Position in bytes
     */
    public long position() {
      throw new UnsupportedOperationException();
    }
  }

  public abstract class RandomAccessInputSource extends InputStream {
    /**
     * Set the position (in bytes) from the beginning of the source.
     *
     * @param pos Position in bytes
     */
    public void position(long pos) {
      throw new UnsupportedOperationException();
    }

    /**
     * Returns the size (in bytes) of the source.
     * 
     * @return Size in bytes
     */
    public long size() {
      throw new UnsupportedOperationException();
    }

    /**
     * Returns the position (in bytes) from the beginning of the source.
     * 
     * @return Position in bytes
     */
    public long position() {
      throw new UnsupportedOperationException();
    }
  }

  HashMap<Long, Index> indexBySID;
  RandomAccessInputSource fis;
  MXFInputStream mis;
  RandomIndexPack rip;

  RandomAccessReader(RandomAccessInputSource raip) throws IOException, KLVException, MXFException {
    super(raip, null);

    this.fis = raip;
    this.mis = new MXFInputStream(this.fis);

    /* load RIP */

    /* look for the RIP start */

    this.fis.position(this.fis.size() - 4);

    long ripSize = mis.readUnsignedInt();

    this.fis.position(this.fis.size() - ripSize);

    Triplet t = mis.readTriplet();

    if (t == null) {
      throw new RuntimeException();
    }

    this.rip = RandomIndexPack.fromTriplet(t);

    if (this.rip == null) {
      throw new RuntimeException();
    }

    /* build index table and identify the right header metadata */

    PartitionPack headerMetadataPartition = null;
    long lastIndexStart = 0;

    for (int i = 0; i < this.rip.getOffsets().size(); i++) {

      /* seek to and read partition */
      this.fis.position(this.rip.getOffsets().get(i).getOffset());
      t = mis.readTriplet();
      PartitionPack pp = PartitionPack.fromTriplet(t);

      /* look for header metadata */
      if ((i == 0 || i == this.rip.getOffsets().size() - 1) &&
          (pp.getStatus() == PartitionPack.Status.CLOSED_COMPLETE
              || pp.getStatus() == PartitionPack.Status.CLOSED_INCOMPLETE)
          &&
          pp.getHeaderByteCount() > 0) {
        headerMetadataPartition = pp;
      }

      /* look for an index table */
      if (pp.getIndexSID() > 0 && pp.getIndexByteCount() > 0) {
        t = mis.readTriplet();
      }
    }

    /* VBE */

    /* CBE */

    /* Load header metadata */

  }

  public void seek(long editUnitOffset) {

  }

  public Fraction getEditRate() {
    return Fraction.of(0);
  }

  public long getDuration() {
    return 0;
  }
}
