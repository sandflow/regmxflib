package com.sandflow.smpte.mxf;

import java.io.IOException;
import java.util.HashMap;

import com.sandflow.smpte.klv.Triplet;
import com.sandflow.smpte.klv.exceptions.KLVException;
import com.sandflow.smpte.util.UL;
import com.sandflow.smpte.util.UMID;
import com.sandflow.smpte.util.UUID;

public class FilePackage {
  private static final UL PACKAGEUID_KEY = UL.fromURN("urn:smpte:ul:060e2b34.01010101.01011510.00000000");
  private static final UL DESCRIPTOR_KEY = UL.fromURN("urn:smpte:ul:060e2b34.01010102.06010104.02030000");

  UMID packageUID;
  FileDescriptor descriptor;

  public static FilePackage fromSet(Set s, HashMap<UUID, Set> resolver) throws KLVException, IOException {
    FilePackage fp = new FilePackage();

    for (Triplet item : s.getItems()) {
      if (item.getKey().equals(PACKAGEUID_KEY)) {
        MXFInputStream mis = new MXFInputStream(item.getValueAsStream());
        fp.packageUID = mis.readUMID();
      } else if (item.getKey().equals(DESCRIPTOR_KEY)) {
        MXFInputStream mis = new MXFInputStream(item.getValueAsStream());
        fp.descriptor = FileDescriptor.fromSet(resolver.get(mis.readUUID()), resolver);
      }
    }

    return fp;
  }
}
