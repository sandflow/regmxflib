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

import com.sandflow.smpte.klv.MemoryTriplet;
import com.sandflow.smpte.klv.Triplet;
import com.sandflow.smpte.klv.adapters.ULValueAdapter;
import com.sandflow.smpte.klv.exceptions.KLVException;
import com.sandflow.smpte.util.AUID;
import com.sandflow.smpte.util.UL;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;

/**
 * Represents a MXF Partition Pack Item (see SMPTE ST 377-1)
 */
public class PartitionPack {

  private static final UL KEY = new UL(new byte[] { 0x06, 0x0e, 0x2b, 0x34, 0x02, 0x05, 0x01, 0x01, 0x0d, 0x01, 0x02,
      0x01, 0x01, 0x00, 0x00, 0x00 });
  private static final int PARTITION_STATUS_OCTET = 14;
  private static final int PARTITION_KIND_OCTET = 13;

  PartitionPack() {
    this.majorVersion = 1;
    this.minorVersion = 3;
    this.kagSize = 0;
    this.thisPartition = 0;
    this.previousPartition = 0;
    this.footerPartition = 0;
    this.headerByteCount = 0;
    this.indexByteCount = 0;
    this.indexSID = 0;
    this.bodySID = 0;
  }

  /**
   * Returns the Partition Pack Key
   * 
   * @return Key
   */
  public static UL getKey() {
    return KEY;
  }

  /**
   * Indicates whether the key is a Partition Pack key
   * 
   * @param key
   * @return true if the key is a Partition Pack key, false otherwise
   */
  public static boolean isInstance(AUID key) {
    return KEY.equalsWithMask(key, 0xfef9 /* 11111110 11111001 */);
  }

  /**
   * Writes a Partition Pack to a Triplet
   *
   * @param pp      Partition pack to write
   * @param triplet Triplet from which to create the Partition Pack
   * @throws KLVException
   */
  public static Triplet toTriplet(PartitionPack pp, Kind kind, Status status) throws KLVException {
    byte[] ppKey = KEY.getValue().clone();

    ppKey[PARTITION_STATUS_OCTET] = status.getByte14();
    ppKey[PARTITION_KIND_OCTET] = kind.getByte13();

    ByteArrayOutputStream bos = new ByteArrayOutputStream();
    MXFOutputStream mos = new MXFOutputStream(bos);

    try {
      mos.writeUnsignedShort(pp.getMajorVersion());
      mos.writeUnsignedShort(pp.getMinorVersion());
      mos.writeUnsignedInt(pp.getKagSize());
      mos.writeLong(pp.getThisPartition());
      mos.writeLong(pp.getPreviousPartition());
      mos.writeLong(pp.getFooterPartition());
      mos.writeLong(pp.getHeaderByteCount());
      mos.writeLong(pp.getIndexByteCount());
      mos.writeUnsignedInt(pp.getIndexSID());
      mos.writeLong(pp.getBodyOffset());
      mos.writeUnsignedInt(pp.getBodySID());
      mos.writeUL(pp.getOperationalPattern());
      mos.writeBatch(pp.getEssenceContainers(), 16L, ULValueAdapter::toValue);

    } catch (IOException e) {
      throw new KLVException(e);
    }

    return new MemoryTriplet(new AUID(ppKey), bos.toByteArray());
  }

  /**
   * Creates a Partition Pack from a Triplet
   * 
   * @param triplet Triplet from which to create the Partition Pack
   * @return PartitionPack or null if the Triplet is not a Partition Pack
   * @throws KLVException
   */
  public static PartitionPack fromTriplet(Triplet triplet) throws KLVException {
    PartitionPack pp = new PartitionPack();

    if (!KEY.equalsWithMask(triplet.getKey(), 0xfef9 /* 11111110 11111001 */)) {
      return null;
    }

    switch (triplet.getKey().asUL().getValueOctet(14)) {
      case 0x01:
        pp.setStatus(Status.OPEN_INCOMPLETE);
        break;
      case 0x02:
        pp.setStatus(Status.CLOSED_INCOMPLETE);
        break;
      case 0x03:
        pp.setStatus(Status.OPEN_COMPLETE);
        break;
      case 0x04:
        pp.setStatus(Status.CLOSED_COMPLETE);
        break;
      default:
        return null;
    }

    switch (triplet.getKey().asUL().getValueOctet(13)) {
      case 0x02:
        pp.setKind(Kind.HEADER);

        break;
      case 0x03:
        pp.setKind(Kind.BODY);

        break;
      case 0x04:
        pp.setKind(Kind.FOOTER);
        if (pp.getStatus() == Status.OPEN_COMPLETE
            || pp.getStatus() == Status.OPEN_INCOMPLETE) {
          return null;
        }
        break;
      default:
        return null;
    }

    MXFInputStream kis = new MXFInputStream(triplet.getValueAsStream());

    try {

      pp.setMajorVersion(kis.readUnsignedShort());

      pp.setMinorVersion(kis.readUnsignedShort());

      pp.setKagSize(kis.readUnsignedInt());

      pp.setThisPartition(kis.readLong());

      pp.setPreviousPartition(kis.readLong());

      pp.setFooterPartition(kis.readLong());

      pp.setHeaderByteCount(kis.readLong());

      pp.setIndexByteCount(kis.readLong());

      pp.setIndexSID(kis.readUnsignedInt());

      pp.setBodyOffset(kis.readLong());

      pp.setBodySID(kis.readUnsignedInt());

      pp.setOperationalPattern(kis.readUL());

      pp.setEssenceContainers(kis.readBatch(ULValueAdapter::fromValue));

    } catch (IOException e) {
      throw new KLVException(e);
    }

    return pp;
  }

