package org.scan;

import org.demo.annotation.ComponentScan;
import org.demo.annotation.Import;
import org.imported.LocalDateConfiguration;
import org.imported.ZonedDateConfiguration;

@ComponentScan
@Import({ LocalDateConfiguration.class, ZonedDateConfiguration.class })
public class ScanApplication {

}
