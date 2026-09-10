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
package com.sandflow.smpte.util;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class IDAUTest {

  static final String TEST_UL_URN = "urn:smpte:ul:060e2b34.01010101.0d010201.01010100";
  static final byte[] TEST_UL_BYTES = {
      (byte) 0x06, (byte) 0x0e, (byte) 0x2b, (byte) 0x34,
      (byte) 0x01, (byte) 0x01, (byte) 0x01, (byte) 0x01,
      (byte) 0x0d, (byte) 0x01, (byte) 0x02, (byte) 0x01,
      (byte) 0x01, (byte) 0x01, (byte) 0x01, (byte) 0x00
  };

  static final String TEST_UUID_URN = "urn:uuid:f81d4fae-7dec-11d0-a765-00a0c91e6bf6";
  static final byte[] TEST_UUID_BYTES = {
      (byte) 0xf8, (byte) 0x1d, (byte) 0x4f, (byte) 0xae,
      (byte) 0x7d, (byte) 0xec, (byte) 0x11, (byte) 0xd0,
      (byte) 0xa7, (byte) 0x65, (byte) 0x00, (byte) 0xa0,
      (byte) 0xc9, (byte) 0x1e, (byte) 0x6b, (byte) 0xf6
  };

  static final byte[] TEST_IDAU_FROM_UL_BYTES = {
      (byte) 0x0d, (byte) 0x01, (byte) 0x02, (byte) 0x01,
      (byte) 0x01, (byte) 0x01, (byte) 0x01, (byte) 0x00,
      (byte) 0x06, (byte) 0x0e, (byte) 0x2b, (byte) 0x34,
      (byte) 0x01, (byte) 0x01, (byte) 0x01, (byte) 0x01
  };

  @Test
  public void testFromURNWithUL() {
    IDAU idau = IDAU.fromURN(TEST_UL_URN);
    assertNotNull(idau);
    assertTrue(idau.isUL());
    assertFalse(idau.isUUID());
    assertArrayEquals(TEST_IDAU_FROM_UL_BYTES, idau.getBytes());
  }

  @Test
  public void testFromURNWithUUID() {
    IDAU idau = IDAU.fromURN(TEST_UUID_URN);
    assertNotNull(idau);
    assertFalse(idau.isUL());
    assertTrue(idau.isUUID());
    assertArrayEquals(TEST_UUID_BYTES, idau.getBytes());
  }

  @Test
  public void testFromURNWithInvalid() {
    assertNull(IDAU.fromURN("urn:smpte:umid:blah"));
    assertNull(IDAU.fromURN(null));
    assertNull(IDAU.fromURN("invalid-urn"));
  }

  @Test
  public void testConstructorWithBytes() {
    IDAU idau = new IDAU(TEST_IDAU_FROM_UL_BYTES);
    assertArrayEquals(TEST_IDAU_FROM_UL_BYTES, idau.getBytes());
    /* immutability check */
    byte[] original = new byte[16];
    IDAU idau2 = new IDAU(original);
    original[0] = 1;
    assertNotEquals(original[0], idau2.getBytes()[0]);
  }

  @Test
  public void testConstructorWithUL() {
    UL ul = new UL(TEST_UL_BYTES);
    IDAU idau = new IDAU(ul);
    assertArrayEquals(TEST_IDAU_FROM_UL_BYTES, idau.getBytes());
  }

  @Test
  public void testConstructorWithUUID() {
    UUID uuid = new UUID(TEST_UUID_BYTES);
    IDAU idau = new IDAU(uuid);
    assertArrayEquals(TEST_UUID_BYTES, idau.getBytes());
  }

  @Test
  public void testEquals() {
    IDAU idau1 = new IDAU(TEST_IDAU_FROM_UL_BYTES);
    IDAU idau2 = new IDAU(TEST_IDAU_FROM_UL_BYTES);
    IDAU idau3 = new IDAU(TEST_UUID_BYTES);

    assertTrue(idau1.equals(idau2));
    assertFalse(idau1.equals(idau3));
    assertFalse(idau1.equals(null));
    assertFalse(idau1.equals(new Object()));
  }

  @Test
  public void testHashCode() {
    IDAU idau1 = new IDAU(TEST_IDAU_FROM_UL_BYTES);
    IDAU idau2 = new IDAU(TEST_IDAU_FROM_UL_BYTES);
    IDAU idau3 = new IDAU(TEST_UUID_BYTES);

    assertEquals(idau1.hashCode(), idau2.hashCode());
    assertNotEquals(idau1.hashCode(), idau3.hashCode());
  }

  @Test
  public void testToString() {
    IDAU idauUL = IDAU.fromURN(TEST_UL_URN);
    assertEquals(TEST_UL_URN, idauUL.toString());

    IDAU idauUUID = IDAU.fromURN(TEST_UUID_URN);
    assertEquals(TEST_UUID_URN, idauUUID.toString());
  }

  @Test
  public void testAsUUID() {
    IDAU idauUL = new IDAU(new UL(TEST_UL_BYTES));
    assertNull(idauUL.asUUID());

    UUID uuid = new UUID(TEST_UUID_BYTES);
    IDAU idauUUID = new IDAU(uuid);
    assertEquals(uuid, idauUUID.asUUID());
  }

  @Test
  public void testAsAUID() {
    UL ul = new UL(TEST_UL_BYTES);
    IDAU idauUL = new IDAU(ul);
    AUID auid = new AUID(ul);
    assertEquals(auid, idauUL.asAUID());

    UUID uuid = new UUID(TEST_UUID_BYTES);
    IDAU idauUUID = new IDAU(uuid);
    AUID auidFromUUID = new AUID(uuid);
    assertEquals(auidFromUUID, idauUUID.asAUID());
  }

  @Test
  public void testAsUL() {
    UL ul = new UL(TEST_UL_BYTES);
    IDAU idauUL = new IDAU(ul);
    assertEquals(ul, idauUL.asUL());

    IDAU idauUUID = new IDAU(new UUID(TEST_UUID_BYTES));
    assertNull(idauUUID.asUL());
  }
}