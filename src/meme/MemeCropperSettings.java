package meme;

import org.w3c.dom.*;
import util.Settings;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.awt.*;
import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Objects;

/**
 * Class to load/save settings for the MemeCropper class
 */
public class MemeCropperSettings {
    /**
     * Represents various tokens for XML parsing
     */
    enum ConfigToken {
        Config("config"), Debug("debug"), DirToCrop("crop-dir"),
        DirOfCropped("output-dir"), Value("value"), Color("color"),
        RedColor("r"), GreenColor("g"), BlueColor("b"),
        DeleteOriginal("del-original"), Sensitivity("sens"),
        Speed("speed"), NumThreads("num-threads"),
        CroppedColors("cropped-colors");
        /**
         * Name of node/attribute
         */
        String token;

        ConfigToken(String str) {
            this.token = str;
        }
    }

    /**
     * Name of program (i.e. folder name of settings)
     */
    public static final String MEME_CROPPER_FILE = "MemeCropper";
    /**
     * Whether debug information is printed
     */
    public boolean debug = false;
    /**
     * File directory path containing images to crop
     */
    public String fileDirToCrop = "/Users/adminasaurus/Desktop/le memes/";
    /**
     * File directory containing images to crop
     */
    public File dirToCrop = new File(fileDirToCrop);
    /**
     * File directory path containing the output directory of cropped images
     */
    public String fileDirCropped = "/Users/adminasaurus/Desktop/le memes/cropped/";
    /**
     * Whether the original image should be deleted
     */
    public boolean deleteOriginal = true;
    /**
     * How sensitive detection is - higher values means more pixels will be cropped
     */
    public short sensitivity = 10;
    /**
     * How many columns/rows to skip during checks, 1 being 0 skipping, 2 skipping 1, etc.
     */
    public short speed = 4;
    /**
     * Number of threads created
     */
    public short numThreads = 4;
    /**
     * List of cropped colors
     */
    public Color[] croppedColors = {
            Color.WHITE,
            Color.BLACK,
            new Color(22, 28, 44, 255),
            new Color(33, 33, 33, 255),
            new Color(18, 18, 18, 255),
            new Color(20, 20, 20, 255),
            new Color(54, 57, 66, 255),
            new Color(19, 31, 45, 255),
            new Color(23, 40, 50, 255)
    };
    /**
     * Corresponding comments to cropped colors
     */
    public String[] croppedColorsComment = {
            "White",
            "Black",
            "Twitter dark screen",
            "Some dark grey",
            "Reddit dark theme",
            "Born Pub",
            "Discord grey",
            "Some dark blue, twitter",
            "Some dark blue, twitter"
    };
    /**
     * RGB representations of each color
     */
    public int[] croppedColorsInt = new int[croppedColors.length];

