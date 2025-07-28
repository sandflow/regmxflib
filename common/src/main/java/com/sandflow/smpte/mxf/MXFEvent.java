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

import com.sandflow.util.events.BasicEvent;
import com.sandflow.util.events.Event;

/**
 * All events raised by this class are instance of this class
 */
public class MXFEvent extends BasicEvent {

  /**
   * Defines all events raised by this class
   */
  public static enum EventCodes {

    /**
     * No root object found
     */
    MISSING_ROOT_OBJECT(Event.Severity.FATAL),
    /**
     * No partition pack found in the MXF file
     */
    MISSING_PARTITION_PACK(Event.Severity.FATAL),
    /**
     * No primer pack found in the MXF file
     */
    MISSING_PRIMER_PACK(Event.Severity.FATAL),
    /**
     * Unexpected group sequence encountered
     */
    UNEXPECTED_STRUCTURE(Event.Severity.ERROR),
    /**
     * Failed to read Group
     */
    GROUP_READ_FAILED(Event.Severity.ERROR),
    /**
     * Inconsistent header metadata length
     */
    INCONSISTENT_HEADER_LENGTH(Event.Severity.FATAL),
    /**
     * Inconsistent header metadata length
     */
    RIP_NOTFOUND(Event.Severity.FATAL),
    /**
     * Inconsistent generic stream partition
     */
    BAD_GS_PARTITION(Event.Severity.WARN),
    /**
     * More than one essence container is present
     */
    TOO_MANY_ECS(Event.Severity.FATAL),
    /**
     * Invalid index segment
     */
    BAD_INDEX_SEGMENT(Event.Severity.FATAL),
    /**
     * No class found
     */
    CLASS_NOT_FOUND(Event.Severity.ERROR),
    /**
     * Could not read an item
     */
    ITEM_READ_FAILED(Event.Severity.WARN),
    /**
     * Header metadata set couldn't be found
     */
    MISSING_HEADER_SET(Event.Severity.WARN),
    /**
     * Inconsistent header metadata information
     */
    INCONSISTENT_HEADER(Event.Severity.WARN);



    public final Event.Severity severity;

    private EventCodes(Event.Severity severity) {
      this.severity = severity;
    }

  }

  public MXFEvent(MXFEvent.EventCodes kind, String message) {
    super(kind.severity, kind, message);
  }

}