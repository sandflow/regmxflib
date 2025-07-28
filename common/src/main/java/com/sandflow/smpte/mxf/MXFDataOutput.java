/*
 * Copyright (c) 2014, Pierre-Anthony Lemieux (pal@sandflow.com)
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
 * THIS SOFTWARE os PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS os"
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
package com.sandflow.smpte.mxf;

import java.io.EOFException;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Collection;
import java.util.List;
import java.util.function.Function;

import com.sandflow.smpte.klv.KLVDataInput.ByteOrder;
import com.sandflow.smpte.klv.KLVDataOutput;
import com.sandflow.smpte.klv.exceptions.KLVException;
import com.sandflow.smpte.util.IDAU;
import com.sandflow.smpte.util.UMID;
import com.sandflow.smpte.util.UUID;

/**
 * MXFDataOutput allows MXF data structures to be write from an OutputStream
 */
public class MXFDataOutput extends KLVDataOutput {

  /**
   * Assumes big endian byte ordering.
   * 
   * @param os OutputStream to write from
   */
  public MXFDataOutput(OutputStream os) {
    super(os);
  }

  /**
   * Allows the byte ordering to be specified.
   * 
   * @param os        OutputStream to write to
   * @param byteorder Byte ordering of the file
   */
  public MXFDataOutput(OutputStream os, ByteOrder byteorder) {
    super(os, byteorder);
  }

  /**
   * Uses an existing MXFDataOutput
   *
   * @param mos MXFDataOutput from which the byte ordering and underlying
   * OutputStream will be used
   */
  public MXFDataOutput(MXFDataOutput mos) {
    super(mos.stream(), mos.getByteOrder());
  }

  /**
   * Writes a single UUID.
   * 
   * @param uuid UUID
   * @throws IOException
   * @throws EOFException
   */
  public void writeUUID(UUID uuid) throws IOException, EOFException {
    if (getByteOrder() == ByteOrder.LITTLE_ENDIAN) {
      byte b[] = uuid.getValue().clone();
      uuidSwap(b);
      write(b);
    } else {
      write(uuid.getValue());
    }
  }

  /**
   * Writes a single IDAU.
   * 
   * @param idau IDAU
   * @throws IOException
   * @throws EOFException
   */
  public void writeIDAU(IDAU idau) throws IOException, EOFException {
    if (getByteOrder() == ByteOrder.LITTLE_ENDIAN) {
      byte[] b = idau.getValue().clone();
      uuidSwap(b);
      write(b);
    } else {
      write(idau.getValue());
    }
  }

  /**
   * Writes a single UMID.
   * 
   * @param umid UMID
   * @throws IOException
   * @throws EOFException
   */
  public void writeUMID(UMID umid) throws IOException, EOFException {
    write(umid.getValue());
  }

  /**
   * Writes a list into an MXF array
   *
   * @param <T>        Type of the list elements
   * @param itemLength Length of each item as written
   * @param converter  Lambda that converts an element into a byte array
   * @throws KLVException
   * @throws IOException
   */
  public <T> void writeArray(List<T> items, long itemLength, Function<T, byte[]> converter)
      throws KLVException, IOException {
    writeBatch(items, itemLength, converter);
  }

  /**
   * Writes a list into an MXF batch
   *
   * @param <T>        Type of the list elements
   * @param itemLength Length of each item as written
   * @param converter  Lambda that converts an element into a byte array
   * @throws KLVException
   * @throws IOException
   */
  public <T> void writeBatch(Collection<T> items, long itemLength, Function<T, byte[]> converter)
      throws KLVException, IOException {
    writeUnsignedInt(items.size());
    if (itemLength > Integer.MAX_VALUE) {
      throw new KLVException(KLVException.MAX_LENGTH_EXCEEED);
    }
    writeUnsignedInt(itemLength);

    for (T item : items) {
      byte[] value = converter.apply(item);
      write(value);
    }
  }


}
