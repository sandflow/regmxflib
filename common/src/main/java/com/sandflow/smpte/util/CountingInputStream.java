/*
 * Copyright (c) Pierre-Anthony Lemieux (pal@sandflow.com)
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * * Redistributions of source code must retain the above copyright notice, this
 *   list of conditions and the following disclaimer.
 * * Redistributions in binary form must reproduce the above copyright notice,
 *   this list of conditions and the following disclaimer in the documentation
 *   and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */
package com.sandflow.smpte.util;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Counts the number of bytes read from an InputStream and allows a maximum
 * number of bytes to be read to be set (optionally)
 */
public class CountingInputStream extends FilterInputStream {

  long readCount = 0;
  long markCount = 0;

  /**
   * Instantiates a CountingInputStream
   *
   * @param is InputStream from which data will be read
   */
  public CountingInputStream(InputStream is) {
    super(is);
  }

  @Override
  public synchronized void mark(int i) {
    markCount = readCount;
    super.mark(i);
  }

  @Override
  public long skip(long l) throws IOException {
    long sb = super.skip(l);
    if (sb >= 0)
      readCount += sb;
    return sb;
  }

  @Override
  public int read(byte[] bytes, int off, int len) throws IOException {
    int sb = in.read(bytes, off, len);
    if (sb >= 0)
      readCount += sb;
    return sb;
  }

  @Override
  public int read() throws IOException {
    int sb = super.read();
    if (sb >= 0)
      readCount += 1;
    return sb;
  }

  @Override
  public synchronized void reset() throws IOException {
    readCount = markCount;
    super.reset();
  }

  /**
   * @return Returns the number of bytes read since the object was created or
   *         resetCount was called
   */
  public long getReadCount() {
    return readCount;
  }

  /**
   * Resets the number of bytes read to zero.
   */
  public void resetCount() {
    readCount = 0;
  }

}
