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
package com.sandflow.smpte.klv;

import java.io.EOFException;
import java.io.IOException;
import java.io.OutputStream;

import com.sandflow.smpte.klv.KLVDataInput.ByteOrder;
import com.sandflow.smpte.klv.exceptions.KLVException;
import com.sandflow.smpte.util.AUID;
import com.sandflow.smpte.util.UL;

/**
 * KLVDataOutput allows KLV data structures to be write to an OutputStream
 * 
 * TODO: documentation
 */
public class KLVDataOutput {

  private ByteOrder byteorder;
  private OutputStream os;
  private long written = 0;

  /**
   * Assumes big endian byte ordering.
   * 
   * @param os OutputStream to write to
   */
  public KLVDataOutput(OutputStream os) {
    this(os, ByteOrder.BIG_ENDIAN);
  }

  /**
   * Allows the byte ordering to be specified.
   * 
   * @param os        OutputStream to write to
   * @param byteorder Byte ordering of the file
   */
  public KLVDataOutput(OutputStream os, ByteOrder byteorder) {

    this.os = os;
    this.byteorder = byteorder;
  }

  public long getWrittenCount() {
    return this.written;
  }

  /**
   * @return The underlying output stream
   */
  public OutputStream stream() {
    return this.os;
  }

  /**
   * Byte order of the stream.
   * 
   * @return Byte order of the stream
   */
  public ByteOrder getByteOrder() {
    return byteorder;
  }

  /**
   * Writes a single UL.
   * 
   * @return UL
   * @throws IOException
   * @throws EOFException
   */
  public void writeUL(UL ul) throws IOException, EOFException {
    this.write(ul.getValue());
  }

  /**
   * Writes a single AUID.
   * 
   * @return AUID
   * @throws IOException
   * @throws EOFException
   */
  public void writeAUID(AUID auid) throws IOException, EOFException {
    this.write(auid.getValue());
  }

  /**
   * Writes a single BER-encoded length. The maximum length of the encoded
   * length is 8 bytes. DEVIATION: the minimum length is 4 bytes for
   * compatibility with ASDCPLib
   *
   * @return Length
   * @throws EOFException
   * @throws IOException
   * @throws KLVException
   */
  public void writeBERLength(long l) throws IOException {
    if (l < 0) {
      throw new IllegalArgumentException("Length cannot be negative");
    }

    // if (l < 0x80) {
    // this.write((int) l);
    // return;
    // }

    int n = 3;
    long tmp = l >> (n * 8);
    while (tmp > 0) {
      tmp >>= 8;
      n++;
    }
    this.write(0x80 | n);
    for (int i = n - 1; i >= 0; i--) {
      write((int) (l >> (i << 3)) & 0xFF);
    }
  }

  /**
   * Writes a single 4-byte BER-encoded length.
   * 
   * @return Length
   * @throws EOFException
   * @throws IOException
   * @throws KLVException
   */
  public void writeBER4Length(long l) throws IOException {
    if (l < 0) {
      throw new IllegalArgumentException("Length cannot be negative");
    }

    if (l > 16_777_215) {
      throw new IllegalArgumentException("Length cannot be greater than 16,777,215");
    }

    int n = 3;
    this.write(0x80 | n);
    for (int i = n - 1; i >= 0; i--) {
      write((int) (l >> (i << 3)) & 0xFF);
    }
  }

  /**
   * Writes a single KLV triplet.
   * 
   * @return KLV Triplet
   * @throws IOException
   * @throws EOFException
   * @throws KLVException
   */
  public void writeTriplet(Triplet t) throws IOException {
    this.writeAUID(t.getKey());
    this.writeBERLength(t.getLength());
    this.write(t.getValue());
  }

  /**
   * This is for compatibility with ASDCPLib, which expects BER Lenghts to be 4
   * bytes for Partition Packs
   *
   */
  public void writeBER4Triplet(Triplet triplet) throws IOException {
    writeAUID(triplet.getKey());
    writeBER4Length(triplet.getLength());
    write(triplet.getValue());
  }

  protected static final void swap(byte[] array, int i, int j) {
    byte tmp = array[i];
    array[i] = array[j];
    array[j] = tmp;
  }

  protected static final void uuidSwap(byte[] uuid) {
    /* swap the 32-bit word of the UUID */
    swap(uuid, 0, 3);
    swap(uuid, 1, 2);

    /* swap the first 16-bit word of the UUID */
    swap(uuid, 4, 5);

    /* swap the second 16-bit word of the UUID */
    swap(uuid, 6, 7);

  }

  public void writeUnsignedByte(short v) throws IOException, EOFException {
    this.write((byte) (v & 0xFF));
  }

  public void writeByte(byte v) throws IOException {
    this.write(v);
  }

  public void writeUnsignedInt(long v) throws IOException, EOFException {
    this.writeInt((int) (v & 0xFFFFFFFF));
  }

  public void write(int b) throws IOException {
    this.os.write(b);
    this.written++;
  }

  public void write(byte[] b, int off, int len) throws IOException {
    this.os.write(b, off, len);
    this.written += len;
  }

  public void write(byte[] b) throws IOException {
    this.write(b, 0, b.length);
  }

  public void flush() throws IOException {
    this.os.flush();
  }

  public void writeInt(int v) throws IOException {
    if (byteorder == ByteOrder.BIG_ENDIAN) {
      write(((v >> 24) & 0xFF));
      write(((v >> 16) & 0xFF));
      write(((v >> 8) & 0xFF));
      write((v & 0xFF));
    } else {
      write((v & 0xFF));
      write(((v >> 8) & 0xFF));
      write(((v >> 16) & 0xFF));
      write(((v >> 24) & 0xFF));
    }
  }

  public void writeLong(long v) throws IOException {
    if (byteorder == ByteOrder.BIG_ENDIAN) {
      write((int) ((v >> 56) & 0xFF));
      write((int) ((v >> 48) & 0xFF));
      write((int) ((v >> 40) & 0xFF));
      write((int) ((v >> 32) & 0xFF));
      write((int) ((v >> 24) & 0xFF));
      write((int) ((v >> 16) & 0xFF));
      write((int) ((v >> 8) & 0xFF));
      write((int) (v & 0xFF));

    } else {
      write((int) (v & 0xFF));
      write((int) ((v >> 8) & 0xFF));
      write((int) ((v >> 16) & 0xFF));
      write((int) ((v >> 24) & 0xFF));
      write((int) ((v >> 32) & 0xFF));
      write((int) ((v >> 40) & 0xFF));
      write((int) ((v >> 48) & 0xFF));
      write((int) ((v >> 56) & 0xFF));
    }
  }

  public void writeUnsignedShort(int v) throws IOException, EOFException {
    this.writeShort((short) (v & 0xFFFF));
  }

  public void writeShort(int v) throws IOException {
    if (byteorder == ByteOrder.BIG_ENDIAN) {
      this.write((v >> 8) & 0xFF);
      this.write(v & 0xFF);
    } else {
      this.write(v & 0xFF);
      this.write((v >> 8) & 0xFF);
    }
  }

}
