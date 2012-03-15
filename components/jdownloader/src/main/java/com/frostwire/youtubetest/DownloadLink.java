//    jDownloader - Downloadmanager
//    Copyright (C) 2008  JD-Team support@jdownloader.org
//
//    This program is free software: you can redistribute it and/or modify
//    it under the terms of the GNU General Public License as published by
//    the Free Software Foundation, either version 3 of the License, or
//    (at your option) any later version.
//
//    This program is distributed in the hope that it will be useful,
//    but WITHOUT ANY WARRANTY; without even the implied warranty of
//    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
//    GNU General Public License for more details.
//
//    You should have received a copy of the GNU General Public License
//    along with this program.  If not, see <http://www.gnu.org/licenses/>.

package com.frostwire.youtubetest;

import java.io.File;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.net.URL;
import java.util.ArrayList;
import java.util.Locale;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import javax.swing.ImageIcon;

import jd.config.Property;
import jd.controlling.JDLogger;
import jd.controlling.linkcrawler.CheckableLink;
import jd.controlling.packagecontroller.AbstractNodeNotifier;
import jd.controlling.packagecontroller.AbstractPackageChildrenNode;
import jd.http.Browser;
import jd.nutils.io.JDIO;
import jd.plugins.download.DownloadInterface;
import jd.utils.JDUtilities;

import org.appwork.utils.Regex;
import org.appwork.utils.StringUtils;
import org.appwork.utils.logging.Log;
import org.appwork.utils.os.CrossSystem;
import org.jdownloader.DomainInfo;
import org.jdownloader.controlling.UniqueSessionID;
import org.jdownloader.images.NewTheme;
import org.jdownloader.settings.GeneralSettings;

/**
 * Hier werden alle notwendigen Informationen zu einem einzelnen Download
 * festgehalten. Die Informationen werden dann in einer Tabelle dargestellt
 * 
 * @author astaldo
 */
public class DownloadLink extends Property implements Serializable, AbstractPackageChildrenNode<FilePackage>, CheckableLink {

    public static enum AvailableStatus {
        UNCHECKED,
        FALSE,
        UNCHECKABLE,
        TRUE;
    }

    private static final String                          PROPERTY_MD5            = "MD5";
    private static final String                          PROPERTY_SHA1           = "SHA1";
    private static final String                          PROPERTY_PASS           = "pass";
    private static final String                          PROPERTY_FINALFILENAME  = "FINAL_FILENAME";
    private static final String                          PROPERTY_FORCEDFILENAME = "FORCED_FILENAME";
    private static final String                          PROPERTY_COMMENT        = "COMMENT";
    private static final String                          PROPERTY_PRIORITY       = "PRIORITY";
    private static final String                          PROPERTY_FINISHTIME     = "FINISHTIME";
    private static final String                          PROPERTY_ENABLED        = "ENABLED";
    private static final String                          PROPERTY_PWLIST         = "PWLIST";
    private static final String                          PROPERTY_LINKDUPEID     = "LINKDUPEID";
    private static final String                          PROPERTY_SPEEDLIMIT     = "SPEEDLIMIT";

    public static final int                              LINKTYPE_CONTAINER      = 1;

    public static final int                              LINKTYPE_NORMAL         = 0;

    private transient static Logger                      logger                  = JDLogger.getLogger();

    private static final long                            serialVersionUID        = 1981079856214268373L;

    public static final String                           UNKNOWN_FILE_NAME       = "unknownFileName.file";
    private static final String                          PROPERTY_CHUNKS         = "CHUNKS";

    private transient AvailableStatus                    availableStatus         = AvailableStatus.UNCHECKED;

    private long[]                                       chunksProgress          = null;

    /** Aktuell heruntergeladene Bytes der Datei */
    private long                                         downloadCurrent         = 0;

    private transient DownloadInterface                  downloadInstance;

    private transient SingleDownloadController           downloadLinkController;

    /** Maximum der heruntergeladenen Datei (Dateilaenge) */
    private long                                         downloadMax             = 0;

    private String                                       browserurl              = null;

    private FilePackage                                  filePackage;

    /** Hoster des Downloads */
    private String                                       host;

    /** Zeigt an, ob dieser Downloadlink aktiviert ist */
    private boolean                                      isEnabled;

    private LinkStatus                                   linkStatus;

