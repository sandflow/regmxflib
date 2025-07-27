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
package com.sandflow.smpte.klv;

import static com.sandflow.smpte.klv.exceptions.KLVException.MAX_LENGTH_EXCEEED;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;

import com.sandflow.smpte.klv.exceptions.KLVException;
import com.sandflow.smpte.util.AUID;
import com.sandflow.smpte.util.UL;

/**
 * KLVInputStream allows KLV data structures to be read from an InputStream
 */
public class KLVInputStream {

  private final InputStream is;
  private long read = 0;

  /**
   * Possible byte ordering of a KLV packet
   */
  public enum ByteOrder {
    LITTLE_ENDIAN,
    BIG_ENDIAN
  }

  private ByteOrder byteorder;

  /**
   * Assumes big endian byte ordering.
   * 
   * @param is InputStream to read from
   */
  public KLVInputStream(InputStream is) {
    this(is, ByteOrder.BIG_ENDIAN);
  }

  /**
   * Allows the byte ordering to be specified.
   * 
   * @param is        InputStream to read from
   * @param byteorder Byte ordering of the file
   */
  public KLVInputStream(InputStream is, ByteOrder byteorder) {
    this.is = is;
    this.byteorder = byteorder;
  }

  public InputStream stream() {
    return this.is;
  }

  public int read() throws IOException {
    int r = this.is.read();
    this.read++;
    return r;
  }

  public void resetCount() {
    this.read = 0;
  }

  public long getReadCount() {
    return this.read;
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
   * Reads a single UL.
   * 
   * @return UL
   * @throws IOException
   * @throws EOFException
   */
  public UL readUL() throws IOException, EOFException {
    byte[] ul = new byte[16];

    readFully(ul);

    return new UL(ul);
  }

  /**
   * Reads a single AUID.
   * 
   * @return AUID
   * @throws IOException
   * @throws EOFException
   */
  public AUID readAUID() throws IOException, EOFException {
    byte[] auid = new byte[16];

    readFully(auid);

    return new AUID(auid);
  }

  /**
   * Reads a single BER-encoded length. The maximum length of the encoded length
   * is 8 bytes.
   * 
   * @return Length
   * @throws EOFException
   * @throws IOException
   * @throws KLVException
   */
  public long readBERLength() throws EOFException, IOException, KLVException {

    long val = 0;

    int b = read();

    if (b <= 0) {
      throw new EOFException();
    }

    if ((b & 0x80) == 0) {
      return b;
    }

    int bersz = (b & 0x0f);

    if (bersz > 8) {
      throw new KLVException(MAX_LENGTH_EXCEEED);
    }

    byte[] octets = new byte[bersz];

    readFully(octets);

    for (int i = 0; i < bersz; i++) {
      int tmp = (((int) octets[i]) & 0xFF);
      val = (val << 8) + tmp;

      if (val > Integer.MAX_VALUE) {
        throw new KLVException(MAX_LENGTH_EXCEEED);
      }
    }

    return val;
  }

  /**
   * Reads a single KLV triplet.
   * 
   * @return KLV Triplet
   * @throws IOException
   * @throws EOFException
   * @throws KLVException
   */
  public Triplet readTriplet() throws IOException, EOFException, KLVException {
    AUID auid = readAUID();

    long len = readBERLength();

    if (len > Integer.MAX_VALUE) {
      throw new KLVException(MAX_LENGTH_EXCEEED);
    }

    byte[] value = new byte[(int) len];

    readFully(value);

    return new MemoryTriplet(auid, value);
  }

  public int read(byte[] b, int off, int len) throws IOException {
    int r = this.is.read(b, off, len);
    if (r != -1)
      this.read += r;
    return r;
  }

  public int read(byte[] b) throws IOException {
    return this.read(b, 0, b.length);
  }

  public final void readFully(byte[] b) throws IOException {
    this.readFully(b, 0, b.length);
  }

  public final void readFully(byte[] b, int off, int len) throws IOException {
    if (off + len > b.length) {
      throw new IOException();
    }
    int c;
    for (int n = 0; n < len; n += c) {
      c = this.read(b, off + n, len - n);
      if (c < 0) {
        throw new EOFException();
      }
    }
  }

  public final byte readByte() throws IOException {
    return (byte) this.read();
  }

  public final int readUnsignedByte() throws IOException {
    return this.read();
  }

  public final short readShort() throws IOException {
    int hi, lo;

    if (byteorder == ByteOrder.BIG_ENDIAN) {
      hi = readUnsignedByte();
      lo = readUnsignedByte();
    } else {
      lo = readUnsignedByte();
      hi = readUnsignedByte();
    }

    return (short) (lo + (hi << 8));
  }

  public final int readUnsignedShort() throws IOException {
    return this.readShort() & 0xFFFF;
  }

  public final int readInt() throws IOException {
    int b0, b1, b2, b3;

    if (byteorder == ByteOrder.BIG_ENDIAN) {
      b3 = readUnsignedByte();
      b2 = readUnsignedByte();
      b1 = readUnsignedByte();
      b0 = readUnsignedByte();
    } else {
      b0 = readUnsignedByte();
      b1 = readUnsignedByte();
      b2 = readUnsignedByte();
      b3 = readUnsignedByte();
    }

    return b0 + (b1 << 8) + (b2 << 16) + (b3 << 24);
  }

  public long readUnsignedInt() throws IOException, EOFException {
    return this.readInt() & 0xFFFFFFFFL;
  }

  public final long readLong() throws IOException {
    int b0, b1, b2, b3, b4, b5, b6, b7;

    if (byteorder == ByteOrder.BIG_ENDIAN) {
      b7 = readUnsignedByte();
      b6 = readUnsignedByte();
      b5 = readUnsignedByte();
      b4 = readUnsignedByte();
      b3 = readUnsignedByte();
      b2 = readUnsignedByte();
      b1 = readUnsignedByte();
      b0 = readUnsignedByte();
    } else {
      b0 = readUnsignedByte();
      b1 = readUnsignedByte();
      b2 = readUnsignedByte();
      b3 = readUnsignedByte();
      b4 = readUnsignedByte();
      b5 = readUnsignedByte();
      b6 = readUnsignedByte();
      b7 = readUnsignedByte();
    }

    return b0 | (b1 << 8) | (b2 << 16) | (b3 << 24) | (b4 << 32) | (b5 << 40) | (b6 << 48) | (b7 << 56);
  }

  public long skipFully(long n) throws IOException {
    if (n < 0)
      throw new IllegalArgumentException("Cannot skip over negative legnths");

    if (n == 0)
      return 0;

    byte[] buf = new byte[1024];
    long r = n;
    while (r > 0) {
      long s = this.skip(r);
      if (s > 0) {
        r -= s;
      } else {
        int b = this.read(buf);
        if (b == -1) {
          break;
        }
        r = r - b;
      }
    }
    return n - r;
  }

  public long skip(long n) throws IOException {
    long r = this.is.skip(n);
    this.read += r;
    return r;
  }

}
