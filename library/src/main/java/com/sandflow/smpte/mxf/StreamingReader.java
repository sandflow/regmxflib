package com.sandflow.smpte.mxf;

import java.io.IOException;
import java.io.InputStream;

import com.sandflow.smpte.klv.exceptions.KLVException;
import com.sandflow.smpte.mxf.HeaderInfo.TrackInfo;
import com.sandflow.smpte.mxf.MXFFiles.ElementInfo;
import com.sandflow.util.events.EventHandler;

/**
 * StreamingReader provides a streaming interface to read MXF (Material Exchange
 * Format) files.
 */
public class StreamingReader extends InputStream {

  enum State {
    READY,
    IN_PAYLOAD,
    DONE
  }

  private MXFInputStream mis;
  protected State state;
  private final HeaderInfo info;
  private ElementInfo elementInfo;
  private long remainingElementBytes = 0;
  private TrackInfo trackInfo;

  /**
   * Creates a new StreamingReader from an InputStream.
   *
   * @param is         InputStream containing the MXF file data.
   * @param evthandler Event handler for reporting parsing events or
   *                   inconsistencies.
   */
  StreamingReader(HeaderInfo info, InputStream is, EventHandler evthandler)
      throws IOException, KLVException, MXFException {
    if (is == null) {
      throw new NullPointerException("InputStream cannot be null");
    }

    if (info == null) {
      throw new NullPointerException("Info cannot be null");
    }
    this.info = info;

    /* TODO: handle byte ordering */
    this.mis = new MXFInputStream(is);

    this.state = State.READY;
  }

  /**
   * Advances the stream to the next essence unit.
   *
   * @return true if a new unit is available; false if end of stream.
   * @throws IOException  if an I/O error occurs.
   * @throws KLVException if a KLV reading error occurs.
   */
  public boolean nextElement() throws KLVException, IOException {

    if (this.state == State.DONE) {
      return false;
    }

    if (this.state == State.IN_PAYLOAD) {
      this.mis.skipFully(this.remainingElementBytes);
    }

    this.elementInfo = MXFFiles.nextElement(this.mis);
    if (this.elementInfo == null) {
      this.state = State.DONE;
      return false;
    }

    this.trackInfo = info.getTrackInfo(this.elementInfo.key().asUL());
    if (this.trackInfo == null) {
      throw new RuntimeException();
    }

    this.remainingElementBytes = this.elementInfo.length();

    this.state = State.IN_PAYLOAD;

    return true;
  }

  /**
   * Returns metadata about the current essence unit's track.
   *
   * @return TrackInfo object associated with the current unit.
   */
  public TrackInfo getElementTrackInfo() {
    if (this.state != State.IN_PAYLOAD) {
      throw new RuntimeException();
    }
    return this.trackInfo;
  }

  /**
   * Returns the length of the current payload in bytes.
   *
   * @return Payload length.
   */
  public long getElementLength() {
    if (this.state != State.IN_PAYLOAD) {
      throw new RuntimeException();
    }
    return this.elementInfo.length();
  }


  public boolean isDone() {
    return this.state == State.DONE;
  }

  @Override
  public int read() throws IOException {
    if (this.state != State.IN_PAYLOAD) {
      throw new RuntimeException();
    }
    if (this.remainingElementBytes == 0)
      return -1;
    int r = this.mis.read();
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
    int r = this.mis.read(b, off, len);
    this.remainingElementBytes = r == -1 ? 0 : this.remainingElementBytes - r;
    return r;
  }

  @Override
  public long skip(long n) throws IOException {
    if (this.state != State.IN_PAYLOAD) {
      throw new RuntimeException();
    }
    if (this.remainingElementBytes == 0)
      return -1;
    long s = this.mis.skip(n);
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