    private int                                          linkType                = LINKTYPE_NORMAL;

    /** Beschreibung des Downloads */
    /* kann sich noch ändern, NICHT final */
    private String                                       name;

    private transient PluginForHost                      defaultplugin;

    private transient PluginForHost                      liveplugin;

    /*
     * we need to keep this some time to perfom conversion from variable to
     * property
     */
    private String                                       finalFileName;

    /**
     * /** Von hier soll der Download stattfinden
     */
    private String                                       urlDownload;

    /**
     * Password welches einem weiteren Decrypter-Plugin uebergeben werden soll
     * (zb FolderPassword)
     */

    private transient PluginProgress                     pluginProgress;

    private transient ImageIcon                          icon                    = null;

    private long                                         created                 = -1l;

    private transient UniqueSessionID                    uniqueID                = null;
    transient private AbstractNodeNotifier<DownloadLink> propertyListener;
    transient DomainInfo                                 domainInfo              = null;

    /**
     * Erzeugt einen neuen DownloadLink
     * 
     * @param plugin
     *            Das Plugins, das fuer diesen Download zustaendig ist
     * @param name
     *            Bezeichnung des Downloads
     * @param host
     *            Anbieter, von dem dieser Download gestartet wird
     * @param urlDownload
     *            Die Download URL (Entschluesselt)
     * @param isEnabled
     *            Markiert diesen DownloadLink als aktiviert oder deaktiviert
     */
    public DownloadLink(PluginForHost plugin, String name, String host, String urlDownload, boolean isEnabled) {
        uniqueID = new UniqueSessionID();
        this.defaultplugin = plugin;
        setName(name);
        downloadMax = 0;
        this.host = host == null ? null : host.toLowerCase(Locale.ENGLISH);
        this.isEnabled = isEnabled;
        created = System.currentTimeMillis();
        this.setUrlDownload(urlDownload);
        if (plugin != null && this.getDownloadURL() != null) {
            try {
                plugin.correctDownloadLink(this);
            } catch (Throwable e) {
                Log.exception(e);
            }
        }
        if (name == null && urlDownload != null) {
            setName(Plugin.extractFileNameFromURL(getDownloadURL()));
        }
    }

    public long getFinishedDate() {
        return this.getLongProperty(PROPERTY_FINISHTIME, -1l);
    }

    public void setFinishedDate(long finishedDate) {
        if (finishedDate <= 0) {
            this.setProperty(PROPERTY_FINISHTIME, Property.NULL);
        } else {
            this.setProperty(PROPERTY_FINISHTIME, finishedDate);
        }
    }

    public long getCreated() {
        return created;
    }

    public void setCreated(long created) {
        this.created = created;
    }

    private void readObject(ObjectInputStream stream) throws IOException, ClassNotFoundException {
        /* deserialize object and then fill other stuff(transient..) */
        stream.defaultReadObject();
        uniqueID = new UniqueSessionID();
    }

    public UniqueSessionID getUniqueID() {
        return uniqueID;
    }

    public DownloadLink addSourcePluginPassword(String sourcePluginPassword) {
        if (sourcePluginPassword == null || sourcePluginPassword.length() == 0) return this;
        synchronized (this) {
            // String pwadd = sourcePluginPassword.trim();
            // if (sourcePluginPasswordList == null) sourcePluginPasswordList =
            // new ArrayList<String>();
            // if (!sourcePluginPasswordList.contains(pwadd))
            // sourcePluginPasswordList.add(pwadd);
            return this;
        }
    }

    public void addSourcePluginPasswords(String[] sourcePluginPasswords) {
        if (sourcePluginPasswords == null || sourcePluginPasswords.length == 0) return;
        synchronized (this) {
            for (String sourcePluginPassword : sourcePluginPasswords) {
                addSourcePluginPassword(sourcePluginPassword);
            }
        }
    }

    public void addSourcePluginPasswordList(ArrayList<String> sourcePluginPasswords) {
        if (sourcePluginPasswords == null || sourcePluginPasswords.size() == 0) return;
        synchronized (this) {
            for (String pw : sourcePluginPasswords) {
                addSourcePluginPassword(pw);
            }
        }
    }

    public int getPriority() {
        return this.getIntegerProperty(PROPERTY_PRIORITY, 0);
    }

    public int getChunks() {
        return getIntegerProperty(PROPERTY_CHUNKS, -1);
    }

