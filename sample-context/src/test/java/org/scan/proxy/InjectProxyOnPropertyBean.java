package org.scan.proxy;


import org.demo.annotation.Autowired;
import org.demo.annotation.Component;

@Component
public class InjectProxyOnPropertyBean {

    @Autowired
    public OriginBean injected;
}
