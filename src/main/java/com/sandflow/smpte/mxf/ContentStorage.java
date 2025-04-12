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
        UUID uuids[] = mis.readBatch(UUIDValueAdapter::fromValue).toArray(UUID[]::new);
        cs.essenceContainerData = new EssenceContainerData[uuids.length];
        for (int i = 0; i < uuids.length; i++) {
          cs.essenceContainerData[i] = EssenceContainerData.fromSet(resolver.get(uuids[i]), resolver);
        }
      }
    }
    
    return cs;
  }

}