    public void setChunks(int chunks) {
        if (chunks <= 0) {
            setProperty(PROPERTY_CHUNKS, Property.NULL);
        } else {
            setProperty(PROPERTY_CHUNKS, chunks);
        }
    }

    public void setCustomSpeedLimit(int limit) {
        if (limit == 0) {
            setProperty(PROPERTY_SPEEDLIMIT, Property.NULL);
        } else {
            if (limit < 0) limit = 1;
            setProperty(PROPERTY_SPEEDLIMIT, limit);
        }
        // DownloadInterface dli = downloadInstance;
        // if (dli != null) dli.manageCustomSpeed(null);
    }

    public int getCustomSpeedLimit() {
        return this.getIntegerProperty(PROPERTY_SPEEDLIMIT, 0);
    }

    public void setPriority(int pr) {
        int priority = 0;
        if (pr >= -1 && pr < 4) {
            priority = pr;
        }
        if (priority == 0) {
            this.setProperty(PROPERTY_PRIORITY, Property.NULL);
        } else {
            this.setProperty(PROPERTY_PRIORITY, priority);
        }
    }

    /**
     * Gibt ein arry mit den Chunkfortschritten zurueck. Dieses Array wird von
     * der Downlaodinstanz zu resumezwecken verwendet
     * 
     * @return
     */
    public long[] getChunksProgress() {
        return chunksProgress;
    }

    /**
     * Liefert die bisher heruntergeladenen Bytes zurueck
     * 
     * @return Anzahl der heruntergeladenen Bytes
     */
    public long getDownloadCurrent() {
        DownloadInterface dli = downloadInstance;
        if (dli != null) return dli.getTotaleLinkBytesLoaded();
        return downloadCurrent;
    }

    public long getRemainingKB() {
        return getDownloadSize() - getDownloadCurrent();
    }

    public DownloadInterface getDownloadInstance() {
        return downloadInstance;
    }

    public SingleDownloadController getDownloadLinkController() {
        return downloadLinkController;
    }

    /**
     * Die Groesse der Datei
     * 
     * @return Die Groesse der Datei
     */
    public long getDownloadSize() {
        return Math.max(getDownloadCurrent(), downloadMax);
    }

    /**
     * Gibt die aktuelle Downloadgeschwindigkeit in bytes/sekunde zurueck
     * 
     * @return Downloadgeschwindigkeit in bytes/sekunde
     */
    public long getDownloadSpeed() {
        DownloadInterface dli = getDownloadInstance();
        if (dli == null || !getLinkStatus().hasStatus(LinkStatus.DOWNLOADINTERFACE_IN_PROGRESS)) return 0;
        return dli.getSpeed();
    }

    public String getDownloadURL() {
        return urlDownload;
    }

    public String getBrowserUrl() {
        if (browserurl != null) return browserurl;
        return getDownloadURL();
    }

    public void setBrowserUrl(String url) {
        browserurl = url;
    }

    public boolean gotBrowserUrl() {
        return browserurl != null;
    }

    public String getFileOutput() {
        if (getFilePackage().getDownloadDirectory() != null && getFilePackage().getDownloadDirectory().length() > 0) {
            return new File(new File(getFilePackage().getDownloadDirectory()), getName()).getAbsolutePath();
        } else {
            return null;
        }
    }

    /**
     * return the FilePackage that contains this DownloadLink, if none is set it
     * will return defaultFilePackage
     * 
     * @return
     */
    public FilePackage getFilePackage() {
        if (filePackage == null) return FilePackage.getDefaultFilePackage();
        return filePackage;
    }

    /**
     * Gibt den Hoster dieses Links azurueck.
     * 
     * @return Der Hoster, auf dem dieser Link verweist
     */
    public String getHost() {
        return host;
    }

    public void setHost(String newHost) {
        this.host = newHost;
    }

    public LinkStatus getLinkStatus() {
        if (linkStatus == null) {
            linkStatus = new LinkStatus(this);
        }
        return linkStatus;
    }

    public int getLinkType() {
        return linkType;
    }

