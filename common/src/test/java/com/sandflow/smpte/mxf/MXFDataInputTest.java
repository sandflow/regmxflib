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
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayInputStream;
import java.util.Collection;
import java.util.Iterator;

import org.junit.jupiter.api.Test;

import com.sandflow.smpte.klv.KLVDataInput.ByteOrder;
import com.sandflow.smpte.util.IDAU;
import com.sandflow.smpte.util.UMID;
import com.sandflow.smpte.util.UUID;

class MXFDataInputTest {

  @Test
  void testReadUUID() throws Exception {
    byte[] uuidBytes = {
        0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07,
        0x08, 0x09, 0x0A, 0x0B, 0x0C, 0x0D, 0x0E, 0x0F
    };
    
    /* Big Endian */
    MXFDataInput mis = new MXFDataInput(new ByteArrayInputStream(uuidBytes));
    UUID uuid = mis.readUUID();
    assertArrayEquals(uuidBytes, uuid.getBytes());

    /* Little Endian (Input bytes are swapped relative to canonical) */
    byte[] leBytes = {
        0x03, 0x02, 0x01, 0x00, 0x05, 0x04, 0x07, 0x06,
        0x08, 0x09, 0x0A, 0x0B, 0x0C, 0x0D, 0x0E, 0x0F
    };
    
    mis = new MXFDataInput(new ByteArrayInputStream(leBytes), ByteOrder.LITTLE_ENDIAN);
    uuid = mis.readUUID();
    assertArrayEquals(uuidBytes, uuid.getBytes());
  }

  @Test
  void testReadIDAU() throws Exception {
    byte[] idauBytes = {
        0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07,
        0x08, 0x09, 0x0A, 0x0B, 0x0C, 0x0D, 0x0E, 0x0F
    };
    
    /* Big Endian */
    MXFDataInput mis = new MXFDataInput(new ByteArrayInputStream(idauBytes));
    IDAU idau = mis.readIDAU();
    assertArrayEquals(idauBytes, idau.getBytes());

    /* Little Endian */
    byte[] leBytes = {
        0x03, 0x02, 0x01, 0x00, 0x05, 0x04, 0x07, 0x06,
        0x08, 0x09, 0x0A, 0x0B, 0x0C, 0x0D, 0x0E, 0x0F
    };
    
    mis = new MXFDataInput(new ByteArrayInputStream(leBytes), ByteOrder.LITTLE_ENDIAN);
    idau = mis.readIDAU();
    assertArrayEquals(idauBytes, idau.getBytes());
  }

  @Test
  void testReadUMID() throws Exception {
    byte[] umidBytes = new byte[32];
    for(int i=0; i<32; i++) umidBytes[i] = (byte)i;
    
    MXFDataInput mis = new MXFDataInput(new ByteArrayInputStream(umidBytes));
    UMID umid = mis.readUMID();
    assertArrayEquals(umidBytes, umid.getBytes());
  }

  @Test
  void testReadBatch() throws Exception {
    
    byte[] data = {
        0, 0, 0, 2,
        0, 0, 0, 4,
        0, 0, 0, 1,
        0, 0, 0, 2
    };
    
    MXFDataInput mis = new MXFDataInput(new ByteArrayInputStream(data));
    Collection<Integer> items = mis.readBatch(b -> {
        return ((b[0] & 0xFF) << 24) | ((b[1] & 0xFF) << 16) | ((b[2] & 0xFF) << 8) | (b[3] & 0xFF);
    });
    
    assertEquals(2, items.size());
    Iterator<Integer> it = items.iterator();
    assertEquals(1, it.next());
    assertEquals(2, it.next());
  }
}