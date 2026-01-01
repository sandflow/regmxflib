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
import static org.junit.jupiter.api.Assertions.assertThrowsExactly;

import java.io.ByteArrayOutputStream;

import org.junit.jupiter.api.Test;

import com.sandflow.smpte.klv.KLVDataInput.ByteOrder;
import com.sandflow.smpte.util.AUID;
import com.sandflow.smpte.util.UL;

class KLVDataOutputTest {

  @Test
  void testWriteBER4Length() throws Exception {
    ByteArrayOutputStream bos = new ByteArrayOutputStream();
    KLVDataOutput kos = new KLVDataOutput(bos);
    kos.writeBER4Length(0xf345);
    assertArrayEquals(bos.toByteArray(), new byte[] { (byte) 0x83, 0x00, (byte) 0xf3, 0x45 });
  }

  @Test
  void testWriteBER4LengthOutOfRange() throws Exception {
    ByteArrayOutputStream bos = new ByteArrayOutputStream();
    KLVDataOutput kos = new KLVDataOutput(bos);
    assertThrowsExactly(IllegalArgumentException.class, () -> kos.writeBER4Length(0x01122345));
  }

  @Test
  void testWriteLong() throws Exception {
    ByteArrayOutputStream bos = new ByteArrayOutputStream();
    KLVDataOutput kos = new KLVDataOutput(bos);
    kos.writeLong(0xFFFFFFFFFFFFFFFFL);
    assertArrayEquals(bos.toByteArray(), new byte[] { (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF,
        (byte) 0xFF, (byte) 0xFF, (byte) 0xFF });
  }

  @Test
  void testWriteUnsignedByte() throws Exception {
    ByteArrayOutputStream bos = new ByteArrayOutputStream();
    KLVDataOutput kos = new KLVDataOutput(bos);
    kos.writeUnsignedByte((short) 255);
    assertArrayEquals(bos.toByteArray(), new byte[] { (byte) 0xFF });
  }

  @Test
  void testWriteUL() throws Exception {
    byte[] ulBytes = { 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16 };
    UL ul = new UL(ulBytes);
    ByteArrayOutputStream bos = new ByteArrayOutputStream();
    KLVDataOutput kos = new KLVDataOutput(bos);
    kos.writeUL(ul);
    assertArrayEquals(ulBytes, bos.toByteArray());
    assertEquals(16, kos.getWrittenCount());
  }

  @Test
  void testWriteAUID() throws Exception {
    byte[] bytes = { 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16 };
    AUID auid = new AUID(new UL(bytes));
    ByteArrayOutputStream bos = new ByteArrayOutputStream();
    KLVDataOutput kos = new KLVDataOutput(bos);
    kos.writeAUID(auid);
    assertArrayEquals(bytes, bos.toByteArray());
  }

  @Test
  void testWriteBERLength() throws Exception {
    ByteArrayOutputStream bos = new ByteArrayOutputStream();
    KLVDataOutput kos = new KLVDataOutput(bos);

    kos.writeBERLength(127);
    assertArrayEquals(new byte[] { (byte) 0x83, 0x00, 0x00, 0x7F }, bos.toByteArray());

    bos.reset();
    kos.writeBERLength(0x1000000);
    assertArrayEquals(new byte[] { (byte) 0x84, 0x01, 0x00, 0x00, 0x00 }, bos.toByteArray());

    assertThrowsExactly(IllegalArgumentException.class, () -> kos.writeBERLength(-1));
  }

  @Test
  void testWriteTriplet() throws Exception {
    byte[] keyBytes = new byte[16];
    keyBytes[15] = 1;
    byte[] valBytes = { 0x11, 0x22 };
    Triplet t = new MemoryTriplet(new AUID(new UL(keyBytes)), valBytes);

    ByteArrayOutputStream bos = new ByteArrayOutputStream();
    KLVDataOutput kos = new KLVDataOutput(bos);
    kos.writeTriplet(t);

    byte[] result = bos.toByteArray();
    assertEquals(22, result.length);

    byte[] actualKey = new byte[16];
    System.arraycopy(result, 0, actualKey, 0, 16);
    assertArrayEquals(keyBytes, actualKey);

    assertEquals((byte) 0x83, result[16]);
    assertEquals((byte) 0x00, result[17]);
    assertEquals((byte) 0x00, result[18]);
    assertEquals((byte) 0x02, result[19]);

    assertEquals((byte) 0x11, result[20]);
    assertEquals((byte) 0x22, result[21]);
  }

  @Test
  void testWriteBER4Triplet() throws Exception {
    byte[] keyBytes = new byte[16];
    keyBytes[15] = 2;
    byte[] valBytes = { 0x33 };
    Triplet t = new MemoryTriplet(new AUID(new UL(keyBytes)), valBytes);

    ByteArrayOutputStream bos = new ByteArrayOutputStream();
    KLVDataOutput kos = new KLVDataOutput(bos);
    kos.writeBER4Triplet(t);

    byte[] result = bos.toByteArray();
    assertEquals(21, result.length);

    assertEquals((byte) 0x83, result[16]);
    assertEquals((byte) 0x00, result[17]);
    assertEquals((byte) 0x00, result[18]);
    assertEquals((byte) 0x01, result[19]);
  }

  @Test
  void testEndianness() throws Exception {
    ByteArrayOutputStream bos = new ByteArrayOutputStream();
    KLVDataOutput kos = new KLVDataOutput(bos, ByteOrder.LITTLE_ENDIAN);

    kos.writeShort(0x1234);
    assertArrayEquals(new byte[] { 0x34, 0x12 }, bos.toByteArray());

    bos.reset();
    kos.writeInt(0x12345678);
    assertArrayEquals(new byte[] { 0x78, 0x56, 0x34, 0x12 }, bos.toByteArray());

    bos.reset();
    kos.writeLong(0x1122334455667788L);
    assertArrayEquals(new byte[] { (byte) 0x88, 0x77, 0x66, 0x55, 0x44, 0x33, 0x22, 0x11 }, bos.toByteArray());
  }

  @Test
  void testWriteUnsignedInt() throws Exception {
    ByteArrayOutputStream bos = new ByteArrayOutputStream();
    KLVDataOutput kos = new KLVDataOutput(bos);
    kos.writeUnsignedInt(0x12345678L);
    assertArrayEquals(new byte[] { (byte) 0x12, (byte) 0x34, (byte) 0x56, (byte) 0x78 }, bos.toByteArray());
  }

  @Test
  void testWriteByte() throws Exception {
    ByteArrayOutputStream bos = new ByteArrayOutputStream();
    KLVDataOutput kos = new KLVDataOutput(bos);
    kos.writeByte((byte) 0x80);
    assertArrayEquals(new byte[] { (byte) 0x80 }, bos.toByteArray());
  }

  @Test
  void testWriteBytes() throws Exception {
    ByteArrayOutputStream bos = new ByteArrayOutputStream();
    KLVDataOutput kos = new KLVDataOutput(bos);
    byte[] data = { 1, 2, 3, 4 };
    kos.write(data);
    assertArrayEquals(data, bos.toByteArray());
    assertEquals(4, kos.getWrittenCount());

    bos.reset();
    kos.write(data, 1, 2);
    assertArrayEquals(new byte[] { 2, 3 }, bos.toByteArray());
    assertEquals(6, kos.getWrittenCount());
  }

  @Test
  void testWriteUnsignedShort() throws Exception {
    ByteArrayOutputStream bos = new ByteArrayOutputStream();
    KLVDataOutput kos = new KLVDataOutput(bos);
    kos.writeUnsignedShort(0xAAFF);
    assertArrayEquals(new byte[] { (byte) 0xAA, (byte) 0xFF }, bos.toByteArray());
  }

}