package com.sandflow.smpte.mxf;

import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;

import com.sandflow.smpte.klv.Triplet;
import com.sandflow.smpte.klv.adapters.UUIDValueAdapter;
import com.sandflow.smpte.klv.exceptions.KLVException;
import com.sandflow.smpte.util.UL;
import com.sandflow.smpte.util.UUID;

public class ContentStorage {
  private static final UL ESSENCECONTAINERDATA_KEY = UL.fromURN("urn:smpte:ul:060e2b34.01010102.06010104.05020000");

  EssenceContainerData essenceContainerData[];

  public static ContentStorage fromSet(Set s, HashMap<UUID, Set> resolver) throws KLVException, IOException {
    ContentStorage cs = new ContentStorage();

    for (Triplet item : s.getItems()) {
      if (item.getKey().equals(ESSENCECONTAINERDATA_KEY)) {
        MXFInputStream mis = new MXFInputStream(item.getValueAsStream());
        Collection<UUID> uuids = mis.<UUID, UUIDValueAdapter>readBatch();
        cs.essenceContainerData = new EssenceContainerData[uuids.size()];
        int i = 0;
        for (UUID uuid : uuids) {
          cs.essenceContainerData[i++] = EssenceContainerData.fromSet(resolver.get(uuid), resolver);
        }
      }
    }
    
    return cs;
  }

}
