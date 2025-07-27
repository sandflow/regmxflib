package com.sandflow.smpte.mxf.adapters;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayInputStream;
import java.io.IOException;

import org.junit.jupiter.api.Test;

import com.sandflow.smpte.mxf.MXFDataInput;

public class UTF16StringAdapterTest {
  @Test
  void testFromStream() throws IOException {
    MXFDataInput mis = new MXFDataInput(
        new ByteArrayInputStream(new byte[] { (byte) 0x00, (byte) 0x68, (byte) 0x00, (byte) 0x65, (byte) 0x00,
            (byte) 0x6C, (byte) 0x00, (byte) 0x6C, (byte) 0x00, (byte) 0x6F }));
    assertEquals(UTF16StringAdapter.fromStream(mis, null), "hello");
  }

  @Test
  void testFromStreamWithNull() throws IOException {
    MXFDataInput mis = new MXFDataInput(
        new ByteArrayInputStream(new byte[] { (byte) 0x00, (byte) 0x68, (byte) 0x00, (byte) 0x65, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x6C, (byte) 0x00, (byte) 0x6F }));
    assertEquals(UTF16StringAdapter.fromStream(mis, null), "he");
  }
}
