package com.sandflow.smpte.mxf;

import java.io.IOException;
import java.io.InputStream;

import com.sandflow.smpte.klv.exceptions.KLVException;
import com.sandflow.smpte.mxf.MXFFiles.ElementInfo;
import com.sandflow.smpte.mxf.StreamingReader.TrackInfo;
import com.sandflow.smpte.mxf.types.FileDescriptor;
import com.sandflow.smpte.mxf.types.Track;
import com.sandflow.smpte.util.AUID;

public class FrameReader extends InputStream {

  enum State {
    READY,
    IN_PAYLOAD,
    DONE
  }

  State state;

  final RandomAccessFileInfo info;
  final RandomAccessInputSource source;

  ElementInfo elementInfo;

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
    return this.elementInfo.key();
  }

  public long getElementLength() {
    if (this.state != State.IN_PAYLOAD) {
      throw new RuntimeException();
    }
    return this.elementInfo.length();
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
    if (this.state == State.DONE) {
      return false;
    }

    /* skip any remaining bytes in the current element payload */
    if (this.state == State.IN_PAYLOAD) {
      this.source.skip(this.remainingElementBytes);
    }

    this.elementInfo = MXFFiles.nextElement(this.source);

    if (this.elementInfo == null) {
      this.state = State.DONE;
      return false;
    }

    /* we have reached an essence element */
    TrackInfo ti = this.info.getTrackInfo(this.elementInfo.key().asUL());

    this.remainingElementBytes = this.elementInfo.length();
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
    int r = this.source.read();
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
    int r = this.source.read(b, off, len);
    this.remainingElementBytes = r == -1 ? 0 : this.remainingElementBytes - r;
    return r;
  }

  @Override
  public long skip(long n) throws IOException {
    if (this.state != State.IN_PAYLOAD) {
      throw new RuntimeException();
    }
    long s = this.source.skip(n);
    this.remainingElementBytes = this.remainingElementBytes - s;
    return s;
  }

  @Override
  public void close() throws IOException {
    /*
     * do nothing: it is the responsibility of the caller to close the
     * underlying RandomAccessInputSource
     */
  }

}
