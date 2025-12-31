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
import java.net.URI;
import static org.junit.jupiter.api.Assertions.*;

public class UUIDTest {

  @Test
  public void testFromURN() {
    String urn = "urn:uuid:f81d4fae-7dec-11d0-a765-00a0c91e6bf6";
    UUID uuid = UUID.fromURN(urn);
    assertNotNull(uuid);
    assertEquals(urn, uuid.toString());

    byte[] expectedBytes = {
        (byte) 0xf8, (byte) 0x1d, (byte) 0x4f, (byte) 0xae,
        (byte) 0x7d, (byte) 0xec, (byte) 0x11, (byte) 0xd0,
        (byte) 0xa7, (byte) 0x65, (byte) 0x00, (byte) 0xa0,
        (byte) 0xc9, (byte) 0x1e, (byte) 0x6b, (byte) 0xf6
    };
    assertArrayEquals(expectedBytes, uuid.getBytes());
  }

  @Test
  public void testFromURNInvalid() {
    assertNull(UUID.fromURN("urn:uuid:invalid"));
    assertNull(UUID.fromURN("urn:smpte:ul:060e2b34.01010101.0d010201.01010100"));
    assertNull(UUID.fromURN("not-a-urn"));
  }

  @Test
  public void testFromRandom() {
    UUID uuid1 = UUID.fromRandom();
    UUID uuid2 = UUID.fromRandom();

    assertNotNull(uuid1);
    assertNotNull(uuid2);
    assertNotEquals(uuid1, uuid2);

    byte[] bytes = uuid1.getBytes();
    // Version 4: xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx
    assertEquals(4, (bytes[6] & 0xf0) >> 4);
    // Variant 2 (IETF): 10xxxxxx -> 0x80 mask
    assertEquals(0x80, bytes[8] & 0xc0);
  }

  @Test
  public void testFromURIName() {
    // Test with a known value
    // Name: "www.example.com"
    // Namespace: DNS (6ba7b811-9dad-11d1-80b4-00c04fd430c8)
    // Expected V5 UUID: 2ed6657d-e927-568b-95e1-2665a8aea6a2

    String name = "www.example.com";
    UUID uuid = UUID.fromURIName(name);
    assertNotNull(uuid);

    assertEquals("urn:uuid:2ed6657d-e927-568b-95e1-2665a8aea6a2", uuid.toString());

    // Check version and variant
    byte[] bytes = uuid.getBytes();
    assertEquals(5, (bytes[6] & 0xf0) >> 4);
    assertEquals(0x80, bytes[8] & 0xc0);

    // Deterministic check
    UUID uuid2 = UUID.fromURIName(name);
    assertEquals(uuid, uuid2);
  }

  @Test
  public void testFromURINameWithURI() {
    URI uri = URI.create("http://www.example.com");
    UUID uuid = UUID.fromURIName(uri);
    assertNotNull(uuid);

    UUID uuid2 = UUID.fromURIName(uri.toString());
    assertEquals(uuid2, uuid);
  }

  @Test
  public void testConstructorAndGetBytes() {
    byte[] bytes = new byte[16];
    for (int i = 0; i < 16; i++)
      bytes[i] = (byte) i;

    UUID uuid = new UUID(bytes);
    assertArrayEquals(bytes, uuid.getBytes());

    // Immutability check
    bytes[0] = (byte) 0xFF;
    assertNotEquals(bytes[0], uuid.getBytes()[0]);

    byte[] internal = uuid.getBytes();
    internal[0] = (byte) 0xFF;
    assertNotEquals(internal[0], uuid.getBytes()[0]);
  }

  @Test
  public void testEqualsAndHashCode() {
    UUID uuid1 = UUID.fromURN("urn:uuid:f81d4fae-7dec-11d0-a765-00a0c91e6bf6");
    UUID uuid2 = UUID.fromURN("urn:uuid:f81d4fae-7dec-11d0-a765-00a0c91e6bf6");
    UUID uuid3 = UUID.fromRandom();

    assertEquals(uuid1, uuid2);
    assertNotEquals(uuid1, uuid3);
    assertNotEquals(uuid1, null);
    assertNotEquals(uuid1, "string");

    assertEquals(uuid1.hashCode(), uuid2.hashCode());
  }
}