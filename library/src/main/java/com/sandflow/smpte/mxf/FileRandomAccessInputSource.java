package com.sandflow.smpte.mxf;

import java.io.IOException;
import java.io.RandomAccessFile;

import com.sandflow.smpte.mxf.RandomAccessReader.RandomAccessInputSource;

public class FileRandomAccessInputSource extends RandomAccessInputSource {

  final RandomAccessFile rap;

  FileRandomAccessInputSource(RandomAccessFile rap) {
    this.rap = rap;
  }

  @Override
  public int read() throws IOException {
    return rap.read();
  }

  @Override
  public void position(long pos) throws IOException {
    rap.seek(pos);
  }

  @Override
  public long size() throws IOException {
    return rap.length();
  }

  @Override
  public long position() throws IOException {
    return rap.getFilePointer();
  }

  @Override
  public int read(byte[] b, int off, int len) throws IOException {
    return rap.read(b, off, len);
  }

  @Override
  public int available() throws IOException {
    return (int) Long.min(this.size() - this.position(), Integer.MAX_VALUE);
  }

  @Override
  public void close() throws IOException {
    this.rap.close();
  }

  @Override
  public boolean markSupported() {
    return false;
  }

  @Override
  public int read(byte[] b) throws IOException {
    return rap.read(b);
  }

  @Override
  public long skip(long n) throws IOException {
    long actualSkip = Long.min(n, this.available());
    this.position(this.position() + actualSkip);
    return actualSkip;
  }
  
}
