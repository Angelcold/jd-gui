/*
 * Copyright (c) 2008-2015 Emmanuel Dupuy
 * This program is made available under the terms of the GPLv3 License.
 */

package org.jd.gui.service.configuration

import groovy.xml.MarkupBuilder
import org.jd.gui.Constants
import org.jd.gui.model.configuration.Configuration
import org.jd.gui.service.platform.PlatformService

import java.awt.*

class ConfigurationXmlPersisterProvider implements ConfigurationPersister {
    static final String ERROR_BACKGROUND_COLOR = 'JdGuiPreferences.errorBackgroundColor'

    static final File FILE = getConfigFile()

    static File getConfigFile() {
        if (PlatformService.instance.isLinux) {
            // See: http://standards.freedesktop.org/basedir-spec/basedir-spec-latest.html
            def xdgConfigHome = System.getenv('XDG_CONFIG_HOME')
            if (xdgConfigHome) {
                def xdgConfigHomeFile = new File(xdgConfigHome)
                if (xdgConfigHomeFile.exists()) {
                    return new File(xdgConfigHomeFile, Constants.CONFIG_FILENAME)
                }
            }

            def userConfigFile = new File(System.getProperty('user.home'), '.config')
            if (userConfigFile.exists()) {
                return new File(userConfigFile, Constants.CONFIG_FILENAME)
            }
        } else if (PlatformService.instance.isWindows) {
            // See: http://blogs.msdn.com/b/patricka/archive/2010/03/18/where-should-i-store-my-data-and-configuration-files-if-i-target-multiple-os-versions.aspx
            def roamingConfigHome = System.getenv('APPDATA')
            if (roamingConfigHome) {
                def roamingConfigHomeFile = new File(roamingConfigHome)
                if (roamingConfigHomeFile.exists()) {
                    return new File(roamingConfigHomeFile, Constants.CONFIG_FILENAME)
                }
            }
        }

        return new File(Constants.CONFIG_FILENAME)
    }

    Configuration load() {
        // Default values
        def screenSize = Toolkit.defaultToolkit.screenSize

        int w = (screenSize.width>Constants.DEFAULT_WIDTH) ? Constants.DEFAULT_WIDTH : screenSize.width
        int h = (screenSize.height>Constants.DEFAULT_HEIGHT) ? Constants.DEFAULT_HEIGHT : screenSize.height
        int x = (screenSize.width-w)/2
        int y = (screenSize.height-h)/2

        def config = new Configuration()
        config.mainWindowLocation = new Point(x, y)
        config.mainWindowSize = new Dimension(w, h)
        config.mainWindowMaximize = false
        config.lookAndFeel = 'system'
        config.configRecentLoadDirectory = config.configRecentSaveDirectory = new File(System.getProperty('user.dir'))

        try {
            // Load values
            def configuration = new XmlSlurper().parse(FILE)
            if (configuration) {
                config.recentFiles = configuration.recentFilePaths?.filePath?.collect({ new File(it.text()) }).grep({ it.exists() })

                def recentDirectories = configuration.recentDirectories
                if (recentDirectories) {
                    if (recentDirectories.loadPath) {
                        def loadDirectory = new File(recentDirectories.loadPath.text())
                        if (loadDirectory.exists())
                            config.configRecentLoadDirectory = loadDirectory
                    }
                    if (recentDirectories.savePath) {
                        def saveDirectory = new File(recentDirectories.savePath.text())
                        if (saveDirectory.exists())
                            config.configRecentSaveDirectory = saveDirectory
                    }
                }

                config.lookAndFeel = configuration.gui.lookAndFeel ?: System.getProperty('swing.defaultlaf') ?: 'system'

                def mainWindow = configuration.gui.mainWindow
                def l = mainWindow.location
                def s = mainWindow.size
                def m = Boolean.valueOf(mainWindow.maximize.text())
                def lx = l.@x.text() as int, ly = l.@y.text() as int
                def sw = s.@w.text() as int, sh = s.@h.text() as int

                if ((lx >= 0) && (ly >= 0) && (lx + sw < screenSize.width) && (ly + sh < screenSize.height)) {
                    // Update preferences
                    config.mainWindowLocation = new Point(lx, ly)
                    config.mainWindowSize = new Dimension(sw, sh)
                    config.mainWindowMaximize = m
                }

                for (def node : configuration.preferences.childNodes()) {
                    config.preferences.put(node.name(), node.text())
                }
            }
        } catch (Exception ignore) {
        }

        if (! config.preferences.containsKey(ERROR_BACKGROUND_COLOR)) {
            config.preferences.put(ERROR_BACKGROUND_COLOR, '0xFF6666')
        }

        return config
    }

    void save(Configuration configuration) {
        Point l = configuration.mainWindowLocation
        Dimension s = configuration.mainWindowSize
        Writer writer = new StringWriter()
        MarkupBuilder xml = new MarkupBuilder(writer)

        xml.configuration() {
            gui() {
                mainWindow() {
                    location(x:l.x as int, y:l.y as int)
                    size(w:s.width as int, h:s.height as int)
                    maximize configuration.mainWindowMaximize
                }
                lookAndFeel configuration.lookAndFeel
            }
            recentFilePaths() {
                configuration.recentFiles.each { file ->
                    filePath file.absolutePath
                }
            }
            recentDirectories() {
                loadPath configuration.configRecentLoadDirectory?.absolutePath
                savePath configuration.configRecentSaveDirectory?.absolutePath
            }
            preferences() {
                configuration.preferences.each { key, value ->
                    "$key" "$value"
                }
            }
        }

        FILE.write(writer.toString())
    }
}
