/*
 * Copyright (c) 2014, Pierre-Anthony Lemieux (pal@sandflow.com)
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
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

import com.sandflow.smpte.klv.Triplet;
import com.sandflow.smpte.klv.exceptions.KLVException;
import com.sandflow.smpte.util.UL;

/**
 * Represents a MXF Partition Pack Item (see SMPTE ST 377-1)
 */
public class RandomIndexPack {

  private static final UL KEY = UL.fromURN("urn:smpte:ul:060e2b34.02050101.0d010201.01110100");

  /**
   * Returns the Pack Key
   * 
   * @return Key
   */
  public static UL getKey() {
    return KEY;
  }

  /**
   * Creates a Random Index Pack (RIP) from a Triplet
   * 
   * @param triplet Triplet from which to create the RIP
   * @return Random Index Pack or null if the Triplet is not a Random Index Pack
   * @throws KLVException
   */
  public static RandomIndexPack fromTriplet(Triplet triplet) throws KLVException {
    RandomIndexPack pp = new RandomIndexPack();

    if (!KEY.equals(triplet.getKey())) {
      return null;
    }

    /* does the length make sense? */

    if ((triplet.getLength() - 4) % 12 != 0) {
      return null;
    }

    long count = (triplet.getLength() - 4) / 12;

    try {
      MXFDataInput kis = new MXFDataInput(triplet.getValueAsStream());

      for (int i = 0; i < count; i++) {

        long bodySID = kis.readUnsignedInt();
        long offset = kis.readLong();

        PartitionOffset po = new PartitionOffset(bodySID, offset);

        pp.offsets.add(po);
      }

    } catch (IOException e) {
      throw new KLVException(e);
    }

    return pp;
  }

  /**
   * Creates a Triplet
   * 
   * @return triplet RIP as a triplet
   * @throws KLVException
   * @throws IOException
   */
  public void toStream(OutputStream os) throws KLVException, IOException {
    MXFDataOutput mos = new MXFDataOutput(os);
    mos.writeUL(KEY);
    mos.writeBER4Length(12 * this.offsets.size() + 4);
    for (PartitionOffset offset : this.offsets) {
      mos.writeUnsignedInt(offset.getBodySID());
      mos.writeLong(offset.getOffset());
    }
    long ripSize = mos.getWrittenCount() + 4;
    mos.writeUnsignedInt(ripSize);
  }

  /**
   * @return Ordered array containing the offsets stored in the RIP
   */
  public List<PartitionOffset> getOffsets() {
    return offsets;
  }

  public void addOffset(PartitionOffset offset) {
    this.offsets.add(offset);
  }

  private final ArrayList<PartitionOffset> offsets = new ArrayList<>();

  /**
   * Represents one partition offset entry stored in the RIP
   */
  static public class PartitionOffset {
    private final long bodySID;
    private final long offset;

    public PartitionOffset(long bodySID, long offset) {
      this.bodySID = bodySID;
      this.offset = offset;
    }

    /**
     *
     * @return Identifies the partition
     */
    public long getBodySID() {
      return bodySID;
    }

    /**
     *
     * @return Offset (in bytes) of the partition
     */
    public long getOffset() {
      return offset;
    }

  }
}
