package com.frostwire;

import java.io.File;
import java.util.concurrent.CountDownLatch;

import jd.utils.JDUtilities;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.appwork.utils.Application;
import org.gudy.azureus2.core3.config.COConfigurationManager;
import org.limewire.concurrent.ThreadExecutor;
import org.limewire.util.CommonUtils;

import com.aelitis.azureus.core.AzureusCore;
import com.aelitis.azureus.core.AzureusCoreComponent;
import com.aelitis.azureus.core.AzureusCoreException;
import com.aelitis.azureus.core.AzureusCoreFactory;
import com.aelitis.azureus.core.AzureusCoreLifecycleListener;
import com.limegroup.gnutella.settings.SharingSettings;

/**
 * Class to initialize the azureus core. Keeps a static reference to the initialized core
 * @author gubatron
 *
 */
public final class AzureusStarter {
	 
	private static final Log LOG = LogFactory.getLog(AzureusStarter.class);
	
	private static AzureusCore AZUREUS_CORE;

	public final static void start() {
	    ThreadExecutor.startThread(new Runnable() {
            @Override
            public void run() {
                azureusInit();                
            }
	    }, "AzureusStarter");
	}
	
	/**
	 * Returns the azureus core singleton
	 * @return
	 */
	public static AzureusCore getAzureusCore() {
		if (AZUREUS_CORE != null && AZUREUS_CORE.isStarted())
			return AZUREUS_CORE;
		else azureusInit();
				
		return AZUREUS_CORE;
	}
	
	/*
	 * Initializes synchronously the azureus core
	 */
	private static synchronized void azureusInit() {
	    
	    Application.setApplication(CommonUtils.getUserSettingsDir().getAbsolutePath() + File.separator + "appwork" + File.separator);
	    File jdHome = new File(CommonUtils.getUserSettingsDir().getAbsolutePath() + File.separator + "jd_home" + File.separator);
	    if (!jdHome.exists()) {
	        jdHome.mkdir();
	    }
	    JDUtilities.setJDHomeDirectory(jdHome);
	    JDUtilities.getConfiguration().setProperty("DOWNLOAD_DIRECTORY", SharingSettings.TORRENT_DATA_DIR_SETTING.getValue().getAbsolutePath());
	    
	    File azureusUserPath = new File(CommonUtils.getUserSettingsDir() + File.separator + "azureus" + File.separator);
        if (!azureusUserPath.exists()) {
            azureusUserPath.mkdirs();
        }
        
	    System.setProperty("azureus.loadplugins", "0"); // disable third party azureus plugins
	    System.setProperty("azureus.config.path", azureusUserPath.getAbsolutePath());
	    System.setProperty("azureus.install.path", azureusUserPath.getAbsolutePath());
		try {
			if (AZUREUS_CORE != null && AZUREUS_CORE.isStarted()) {
				LOG.debug("azureusInit(): core already started. skipping.");
				return;
			}
		} catch (Exception ignore) {}
		
		if (!AzureusCoreFactory.isCoreAvailable()) {
			//This does work
			org.gudy.azureus2.core3.util.SystemProperties.APPLICATION_NAME = "azureus";
			
			org.gudy.azureus2.core3.util.SystemProperties.setUserPath(azureusUserPath.getAbsolutePath());
			
			if (!SharingSettings.TORRENTS_DIR_SETTING.getValue().exists()) {
			    SharingSettings.TORRENTS_DIR_SETTING.getValue().mkdirs();
			}
			
			COConfigurationManager.setParameter( "Auto Adjust Transfer Defaults", false );
			COConfigurationManager.setParameter("General_sDefaultTorrent_Directory", SharingSettings.TORRENTS_DIR_SETTING.getValue().getAbsolutePath());
			
			try {
				AZUREUS_CORE = AzureusCoreFactory.create();
			} catch (AzureusCoreException coreException) {
				//so we already had one eh...
				if (AZUREUS_CORE == null) {
					AZUREUS_CORE = AzureusCoreFactory.getSingleton();
				}
			}
				
			//to guarantee a synchronous start
			final CountDownLatch signal = new CountDownLatch(1);
			
			AZUREUS_CORE
					.addLifecycleListener(new AzureusCoreLifecycleListener() {

						@Override	

						public boolean syncInvokeRequired() {
							return false;
						}

						@Override
						public void stopping(AzureusCore core) {
							core.getGlobalManager().pauseDownloads();
						}

						@Override
						public void stopped(AzureusCore core) {
						}

						@Override
						public boolean stopRequested(AzureusCore core)
								throws AzureusCoreException {
							return false;
						}

						@Override
						public void started(AzureusCore core) {
							signal.countDown();
						}

						@Override
						public boolean restartRequested(AzureusCore core)
								throws AzureusCoreException {
							return false;
						}

						@Override
						public boolean requiresPluginInitCompleteBeforeStartedEvent() {
							return false;
						}

						@Override
						public void componentCreated(AzureusCore core,
								AzureusCoreComponent component) {
						}
					});			

			if (!AZUREUS_CORE.isStarted() && !AZUREUS_CORE.isRestarting()) {
				AZUREUS_CORE.start();
			}
			
			AZUREUS_CORE.getGlobalManager().resumeDownloads();
			
			
			LOG.debug("azureusInit(): core.start() waiting...");
			try {
				signal.await(); LOG.debug("azureusInit(): core started...");
			} catch (InterruptedException e) {
				e.printStackTrace();
			} 
		}
	} //azureusInit
	
