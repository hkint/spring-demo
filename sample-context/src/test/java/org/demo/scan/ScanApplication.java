package org.demo.scan;

import org.demo.annotation.ComponentScan;
import org.demo.annotation.Import;
import org.demo.imported.LocalDateConfiguration;
import org.demo.imported.ZonedDateConfiguration;

@ComponentScan
@Import({ LocalDateConfiguration.class, ZonedDateConfiguration.class })
public class ScanApplication {

}
