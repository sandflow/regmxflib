/*
 * Copyright (c) Sandflow Consulting LLC
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
package com.sandflow.smpte.mxf;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.function.Function;

import com.sandflow.smpte.klv.KLVDataInput;
import com.sandflow.smpte.klv.exceptions.KLVException;
import com.sandflow.smpte.util.IDAU;
import com.sandflow.smpte.util.UMID;
import com.sandflow.smpte.util.UUID;

/**
 * MXFDataInput allows MXF data structures to be read from an InputStream
 */
public class MXFDataInput extends KLVDataInput {

  /**
   * Assumes big endian byte ordering.
   * 
   * @param is InputStream to read from
   */
  public MXFDataInput(InputStream is) {
    super(is);
  }

  /**
   * Allows the byte ordering to be specified.
   * 
   * @param is        InputStream to read from
   * @param byteorder Byte ordering of the file
   */
  public MXFDataInput(InputStream is, ByteOrder byteorder) {
    super(is, byteorder);
  }

  private static final void swap(byte[] array, int i, int j) {
    byte tmp = array[i];
    array[i] = array[j];
    array[j] = tmp;
  }

  private static final void uuidLEtoBE(byte[] uuid) {
    /* swap the 32-bit word of the UUID */
    swap(uuid, 0, 3);
    swap(uuid, 1, 2);

    /* swap the first 16-bit word of the UUID */
    swap(uuid, 4, 5);

    /* swap the second 16-bit word of the UUID */
    swap(uuid, 6, 7);
  }

  /**
   * Reads a single UUID.
   * 
   * @return UUID
   * @throws IOException
   * @throws EOFException
   */
  public UUID readUUID() throws IOException, EOFException {
    byte[] uuid = new byte[16];

    readFully(uuid);

    if (getByteOrder() == ByteOrder.LITTLE_ENDIAN) {

      uuidLEtoBE(uuid);

    }

    return new UUID(uuid);
  }

  /**
   * Reads a single IDAU.
   * 
   * @return IDAU
   * @throws IOException
   * @throws EOFException
   */
  public IDAU readIDAU() throws IOException, EOFException {
    byte[] idau = new byte[16];

    readFully(idau);

    if (getByteOrder() == ByteOrder.LITTLE_ENDIAN) {

      uuidLEtoBE(idau);

    }

    return new IDAU(idau);
  }

  /**
   * Reads a single UMID.
   * 
   * @return UMID
   * @throws IOException
   * @throws EOFException
   */
  public UMID readUMID() throws IOException, EOFException {
    byte[] umid = new byte[32];

    readFully(umid);

    return new UMID(umid);
  }

  /**
   * Reads an MXF array into a Java Collection
   *
   * @param <T> Type of the collection elements
   * @param <W> TripletValueAdapter that is used to convert MXF array elements
   *            into Java collection elements
   * @return Collection of elements of type T
   * @throws KLVException
   * @throws IOException
   */
  public <T> Collection<T> readArray(Function<byte[], T> converter) throws KLVException, IOException {
    return readBatch(converter);
  }

  /**
   * Reads an MXF batch into a Java Collection
   *
   * @param <T> Type of the collection elements
   * @param <W> TripletValueAdapter that is used to convert MXF batch elements
   *            into Java collection elements
   * @return Collection of elements of type T
   * @throws KLVException
   * @throws IOException
   */
  public <T> Collection<T> readBatch(Function<byte[], T> converter) throws KLVException, IOException {
    ArrayList<T> batch = new ArrayList<>();
    long itemcount = readUnsignedInt();
    long itemlength = readUnsignedInt();
    if (itemlength > Integer.MAX_VALUE) {
      throw new KLVException(KLVException.MAX_LENGTH_EXCEEED);
    }
    for (int i = 0; i < itemcount; i++) {
      byte[] value = new byte[(int) itemlength];
      read(value);
      batch.add(converter.apply(value));
    }
    return batch;
  }
}