    /**
     * Loads the configuration from the default file location.
     * If not found, creates required folders/files and stores default information.
     * If unsuccessful, loads default values.
     */
    public MemeCropperSettings() {
        File appDir = Settings.getConfigLocation(MEME_CROPPER_FILE);
        try {
            if (!appDir.exists()) {
                if (!appDir.mkdir()) throw new FileNotFoundException();
            }
            File configFileLocation = new File(appDir.getPath() + "/" + "config.xml");
            if (configFileLocation.exists()) {
                System.out.println("File exists!");
                boolean result = loadSettings(configFileLocation.getPath());
                if (!result) System.err.println("Couldn't load settings from file " + configFileLocation.getPath());
            } else {
                if (configFileLocation.createNewFile()) {
                    boolean result = writeSettings(configFileLocation.getPath());
                    if (!result) System.err.println("Couldn't write settings to file " + configFileLocation.getPath());
                } else {
                    System.err.println("Couldn't create file " + configFileLocation.getPath());
                }
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }

        initNonPersistentValues();
    }

    /**
     * Determinant for whether this config is valid (i.e. no null values, ensures speed/numThreads are positive and non-zero
     *
     * @return Whether this config is valid
     */
    public boolean isValid() {
        for (Color c : croppedColors) {
            if (c == null) return false;
        }
        for (String str : croppedColorsComment) {
            if (str == null) return false;
        }
        return speed >= 1 && numThreads > 0 && fileDirToCrop != null && fileDirCropped != null && dirToCrop != null;
    }

    /**
     * Given a file path, loads all the settings provided in that path into this object
     *
     * @param path File path of XML config
     * @return Whether the loading was successful
     */
    public boolean loadSettings(String path) {
        try {
            File input = new File(path);
            DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder db = dbFactory.newDocumentBuilder();
            Document doc = db.parse(input);
            Element root = doc.getDocumentElement();
            root.normalize();
            if (hasValue(root, ConfigToken.Debug)) debug = Boolean.parseBoolean(getValue(root, ConfigToken.Debug));
            if (hasValue(root, ConfigToken.DirToCrop)) fileDirToCrop = getValue(root, ConfigToken.DirToCrop);
            if (hasValue(root, ConfigToken.DirOfCropped)) fileDirCropped = getValue(root, ConfigToken.DirOfCropped);
            if (hasValue(root, ConfigToken.DeleteOriginal))
                deleteOriginal = Boolean.parseBoolean(getValue(root, ConfigToken.DeleteOriginal));
            if (hasValue(root, ConfigToken.Sensitivity))
                sensitivity = Short.parseShort(Objects.requireNonNull(getValue(root, ConfigToken.Sensitivity)));
            if (hasValue(root, ConfigToken.Speed))
                speed = Short.parseShort(Objects.requireNonNull(getValue(root, ConfigToken.Speed)));
            if (hasValue(root, ConfigToken.NumThreads))
                numThreads = Short.parseShort(Objects.requireNonNull(getValue(root, ConfigToken.NumThreads)));
            loadColors(root);

            initNonPersistentValues();
            return true;
        } catch (Exception ex) {
            return false;
        }
    }

    /**
     * Loads only the colors into this object from a given XML element
     *
     * @param root XML element of document root
     */
    private void loadColors(Element root) {
        NodeList list = root.getElementsByTagName(ConfigToken.CroppedColors.token);
        if (list == null || list.getLength() == 0) return;
        for (int i = 0; i < list.getLength(); i++) {
            if (list.item(i).getNodeType() == Node.ELEMENT_NODE) {
                NodeList colors = list.item(i).getChildNodes();
                ArrayList<String> comments = new ArrayList<>();
                ArrayList<Color> colorList = new ArrayList<>();
                for (int j = 0; j < colors.getLength(); j++) {
                    Node node = colors.item(j);
                    if (node.getNodeType() == Node.ELEMENT_NODE) {
                        if (node.getNodeName().equals(ConfigToken.Color.token)) {
                            NamedNodeMap attributes = node.getAttributes();
                            int r = Integer.parseInt(attributes.getNamedItem(ConfigToken.RedColor.token).getNodeValue());
                            int g = Integer.parseInt(attributes.getNamedItem(ConfigToken.GreenColor.token).getNodeValue());
                            int b = Integer.parseInt(attributes.getNamedItem(ConfigToken.BlueColor.token).getNodeValue());
                            colorList.add(new Color(r, g, b));
                        }
                    } else if (node.getNodeType() == Node.COMMENT_NODE) {
                        comments.add(node.getNodeValue());
                    }
                }
                croppedColors = colorList.toArray(new Color[0]);
                croppedColorsComment = comments.toArray(new String[0]);
            }
        }
    }

    /**
     * Of a given node, determine whether there is a child node with given name described in a token with a value attribute
     *
     * @param parent Parent node in question
     * @param token  ConfigToken to determine which node value to get
     * @return Whether a value/node exists
     */
    private static boolean hasValue(Element parent, ConfigToken token) {
        NodeList list = parent.getElementsByTagName(token.token);
        if (list == null || list.getLength() == 0) return false;
        for (int i = 0; i < list.getLength(); i++) {
            if (list.item(i).getNodeType() == Node.ELEMENT_NODE) {
                NamedNodeMap attributes = list.item(i).getAttributes();
                if (attributes.getNamedItem(ConfigToken.Config.token) != null) return true;
            }
        }
        return false;
    }

    /**
     * Of a given node, get the value attribute of  a child node with given name
     *
     * @param parent Parent node in question
     * @param token  ConfigToken to determine which node value to get
     * @return Value in value attribute
     */
    private static String getValue(Element parent, ConfigToken token) {
        NodeList list = parent.getElementsByTagName(token.token);
        if (list.getLength() == 0) return null;
        String result = null;
        for (int i = 0; i < list.getLength(); i++) {
            if (list.item(i).getNodeType() == Node.ELEMENT_NODE) {
                NamedNodeMap attributes = list.item(i).getAttributes();
                result = attributes.getNamedItem(ConfigToken.Value.token).getNodeValue();
            }
        }
        return result;
    }

    /**
     * Writes out this settings object to a file with given path
     *
     * @param path Path of file
     * @return Whether writing was successful
     */
    public boolean writeSettings(String path) {
        try {
            DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder db = dbFactory.newDocumentBuilder();
            Document doc = db.newDocument();

            Element root = doc.createElement(ConfigToken.Config.token);
            doc.appendChild(root);

            root.appendChild(createElement(ConfigToken.Debug, "" + debug, doc));
            root.appendChild(createElement(ConfigToken.DirToCrop, fileDirToCrop, doc));
            root.appendChild(createElement(ConfigToken.DirOfCropped, fileDirCropped, doc));
            root.appendChild(createElement(ConfigToken.DeleteOriginal, "" + deleteOriginal, doc));
            root.appendChild(createElement(ConfigToken.Sensitivity, "" + sensitivity, doc));
            root.appendChild(createElement(ConfigToken.Speed, "" + speed, doc));
            root.appendChild(createElement(ConfigToken.NumThreads, "" + numThreads, doc));
            Element colors = doc.createElement(ConfigToken.CroppedColors.token);
            for (int i = 0; i < croppedColors.length; i++) {
                colors.appendChild(doc.createComment(croppedColorsComment[i]));
                colors.appendChild(createColorElement(croppedColors[i], doc));
            }
            root.appendChild(colors);
            TransformerFactory transformerFactory = TransformerFactory.newInstance();
            Transformer transformer = transformerFactory.newTransformer();
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
            DOMSource source = new DOMSource(doc);
            StreamResult result = new StreamResult(new File(path));
            transformer.transform(source, result);
            return true;
        } catch (Exception ex) {
            ex.printStackTrace();
            return false;
        }
    }

    /**
     * Creates a color XML element with given color
     *
     * @param color Color to create an element of
     * @param doc   Document to create the element from
     * @return Created XML element
     */
    private Element createColorElement(Color color, Document doc) {
        Element element = doc.createElement(ConfigToken.Color.token);
        Attr r = doc.createAttribute(ConfigToken.RedColor.token);
        r.setValue("" + color.getRed());
        element.setAttributeNode(r);
        Attr g = doc.createAttribute(ConfigToken.GreenColor.token);
        g.setValue("" + color.getGreen());
        element.setAttributeNode(g);
        Attr b = doc.createAttribute(ConfigToken.BlueColor.token);
        b.setValue("" + color.getBlue());
        element.setAttributeNode(b);
        return element;
    }

    /**
     * Creates an XML element with given name and value
     *
     * @param token ConfigToken to determine the name of the element
     * @param value String to put inside value attribute of element
     * @param doc   Document to create the element from
     * @return Created XML element
     */
    private Element createElement(ConfigToken token, String value, Document doc) {
        Element element = doc.createElement(token.token);
        Attr attr = doc.createAttribute(ConfigToken.Value.token);
        attr.setValue(value);
        element.setAttributeNode(attr);
        return element;
    }

    /**
     * Initializes non-persistent values (e.g. RGB integer values and dirToCrop)
     */
    private void initNonPersistentValues() {
        for (int i = 0; i < croppedColors.length; i++) croppedColorsInt[i] = croppedColors[i].getRGB();
        dirToCrop = new File(fileDirToCrop);
    }
}