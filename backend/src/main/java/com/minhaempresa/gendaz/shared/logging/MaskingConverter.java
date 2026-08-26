package com.minhaempresa.gendaz.shared.logging;

import ch.qos.logback.classic.pattern.ClassicConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;

/** Applies the final sensitive-data barrier to every formatted application log. */
public class MaskingConverter extends ClassicConverter {
    @Override
    public String convert(ILoggingEvent event) {
        return event == null ? "" : SensitiveDataSanitizer.sanitize(event.getFormattedMessage());
    }
}