	public static void revertToDefaultConfiguration() {
	    COConfigurationManager.resetToDefaults();
	    autoAdjustBittorrentSpeed();
	}
	
	public static void autoAdjustBittorrentSpeed()
    {
        if ( COConfigurationManager.getBooleanParameter( "Auto Adjust Transfer Defaults" )){
            
            int up_limit_bytes_per_sec      = 0;//getEstimatedUploadCapacityBytesPerSec().getBytesPerSec();
            //int down_limit_bytes_per_sec    = 0;//getEstimatedDownloadCapacityBytesPerSec().getBytesPerSec();
                        
            int up_kbs = up_limit_bytes_per_sec/1024;
            
                
            final int[][] settings = {
                    
                    { 56,       2,      20,     40  },      // 56 k/bit
                    { 96,       3,      30,     60 },
                    { 128,      3,      40,     80 },
                    { 192,      4,      50,     100 },      // currently we don't go lower than this
                    { 256,      4,      60,     200 },
                    { 512,      5,      70,     300 },
                    { 1024,     6,      80,     400 },      // 1Mbit
                    { 2*1024,   8,      90,     500 },
                    { 5*1024,   10,     100,    600 },
                    { 10*1024,  20,     110,    750 },      // 10Mbit
                    { 20*1024,  30,     120,    900 },
                    { 50*1024,  40,     130,    1100 },
                    { 100*1024, 50,     140,    1300 },
                    { -1,       60,     150,    1500 },
            };
            
            int[] selected = settings[ settings.length-1 ];
            
                // note, we start from 3 to avoid over-restricting things when we don't have
                // a reasonable speed estimate
            
            for ( int i=3;i<settings.length;i++ ){
            
                int[]   setting = settings[i];
                
                int line_kilobit_sec = setting[0];
                
                    // convert to upload kbyte/sec assuming 80% achieved
                
                int limit = (line_kilobit_sec/8)*4/5;
                
                if ( up_kbs <= limit ){
                    
                    selected = setting;
                    
                    break;
                }
            }
            
            int upload_slots            = selected[1];
            int connections_torrent     = selected[2];
            int connections_global      = selected[3];

            
            if ( upload_slots != COConfigurationManager.getIntParameter( "Max Uploads" )){
                
                COConfigurationManager.setParameter( "Max Uploads", upload_slots );
                COConfigurationManager.setParameter( "Max Uploads Seeding", upload_slots );
            }
            
            if ( connections_torrent != COConfigurationManager.getIntParameter( "Max.Peer.Connections.Per.Torrent" )){
                
                COConfigurationManager.setParameter( "Max.Peer.Connections.Per.Torrent", connections_torrent );
                
                COConfigurationManager.setParameter( "Max.Peer.Connections.Per.Torrent.When.Seeding", connections_torrent / 2 );
            }
            
            if ( connections_global != COConfigurationManager.getIntParameter( "Max.Peer.Connections.Total" )){
                
                COConfigurationManager.setParameter( "Max.Peer.Connections.Total", connections_global );
            }
        }
    }
}
