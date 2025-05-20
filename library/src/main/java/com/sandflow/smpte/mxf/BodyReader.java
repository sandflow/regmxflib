package com.sandflow.smpte.mxf;

import java.io.IOException;
import java.io.InputStream;

import com.sandflow.smpte.klv.MemoryTriplet;
import com.sandflow.smpte.klv.Triplet;
import com.sandflow.smpte.klv.exceptions.KLVException;
import com.sandflow.smpte.mxf.PartitionPack.Kind;
import com.sandflow.smpte.mxf.StreamingReader.TrackState;
import com.sandflow.smpte.util.AUID;
import com.sandflow.smpte.util.BoundedInputStream;
import com.sandflow.smpte.util.UL;

public class BodyReader {

  private final MXFInputStream mis;

  BodyReader(InputStream is) {
    this.mis = new MXFInputStream(is);
  }

  AUID elementKey;

  public AUID essenceKey() {
    return this.elementKey;
  }

  long elementLength;

  public long elementength() {
    return this.elementLength;
  }

  BoundedInputStream elementPayload;

  public InputStream elementPayload() {
    return elementPayload;
  }

  long elementBodySID;
  public long elementSID() {
    return elementBodySID;
  }

  /**
   * Advances the stream to the next essence unit.
   *
   * @return true if a new unit is available; false if end of stream.
   * @throws IOException  if an I/O error occurs.
   * @throws KLVException if a KLV reading error occurs.
   */
  public boolean nextElement() throws KLVException, IOException {

    if (this.elementPayload != null) {
      this.elementPayload.exhaust();
      this.elementPayload = null;
    }

    /* skip over partitions */
    while (true) {
      this.elementKey = this.mis.readAUID();
      this.elementLength = this.mis.readBERLength();

      if (!PartitionPack.isInstance(this.elementKey)) {
        break;
      }

      /* partition pack is fixed length so that cast is ok */
      byte[] value = new byte[(int) this.elementLength];
      this.mis.readFully(value);
      Triplet t = new MemoryTriplet(this.elementKey, value);
      PartitionPack pp = PartitionPack.fromTriplet(t);

      /* we are done when we reach the footer partition */
      if (pp.getKind() == Kind.FOOTER) {
        return false;
      }

      this.elementBodySID = pp.getBodySID();

      /* do we have header metadata and/or an index table to skip? */

      if (pp.getHeaderByteCount() + pp.getIndexByteCount() > 0) {
        /* skip the optional fill item that follows the partition pack */
        this.elementKey = this.mis.readAUID();
        if (FillItem.isInstance(this.elementKey)) {
          this.elementLength = this.mis.readBERLength();
          this.mis.skip(this.elementLength);
        }
        this.mis.skip(pp.getHeaderByteCount() + pp.getIndexByteCount() - UL.SIZE);
      }

    }

    /* skip over any fill items */
    while (FillItem.isInstance(this.elementKey)) {
      this.mis.skip(this.elementLength);
      this.elementKey = this.mis.readAUID();
      this.elementLength = this.mis.readBERLength();
    }

    this.elementPayload = new BoundedInputStream(mis, elementLength);

    return true;
  }
}
