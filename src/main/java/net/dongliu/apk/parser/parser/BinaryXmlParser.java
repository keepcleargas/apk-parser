package net.dongliu.apk.parser.parser;

import net.dongliu.apk.parser.bean.Locale;
import net.dongliu.apk.parser.exception.ParserException;
import net.dongliu.apk.parser.io.SU;
import net.dongliu.apk.parser.io.TellableInputStream;
import net.dongliu.apk.parser.struct.*;
import net.dongliu.apk.parser.struct.resource.ResourceTable;
import net.dongliu.apk.parser.struct.xml.*;

import java.io.IOException;
import java.io.InputStream;

/**
 * Android Binary XML format
 * see http://justanapplication.wordpress.com/category/android/android-binary-xml/
 *
 * @author dongliu
 */
public class BinaryXmlParser {


    /**
     * By default the data in Chunks is in little-endian byte order both at runtime and when stored in files.
     */
    private ByteOrder byteOrder = ByteOrder.LITTLE;
    private StringPool stringPool;
    private String[] resourceMap;
    private long[] resourceIds;
    private XmlNamespaceStartTag namespace;
    private TellableInputStream in;
    private XmlStreamer xmlStreamer;
    private ResourceTable resourceTable;
    /**
     * default locale.
     */
    private Locale locale = Locale.any;

    public BinaryXmlParser(InputStream in, ResourceTable resourceTable) {
        this.in = new TellableInputStream(in, byteOrder);
        this.resourceTable = resourceTable;
    }

    /**
     * Parse binary xml.
     *
     * @throws java.io.IOException
     */
    public void parse() throws IOException {
        try {
            ChunkHeader chunkHeader = readChunkHeader();
            if (chunkHeader == null || chunkHeader.chunkType != ChunkType.XML) {
                //TODO: may be a plain xml file.
                return;
            }
            XmlHeader xmlHeader = (XmlHeader) chunkHeader;

            // read string pool chunk
            chunkHeader = readChunkHeader();
            SU.checkChunkType(ChunkType.STRING_POOL, chunkHeader.chunkType);
            stringPool = SU.readStringPool(in, (StringPoolHeader) chunkHeader);

            // read on chunk, check if it was an optional XMLResourceMap chunk
            chunkHeader = readChunkHeader();
            if (chunkHeader.chunkType == ChunkType.XML_RESOURCE_MAP) {
                resourceIds = readXmlResourceMap((XmlResourceMapHeader) chunkHeader);
                resourceMap = new String[resourceIds.length];
                for (int i = 0; i < resourceIds.length; i++) {
                    resourceMap[i] = Attribute.AttrIds.getString(resourceIds[i]);
                }
                chunkHeader = readChunkHeader();
            }

            // should be StartNamespace chunk
            SU.checkChunkType(ChunkType.XML_START_NAMESPACE, chunkHeader.chunkType);

            namespace = readXmlNamespaceStartTag();

            xmlStreamer.onNamespace(namespace);

            int shift = 0;
            do {
                // root startElement chunk
                chunkHeader = readChunkHeader();
                if (chunkHeader.chunkType == ChunkType.XML_END_NAMESPACE) {
                    break;
                }
                long beginPos = in.tell();
                switch (chunkHeader.chunkType) {
                    case ChunkType.XML_START_ELEMENT:
                        XmlNodeStartTag xmlNodeStartTag = readXmlNodeStartTag();
                        shift++;
                        break;
                    case ChunkType.XML_END_ELEMENT:
                        XmlNodeEndTag xmlNodeEndTag = readXmlNodeEndTag();
                        shift--;
                        break;
                    case ChunkType.XML_CDATA:
                        XmlCData xmlCData = readXmlCData();
                        break;
                    case ChunkType.XML_FIRST_CHUNK:
                    case ChunkType.XML_LAST_CHUNK:
                        //TODO: what is this for
                        in.skip((int) (chunkHeader.chunkSize - chunkHeader.headerSize));
                        break;
                    default:
                        throw new ParserException("Unexpected chunk type:" + chunkHeader.chunkType);
                }
                in.advanceIfNotRearch(beginPos + chunkHeader.chunkSize - chunkHeader.headerSize);

            } while (true);

            XmlNamespaceEndTag xmlNamespaceEndTag = readXmlNamespaceEndTag();
        } finally {
            this.in.close();
        }
    }

    private XmlCData readXmlCData() throws IOException {
        XmlCData xmlCData = new XmlCData();
        int dataRef = in.readInt();
        if (dataRef > 0) {
            xmlCData.data = stringPool.get(dataRef);
        }
        xmlCData.typedData = SU.readResValue(in, stringPool, resourceTable, locale);
        if (xmlStreamer != null) {
            xmlStreamer.onCData(xmlCData);
        }
        return xmlCData;
    }

    private XmlNodeEndTag readXmlNodeEndTag() throws IOException {
        XmlNodeEndTag xmlNodeEndTag = new XmlNodeEndTag();
        int nsRef = in.readInt();
        int nameRef = in.readInt();
        if (nsRef > 0) {
            xmlNodeEndTag.namespace = stringPool.get(nsRef);
        }
        xmlNodeEndTag.name = stringPool.get(nameRef);
        if (xmlStreamer != null) {
            xmlStreamer.onEndTag(xmlNodeEndTag);
        }
        return xmlNodeEndTag;
    }

