package net.dongliu.apk.parser;

import net.dongliu.apk.parser.bean.ApkMeta;
import net.dongliu.apk.parser.bean.CertificateMeta;
import net.dongliu.apk.parser.bean.Locale;
import net.dongliu.apk.parser.exception.ParserException;
import net.dongliu.apk.parser.parser.*;
import net.dongliu.apk.parser.struct.AndroidConstants;
import net.dongliu.apk.parser.struct.resource.ResourceTable;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.security.cert.CertificateEncodingException;
import java.util.*;

/**
 * ApkParser and result holder.
 * This class is not thread-safe.
 *
 * @author dongliu
 */
public class ApkParser implements Closeable {

    private ResourceTable resourceTable;

    private Map<Locale, String> manifestXmlMap;
    private Map<Locale, ApkMeta> apkMetaMap;
    private Set<Locale> locales;
    private List<CertificateMeta> certificateMetas;
    private final ZipFile zf;

    private Locale preferredLocale;

    public ApkParser(File apkFile) throws IOException {
        this.zf = new ZipFile(apkFile);
        this.preferredLocale = Locale.any;
        this.manifestXmlMap = new HashMap<Locale, String>();
        this.apkMetaMap = new HashMap<Locale, ApkMeta>();
    }

    /**
     * return decoded AndroidManifest.xml
     *
     * @return
     */
    public String getManifestXml() throws IOException {
        if (!manifestXmlMap.containsKey(preferredLocale)) {
            parseManifestXml();
        }
        return manifestXmlMap.get(preferredLocale);
    }

    /**
     * return decoded AndroidManifest.xml
     *
     * @return
     */
    public ApkMeta getApkMeta() throws IOException {
        if (!apkMetaMap.containsKey(preferredLocale)) {
            parseManifestXml();
        }
        return apkMetaMap.get(preferredLocale);
    }

    /**
     * get locales supported from resource file
     *
     * @return
     * @throws IOException
     */
    public Set<Locale> getLocales() throws IOException {
        if (this.locales == null) {
            parseResourceTable();
        }
        return this.locales;
    }

    /**
     * get the apk's certificates.
     *
     * @return
     */
    public List<CertificateMeta> getCertificateMetas() throws IOException,
            CertificateEncodingException {
        if (this.certificateMetas == null) {
            parseCertificate();
        }
        return this.certificateMetas;
    }

    private void parseCertificate() throws IOException, CertificateEncodingException {
        ZipArchiveEntry entry = null;
        Enumeration<ZipArchiveEntry> enu = zf.getEntries();
        while (enu.hasMoreElements()) {
            entry = enu.nextElement();
            if (entry.isDirectory()) {
                continue;
            }
            if (entry.getName().toUpperCase().endsWith(".RSA")
                    || entry.getName().toUpperCase().endsWith(".DSA")) {
                break;
            }
        }
        if (entry == null) {
            throw new ParserException("ApkParser certificate not found");
        }
        CetificateParser parser = new CetificateParser(zf.getInputStream(entry));
        parser.parse();
        this.certificateMetas = parser.getCertificateMetas();
    }

    /**
     * parse manifest.xml, get apkMeta and manifestXml text.
     *
     * @throws IOException
     */
    private void parseManifestXml() throws IOException {
        ZipArchiveEntry manifestEntry = null;
        Enumeration<ZipArchiveEntry> enu = zf.getEntries();
        while (enu.hasMoreElements()) {
            ZipArchiveEntry entry = enu.nextElement();
            if (entry.isDirectory()) {
                continue;
            }
            if (entry.getName().equals(AndroidConstants.MANIFEST)) {
                manifestEntry = entry;
            }

        }

        if (manifestEntry == null) {
            throw new ParserException("manifest xml file not found");
        }

        if (this.resourceTable == null) {
            parseResourceTable();
        }

        BinaryXmlParser binaryXmlParser = new BinaryXmlParser(zf.getInputStream(manifestEntry),
                resourceTable);
        binaryXmlParser.setLocale(preferredLocale);
        XmlTranslator xmlTranslator = new XmlTranslator();
        ApkMetaConstructor apkMetaConstructor = new ApkMetaConstructor();
        CompositeXmlStreamer xmlStreamer = new CompositeXmlStreamer(xmlTranslator,
                apkMetaConstructor);
        binaryXmlParser.setXmlStreamer(xmlStreamer);
        binaryXmlParser.parse();
        manifestXmlMap.put(preferredLocale, xmlTranslator.getXml());
        apkMetaMap.put(preferredLocale, apkMetaConstructor.getApkMeta());
    }

    /**
     * parse resource table.
     */
    private void parseResourceTable() throws IOException {
        ZipArchiveEntry resourceEntry = null;
        Enumeration<ZipArchiveEntry> enu = zf.getEntries();
        while (enu.hasMoreElements()) {
            ZipArchiveEntry entry = enu.nextElement();
            if (entry.isDirectory()) {
                continue;
            }
            if (entry.getName().equals(AndroidConstants.RESOURCE)) {
                resourceEntry = entry;
            }
        }
        if (resourceEntry == null) {
            throw new ParserException("resource table not found");
        }
        ResourceTableParser resourceTableParser = new ResourceTableParser(
                zf.getInputStream(resourceEntry));
        resourceTableParser.parse();
        this.resourceTable = resourceTableParser.getResourceTable();
        this.locales = resourceTableParser.getLocales();
    }

    @Override
    public void close() throws IOException {
        this.certificateMetas = null;
        this.apkMetaMap = null;
        this.manifestXmlMap = null;
        this.resourceTable = null;
        this.certificateMetas = null;
        zf.close();
    }

    public Locale getPreferredLocale() {
        return preferredLocale;
    }

    /**
     * The locale prefrerred.
     * Will cause getManifestXml / getApkMeta to return diffrent values.
     *
     * @param preferredLocale
     */
    public void setPreferredLocale(Locale preferredLocale) {
        this.preferredLocale = preferredLocale;
    }
}
