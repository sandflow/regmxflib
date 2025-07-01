package com.sandflow.smpte.util;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;

public class CountingOutputStream extends FilterOutputStream {

  private long writtenCount = 0;

  public CountingOutputStream(OutputStream out) {
    super(out);
  }

  public long getWrittenCount() {
    return this.writtenCount;
  }

  @Override
  public void write(int b) throws IOException {
    this.out.write(b);
    this.writtenCount++;
  }

  @Override
  public void write(byte[] buf, int off, int len) throws IOException {
    this.out.write(buf, off, len);
    this.writtenCount += len;
  }

}
