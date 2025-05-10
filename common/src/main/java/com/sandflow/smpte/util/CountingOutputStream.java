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

  public long written() {
    return this.written;
  }
}
