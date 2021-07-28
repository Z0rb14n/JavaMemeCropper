package util;

import java.io.File;
import java.util.Locale;

/**
 * Class to locate folders to store app settings for each operating system
 */
public class Settings {
    /**
     * Enumeration of operating systems
     */
    public enum OSType {
        Windows(Settings.home + "/AppData/Roaming"),
        MacOS(Settings.home + "/Library/Preferences"),
        Linux(Settings.home + "/.config"),
        Other(Settings.home + "/.config");
        /**
         * Folder to save each settings folder per app to
         */
        public String loc;

        OSType(String location) {
            this.loc = location;
        }
    }

    /**
     * User home
     */
    private static final String home = System.getProperty("user.home");
    /**
     * Type of current OS
     */
    private static OSType type;

    /**
     * Returns the singleton OS type
     *
     * @return current OS this machine has
     */
    public static OSType getOSType() {
        if (type == null) {
            String os = System.getProperty("os.name", "generic").toLowerCase(Locale.ENGLISH);
            if (os.contains("mac") || os.contains("darwin")) type = OSType.MacOS;
            else if (os.contains("win")) type = OSType.Windows;
            else if (os.contains("nux")) type = OSType.Linux;
            else type = OSType.Other;
        }
        return type;
    }

    /**
     * Gets the folder to save all config information to
     *
     * @param name Name of application
     * @return File object containing path to save all config information to
     */
    public static File getConfigLocation(String name) {
        return new File(getOSType().loc + "/" + name);
    }
}
