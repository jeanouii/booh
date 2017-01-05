/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.tomitribe.booh;

import geb.Browser;
import geb.Configuration;
import geb.waiting.Wait;
import groovy.lang.Closure;
import org.apache.openejb.loader.Files;
import org.apache.openejb.loader.IO;
import org.apache.openejb.loader.SystemInstance;
import org.apache.openejb.loader.Zips;
import org.apache.openejb.loader.provisining.ProvisioningResolver;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.phantomjs.PhantomJSDriver;
import org.openqa.selenium.phantomjs.PhantomJSDriverService;
import org.openqa.selenium.remote.DesiredCapabilities;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class Booh {
    static {
        if (!SystemInstance.isInitialized()) {
            SystemInstance.get().setComponent(ProvisioningResolver.class, new ProvisioningResolver());
        }
    }

    private Booh() {
        // no-op
    }

    public static Browser drive(final Map config, final Closure closure) {
        final Configuration conf = new Configuration(new HashMap(config) {{
            putIfAbsent("driver", new Closure<WebDriver>(null) {
                @Override
                public WebDriver call() {
                    return newDriver();
                }
            });
            putIfAbsent("reportsDir", System.getProperty("booh.report.dir", "target/booh"));
        }});
        if (conf.getDefaultWaitTimeout().equals(Wait.DEFAULT_TIMEOUT)) {
            conf.setDefaultWaitTimeout(15.);
        }
        return Browser.drive(conf, closure);
    }

    public static Browser drive(final Closure closure) {
        return drive(new HashMap(), closure);
    }

    // for manual building of Browser
    public static WebDriver newDriver() {
        final File phantomJs = new File(System.getProperty("java.io.tmpdir", "temp"), "phantomjs-" + System.nanoTime());
        Files.deleteOnExit(phantomJs);
        phantomJs.mkdirs();

        final File exec = new File(phantomJs, "bin/phantomjs" + (System.getProperty("os.name").toLowerCase(Locale.ENGLISH).contains("win") ? ".exe" : ""));
        if (!exec.isFile()) {
            final File tmp = new File(phantomJs.getParentFile(), "phantom.jar");
            try {
                try (final InputStream stream = SystemInstance.get().getComponent(ProvisioningResolver.class)
                        .resolveStream("mvn:org.jboss.arquillian.extension:arquillian-phantom-binary:2.1.1:jar:" + findSuffix());
                     final OutputStream out = new FileOutputStream(tmp)) {
                    IO.copy(stream, out);
                }
                Zips.unzip(tmp, phantomJs);
            } catch (final IOException e) {
                throw new IllegalStateException(e);
            }
            exec.setExecutable(true);
        }

        final PhantomJSDriverService service = new PhantomJSDriverService.Builder().usingPhantomJSExecutable(exec).usingAnyFreePort().build();
        final PhantomJSDriver driver = new PhantomJSDriver(service, DesiredCapabilities.chrome());
        Runtime.getRuntime().addShutdownHook(new Thread(driver::close));
        return driver;
    }

    private static String findSuffix() {
        final String os = System.getProperty("os.name").toLowerCase(Locale.ENGLISH);
        if (os.contains("mac")) {
            return "macosx";
        }
        if (os.contains("win")) {
            return "windows";
        }
        if (os.contains("linux")) {
            return "linux-64";
        }
        throw new IllegalArgumentException("Unsupported platform (or force os.name system property to contain mac, win or linux): " + os);
    }
}
