/*
 * Copyright (c) Pierre-Anthony Lemieux <pal@sandflow.com>
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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrowsExactly;

import java.io.ByteArrayOutputStream;

import org.junit.jupiter.api.Test;

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

}