    private XmlNodeStartTag readXmlNodeStartTag() throws IOException {
        int nsRef = in.readInt();
        int nameRef = in.readInt();
        XmlNodeStartTag xmlNodeStartTag = new XmlNodeStartTag();
        if (nsRef > 0) {
            xmlNodeStartTag.namespace = stringPool.get(nsRef);
        }
        xmlNodeStartTag.name = stringPool.get(nameRef);

        if (xmlStreamer != null) {
            xmlStreamer.onStartTag(xmlNodeStartTag);
        }

        // read attribute infos.
        // attributeStart and attributeSize are always 20 (0x14)
        int attributeStart = in.readUShort();
        int attributeSize = in.readUShort();
        int attributeCount = in.readUShort();
        int idIndex = in.readUShort();
        int classIndex = in.readUShort();
        int styleIndex = in.readUShort();

        // read attributes
        for (int count = 0; count < attributeCount; count++) {
            Attribute attribute = readAttribute();
            if (xmlStreamer != null) {
                xmlStreamer.onAttribute(attribute);
            }
        }

        return xmlNodeStartTag;
    }

    private Attribute readAttribute() throws IOException {
        int nsRef = in.readInt();
        int nameRef = in.readInt();
        Attribute attribute = new Attribute();
        if (nsRef > 0) {
            attribute.namespace = stringPool.get(nsRef);
        }

        attribute.name = stringPool.get(nameRef);
        if (attribute.name.isEmpty() && resourceMap != null && nameRef < resourceMap.length) {
            // some processed apk file make the string pool value empty, if it is a xmlmap attr.
            attribute.name = resourceMap[nameRef];
            if (namespace.uri != null && !namespace.uri.isEmpty()) {
                attribute.namespace = namespace.uri;
            }
        }

        int rawValueRef = in.readInt();
        if (rawValueRef > 0) {
            attribute.rawValue = stringPool.get(rawValueRef);
        }
        attribute.typedValue = SU.readResValue(in, stringPool, resourceTable, locale);

        return attribute;
    }

    private XmlNamespaceStartTag readXmlNamespaceStartTag() throws IOException {
        int prefixRef = in.readInt();
        int uriRef = in.readInt();
        XmlNamespaceStartTag nameSpace = new XmlNamespaceStartTag();
        if (prefixRef > 0) {
            nameSpace.prefix = stringPool.get(prefixRef);
        }
        if (uriRef > 0) {
            nameSpace.uri = stringPool.get(uriRef);
        }
        return nameSpace;
    }

    private XmlNamespaceEndTag readXmlNamespaceEndTag() throws IOException {
        int prefixRef = in.readInt();
        int uriRef = in.readInt();
        XmlNamespaceEndTag nameSpace = new XmlNamespaceEndTag();
        if (prefixRef > 0) {
            nameSpace.prefix = stringPool.get(prefixRef);
        }
        if (uriRef > 0) {
            nameSpace.uri = stringPool.get(uriRef);
        }
        return nameSpace;
    }

    private long[] readXmlResourceMap(XmlResourceMapHeader chunkHeader) throws IOException {
        int count = (int) ((chunkHeader.chunkSize - chunkHeader.headerSize) / 4);
        long[] resourceIds = new long[count];
        for (int i = 0; i < count; i++) {
            resourceIds[i] = in.readUInt();
        }
        return resourceIds;
    }


    private ChunkHeader readChunkHeader() throws IOException {
        int chunkType = in.readUShort();

        int headSize = in.readUShort();
        long chunkSize = in.readUInt();

        switch (chunkType) {
            case ChunkType.XML:
                return new XmlHeader(chunkType, headSize, chunkSize);
            case ChunkType.STRING_POOL:
                StringPoolHeader stringPoolHeader = new StringPoolHeader(chunkType, headSize, chunkSize);
                stringPoolHeader.stringCount = in.readUInt();
                stringPoolHeader.styleCount = in.readUInt();
                stringPoolHeader.flags = in.readUInt();
                stringPoolHeader.stringsStart = in.readUInt();
                stringPoolHeader.stylesStart = in.readUInt();
                return stringPoolHeader;
            case ChunkType.XML_RESOURCE_MAP:
                return new XmlResourceMapHeader(chunkType, headSize, chunkSize);
            case ChunkType.XML_START_NAMESPACE:
            case ChunkType.XML_END_NAMESPACE:
            case ChunkType.XML_START_ELEMENT:
            case ChunkType.XML_END_ELEMENT:
            case ChunkType.XML_CDATA:
                XmlNodeHeader header = new XmlNodeHeader(chunkType, headSize, chunkSize);
                header.lineNum = (int) in.readUInt();
                header.commentRef = (int) in.readUInt();
                return header;
            case ChunkType.NULL:
                in.skip((int) (chunkSize - headSize));
            default:
                throw new ParserException("Unexpected chunk type:" + chunkType);
        }
    }

    public void setLocale(Locale locale) {
        if (locale != null) {
            this.locale = locale;
        }
    }

    public Locale getLocale() {
        return locale;
    }

    public XmlStreamer getXmlStreamer() {
        return xmlStreamer;
    }

    public void setXmlStreamer(XmlStreamer xmlStreamer) {
        this.xmlStreamer = xmlStreamer;
    }
}
