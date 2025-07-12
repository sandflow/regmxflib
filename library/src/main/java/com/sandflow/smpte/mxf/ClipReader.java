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

import com.sandflow.smpte.klv.exceptions.KLVException;
import com.sandflow.smpte.mxf.ECTracks.TrackInfo;
import com.sandflow.smpte.util.AUID;
import com.sandflow.smpte.util.RandomAccessInputSource;

public class ClipReader extends InputStream {
  final AUID elementKey;
  final long elementLength;
  final long essenceOffset;
  final RandomAccessFileInfo info;
  final RandomAccessInputSource source;

  long remainingElementBytes;

  ClipReader(RandomAccessFileInfo info, RandomAccessInputSource source) throws IOException, KLVException {
    this.info = info;
    this.source = source;

    long clipStartPosition = this.info.ecToFilePositions(0);
    this.source.position(clipStartPosition);
    MXFInputStream mis = new MXFInputStream(this.source);
    this.elementKey = mis.readAUID();
    this.elementLength = mis.readBERLength();

    /*
     * DEVIATION: Some versions of ASDCPLib index from the start of the K of the
     * clip instead of from the start of the V of the clip
     */
    ECTracks tracks = new ECTracks(this.info.getPreface());
    TrackInfo ti = tracks.getTrackInfo(this.elementKey);
    if (ti != null && Labels.IMF_IABEssenceClipWrappedContainer.equals(ti.descriptor().ContainerFormat)
        && this.info.euToECPosition(0) != 0) {
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

  public long getRemainingElementBytes() {
    return this.remainingElementBytes;
  }

  public long getSize() {
    return this.info.getEUCount();
  }

  public void seek(long euPosition) throws IOException {
    /* TODO: handle EOF */
    long ecPosition = this.info.euToECPosition(euPosition);
    long filePosition = this.info.ecToFilePositions(ecPosition) + this.essenceOffset;
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
