package org.demo.aop.around;


import org.demo.annotation.Autowired;
import org.demo.annotation.Component;
import org.demo.annotation.Order;

@Order(0)
@Component
public class OtherBean {

    public OriginBean origin;

    public OtherBean(@Autowired OriginBean origin) {
        this.origin = origin;
    }
}
