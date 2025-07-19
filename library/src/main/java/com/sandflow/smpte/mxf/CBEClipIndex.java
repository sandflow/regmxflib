package com.sandflow.smpte.mxf;

import java.io.IOException;

import org.apache.commons.numbers.fraction.Fraction;

import com.sandflow.smpte.mxf.helpers.IndexSegmentHelper;
import com.sandflow.smpte.mxf.types.IndexTableSegment;
import com.sandflow.smpte.util.UUID;

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

  public byte[] toBytes(long ecSID, long indexSID, Fraction editRate) throws IOException {
    var its = new IndexTableSegment();
    its.InstanceID = UUID.fromRandom();
    its.IndexEditRate = editRate;
    its.IndexStartPosition = 0L;
    its.IndexDuration = this.length;
    its.IndexStreamID = indexSID;
    its.EssenceStreamID = ecSID;
    its.EditUnitByteCount = this.cbeSize;

    return IndexSegmentHelper.toBytes(its);
  }
}