package com.sandflow.smpte.mxf;

import java.io.IOException;
import java.io.InputStream;

import com.sandflow.smpte.klv.MemoryTriplet;
import com.sandflow.smpte.klv.exceptions.KLVException;
import com.sandflow.smpte.mxf.PartitionPack.Kind;
import com.sandflow.smpte.mxf.StreamingReader.TrackInfo;
import com.sandflow.smpte.mxf.types.FileDescriptor;
import com.sandflow.smpte.mxf.types.Track;
import com.sandflow.smpte.util.AUID;
import com.sandflow.smpte.util.UL;

public class FrameReader extends InputStream {

  enum State {
    READY,
    IN_PAYLOAD
  }

  State state;

  final RandomAccessFileInfo info;
  final RandomAccessInputSource source;

  AUID elementKey;
  long elementLength;
  FileDescriptor descriptor;
  Track track;
  long remainingElementBytes;

  FrameReader(RandomAccessFileInfo info, RandomAccessInputSource source) throws IOException, KLVException {
    this.info = info;
    this.source = source;

    this.seek(0);
  }

  public long getSize() {
    return this.info.getSize();
  }

  public AUID getElementKey() {
    if (this.state != State.IN_PAYLOAD) {
      throw new RuntimeException();
    }
    return this.elementKey;
  }

  public long getElementLength() {
    if (this.state != State.IN_PAYLOAD) {
      throw new RuntimeException();
    }
    return this.elementLength;
  }

  public FileDescriptor getFileDescriptor() {
    if (this.state != State.IN_PAYLOAD) {
      throw new RuntimeException();
    }
    return this.descriptor;
  }

  public Track getTrack() {
    if (this.state != State.IN_PAYLOAD) {
      throw new RuntimeException();
    }
    return this.track;
  }

  public long getRemainingElementBytes() {
    if (this.state != State.IN_PAYLOAD) {
      throw new RuntimeException();
    }
    return this.remainingElementBytes;
  }

  public void seek(long euPosition) throws IOException, KLVException {
    long filePosition = this.info.fileFromECPosition(this.info.ecFromEUPosition(euPosition));
    this.source.position(filePosition);
    this.state = State.READY;
  }

  public boolean nextElement() throws KLVException, IOException {
    MXFInputStream mis = new MXFInputStream(this.source);

    /* skip any remaining bytes in the current element payload */
    if (this.state == State.IN_PAYLOAD) {
      this.source.skip(this.remainingElementBytes);
    }

    /* skip over partitions */
    while (true) {
      this.elementKey = mis.readAUID();
      this.elementLength = mis.readBERLength();

      if (!PartitionPack.isInstance(this.elementKey)) {
        break;
      }

      /* partition pack is fixed length so that cast is ok */
      byte[] value = new byte[(int) this.elementLength];
      mis.readFully(value);
      PartitionPack pp = PartitionPack.fromTriplet(new MemoryTriplet(this.elementKey, value));

      /* we are done when we reach the footer partition */
      if (pp.getKind() == Kind.FOOTER) {
        return false;
      }

      /* do we have header metadata and/or an index table to skip? */

      if (pp.getHeaderByteCount() + pp.getIndexByteCount() > 0) {
        /* skip the optional fill item that follows the partition pack */
        this.elementKey = mis.readAUID();
        if (FillItem.isInstance(this.elementKey)) {
          this.elementLength = mis.readBERLength();
          mis.skip(this.elementLength);
        }
        mis.skip(pp.getHeaderByteCount() + pp.getIndexByteCount() - UL.SIZE);
      }

    }

    /* skip over any fill items */
    while (FillItem.isInstance(this.elementKey)) {
      this.source.skip(this.elementLength);
      this.elementKey = mis.readAUID();
      this.elementLength = mis.readBERLength();
    }

    /* we have reached an essence element */
    TrackInfo ti = this.info.getTrackInfo(this.elementKey.asUL());

    this.descriptor = ti.descriptor();
    this.track = ti.track();

    this.state = State.IN_PAYLOAD;

    return true;
  }

  @Override
  public int read() throws IOException {
    if (this.state != State.IN_PAYLOAD) {
      throw new RuntimeException();
    }
    if (this.remainingElementBytes == 0)
      return -1;
    int r = source.read();
    this.remainingElementBytes = r == -1 ? 0 : this.remainingElementBytes - 1;
    return r;
  }

  @Override
  public int read(byte[] b, int off, int len) throws IOException {
    if (this.state != State.IN_PAYLOAD) {
      throw new RuntimeException();
    }
    if (this.remainingElementBytes == 0)
      return -1;
    int r = source.read(b, off, len);
    this.remainingElementBytes = r == -1 ? 0 : this.remainingElementBytes - r;
    return r;
  }

  @Override
  public long skip(long n) throws IOException {
    if (this.state != State.IN_PAYLOAD) {
      throw new RuntimeException();
    }
    return this.source.skip(n);
  }

  @Override
  public void close() throws IOException {
    /*
     * do nothing: it is the responsibility of the caller to close the
     * underlying RandomAccessInputSource
     */
  }

}
