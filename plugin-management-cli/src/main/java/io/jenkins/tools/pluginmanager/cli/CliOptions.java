package io.jenkins.tools.pluginmanager.cli;

import io.jenkins.tools.pluginmanager.config.Config;
import io.jenkins.tools.pluginmanager.config.Settings;
import io.jenkins.tools.pluginmanager.impl.Plugin;
import java.io.File;
import java.io.FileNotFoundException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Scanner;
import org.apache.commons.lang3.StringUtils;
import org.kohsuke.args4j.Option;
import org.kohsuke.args4j.spi.BooleanOptionHandler;
import org.kohsuke.args4j.spi.FileOptionHandler;
import org.kohsuke.args4j.spi.StringArrayOptionHandler;
import org.kohsuke.args4j.spi.URLOptionHandler;

import static java.util.stream.Collectors.toList;

class CliOptions {
    //path must include plugins.txt
    @Option(name = "--plugin-file", aliases = {"-f"}, usage = "Path to plugins.txt file",
            handler = FileOptionHandler.class)
    private File pluginTxt;

    @Option(name = "--plugin-download-directory", aliases = {"-d"},
            usage = "Path to directory in which to install plugins",
            handler = FileOptionHandler.class)
    private File pluginDir;

    @Option(name = "--plugins", aliases = {"-p"}, usage = "List of plugins to install, separated by a space",
            handler = StringArrayOptionHandler.class)
    private String[] plugins = new String[0];

    @Option(name = "--war", aliases = {"-w"}, usage = "Path to Jenkins war file")
    private String jenkinsWarFile;

    @Option(name = "--view-security-warnings",
            usage = "Set to true to show specified plugins that have security warnings",
            handler = BooleanOptionHandler.class)
    private boolean showWarnings;

    @Option(name = "--view-all-security-warnings",
            usage = "Set to true to show all plugins that have security warnings",
            handler = BooleanOptionHandler.class)
    private boolean showAllWarnings;

    @Option(name = "--jenkins-update-center",
            usage = "Sets main update center; will override JENKINS_UC environment variable. If not set via CLI " +
                    "option or environment variable, will default to " + Settings.DEFAULT_UPDATE_CENTER_LOCATION,
            handler = URLOptionHandler.class)
    private URL jenkinsUc;

    @Option(name = "--jenkins-experimental-update-center",
            usage = "Sets experimental update center; will override JENKINS_UC_EXPERIMENTAL environment variable. If " +
                    "not set via CLI option or environment variable, will default to " +
                    Settings.DEFAULT_EXPERIMENTAL_UPDATE_CENTER_LOCATION,
            handler = URLOptionHandler.class
    )
    private URL jenkinsUcExperimental;

    @Option(name = "--jenkins-incrementals-repo-mirror",
            usage = "Set Maven mirror to be used to download plugins from the Incrementals repository, will override " +
                    "the JENKINS_INCREMENTALS_REPO_MIRROR environment variable. If not set via CLI option or " +
                    "environment variable, will default to " + Settings.DEFAULT_INCREMENTALS_REPO_MIRROR_LOCATION
            ,
            handler = URLOptionHandler.class)
    private URL jenkinsIncrementalsRepoMirror;

    /**
     * Creates a configuration class with configurations specified from the CLI and/or environment variables.
     *
     * @return a configuration class that can be passed to the PluginManager class
     */
    Config setup() {
        return Config.builder()
                .withPlugins(getPlugins())
                .withPluginDir(getPluginDir())
                .withJenkinsUc(getUpdateCenter())
                .withJenkinsUcExperimental(getExperimentalUpdateCenter())
                .withJenkinsWar(getJenkinsWar())
                .withShowWarnings(isShowWarnings())
                .withShowAllWarnings(isShowAllWarnings())
                .build();
    }


    private File getPluginTxt() {
        if (pluginTxt == null) {
            System.out.println("No file containing list of plugins to be downloaded entered. Will use default of " +
                    Settings.DEFAULT_PLUGIN_TXT);
            return Settings.DEFAULT_PLUGIN_TXT;
        } else {
            System.out.println("File containing list of plugins to be downloaded: " + pluginTxt);
            return pluginTxt;
        }
    }

    /**
     * Gets the user specified plugin download directory from the CLI option and sets this in the configuration class
     *
     */
    private File getPluginDir() {
        if (pluginDir == null) {
            System.out.println("No directory to download plugins entered. " +
                    "Will use default of " + Settings.DEFAULT_PLUGIN_DIR);
            return Settings.DEFAULT_PLUGIN_DIR;
        } else {
            System.out.println("Plugin download location: " + pluginDir);
            return pluginDir;
        }
    }

    /**
     * Gets the user specified Jenkins war from the CLI option and sets this in the configuration class
     *
     */
    private String getJenkinsWar() {
        if (jenkinsWarFile == null) {
            System.out.println("No war entered. Will use default of " + Settings.DEFAULT_WAR);
            return Settings.DEFAULT_WAR;
        } else {
            System.out.println("Will use war file: " + jenkinsWarFile);
            return jenkinsWarFile;
        }
    }

