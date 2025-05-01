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

import java.io.DataOutput;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.OutputStream;

import com.sandflow.smpte.klv.KLVInputStream.ByteOrder;
import com.sandflow.smpte.klv.exceptions.KLVException;
import com.sandflow.smpte.util.AUID;
import com.sandflow.smpte.util.UL;

/**
 * KLVOutputStream allows KLV data structures to be write to an OutputStream
 */
public class KLVOutputStream extends OutputStream implements DataOutput {

  private OutputStream os;
  private DataOutputStream dos;
  private ByteOrder byteorder;

  /**
   * Assumes big endian byte ordering.
   * 
   * @param os OutputStream to write to
   */
  public KLVOutputStream(OutputStream os) {
    this(os, ByteOrder.BIG_ENDIAN);
  }

  /**
   * Allows the byte ordering to be specified.
   * 
   * @param os        OutputStream to write to
   * @param byteorder Byte ordering of the file
   */
  public KLVOutputStream(OutputStream os, ByteOrder byteorder) {

    if (os == null)
      throw new NullPointerException();

    this.os = os;
    dos = new DataOutputStream(os);
    this.byteorder = byteorder;
  }

  /**
   * @return The underlying output stream
   */
  OutputStream stream() {
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
    dos.write(ul.getValue());
  }

  /**
   * Writes a single AUID.
   * 
   * @return AUID
   * @throws IOException
   * @throws EOFException
   */
  public void writeAUID(AUID auid) throws IOException, EOFException {
    dos.write(auid.getValue());
  }

  /**
   * Writes a single BER-encoded length. The maximum length of the encoded length
   * os 8 bytes.
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

    /* short form */
    if (l < 0x80) {
      dos.write((int) l);
      return;
    }

    /* long form */
    int n = 0;
    long tmp = l;
    while (tmp > 0) {
      tmp >>= 8;
      n++;
    }
    dos.write(0x80 | n);
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
  public void writeTriplet(Triplet t) throws IOException, EOFException, KLVException {
    writeAUID(t.getKey());
    writeBERLength(t.getLength());
    write(t.getValue());
  }

  @Override
  public final void write(byte[] bytes) throws IOException {
    dos.write(bytes);
  }

  @Override
  public final void write(byte[] bytes, int i, int i1) throws IOException {
    dos.write(bytes, i, i1);
  }

  @Override
  public final void writeBoolean(boolean b) throws IOException {
    dos.writeBoolean(b);
  }

  public void writeUnsignedByte(short v) throws IOException, EOFException {
    write((byte) (v & 0xFF));
  }

  public void writeUnsignedShort(int v) throws IOException, EOFException {
    writeShort((short) (v & 0xFFFF));
  }

  public void writeUnsignedInt(long v) throws IOException, EOFException {
    writeInt((int) (v & 0xFFFFFFFF));
  }

  @Override
  public void close() throws IOException {
    /* do nothink */
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

  @Override
  public void flush() throws IOException {
    dos.flush();
  }

  @Override
  public void write(int b) throws IOException {
    dos.write(b);
  }

  @Override
  public void writeByte(int v) throws IOException {
    dos.writeByte(v);
  }

  @Override
  public void writeBytes(String s) throws IOException {
    dos.writeBytes(s);
  }

  @Override
  public void writeChar(int v) throws IOException {
    dos.writeChar(v);
  }

  @Override
  public void writeChars(String s) throws IOException {
    dos.writeChars(s);
  }

  @Override
  public void writeDouble(double v) throws IOException {
    dos.writeDouble(v);
  }

  @Override
  public void writeFloat(float v) throws IOException {
    dos.writeFloat(v);
  }

  @Override
  public void writeInt(int v) throws IOException {
    if (byteorder == ByteOrder.BIG_ENDIAN) {
      dos.writeInt(v);
    } else {
      writeByte((short) (v & 0xFF));
      writeByte((short) ((v >> 8) & 0xFF));
      writeByte((short) ((v >> 16) & 0xFF));
      writeByte((short) ((v >> 24) & 0xFF));
    }
  }

  @Override
  public void writeLong(long v) throws IOException {
    if (byteorder == ByteOrder.BIG_ENDIAN) {
      dos.writeLong(v);
    } else {
      writeByte((short) (v & 0xFF));
      writeByte((short) ((v >> 8) & 0xFF));
      writeByte((short) ((v >> 16) & 0xFF));
      writeByte((short) ((v >> 24) & 0xFF));
      writeByte((short) ((v >> 32) & 0xFF));
      writeByte((short) ((v >> 40) & 0xFF));
      writeByte((short) ((v >> 48) & 0xFF));
      writeByte((short) ((v >> 56) & 0xFF));
    }
  }

  @Override
  public void writeShort(int v) throws IOException {
    if (byteorder == ByteOrder.BIG_ENDIAN) {
      dos.writeShort(v);
    } else {
      dos.writeByte(v & 0xFF);
      dos.writeByte((v >> 8) & 0xFF);
    }
  }

  @Override
  public void writeUTF(String s) throws IOException {
    dos.writeUTF(s);
  }

}
