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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.sandflow.smpte.klv.KLVDataInput.ByteOrder;
import com.sandflow.smpte.util.IDAU;
import com.sandflow.smpte.util.UMID;
import com.sandflow.smpte.util.UUID;

class MXFDataOutputTest {

  @Test
  void testWriteUUID() throws Exception {
    byte[] uuidBytes = {
        0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07,
        0x08, 0x09, 0x0A, 0x0B, 0x0C, 0x0D, 0x0E, 0x0F
    };
    UUID uuid = new UUID(uuidBytes);

    /* Big Endian */
    ByteArrayOutputStream bos = new ByteArrayOutputStream();
    MXFDataOutput mos = new MXFDataOutput(bos);
    mos.writeUUID(uuid);
    assertArrayEquals(uuidBytes, bos.toByteArray());

    /* Little Endian (Swap 0-3, 4-5, 6-7) */
    bos.reset();
    mos = new MXFDataOutput(bos, ByteOrder.LITTLE_ENDIAN);
    mos.writeUUID(uuid);
    byte[] expectedLE = {
        0x03, 0x02, 0x01, 0x00, 0x05, 0x04, 0x07, 0x06,
        0x08, 0x09, 0x0A, 0x0B, 0x0C, 0x0D, 0x0E, 0x0F
    };
    assertArrayEquals(expectedLE, bos.toByteArray());
  }

  @Test
  void testWriteIDAU() throws Exception {
    byte[] idauBytes = {
        0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07,
        0x08, 0x09, 0x0A, 0x0B, 0x0C, 0x0D, 0x0E, 0x0F
    };
    IDAU idau = new IDAU(idauBytes);

    /* Big Endian */
    ByteArrayOutputStream bos = new ByteArrayOutputStream();
    MXFDataOutput mos = new MXFDataOutput(bos);
    mos.writeIDAU(idau);
    assertArrayEquals(idauBytes, bos.toByteArray());

    /* Little Endian */
    bos.reset();
    mos = new MXFDataOutput(bos, ByteOrder.LITTLE_ENDIAN);
    mos.writeIDAU(idau);
    byte[] expectedLE = {
        0x03, 0x02, 0x01, 0x00, 0x05, 0x04, 0x07, 0x06,
        0x08, 0x09, 0x0A, 0x0B, 0x0C, 0x0D, 0x0E, 0x0F
    };
    assertArrayEquals(expectedLE, bos.toByteArray());
  }

  @Test
  void testWriteUMID() throws Exception {
    byte[] umidBytes = new byte[32];
    Arrays.fill(umidBytes, (byte) 0xAA);
    UMID umid = new UMID(umidBytes);

    ByteArrayOutputStream bos = new ByteArrayOutputStream();
    MXFDataOutput mos = new MXFDataOutput(bos);
    mos.writeUMID(umid);
    assertArrayEquals(umidBytes, bos.toByteArray());
  }

  @Test
  void testWriteBatch() throws Exception {
    List<Integer> items = Arrays.asList(1, 2, 3);
    
    ByteArrayOutputStream bos = new ByteArrayOutputStream();
    MXFDataOutput mos = new MXFDataOutput(bos);
    
    mos.writeBatch(items, 4, (i) -> {
        byte[] b = new byte[4];
        b[3] = i.byteValue();
        return b;
    });
    
    byte[] result = bos.toByteArray();
    
    byte[] expected = {
        0, 0, 0, 3, /* Count */
        0, 0, 0, 4, /* Length */
        0, 0, 0, 1,
        0, 0, 0, 2,
        0, 0, 0, 3
    };
    
    assertArrayEquals(expected, result);
  }
}