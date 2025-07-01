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

package com.sandflow.smpte.mxf;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import com.sandflow.smpte.klv.Triplet;
import com.sandflow.smpte.klv.exceptions.KLVException;
import com.sandflow.smpte.mxf.StreamingReader.TrackInfo;
import com.sandflow.smpte.mxf.types.EssenceData;
import com.sandflow.smpte.mxf.types.FileDescriptor;
import com.sandflow.smpte.mxf.types.MaterialPackage;
import com.sandflow.smpte.mxf.types.MultipleDescriptor;
import com.sandflow.smpte.mxf.types.Package;
import com.sandflow.smpte.mxf.types.Preface;
import com.sandflow.smpte.mxf.types.SourcePackage;
import com.sandflow.smpte.mxf.types.Track;
import com.sandflow.smpte.util.UL;
import com.sandflow.util.events.EventHandler;

public class StreamingFileInfo {

  protected static List<TrackInfo> extractTracks(Preface preface) {
    ArrayList<TrackInfo> tracks = new ArrayList<>();

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
      FileDescriptor fds[] = null;
      if (fp.EssenceDescription instanceof MultipleDescriptor) {
        fds = ((MultipleDescriptor) fp.EssenceDescription).SubDescriptors.toArray(fds);
      } else {
        fds = new FileDescriptor[] { (FileDescriptor) fp.EssenceDescription };
      }

      for (FileDescriptor fd : fds) {
        Track foundTrack = null;

        for (Track t : fp.PackageTracks) {
          if (t.TrackID == fd.LinkedTrackID) {
            foundTrack = t;
            break;
          }
        }

        /* TODO: handle missing Track */

        tracks.add(new TrackInfo(fd, foundTrack, ed));
      }
    }

    return tracks;
  }

  private final Preface preface;
  private final List<StreamingReader.TrackInfo> tracks;

  StreamingFileInfo(InputStream is, EventHandler evthandler)
      throws IOException, KLVException, MXFException {
    MXFInputStream mis = new MXFInputStream(is);

    /* look for the header partition pack */
    PartitionPack pp = null;
    for (Triplet t; (t = mis.readTriplet()) != null;) {
      if ((pp = PartitionPack.fromTriplet(t)) != null) {
        break;
      }
    }

    if (pp == null) {
      MXFEvent.handle(evthandler,
          new MXFEvent(MXFEvent.EventCodes.MISSING_PARTITION_PACK, "No Partition Pack found"));
    }

    this.preface = StreamingReader.readHeaderMetadataFrom(mis, pp.getHeaderByteCount(), evthandler);

    /* TODO: handle NULL preface */

    /* we can only handle a single essence container at this point */
    if (this.preface.ContentStorageObject.EssenceDataObjects.size() != 1) {
      throw new RuntimeException("Only one essence container supported");
    }

    /* we can only handle one material package at this point */
    if (this.preface.ContentStorageObject.Packages.stream().filter(e -> e instanceof MaterialPackage).count() != 1) {
      throw new RuntimeException("Only one material package supported");
    }

    this.tracks = StreamingReader.extractTracks(this.preface);

    /*
     * skip over index tables, if any
     */
    if (pp.getIndexSID() != 0) {
      mis.skipFully(pp.getIndexByteCount());
    }
  }

  public TrackInfo getTrack(int i) {
    return this.tracks.get(i);
  }

  public int getTrackCount() {
    return this.tracks.size();
  }

  public Preface getPreface() {
    return this.preface;
  }

  public TrackInfo getTrackInfo(UL elementKey) {
    long trackNum = MXFFiles.getTrackNumber(elementKey);

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
