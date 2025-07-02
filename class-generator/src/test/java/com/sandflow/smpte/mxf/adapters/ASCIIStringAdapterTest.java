package com.sandflow.smpte.mxf.adapters;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayInputStream;
import java.io.IOException;

import org.junit.jupiter.api.Test;

import com.sandflow.smpte.mxf.MXFInputStream;

public class ASCIIStringAdapterTest {
  @Test
  void testFromStream() throws IOException {
    MXFInputStream mis = new MXFInputStream(
        new ByteArrayInputStream(new byte[] { (byte) 0x68, (byte) 0x65, (byte) 0x6C, (byte) 0x6C, (byte) 0x6F }));
    assertEquals(ASCIIStringAdapter.fromStream(mis, null), "hello");
  }

  @Test
  void testFromStreamWithNull() throws IOException {
    MXFInputStream mis = new MXFInputStream(
        new ByteArrayInputStream(new byte[] { (byte) 0x68, (byte) 0x65, (byte) 0x00, (byte) 0x6C, (byte) 0x6F }));
    assertEquals(ASCIIStringAdapter.fromStream(mis, null), "he");
  }
}
