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

/**
* @author Pierre-Anthony Lemieux
*/

package com.sandflow.smpte.klv;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayInputStream;
import java.io.EOFException;

import org.junit.jupiter.api.Test;

import com.sandflow.smpte.klv.KLVDataInput.ByteOrder;
import com.sandflow.smpte.klv.exceptions.KLVException;
import com.sandflow.smpte.util.AUID;
import com.sandflow.smpte.util.UL;

class KLVDataInputTest {

  @Test
  void testReadLong() throws Exception {
    byte[] NEG_ONE = new byte[] {(byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF};
    var kis = new KLVDataInput(new ByteArrayInputStream(NEG_ONE));
    assertEquals(-1L, kis.readLong());
  }

  @Test
  void testReadUnsignedByte() throws Exception {
    var kis = new KLVDataInput(new ByteArrayInputStream(new byte[] {(byte) 0xFF}));
    assertEquals(255, kis.readUnsignedByte());
  }

  @Test
  void testReadUL() throws Exception {
    byte[] ulBytes = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16};
    KLVDataInput kis = new KLVDataInput(new ByteArrayInputStream(ulBytes));
    UL ul = kis.readUL();
    assertArrayEquals(ulBytes, ul.getBytes());
  }

  @Test
  void testReadAUID() throws Exception {
    byte[] auidBytes = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16};
    KLVDataInput kis = new KLVDataInput(new ByteArrayInputStream(auidBytes));
    AUID auid = kis.readAUID();
    assertArrayEquals(auidBytes, auid.getBytes());
  }

  @Test
  void testReadBERLength() throws Exception {
    /* Short form */
    byte[] shortLen = {0x7F};
    KLVDataInput kis = new KLVDataInput(new ByteArrayInputStream(shortLen));
    assertEquals(127, kis.readBERLength());

    /* Long form 1 byte */
    byte[] longLen1 = {(byte) 0x81, (byte) 0x80};
    kis = new KLVDataInput(new ByteArrayInputStream(longLen1));
    assertEquals(128, kis.readBERLength());

    /* Long form 4 bytes */
    byte[] longLen4 = {(byte) 0x84, 0x01, 0x00, 0x00, 0x00};
    kis = new KLVDataInput(new ByteArrayInputStream(longLen4));
    assertEquals(16777216L, kis.readBERLength());

    /* Error: too long (> 8 bytes) */
    byte[] tooLong = {(byte) 0x89};
    KLVDataInput kisErr = new KLVDataInput(new ByteArrayInputStream(tooLong));
    assertThrows(KLVException.class, kisErr::readBERLength);
    
    /* Error: EOF */
    KLVDataInput kisEOF = new KLVDataInput(new ByteArrayInputStream(new byte[0]));
    assertThrows(EOFException.class, kisEOF::readBERLength);
  }

  @Test
  void testReadTriplet() throws Exception {
    byte[] key = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16};
    byte[] len = {0x02};
    byte[] val = {(byte) 0xAA, (byte) 0xBB};
    
    byte[] data = new byte[key.length + len.length + val.length];
    System.arraycopy(key, 0, data, 0, key.length);
    System.arraycopy(len, 0, data, key.length, len.length);
    System.arraycopy(val, 0, data, key.length + len.length, val.length);

    KLVDataInput kis = new KLVDataInput(new ByteArrayInputStream(data));
    Triplet t = kis.readTriplet();
    
    assertArrayEquals(key, t.getKey().getBytes());
    assertEquals(2, t.getLength());
    assertArrayEquals(val, t.getValue());
  }

  @Test
  void testEndianness() throws Exception {
    byte[] data = {0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08};
    
    /* Big Endian (default) */
    KLVDataInput kisBE = new KLVDataInput(new ByteArrayInputStream(data));
    assertEquals(0x0102, kisBE.readShort());
    kisBE = new KLVDataInput(new ByteArrayInputStream(data));
    assertEquals(0x01020304, kisBE.readInt());
    kisBE = new KLVDataInput(new ByteArrayInputStream(data));
    assertEquals(0x0102030405060708L, kisBE.readLong());

    /* Little Endian */
    KLVDataInput kisLE = new KLVDataInput(new ByteArrayInputStream(data), ByteOrder.LITTLE_ENDIAN);
    assertEquals(0x0201, kisLE.readShort());
    kisLE = new KLVDataInput(new ByteArrayInputStream(data), ByteOrder.LITTLE_ENDIAN);
    assertEquals(0x04030201, kisLE.readInt());
    kisLE = new KLVDataInput(new ByteArrayInputStream(data), ByteOrder.LITTLE_ENDIAN);
    assertEquals(0x0807060504030201L, kisLE.readLong());
  }
  
  @Test
  void testSkipFully() throws Exception {
      byte[] data = {1, 2, 3, 4, 5};
      KLVDataInput kis = new KLVDataInput(new ByteArrayInputStream(data));
      kis.skipFully(2);
      assertEquals(3, kis.read());
      assertEquals(3, kis.getReadCount());
      
      kis.skipFully(2);
      assertEquals(-1, kis.read());
      
      assertThrows(EOFException.class, () -> kis.skipFully(10));
  }

}