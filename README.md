# JavaMemeCropper

Automatic cropper of images (designed for memes in particular), with configurable colors to crop, sensitivities and
speed.

## Getting Started

### Configuration Notes

The cropper automatically writes/reads from a config in an unchanging location.

- On Windows OSes, it saves to user-home/AppData/Roaming/MemeCropper/config.xml
- On MacOS X, it saves to user-home/Library/Preferences/MemeCropper/config.xml
- On Linux and other OSes, it saves to user-home/.config/MemeCropper/config.xml

Note that it by default crops files with predefined locations for one of my personal devices, which may cause issues if
a configuration has not already been made.

### Running the program

To run the program,

1. Change the configuration (see Configuration Notes) to have crop-dir and output-dir point to the correct directories
   of the files (on my personal device, it was "/Users/adminasaurus/Desktop/le memes/" and "
   /Users/adminasaurus/Desktop/le memes/cropped/" respectively, which are the hard-coded defaults of the program).
2. Compile the program into a JAR archive (e.g. MemeCropper.jar)
3. Run java -jar MemeCropper.jar

Note that command-line arguments for the image cropper are ignored.

### Prerequisites

This project requires Java 8 or newer. I have tested this on a Mac OS X 10.10 operating system using Java 8, and I have
no guarantees on whether this will run on other OSes or older versions of Java.
