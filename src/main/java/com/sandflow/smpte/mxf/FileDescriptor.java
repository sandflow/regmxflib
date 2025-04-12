package com.sandflow.smpte.mxf;

import java.io.IOException;
import java.util.HashMap;

import com.sandflow.smpte.klv.Triplet;
import com.sandflow.smpte.klv.exceptions.KLVException;
import com.sandflow.smpte.util.UL;
import com.sandflow.smpte.util.UUID;

public class FileDescriptor {
  private static final UL CONTAINERFORMAT_KEY = UL.fromURN("urn:smpte:ul:060e2b34.01010102.06010104.01020000");

  UL containerFormat;

  public static FileDescriptor fromSet(Set s, HashMap<UUID, Set> resolver) throws KLVException, IOException {
    FileDescriptor d = new FileDescriptor();

    for (Triplet item : s.getItems()) {
      if (item.getKey().equals(CONTAINERFORMAT_KEY)) {
        MXFInputStream mis = new MXFInputStream(item.getValueAsStream());
        d.containerFormat = mis.readUL();
      }
    }

    return d;
  }
}