    /**
     * Liefert den Datei Namen dieses Downloads zurueck. Wurde der Name mit
     * setfinalFileName(String) festgelegt wird dieser Name zurueckgegeben
     * 
     * @return Name des Downloads
     */
    public String getName() {
        String ret = this.getForcedFileName();
        if (ret != null) return ret;
        ret = this.getFinalFileName();
        if (ret != null) return ret;
        try {
            String urlName;
            return name == null ? ((urlName = new File(new URL(this.getDownloadURL()).toURI()).getName()) != null ? urlName : UNKNOWN_FILE_NAME) : name;
        } catch (Exception e) {
            return UNKNOWN_FILE_NAME;
        }
    }

    /**
     * Liefert das Plugin zurueck, dass diesen DownloadLink handhabt
     * 
     * @return Das Plugin
     */
    public PluginForHost getDefaultPlugin() {
        return defaultplugin;
    }

    public PluginForHost getLivePlugin() {
        return liveplugin;
    }

    public String getComment() {
        return this.getStringProperty(PROPERTY_COMMENT, null);
    }

    public ArrayList<String> getSourcePluginPasswordList() {
        return this.getGenericProperty(PROPERTY_PWLIST, (ArrayList<String>) null);
    }

    /**
     * Gibt den Finalen Downloadnamen zurueck. Wird null zurueckgegeben, so wird
     * der dateiname von den jeweiligen plugins automatisch ermittelt.
     * 
     * @return Statischer Dateiname
     */
    public String getFinalFileName() {
        if (finalFileName != null) {
            /* convert existing finalFileName into Property System */
            this.setFinalFileName(finalFileName);
        }
        return this.getStringProperty(PROPERTY_FINALFILENAME, null);
    }

    public String getForcedFileName() {
        return this.getStringProperty(PROPERTY_FORCEDFILENAME, null);
    }

    public String getLinkID() {
        String ret = this.getStringProperty(PROPERTY_LINKDUPEID, null);
        if (ret == null) return this.getDownloadURL();
        return ret;
    }

    /* DO NOT USE in plugins for stable 09581, use set Property instead */
    public void setLinkID(String id) {
        if (StringUtils.isEmpty(id)) {
            this.setProperty(PROPERTY_LINKDUPEID, Property.NULL);
        } else {
            this.setProperty(PROPERTY_LINKDUPEID, id);
        }
    }

    /**
     * @return true falls der Download abgebrochen wurde
     */
    public boolean isAborted() {
        SingleDownloadController dlc = getDownloadLinkController();
        if (dlc != null) return dlc.isAborted();
        DownloadInterface dli = this.getDownloadInstance();
        if (dli != null) return dli.externalDownloadStop();
        return false;
    }

    /**
     * this will abort an ongoing download of this DownloadLink, the
     * abortDownload method of the SingleDownloadController will start a new
     * Thread for terminating of the download, so you will have to check
     * manually when the download finally has stopped
     */
    public void abort() {
        SingleDownloadController dlc = getDownloadLinkController();
        if (dlc != null) dlc.abortDownload();
    }

    /**
     * Gibt zurueck ob Dieser Link schon auf verfuegbarkeit getestet wurde.+
     * Diese FUnktion fuehrt keinen!! Check durch. Sie prueft nur ob schon
     * geprueft worden ist. anschiessend kann mit isAvailable() die
     * verfuegbarkeit ueberprueft werden
     * 
     * @return Link wurde schon getestet (true) nicht getestet(false)
     */
    public boolean isAvailabilityStatusChecked() {
        return availableStatus != AvailableStatus.UNCHECKED;
    }

    /**
     * Fuehrt einen verfuegbarkeitscheck durch. GIbt true zurueck wenn der link
     * online ist, oder wenn er nicht pruefbar ist
     * 
     * @return true/false
     */
    public boolean isAvailable() {
        return availableStatus != AvailableStatus.FALSE;
    }

    public AvailableStatus getAvailableStatus() {
        return availableStatus;
    }

    public void setAvailableStatus(AvailableStatus availableStatus) {
        this.availableStatus = availableStatus;
        notifyChanges();
    }

    private void notifyChanges() {
        AbstractNodeNotifier<DownloadLink> pl = propertyListener;
        if (pl == null) pl = this.filePackage;
        if (pl != null) pl.nodeUpdated(this);
    }

    /**
     * Zeigt, ob dieser Download aktiviert ist
     * 
     * @return wahr, falls dieser DownloadLink aktiviert ist
     */
    public boolean isEnabled() {
        return isEnabled;
    }

