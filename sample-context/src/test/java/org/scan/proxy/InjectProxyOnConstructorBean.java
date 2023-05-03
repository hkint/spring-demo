package org.scan.proxy;


import org.demo.annotation.Autowired;
import org.demo.annotation.Component;

/**
 * 用于检测是否注入了Proxy
 */
@Component
public class InjectProxyOnConstructorBean {

    public final OriginBean injected;

    public InjectProxyOnConstructorBean(@Autowired OriginBean injected) {
        this.injected = injected;
    }
}
