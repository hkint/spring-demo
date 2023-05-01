package org.demo.scan.nested;

import org.demo.annotation.Component;

@Component
public class OuterBean {

    @Component
    public static class NestedBean {

    }
}
