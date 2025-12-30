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

/**
 * Provides random access to an MXF Generic Stream.
 */
public class GenericStreamReader extends InputStream {

  final RandomAccessFileInfo info;
  final RandomAccessInputSource source;

  AUID elementKey;
  Long elementLength;
  Long remainingElementBytes;

  /**
   * Creates a GenericStreamReader for a Generic Stream.
   * 
   * @param info   Information about the MXF file.
   * @param source Random access source for the MXF file.
   * @throws IOException  If an I/O error occurs.
   * @throws KLVException If a KLV error occurs.
   */
  GenericStreamReader(RandomAccessFileInfo info, RandomAccessInputSource source) throws IOException, KLVException {
    Objects.requireNonNull(info);
    Objects.requireNonNull(source);

    this.info = info;
    this.source = source;
  }

  /**
   * Gets the key of the Generic Stream element.
   * 
   * @return The element key.
   */
  public AUID getElementKey() {
    return this.elementKey;
  }

  /**
   * Gets the total length of the Generic Stream element's value.
   * 
   * @return The element length in bytes.
   */
  public long getElementLength() {
    return this.elementLength;
  }

  /**
   * Gets the number of bytes remaining in the Generic Stream element from
   * the current position.
   * 
   * @return The number of remaining bytes.
   */
  public long getRemainingElementBytes() {
    return this.remainingElementBytes;
  }

  /**
   * Seeks to the specified Generic Stream.
   * 
   * @param gsSID The BodySID of the Generic Stream to seek to.
   * @throws IOException  If an I/O error occurs.
   * @throws KLVException If a KLV error occurs.
   */
  public void seek(long gsSID) throws IOException, KLVException {
    if (!this.info.getGenericStreams().contains(gsSID)) {
      throw new RuntimeException(String.format("The Generic Stream %d does not exist", gsSID));
    }

    long filePosition = this.info.gsToFilePosition(gsSID, 0);
    this.source.position(filePosition);
    MXFDataInput mis = new MXFDataInput(this.source);
    this.elementKey = mis.readAUID();
    this.elementLength = mis.readBERLength();
    this.remainingElementBytes = this.elementLength;
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
