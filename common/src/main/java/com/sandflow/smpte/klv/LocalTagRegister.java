/*
 * Copyright (c) 2014, Pierre-Anthony Lemieux (pal@sandflow.com)
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
package com.sandflow.smpte.klv;

import java.util.Collection;
import java.util.HashMap;

import com.sandflow.smpte.util.AUID;

/**
 * LocalTagRegister maps Local Tags found in a Local Set to AUID Keys
 */
public class LocalTagRegister implements LocalTagResolver {

  public record Entry(Long localTag, AUID auid) {
  }

  private final HashMap<Long, Entry> tagToEntry = new HashMap<>();
  private final HashMap<AUID, Entry> auidToEntry = new HashMap<>();

  Long nextLocalTag = 0x8000L;

  /**
   * Instantiates an empty LocalTagRegister
   */
  public LocalTagRegister() {
  }

  /**
   * Instantiates a LocalTagRegister with an initial set of mappings from Local
   * Tag values to AUID Keys
   *
   * @param entries Initial set of mappings
   */
  public LocalTagRegister(Collection<Entry> entries) {
    for (Entry entry : entries) {
      this.add(entry.localTag(), entry.auid());
    }
  }

  @Override
  public AUID getAUID(long localtag) {
    Entry e = tagToEntry.get(localtag);

    return e != null ? e.auid() : null;
  }

  @Override
  public Long getLocalTag(AUID auid) {
    Entry e = auidToEntry.get(auid);
    return e == null ? null : e.localTag();
  }

  /**
   * Returns the Local Tag corresponding to a AUID, potentially allocating a
   * dynamic Local Tag if no Local Tag exists
   *
   * @param auid
   * @return Local tag
   */
  public long getOrMakeLocalTag(AUID auid) {
    Entry e = auidToEntry.get(auid);
    if (e != null) {
      return e.localTag();
    }
    /* EXCEPTION: some register entries have local tags above 0x8000 */
    /* look for the next available dynamic tag */
    while (this.tagToEntry.containsKey(this.nextLocalTag)) {
      this.nextLocalTag++;
    }
    this.add(this.nextLocalTag, auid);
    return this.nextLocalTag++;
  }

  /**
   * Adds a Local Tag to the registry.
   *
   * @param localtag Local Tag
   * @param key      Key with which the Local Tag is associated
   * @return True if the Local Tag was not present in the registry, or false
   *         otherwise.
   */
  public boolean add(long localtag, AUID key) {
    Entry e = new Entry(localtag, key);
    tagToEntry.put(localtag, e);
    return auidToEntry.put(key, e) == null;
  }

  /**
   * Returns the number of entries in the registry
   *
   * @return Number of entries in the registry
   */
  public int size() {
    return tagToEntry.size();
  }

  /**
   * Returns the entries in the registry
   * 
   * @return Entries in the registry
   */
  public Collection<Entry> getEntries() {
    return tagToEntry.values();
  }

}
