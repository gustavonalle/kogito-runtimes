package org.kie.kogito.rules.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Target;

@Target({ElementType.METHOD})
public @interface Conditions {
    When[] value();
}
