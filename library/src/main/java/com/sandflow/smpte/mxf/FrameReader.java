package com.sandflow.smpte.mxf;

import java.io.IOException;

import com.sandflow.smpte.klv.exceptions.KLVException;

public class FrameReader extends StreamingReader {

  final RandomAccessFileInfo info;
  final RandomAccessInputSource source;

  FrameReader(RandomAccessFileInfo info, RandomAccessInputSource source) throws IOException, KLVException, MXFException {
    super(info, source, null);

    this.info = info;
    this.source = source;

    this.seek(0);
  }

  public long getSize() {
    return this.info.getSize();
  }

  public void seek(long euPosition) throws IOException, KLVException {
    long filePosition = this.info.fileFromECPosition(this.info.ecFromEUPosition(euPosition));
    this.source.position(filePosition);
    this.state = State.READY;
  }

}