  private int majorVersion;
  private int minorVersion;
  private long kagSize;
  private long thisPartition;
  private long previousPartition;
  private long footerPartition;
  private long headerByteCount;
  private long indexByteCount;
  private long indexSID;
  private long bodyOffset;
  private long bodySID;
  private UL operationalPattern;
  private ArrayList<UL> essenceContainers = new ArrayList<>();
  private Kind kind;
  private Status status;

  public Kind getKind() {
    return kind;
  }

  public void setKind(Kind kind) {
    this.kind = kind;
  }

  public Status getStatus() {
    return status;
  }

  public void setStatus(Status status) {
    this.status = status;
  }

  public int getMajorVersion() {
    return majorVersion;
  }

  public void setMajorVersion(int majorVersion) {
    this.majorVersion = majorVersion;
  }

  public int getMinorVersion() {
    return minorVersion;
  }

  public void setMinorVersion(int minorVersion) {
    this.minorVersion = minorVersion;
  }

  public long getKagSize() {
    return kagSize;
  }

  public void setKagSize(long kagSize) {
    this.kagSize = kagSize;
  }

  public long getThisPartition() {
    return thisPartition;
  }

  public void setThisPartition(long thisPartition) {
    this.thisPartition = thisPartition;
  }

  public long getPreviousPartition() {
    return previousPartition;
  }

  public void setPreviousPartition(long previousPartition) {
    this.previousPartition = previousPartition;
  }

  public long getFooterPartition() {
    return footerPartition;
  }

  public void setFooterPartition(long footerPartition) {
    this.footerPartition = footerPartition;
  }

  public long getHeaderByteCount() {
    return headerByteCount;
  }

  public void setHeaderByteCount(long headerByteCount) {
    this.headerByteCount = headerByteCount;
  }

  public long getIndexByteCount() {
    return indexByteCount;
  }

  public void setIndexByteCount(long indexByteCount) {
    this.indexByteCount = indexByteCount;
  }

  public long getIndexSID() {
    return indexSID;
  }

  public void setIndexSID(long indexSID) {
    this.indexSID = indexSID;
  }

  public long getBodyOffset() {
    return bodyOffset;
  }

  public void setBodyOffset(long bodyOffset) {
    this.bodyOffset = bodyOffset;
  }

  public long getBodySID() {
    return bodySID;
  }

  public void setBodySID(long bodySID) {
    this.bodySID = bodySID;
  }

  public UL getOperationalPattern() {
    return operationalPattern;
  }

  public void setOperationalPattern(UL operationalPattern) {
    this.operationalPattern = operationalPattern;
  }

  public Collection<UL> getEssenceContainers() {
    return essenceContainers;
  }

  public void setEssenceContainers(Collection<UL> essenceContainers) {
    this.essenceContainers = new ArrayList<>(essenceContainers);
  }

  public enum Kind {
    HEADER((byte) 2),
    BODY((byte) 3),
    FOOTER((byte) 4);

    private final byte byte13;

    Kind(byte byte13) {
      this.byte13 = byte13;
    }

    public byte getByte13() {
      return byte13;
    }
  }

  public enum Status {

    OPEN_INCOMPLETE((byte) 1),
    CLOSED_INCOMPLETE((byte) 2),
    OPEN_COMPLETE((byte) 3),
    CLOSED_COMPLETE((byte) 4);

    private final byte byte14;

    Status(byte byte14) {
      this.byte14 = byte14;
    }

    public byte getByte14() {
      return byte14;
    }
  }
}
