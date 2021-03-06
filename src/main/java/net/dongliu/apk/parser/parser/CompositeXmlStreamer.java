package net.dongliu.apk.parser.parser;

import net.dongliu.apk.parser.struct.xml.*;

/**
 * @author dongliu
 */
public class CompositeXmlStreamer implements XmlStreamer {

    public XmlStreamer[] xmlStreamers;

    public CompositeXmlStreamer(XmlStreamer... xmlStreamers) {
        this.xmlStreamers = xmlStreamers;
    }

    @Override
    public void onStartTag(XmlNodeStartTag xmlNodeStartTag) {
        for (XmlStreamer xmlStreamer : xmlStreamers) {
            xmlStreamer.onStartTag(xmlNodeStartTag);
        }
    }

    @Override
    public void onEndTag(XmlNodeEndTag xmlNodeEndTag) {
        for (XmlStreamer xmlStreamer : xmlStreamers) {
            xmlStreamer.onEndTag(xmlNodeEndTag);
        }
    }

    @Override
    public void onAttribute(Attribute attribute) {
        for (XmlStreamer xmlStreamer : xmlStreamers) {
            xmlStreamer.onAttribute(attribute);
        }
    }

    @Override
    public void onCData(XmlCData xmlCData) {
        for (XmlStreamer xmlStreamer : xmlStreamers) {
            xmlStreamer.onCData(xmlCData);
        }
    }

    @Override
    public void onNamespace(XmlNamespaceStartTag namespace) {
        for (XmlStreamer xmlStreamer : xmlStreamers) {
            xmlStreamer.onNamespace(namespace);
        }
    }
}