    private List<Plugin> getPlugins() {
        if (plugins == null) {
            return new ArrayList<>();
        }

        List<Plugin> mappedPlugins = Arrays.stream(plugins)
                .map(this::parsePluginLine)
                .collect(toList());

        File pluginFileLocation = getPluginTxt();
        if (Files.exists(pluginFileLocation.toPath())) {
            try (Scanner scanner = new Scanner(pluginFileLocation, StandardCharsets.UTF_8.name())) {
                System.out.println("Reading in plugins from " + pluginFileLocation.toString() + "\n");
                while (scanner.hasNextLine()) {
                    Plugin plugin = parsePluginLine(scanner.nextLine());
                    mappedPlugins.add(plugin);
                }
            } catch (FileNotFoundException e) {
                System.out.println("Unable to open " + pluginFileLocation);
            }
        } else {
            System.out.println(pluginFileLocation + " file does not exist");
        }

        return mappedPlugins;
    }

    /**
     * For each plugin specified in the CLI using the --plugins option or line in the plugins.txt file, creates a Plugin
     * object containing the  plugin name (required), version (optional), and url (optional)
     *
     * @param pluginLine plugin information to parse
     * @return plugin object containing name, version, and/or url
     */
    private Plugin parsePluginLine(String pluginLine) {
        String[] pluginInfo = pluginLine.split(":");
        String pluginName = pluginInfo[0];
        String pluginVersion = "latest";
        String pluginUrl = null;
        if (pluginInfo.length >= 2) {
            pluginVersion = pluginInfo[1];
        }
        if (pluginInfo.length == 3) {
            pluginUrl = pluginInfo[2];
        }

        return new Plugin(pluginName, pluginVersion, pluginUrl);
    }

    private boolean isShowWarnings() {
        return showWarnings;
    }

    private boolean isShowAllWarnings() {
        return showAllWarnings;
    }

    /**
     * Determines the update center url string. If a value is set via CLI option, it will override a value set via
     * environment variable. If neither are set, the default in the Settings class will be used.
     *
     * @return the update center url
     */
    private URL getUpdateCenter() {
        URL jenkinsUpdateCenter;

        if (jenkinsUc != null) {
            jenkinsUpdateCenter = jenkinsUc;
            System.out.println("Using update center " + jenkinsUpdateCenter + " specified with CLI option");
        } else if (!StringUtils.isEmpty(System.getenv("JENKINS_UC"))) {
            try {
                jenkinsUpdateCenter = new URL(System.getenv("JENKINS_UC"));
            } catch (MalformedURLException e) {
                throw new RuntimeException(e);
            }
            System.out.println("Using update center " + jenkinsUpdateCenter + " from JENKINS_UC environment variable");
        } else {
            jenkinsUpdateCenter = Settings.DEFAULT_UPDATE_CENTER;
            System.out.println("No CLI option or environment variable set for update center, using default of " +
                    jenkinsUpdateCenter);
        }
        return jenkinsUpdateCenter;
    }

    /**
     * Determines the experimental update center url string. If a value is set via CLI option, it will override a value
     * set via environment variable. If neither are set, the default in the Settings class will be used.
     *
     * @return the experimental update center url
     */
    private URL getExperimentalUpdateCenter() {
        URL experimentalUpdateCenter;
        if (jenkinsUcExperimental != null) {
            experimentalUpdateCenter = jenkinsUcExperimental;
            System.out.println(
                    "Using experimental update center " + experimentalUpdateCenter + " specified with CLI option");
        } else if (!StringUtils.isEmpty(System.getenv("JENKINS_UC_EXPERIMENTAL"))) {
            try {
                experimentalUpdateCenter = new URL(System.getenv("JENKINS_UC_EXPERIMENTAL"));
            } catch (MalformedURLException e) {
                throw new RuntimeException(e);
            }
            System.out.println("Using experimental update center " + experimentalUpdateCenter +
                    " from JENKINS_UC_EXPERIMENTAL environemnt variable");
        } else {
            experimentalUpdateCenter = Settings.DEFAULT_EXPERIMENTAL_UPDATE_CENTER;
            System.out.println(
                    "No CLI option or environment variable set for experimental update center, using default of " +
                            experimentalUpdateCenter);
        }
        return experimentalUpdateCenter;
    }

    /**
     * Determines the incrementals repository mirror url string. If a value is set via CLI option, it will override a
     * value set via environment variable. If neither are set, the default in the Settings class will be used.
     *
     * @return the incrementals repository mirror url
     */
    private URL getIncrementalsMirror() {
        URL jenkinsIncrementalsRepo;
        if (jenkinsIncrementalsRepoMirror != null) {
            jenkinsIncrementalsRepo = jenkinsIncrementalsRepoMirror;
            System.out.println("Using incrementals mirror " + jenkinsIncrementalsRepo + " specified with CLI option");
        } else if (!StringUtils.isEmpty(System.getenv("JENKINS_INCREMENTALS_REPO_MIRROR"))) {
            try {
                jenkinsIncrementalsRepo = new URL(System.getenv("JENKINS_INCREMENTALS_REPO_MIRROR"));
            } catch (MalformedURLException e) {
                throw new RuntimeException(e);
            }
            System.out.println("Using incrementals mirror " + jenkinsIncrementalsRepo +
                    " from JENKINS_INCREMENTALS_REPO_MIRROR environment variable");
        } else {
            jenkinsIncrementalsRepo = Settings.DEFAULT_INCREMENTALS_REPO_MIRROR;
            System.out.println("No CLI option or environment variable set for incrementals mirror, using default of " +
                    jenkinsIncrementalsRepo);
        }
        return jenkinsIncrementalsRepo;
    }
}