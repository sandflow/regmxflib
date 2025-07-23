/*
 * Copyright (c) Sandflow Consulting, LLC
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

/**
* @author Pierre-Anthony Lemieux
*/

package com.sandflow.smpte.mxf;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import com.sandflow.smpte.mxf.types.EssenceData;
import com.sandflow.smpte.mxf.types.FileDescriptor;
import com.sandflow.smpte.mxf.types.MultipleDescriptor;
import com.sandflow.smpte.mxf.types.Package;
import com.sandflow.smpte.mxf.types.Preface;
import com.sandflow.smpte.mxf.types.SourcePackage;
import com.sandflow.smpte.mxf.types.Track;
import com.sandflow.smpte.util.AUID;

public class GCEssenceTracks {

  /**
   * Represents information associated with an Essence Track.
   *
   * @param descriptor File descriptor associated with the track.
   * @param track      Track metadata.
   * @param container  Essence container reference.
   */
  public record TrackInfo(
      FileDescriptor descriptor,
      Track track,
      EssenceData container) {
  }

  private final List<TrackInfo> tracks = new ArrayList<>();

  public GCEssenceTracks(Preface preface) {
    /* collect tracks that are stored in essence containers */
    for (EssenceData ed : preface.ContentStorageObject.EssenceDataObjects) {

      /* retrieve the File Package */
      SourcePackage fp = null;
      for (Package p : preface.ContentStorageObject.Packages) {
        if (p.PackageID.equals(ed.LinkedPackageID)) {
          fp = (SourcePackage) p;
          break;
        }
      }

      if (fp == null) {
        throw new RuntimeException("No file packages found");
      }

      /* do we have a multi-descriptor */
      List<FileDescriptor> fds;
      if (fp.EssenceDescription instanceof MultipleDescriptor) {
        fds = ((MultipleDescriptor) fp.EssenceDescription).FileDescriptors;
      } else {
        fds = Collections.singletonList((FileDescriptor) fp.EssenceDescription);
      }

      /* TODO: error if no descriptors are present */

      for (FileDescriptor fd : fds) {
        Optional<Track> foundTrack = fp.PackageTracks.stream().filter(t -> t.TrackID == fd.LinkedTrackID).findFirst();

        if (!foundTrack.isPresent()) {
          /* can simply be a timecode track */
          continue;
        }

        tracks.add(new TrackInfo(fd, foundTrack.get(), ed));
      }
    }
  }

  public TrackInfo getTrackInfo(int i) {
    return this.tracks.get(i);
  }

  public int getTrackCount() {
    return this.tracks.size();
  }

  public TrackInfo getTrackInfo(AUID elementKey) {
    if (! elementKey.isUL())
      return null;

    long trackNum = MXFFiles.getTrackNumber(elementKey.asUL());

    /* find track info */
    for (int i = 0; i < this.tracks.size(); i++) {
      TrackInfo info = this.tracks.get(i);
      if (info.track().EssenceTrackNumber == trackNum) {
        return info;
      }
    }

    return null;
  }
}
