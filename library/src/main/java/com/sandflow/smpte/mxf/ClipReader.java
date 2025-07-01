package com.sandflow.smpte.mxf;

import java.io.IOException;
import java.io.InputStream;

import com.sandflow.smpte.klv.exceptions.KLVException;
import com.sandflow.smpte.mxf.HeaderInfo.TrackInfo;
import com.sandflow.smpte.mxf.types.FileDescriptor;
import com.sandflow.smpte.mxf.types.Track;
import com.sandflow.smpte.util.AUID;

public class ClipReader extends InputStream {
  final AUID elementKey;
  final long elementLength;
  final long essenceOffset;
  final Track track;
  final FileDescriptor descriptor;
  final RandomAccessFileInfo info;
  final RandomAccessInputSource source;

  long remainingElementBytes;

  ClipReader(RandomAccessFileInfo info, RandomAccessInputSource source) throws IOException, KLVException {
    this.info = info;
    this.source = source;

    long clipStartPosition = this.info.fileFromECPosition(0);
    this.source.position(clipStartPosition);
    MXFInputStream mis = new MXFInputStream(this.source);
    this.elementKey = mis.readAUID();
    this.elementLength = mis.readBERLength();

    TrackInfo ti = this.info.getTrackInfo(this.elementKey.asUL());
    this.track = ti.track();
    this.descriptor = ti.descriptor();

    if (Labels.IMF_IABEssenceClipWrappedContainer.equals(this.descriptor.ContainerFormat)) {
      /* ASDCPLib already indexes from the start of the Clip Triplet */
      this.essenceOffset = 0;
    } else {
      this.essenceOffset = this.source.position() - clipStartPosition;
    }

    this.seek(0);
  }

  public AUID getElementKey() {
    return this.elementKey;
  }

  public long getElementLength() {
    return this.elementLength;
  }

  public FileDescriptor getFileDescriptor() {
    return this.descriptor;
  }

  public Track getTrack() {
    return this.track;
  }

  public long getRemainingElementBytes() {
    return this.remainingElementBytes;
  }

  public long getSize() {
    return this.info.getSize();
  }

  public void seek(long euPosition) throws IOException {
    /* TODO: handle EOF */
    long ecPosition = this.info.ecFromEUPosition(euPosition);
    long filePosition = this.info.fileFromECPosition(ecPosition) + this.essenceOffset;
    this.source.position(filePosition);
    this.remainingElementBytes = this.elementLength;
  }

  @Override
  public int read() throws IOException {
    if (this.remainingElementBytes == 0)
      return -1;
    int r = source.read();
    this.remainingElementBytes = r == -1 ? 0 : this.remainingElementBytes - 1;
    return r;
  }

  @Override
  public int read(byte[] b, int off, int len) throws IOException {
    if (this.remainingElementBytes == 0)
      return -1;
    int r = source.read(b, off, len);
    this.remainingElementBytes = r == -1 ? 0 : this.remainingElementBytes - r;
    return r;
  }

  @Override
  public long skip(long n) throws IOException {
    if (this.remainingElementBytes == 0)
      return -1;
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
