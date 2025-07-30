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

package com.sandflow.smpte.mxf;

import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;

import com.sandflow.smpte.klv.exceptions.KLVException;
import com.sandflow.smpte.util.AUID;
import com.sandflow.smpte.util.RandomAccessInputSource;

public class GenericStreamReader extends InputStream {

  final RandomAccessFileInfo info;
  final RandomAccessInputSource source;

  AUID elementKey;
  Long elementLength;
  Long remainingElementBytes;

  GenericStreamReader(RandomAccessFileInfo info, RandomAccessInputSource source) throws IOException, KLVException {
    Objects.requireNonNull(info);
    Objects.requireNonNull(source);
    
    this.info = info;
    this.source = source;
  }

  public AUID getElementKey() {
    return this.elementKey;
  }

  public long getElementLength() {
    return this.elementLength;
  }

  public long getRemainingElementBytes() {
    return this.remainingElementBytes;
  }

  public void seek(long gsSID) throws IOException, KLVException {
    if (! this.info.getGenericStreams().contains(gsSID)) {
      throw new RuntimeException(String.format("The Generic Stream %d does not exist", gsSID));
    }

    long filePosition = this.info.gsToFilePosition(gsSID, 0);
    this.source.position(filePosition);
    MXFDataInput mis = new MXFDataInput(this.source);
    this.elementKey = mis.readAUID();
    this.elementLength = mis.readBERLength();
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
