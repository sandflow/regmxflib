package com.sandflow.smpte.mxf;

import com.sandflow.util.events.BasicEvent;
import com.sandflow.util.events.Event;
import com.sandflow.util.events.EventHandler;

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
    INCONSISTENT_HEADER_LENGTH(Event.Severity.FATAL);
  
    public final Event.Severity severity;
  
    private EventCodes(Event.Severity severity) {
      this.severity = severity;
    }
  
  }

  public MXFEvent(MXFEvent.EventCodes kind, String message) {
    super(kind.severity, kind, message);
  }

  static void handle(EventHandler handler, com.sandflow.util.events.Event evt) throws MXFException {
    if (handler != null) {
      if (!handler.handle(evt) ||
          evt.getSeverity() == MXFEvent.Severity.FATAL) {
        /* die on FATAL events or if requested by the handler */
        throw new MXFException(evt.getMessage());
  
      }
    } else if (evt.getSeverity() == MXFEvent.Severity.ERROR ||
        evt.getSeverity() == MXFEvent.Severity.FATAL) {
      /* if no event handler was provided, die on FATAL and ERROR events */
      throw new MXFException(evt.getMessage());
    }
  }

}