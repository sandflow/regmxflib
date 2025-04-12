package com.sandflow.smpte.mxf;

import java.io.EOFException;
import java.io.IOException;
import java.util.HashMap;

import com.sandflow.smpte.klv.Triplet;
import com.sandflow.smpte.util.UL;
import com.sandflow.smpte.util.UMID;
import com.sandflow.smpte.util.UUID;

public class EssenceContainerData {
  private static final UL LINKEDPACKAGEUID_KEY = UL.fromURN("urn:smpte:ul:060e2b34.01010102.06010106.01000000");

  UMID linkedPackageUID;

  public static EssenceContainerData fromSet(Set s, HashMap<UUID, Set> resolver) throws EOFException, IOException {
    EssenceContainerData ecd = new EssenceContainerData();

    for (Triplet item : s.getItems()) {
      if (item.getKey().equals(LINKEDPACKAGEUID_KEY)) {
        MXFInputStream mis = new MXFInputStream(item.getValueAsStream());
        ecd.linkedPackageUID = mis.readUMID();
      }
    }

    return ecd;
  }
}
