package com.sandflow.smpte.util;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;

public class CountingOutputStream extends FilterOutputStream {

  private long written = 0;

  public CountingOutputStream(OutputStream out) {
    super(out);
  }

  @Override
  public void write(int b) throws IOException {
    super.write(b);
    this.written++;
  }

  @Override
  public void write(byte[] b) throws IOException {
    super.write(b);
    this.written += b.length;
  }

  @Override
  public void write(byte[] b, int off, int len) throws IOException {
    super.write(b, off, len);
    this.written += len;
  }

  public long written() {
    return this.written;
  }
}
