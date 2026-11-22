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
import java.util.Objects;
import java.util.Optional;

import com.sandflow.smpte.mxf.types.EssenceData;
import com.sandflow.smpte.mxf.types.FileDescriptor;
import com.sandflow.smpte.mxf.types.MultipleDescriptor;
import com.sandflow.smpte.mxf.types.Package;
import com.sandflow.smpte.mxf.types.Preface;
import com.sandflow.smpte.mxf.types.Sequence;
import com.sandflow.smpte.mxf.types.SourceClip;
import com.sandflow.smpte.mxf.types.SourcePackage;
import com.sandflow.smpte.mxf.types.Track;
import com.sandflow.smpte.util.AUID;

/**
 * This class inspects the header metadata of an MXF file to build a list of
 * available essence tracks and their associated properties.
 */
public class GCEssenceTracks {

  /**
   * Represents information associated with an Essence Track.
   */
  public static final class TrackInfo {

    private final FileDescriptor descriptor;
    private final Track track;
    private final EssenceData container;

    /**
     * @param descriptor File descriptor associated with the track.
     * @param track      Track metadata.
     * @param container  Essence container reference.
     */
    public TrackInfo(FileDescriptor descriptor, Track track, EssenceData container) {
      this.descriptor = descriptor;
      this.track = track;
      this.container = container;
    }

    public FileDescriptor getDescriptor() {
      return descriptor;
    }

    public Track getTrack() {
      return track;
    }

    public EssenceData getContainer() {
      return container;
    }
  }

  private final List<TrackInfo> tracks = new ArrayList<>();

  /**
   * Returns the File Package that describes an Essence Container.
   *
   * @param preface Preface of the header metadata
   * @param ed      Essence Data set of the Essence Container
   * @return The File Package, or null if it cannot be found
   */
  static SourcePackage findFilePackage(Preface preface, EssenceData ed) {
    if (preface == null || preface.ContentStorageObject == null || preface.ContentStorageObject.Packages == null
        || ed == null || ed.LinkedPackageID == null) {
      return null;
    }

    for (Package p : preface.ContentStorageObject.Packages) {
      if (p instanceof SourcePackage && ed.LinkedPackageID.equals(p.PackageID)) {
        return (SourcePackage) p;
      }
    }

    return null;
  }

  /**
   * Returns the File Descriptors of a File Package, including those contained in
   * a Multiple Descriptor.
   *
   * @param fp File Package
   * @return The File Descriptors, which is empty if there are none
   */
  static List<FileDescriptor> getFileDescriptors(SourcePackage fp) {
    if (fp.EssenceDescription instanceof MultipleDescriptor) {
      List<FileDescriptor> fds = ((MultipleDescriptor) fp.EssenceDescription).FileDescriptors;
      return fds == null ? Collections.emptyList() : fds;
    }

    if (fp.EssenceDescription instanceof FileDescriptor) {
      return Collections.singletonList((FileDescriptor) fp.EssenceDescription);
    }

    return Collections.emptyList();
  }

  /**
   * Computes the total duration of the Source Clips of a track segment.
   *
   * @param segment Track segment, i.e. a Source Clip or a Sequence
   * @return The total duration of the Source Clips, or null if unknown
   */
  static Long computeSourceClipDuration(Object segment) {
    if (segment instanceof SourceClip) {
      return ((SourceClip) segment).ComponentLength;
    }

    if (!(segment instanceof Sequence) || ((Sequence) segment).ComponentObjects == null) {
      return null;
    }

    long duration = 0;
    boolean found = false;
    for (var c : ((Sequence) segment).ComponentObjects) {
      if (!(c instanceof SourceClip)) {
        continue;
      }
      if (c.ComponentLength == null || c.ComponentLength < 0) {
        return null;
      }
      duration += c.ComponentLength;
      found = true;
    }

    return found ? duration : null;
  }

  /**
   * Creates a GCEssenceTracks instance by parsing the header metadata.
   * 
   * @param preface The Preface set of the MXF file.
   */
  public GCEssenceTracks(Preface preface) {
    /* collect tracks that are stored in essence containers */
    for (EssenceData ed : preface.ContentStorageObject.EssenceDataObjects) {

      /* retrieve the File Package */
      SourcePackage fp = findFilePackage(preface, ed);

      if (fp == null) {
        throw new IllegalArgumentException(String.format("Cannot find Package ID %s corresponding to Container SID %s",
            ed.LinkedPackageID, ed.EssenceStreamID));
      }

      List<FileDescriptor> fds = getFileDescriptors(fp);

      if (fds.isEmpty()) {
        throw new IllegalArgumentException(
            String.format("No File Descriptors found for Package ID %s", fp.PackageID));
      }

      for (FileDescriptor fd : fds) {
        Optional<Track> foundTrack = fp.PackageTracks.stream().filter(t -> Objects.equals(t.TrackID, fd.LinkedTrackID)).findFirst();

        if (!foundTrack.isPresent()) {
          /* can simply be a timecode track */
          continue;
        }

        tracks.add(new TrackInfo(fd, foundTrack.get(), ed));
      }
    }
  }

  /**
   * Gets information for a track at a specific index.
   * 
   * @param i The index of the track.
   * @return Information about the track.
   */
  public TrackInfo getTrackInfo(int i) {
    return this.tracks.get(i);
  }

  /**
   * Gets the total number of essence tracks found.
   * 
   * @return The number of tracks.
   */
  public int getTrackCount() {
    return this.tracks.size();
  }

  /**
   * Gets information for a track based on its Essence Element Key.
   * 
   * @param elementKey The Essence Element Key of the track.
   * @return Information about the track, or null if not found.
   */
  public TrackInfo getTrackInfo(AUID elementKey) {
    if (! elementKey.isUL())
      return null;

    long trackNum = MXFFiles.getTrackNumber(elementKey.asUL());

    /* find track info */
    for (int i = 0; i < this.tracks.size(); i++) {
      TrackInfo info = this.tracks.get(i);
      if (info.getTrack().EssenceTrackNumber == trackNum) {
        return info;
      }
    }

    return null;
  }
}