    /** Setzt alle DownloadWErte zurueck */
    public void reset() {
        chunksProgress = null;
        downloadLinkController = null;
        downloadCurrent = 0;
        this.setFinishedDate(-1l);
        linkStatus.reset();
        this.availableStatus = AvailableStatus.UNCHECKED;
        this.setEnabled(true);
        deleteFile(true, true);
        setFinalFileName(null);
        PluginForHost plg = liveplugin;
        if (plg == null) {
            plg = this.defaultplugin.getNewInstance();
        }
        if (plg.getBrowser() == null) {
            plg.setBrowser(new Browser());
        }
        plg.init();
        plg.resetDownloadlink(this);
    }

    /**
     * deletes the final downloaded file if finalfile is true deletes the
     * partfile if partfile is true deletes the downloadfolder if its emptry and
     * NOT equal to default downloadfolder
     */
    public void deleteFile(boolean partfile, boolean finalfile) {
        int maxtries = 5;
        while (getLinkStatus().isPluginActive()) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                JDLogger.exception(e);
            }
            maxtries--;
            if (maxtries == 0) break;
        }
        if (finalfile && new File(this.getFileOutput()).exists()) {
            if (!new File(this.getFileOutput()).delete()) {
                logger.severe("Could not delete file " + this.getFileOutput());
            }
        }
        if (partfile && new File(this.getFileOutput() + ".part").exists()) {
            if (!new File(this.getFileOutput() + ".part").delete()) {
                logger.severe("Could not delete file " + this.getFileOutput());
            }
        }

        /* try to delete folder (if its empty and NOT the default downloadfolder */
        File dlFolder = new File(this.getFileOutput()).getParentFile();
        if (dlFolder != null && dlFolder.exists() && dlFolder.isDirectory() && dlFolder.listFiles() != null && dlFolder.listFiles().length == 0) {
            if (!new File(org.appwork.storage.config.JsonConfig.create(GeneralSettings.class).getDefaultDownloadFolder()).equals(dlFolder)) dlFolder.delete();
        }
    }

    /** returns if partfile or completed file exists on disk */
    public boolean existsFile() {
        return new File(this.getFileOutput()).exists() || new File(this.getFileOutput() + ".part").exists();
    }

    public void setAvailable(boolean available) {
        this.availableStatus = available ? AvailableStatus.TRUE : AvailableStatus.FALSE;
        notifyChanges();
    }

    /**
     * Die Downloadklasse kann hier ein array mit den Fortschritten der chunks
     * ablegen. Damit koennen downloads resumed werden
     * 
     * @param is
     */
    public void setChunksProgress(long[] is) {
        chunksProgress = is;
    }

    /**
     * Setzt die Anzahl der heruntergeladenen Bytes fest und aktualisiert die
     * Fortschrittsanzeige
     * 
     * @param downloadedCurrent
     *            Anzahl der heruntergeladenen Bytes
     */
    public void setDownloadCurrent(long downloadedCurrent) {
        downloadCurrent = downloadedCurrent;
        if (this.getDownloadInstance() == null) notifyChanges();
    }

    public void setDownloadInstance(DownloadInterface downloadInterface) {
        downloadInstance = downloadInterface;
    }

    public void setDownloadLinkController(SingleDownloadController downloadLinkController) {
        this.downloadLinkController = downloadLinkController;
    }

    /**
     * Setzt die Groesse der herunterzuladenden Datei
     * 
     * @param downloadMax
     *            Die Groesse der Datei
     */
    public void setDownloadSize(long downloadMax) {
        this.downloadMax = downloadMax;
        if (this.getDownloadInstance() == null) notifyChanges();
    }

    /*
     * changes the enabled status of this DownloadLink, aborts download if its
     * currently running
     */
    public void setEnabled(boolean isEnabled) {
        this.getLinkStatus().removeStatus(LinkStatus.ERROR_TEMPORARILY_UNAVAILABLE);
        this.isEnabled = isEnabled;
        if (isEnabled == false) {
            abort();
        }
        if (isEnabled == true) {
            setProperty(PROPERTY_ENABLED, Property.NULL);
        } else {
            setProperty(PROPERTY_ENABLED, isEnabled);
        }
        notifyChanges();
    }

    /**
     * set the FilePackage that contains this DownloadLink, DO NOT USE this if
     * you want to add this DownloadLink to a FilePackage
     * 
     * @param filePackage
     */
    public synchronized void _setFilePackage(FilePackage filePackage) {
        if (filePackage == this.filePackage) return;
        if (this.filePackage != null && filePackage != null) {
            this.filePackage.remove(this);
        }
        this.filePackage = filePackage;
    }

    public void setLinkType(int linktypeContainer) {
        if (linktypeContainer == linkType) return;
        if (linkType == LINKTYPE_CONTAINER) {
            logger.severe("You are not allowd to Change the Linktype of " + this);
            return;
        }
        linkType = linktypeContainer;
    }

    /**
     * Setzt nachtraeglich das Plugin. Wird nur zum Laden der Liste benoetigt
     * 
     * @param plugin
     *            Das fuer diesen Download zustaendige Plugin
     */
    public void setDefaultPlugin(PluginForHost plugin) {
        this.defaultplugin = plugin;
    }

    public void setLivePlugin(PluginForHost plugin) {
        this.liveplugin = plugin;
        if (liveplugin != null) {
            liveplugin.setDownloadLink(this);
        }
    }

    /**
     * Setzt den Namen des Downloads neu
     * 
     * @param name
     *            Neuer Name des Downloads
     */
    public void setName(String name) {
        if (name != null && name.length() > 0) {
            this.name = JDUtilities.removeEndingPoints(JDIO.validateFileandPathName(name));
        }
        this.setIcon(null);
        notifyChanges();
    }

    /*
     * use this function to force a name, it has highest priority
     */
    public void forceFileName(String name) {
        if (name == null) {
            this.setProperty(PROPERTY_FORCEDFILENAME, Property.NULL);
        } else {
            this.setProperty(PROPERTY_FORCEDFILENAME, name);
        }
        setIcon(null);
        notifyChanges();
    }

    private void setIcon(ImageIcon icon) {
        this.icon = icon;
    }

    @Deprecated
    public DownloadLink setSourcePluginComment(String sourcePluginComment) {
        setComment(sourcePluginComment);
        return this;
    }

    /*
     * WARNING: DO NOT use in 09581 stable!
     */
    public void setComment(String comment) {
        if (comment == null || comment.length() == 0) {
            this.setProperty(PROPERTY_COMMENT, Property.NULL);
        } else {
            this.setProperty(PROPERTY_COMMENT, comment);
        }
    }

    public DownloadLink setSourcePluginPasswordList(ArrayList<String> sourcePluginPassword) {
        if (sourcePluginPassword == null || sourcePluginPassword.size() == 0) {
            this.setProperty(PROPERTY_PWLIST, Property.NULL);
        } else {
            this.setProperty(PROPERTY_PWLIST, sourcePluginPassword);
        }
        return this;
    }

    /**
     * Setzt den Statischen Dateinamen. Ist dieser wert != null, so wird er zum
     * Speichern der Datei verwendet. ist er == null, so wird der dateiName im
     * Plugin automatisch ermittelt. ACHTUNG: Der angegebene Dateiname ist
     * endgueltig. Diese Funktion sollte nach Moeglichkeit nicht von Plugins
     * verwendet werden. Sie gibt der Gui die Moeglichkeit unabhaengig von den
     * Plugins einen Downloadnamen festzulegen. Userinputs>Automatische
     * Erkennung - Plugins sollten {@link #setName(String)} verwenden um den
     * Speichernamen anzugeben.
     */
    public void setFinalFileName(String newfinalFileName) {
        setName(newfinalFileName);
        if (newfinalFileName != null && newfinalFileName.length() > 0) {
            if (new Regex(newfinalFileName, Pattern.compile("r..\\.htm.?$", Pattern.CASE_INSENSITIVE)).matches()) {
                logger.info("Use Workaround for stupid >>rar.html<< uploaders!");
                newfinalFileName = newfinalFileName.substring(0, newfinalFileName.length() - new Regex(newfinalFileName, Pattern.compile("r..(\\.htm.?)$", Pattern.CASE_INSENSITIVE)).getMatch(0).length());
            }
            this.setProperty(PROPERTY_FINALFILENAME, JDUtilities.removeEndingPoints(JDIO.validateFileandPathName(newfinalFileName)));
        } else {
            this.setProperty(PROPERTY_FINALFILENAME, Property.NULL);
        }
        finalFileName = null;
        setIcon(null);
        notifyChanges();
    }

    /**
     * Setzt die URL, von der heruntergeladen werden soll
     * 
     * @param urlDownload
     *            Die URL von der heruntergeladen werden soll
     */
    public void setUrlDownload(String urlDownload) {
        if (urlDownload != null) {
            this.urlDownload = new String(urlDownload.trim());
        } else {
            this.urlDownload = null;
        }
    }

    /**
     * Diese Methhode fragt das eigene Plugin welche Informationen ueber die
     * File bereit gestellt werden. Der String eignet Sich zur Darstellung in
     * der UI
     */
    @Override
    public String toString() {
        return getName();
    }

    /**
     * returns real downloadMAx Value. use #getDownloadSize if you are not sure
     * 
     * @return
     */
    public long getDownloadMax() {
        return downloadMax;
    }

    /**
     * DO NOT USE in 09581 Stable
     * 
     * @return
     */
    public String getDownloadPassword() {
        return getStringProperty(PROPERTY_PASS, null);
    }

    /**
     * DO NOT USE in 09581 Stable
     * 
     * @return
     */
    public void setDownloadPassword(String pass) {
        if (StringUtils.isEmpty(pass)) {
            this.setProperty(PROPERTY_PASS, Property.NULL);
        } else {
            this.setProperty(PROPERTY_PASS, pass);
        }
    }

    @Deprecated
    public void setDecrypterPassword(String pw) {
        setDownloadPassword(pw);
    }

    public void setMD5Hash(String md5) {
        if (StringUtils.isEmpty(md5)) {
            this.setProperty(PROPERTY_MD5, Property.NULL);
        } else {
            this.setProperty(PROPERTY_MD5, md5);
        }
    }

    public String getMD5Hash() {
        return getStringProperty(PROPERTY_MD5, (String) null);
    }

    public void setPluginProgress(PluginProgress progress) {
        this.pluginProgress = progress;
    }

    public PluginProgress getPluginProgress() {
        return pluginProgress;
    }

    public void setSha1Hash(String sha1) {
        if (StringUtils.isEmpty(sha1)) {
            this.setProperty(PROPERTY_SHA1, Property.NULL);
        } else {
            this.setProperty(PROPERTY_SHA1, sha1);
        }
    }

    public String getSha1Hash() {
        return getStringProperty(PROPERTY_SHA1, (String) null);
    }

    /* TODO: memfresser, anders machen */
    /**
     * ermittel das icon zur datei
     * 
     * @return
     */
    public ImageIcon getIcon() {
        if (icon == null) {
            String ext = JDIO.getFileExtension(getFileOutput());
            if (ext != null) {
                try {
                    icon = CrossSystem.getMime().getFileIcon(ext, 16, 16);
                } catch (Throwable e) {
                    Log.exception(e);
                }
            }
            if (icon == null) icon = NewTheme.I().getIcon("url", 16);
        }
        return icon;
    }

    public DomainInfo getDomainInfo() {
        PluginForHost plugin = this.liveplugin;
        if (plugin != null) {
            /* live plugin available, lets use it */
            return plugin.getDomainInfo();
        } else {
            if (domainInfo == null) {
                if ("ftp".equalsIgnoreCase(getHost()) || "DirectHTTP".equalsIgnoreCase(getHost()) || "http links".equalsIgnoreCase(getHost())) {
                    /* custom iconHost */
                    try {
                        String url = Browser.getHost(new URL(getDownloadURL()));
                        domainInfo = DomainInfo.getInstance(url);
                    } catch (final Throwable e) {
                        e.printStackTrace();
                    }
                }
                if (domainInfo == null) {
                    domainInfo = DomainInfo.getInstance(getHost());
                }
            }
        }
        return domainInfo;
    }

    public FilePackage getParentNode() {
        return getFilePackage();
    }

    public void setParentNode(FilePackage parent) {
        if (parent == this.filePackage) return;
        if (this.filePackage != null && parent != null) {
            this.filePackage.remove(this);
        }
        this.filePackage = (FilePackage) parent;
    }

    public DownloadLink getDownloadLink() {
        return this;
    }

    public void setNodeChangeListener(AbstractNodeNotifier<DownloadLink> propertyListener) {
        this.propertyListener = propertyListener;
    }

    @Deprecated
    public PluginForHost getPlugin() {
        return this.liveplugin;
    }

}