/*
 * Copyright (c) Sandflow Consulting, LLC
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

/**
* @author Pierre-Anthony Lemieux
*/

package com.sandflow.smpte.util;

import java.io.IOException;
import java.io.RandomAccessFile;

public class FileRandomAccessInputSource extends RandomAccessInputSource {

  final RandomAccessFile rap;

  public FileRandomAccessInputSource(RandomAccessFile rap) {
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
