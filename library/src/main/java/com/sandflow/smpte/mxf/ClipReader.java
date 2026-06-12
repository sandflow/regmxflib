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
import com.sandflow.smpte.mxf.GCEssenceTracks.TrackInfo;
import com.sandflow.smpte.util.AUID;
import com.sandflow.smpte.util.RandomAccessInputSource;

/**
 * Provides random access to an MXF clip-wrapped essence container.
 */
public class ClipReader extends InputStream {
  final AUID elementKey;
  final long elementLength;
  final long essenceOffset;
  final RandomAccessFileInfo info;
  final RandomAccessInputSource source;

  long remainingElementBytes;

  /**
   * Creates a ClipReader for a clip-wrapped essence container.
   * 
   * @param info   Information about the MXF file.
   * @param source Random access source for the MXF file.
   * @throws IOException  If an I/O error occurs.
   * @throws KLVException If a KLV error occurs.
   */
  public ClipReader(RandomAccessFileInfo info, RandomAccessInputSource source) throws IOException, KLVException {
    this.info = info;
    this.source = source;

    long clipStartPosition = this.info.ecToFilePositions(0);
    this.source.position(clipStartPosition);
    MXFDataInput mis = new MXFDataInput(this.source);
    this.elementKey = mis.readAUID();
    this.elementLength = mis.readBERLength();

    /*
     * ECXEPTION: Some versions of ASDCPLib index from the start of the K of the
     * clip instead of from the start of the V of the clip
     */
    GCEssenceTracks tracks = new GCEssenceTracks(this.info.getPreface());
    TrackInfo ti = tracks.getTrackInfo(this.elementKey);
    if (ti != null && Labels.IMF_IABEssenceClipWrappedContainer.equals(ti.descriptor().ContainerFormat)
        && this.info.euToECPosition(0) != 0) {
      this.essenceOffset = 0;
    } else {
      this.essenceOffset = this.source.position() - clipStartPosition;
    }

    this.seek(0);
  }

  /**
   * Gets the key of the clip-wrapped essence element.
   * 
   * @return The element key.
   */
  public AUID getElementKey() {
    return this.elementKey;
  }

  /**
   * Gets the total length of the clip-wrapped essence element's value.
   * 
   * @return The element length in bytes.
   */
  public long getElementLength() {
    return this.elementLength;
  }

  /**
   * Gets the number of bytes remaining in the clip-wrapped essence element from
   * the current position.
   * 
   * @return The number of remaining bytes.
   */
  public long getRemainingElementBytes() {
    return this.remainingElementBytes;
  }

  /**
   * Gets the total number of Edit Units in the essence container.
   * 
   * @return The count of Edit Units.
   */
  public long getEUCount() {
    return this.info.getEUCount();
  }

  /**
   * Seeks to the specified Edit Unit within the essence container.
   * 
   * @param euPosition The zero-based index of the Edit Unit to seek to.
   * @throws IOException If an I/O error occurs.
   */
  public void seek(long euPosition) throws IOException {
    long ecPosition = this.info.euToECPosition(euPosition);
    long filePosition = this.info.ecToFilePositions(ecPosition) + this.essenceOffset;
    this.source.position(filePosition);
    this.remainingElementBytes = this.elementLength - ecPosition;
  }

  /**
   * Reads the next byte of data from the input stream.
   * 
   * @return The next byte of data, or -1 if the end of the stream is reached.
   * @throws IOException If an I/O error occurs.
   */
  @Override
  public int read() throws IOException {
    if (this.remainingElementBytes == 0)
      return -1;
    int r = source.read();
    this.remainingElementBytes = r == -1 ? 0 : this.remainingElementBytes - 1;
    return r;
  }

  /**
   * Reads some number of bytes from the input stream and stores them into the
   * buffer array {@code b}.
   * 
   * @param b   The buffer into which the data is read.
   * @param off The start offset in array {@code b} at which the data is
   *            written.
   * @param len The maximum number of bytes to read.
   * @return The total number of bytes read into the buffer, or -1 if there is
   *         no more data because the end of the stream has been reached.
   * @throws IOException If an I/O error occurs.
   */
  @Override
  public int read(byte[] b, int off, int len) throws IOException {
    if (this.remainingElementBytes == 0)
      return -1;
    int r = source.read(b, off, len);
    this.remainingElementBytes = r == -1 ? 0 : this.remainingElementBytes - r;
    return r;
  }

  /**
   * Skips over and discards {@code n} bytes of data from this input stream.
   * 
   * @param n The number of bytes to be skipped.
   * @return The actual number of bytes skipped.
   * @throws IOException If an I/O error occurs.
   */
  @Override
  public long skip(long n) throws IOException {
    if (this.remainingElementBytes == 0)
      return -1;
    long s = this.source.skip(n);
    this.remainingElementBytes = this.remainingElementBytes - s;
    return s;
  }

  /**
   * Closes this input stream and releases any system resources associated with
   * the stream. This implementation does nothing, as the underlying
   * {@link RandomAccessInputSource} is managed externally.
   */
  @Override
  public void close() throws IOException {
    /*
     * do nothing: it is the responsibility of the caller to close the
     * underlying RandomAccessInputSource
     */
  }
}
