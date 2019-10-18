package org.kie.kogito.rules.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Target;

@Target({ElementType.PARAMETER})
public @interface When {
    String value();
}
