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

import com.sandflow.smpte.klv.exceptions.KLVException;
import com.sandflow.smpte.util.RandomAccessInputSource;

/**
 * Provides random access to an MXF frame-wrapped essence container.
 */
public class FrameReader extends StreamingReader {

  final RandomAccessFileInfo info;
  final RandomAccessInputSource source;

  /**
   * Creates a FrameReader for a frame-wrapped essence container.
   * 
   * @param info   Information about the MXF file.
   * @param source Random access source for the MXF file.
   * @throws IOException  If an I/O error occurs.
   * @throws KLVException If a KLV error occurs.
   * @throws MXFException If an MXF error occurs.
   */
  public FrameReader(RandomAccessFileInfo info, RandomAccessInputSource source)
      throws IOException, KLVException, MXFException {
    super(source, null);

    this.info = info;
    this.source = source;

    this.seek(0);
  }

  /**
   * Gets the total number of Edit Units in the essence container.
   * 
   * @return The count of Edit Units.
   */
  public long getSize() {
    return this.info.getEUCount();
  }

  /**
   * Seeks to the specified Edit Unit within the essence container.
   * 
   * @param euPosition The zero-based index of the Edit Unit to seek to.
   * @throws IOException  If an I/O error occurs.
   * @throws KLVException If a KLV error occurs.
   */
  public void seek(long euPosition) throws IOException, KLVException {
    long filePosition = this.info.ecToFilePositions(this.info.euToECPosition(euPosition));
    this.source.position(filePosition);
    this.state = State.READY;
  }

}
