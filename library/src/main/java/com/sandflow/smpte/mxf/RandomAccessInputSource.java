package com.sandflow.smpte.mxf;

import java.io.IOException;
import java.io.InputStream;

public abstract class RandomAccessInputSource extends InputStream {
  /**
   * Set the position (in bytes) from the beginning of the source.
   *
   * @param pos Position in bytes
   * @throws IOException
   */
  public abstract void position(long pos) throws IOException;

  /**
   * Returns the size (in bytes) of the source.
   * 
   * @return Size in bytes
   * @throws IOException
   */
  public abstract long size() throws IOException;

  /**
   * Returns the position (in bytes) from the beginning of the source.
   * 
   * @return Position in bytes
   * @throws IOException
   */
  public abstract long position() throws IOException;
}