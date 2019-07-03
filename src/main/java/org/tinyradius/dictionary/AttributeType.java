package org.tinyradius.dictionary;

import org.tinyradius.attribute.*;

import java.util.HashMap;
import java.util.Map;

import static java.util.Objects.requireNonNull;
import static org.tinyradius.attribute.VendorSpecificAttribute.VENDOR_SPECIFIC;

/**
 * Represents a Radius attribute type.
 */
public class AttributeType {

    private final int vendorId;
    private final int typeCode;
    private final String name;
    private final AttributeBuilder.PacketParser packetParser;
    private final AttributeBuilder.ByteArrayConstructor byteArrayConstructor;
    private final AttributeBuilder.StringConstructor stringConstructor;
    private final Map<Integer, String> enumeration = new HashMap<>();

    /**
     * Create a new attribute type.
     *
     * @param attributeType    Radius attribute type code
     * @param name    Attribute type name
     * @param typeStr string|octets|integer|date|ipaddr|ipv6addr|ipv6prefix
     */
    public AttributeType(int attributeType, String name, String typeStr) {
        this(-1, attributeType, name, typeStr);
    }

    /**
     * Constructs a Vendor-Specific sub-attribute type.
     *
     * @param vendorId vendor ID
     * @param attributeType     sub-attribute type code
     * @param name     sub-attribute name
     * @param dataType  string|octets|integer|date|ipaddr|ipv6addr|ipv6prefix
     */
    public AttributeType(int vendorId, int attributeType, String name, String dataType) {
        if (attributeType < 1 || attributeType > 255)
            throw new IllegalArgumentException("type code out of bounds");
        if (name == null || name.isEmpty())
            throw new IllegalArgumentException("name is empty");
        requireNonNull(dataType, "type is null");
        this.vendorId = vendorId;
        this.typeCode = attributeType;
        this.name = name;

        if (attributeType == VENDOR_SPECIFIC) {
            packetParser = VendorSpecificAttribute::parse;
            byteArrayConstructor = (a, b, c, d) -> {
                throw new IllegalArgumentException("should not instantiate VendorSpecificAttribute with attribute byte array directly");
            };
            stringConstructor = (a, b, c, d) -> {
                throw new IllegalArgumentException("should not instantiate VendorSpecificAttribute with attribute byte array directly");
            };
            return;
        }

        switch (dataType.toLowerCase()) {
            case "string":
                packetParser = StringAttribute::parse;
                byteArrayConstructor = StringAttribute::new;
                stringConstructor = StringAttribute::new;
                break;
            case "integer":
            case "date":
                packetParser = IntegerAttribute::parse;
                byteArrayConstructor = IntegerAttribute::new;
                stringConstructor = IntegerAttribute::new;

                break;
            case "ipaddr":
                packetParser = IpAttribute::parse;
                byteArrayConstructor = IpAttribute::new;
                stringConstructor = IpAttribute::new;

                break;
            case "ipv6addr":
                packetParser = Ipv6Attribute::parse;
                byteArrayConstructor = Ipv6Attribute::new;
                stringConstructor = Ipv6Attribute::new;

                break;
            case "ipv6prefix":
                packetParser = Ipv6PrefixAttribute::parse;
                byteArrayConstructor = Ipv6PrefixAttribute::new;
                stringConstructor = Ipv6PrefixAttribute::new;

                break;
            case "vsa":
                packetParser = VendorSpecificAttribute::parse;
                byteArrayConstructor = (a, b, c, d) -> {
                    throw new IllegalArgumentException("should not instantiate VendorSpecificAttribute with attribute byte array directly");
                };
                stringConstructor = (a, b, c, d) -> {
                    throw new IllegalArgumentException("should not instantiate VendorSpecificAttribute with attribute byte array directly");
                };
                break;
            case "octets":
            default:
                packetParser = RadiusAttribute::parse;
                byteArrayConstructor = RadiusAttribute::new;
                stringConstructor = (a, b, c, d) -> {
                    throw new RuntimeException("cannot set the value of attribute " + attributeType + " as a string");
                };

        }
    }

    /**
     * Retrieves the Radius type code for this attribute type.
     *
     * @return Radius type code
     */
    public int getTypeCode() {
        return typeCode;
    }

    /**
     * Retrieves the name of this type.
     *
     * @return name
     */
    public String getName() {
        return name;
    }

    /**
     * Retrieves the RadiusAttribute descendant class which represents
     * attributes of this type.
     *
     * @return class
     */
    public AttributeBuilder.PacketParser getPacketParser() {
        return packetParser;
    }

    public AttributeBuilder.ByteArrayConstructor getByteArrayConstructor() {
        return byteArrayConstructor;
    }

    public AttributeBuilder.StringConstructor getStringConstructor() {
        return stringConstructor;
    }

    /**
     * Returns the vendor ID.
     * No vendor specific attribute = -1
     *
     * @return vendor ID
     */
    public int getVendorId() {
        return vendorId;
    }

    /**
     * @param value int value
     * @return the name of the given integer value if this attribute
     * is an enumeration, or null if it is not or if the integer value
     * is unknown.
     */
    public String getEnumeration(int value) {
        return enumeration.get(value);
    }

    /**
     * @param value string value
     * @return the number of the given string value if this attribute is
     * an enumeration, or null if it is not or if the string value is unknown.
     */
    public Integer getEnumeration(String value) {
        if (value == null || value.isEmpty())
            throw new IllegalArgumentException("value is empty");
        for (Map.Entry<Integer, String> e : enumeration.entrySet()) {
            if (e.getValue().equals(value))
                return e.getKey();
        }
        return null;
    }

    /**
     * Adds a name for an integer value of this attribute.
     *
     * @param num  number that shall get a name
     * @param name the name for this number
     */
    public void addEnumerationValue(int num, String name) {
        if (name == null || name.isEmpty())
            throw new IllegalArgumentException("name is empty");
        enumeration.put(num, name);
    }

    /**
     * String representation of AttributeType object
     * for debugging purposes.
     *
     * @return string
     */
    public String toString() {
        String s = getTypeCode() + "/" + getName() + ": " + packetParser.getClass();
        if (getVendorId() != -1)
            s += " (vendor " + getVendorId() + ")";
        return s;
    }
}
