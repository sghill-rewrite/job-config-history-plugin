package hudson.plugins.jobConfigHistory;

import hudson.XmlFile;
import hudson.model.AbstractItem;
import hudson.model.AbstractProject;
import hudson.model.Hudson;
import hudson.security.AccessControlled;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.logging.Logger;

import javax.xml.transform.stream.StreamSource;

import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

/**
 * @author Stefan Brausch
 */
public class JobConfigHistoryProjectAction extends JobConfigHistoryBaseAction {

    /** Our logger. */
  private static final Logger LOG = Logger.getLogger(JobConfigHistoryProjectAction.class.getName());

    /** The project. */
    private final transient AbstractItem project;

    /**
     * @param project
     *            for which configurations should be returned.
     */
    public JobConfigHistoryProjectAction(AbstractItem project) {
        super();
        this.project = project;
    }

    /**
     * {@inheritDoc}
     * 
     * Make method final, as we always want the same icon file. Returns
     * {@code null} to hide the icon if the user is not allowed to configure
     * jobs.
     */
    public final String getIconFileName() {
        return hasConfigurePermission() ? JobConfigHistoryConsts.ICONFILENAME
               : null;
    }
    
    /**
     * Returns the configuration history entries for one {@link AbstractItem}.
     *
     * @return history list for one {@link AbstractItem}.
     * @throws IOException
     *             if {@link JobConfigHistoryConsts#HISTORY_FILE} might not be read or the path might not be urlencoded.
     */
    public final List<ConfigInfo> getJobConfigs() throws IOException {
        checkConfigurePermission();
        final ArrayList<ConfigInfo> configs = new ArrayList<ConfigInfo>();
        final File historyRootDir = getPlugin().getHistoryDir(project.getConfigFile());
        if (historyRootDir.exists()) {
            for (final File historyDir : historyRootDir.listFiles(JobConfigHistory.HISTORY_FILTER)) {
                final XmlFile historyXml = new XmlFile(new File(historyDir, JobConfigHistoryConsts.HISTORY_FILE));
                final HistoryDescr histDescr = (HistoryDescr) historyXml.read();
                final ConfigInfo config = ConfigInfo.create(project, historyDir, histDescr);
                configs.add(config);
            }
        }
        Collections.sort(configs, ConfigInfoComparator.INSTANCE);
        return configs;
    }

    /**
     * Returns the project for which we want to see the config history, the config files or the diff.
     *
     * @return project
     */
    public final AbstractItem getProject() {
        return project;
    }

    /**
     * {@inheritDoc} Returns the project.
     */
    @Override
    protected AccessControlled getAccessControlledObject() {
        return project;
    }

    @Override
    protected void checkConfigurePermission() {
        getAccessControlledObject().checkPermission(AbstractProject.CONFIGURE);
    }

    @Override
    protected boolean hasConfigurePermission() {
        return getAccessControlledObject().hasPermission(AbstractProject.CONFIGURE);
    }
    
    /**
     * Action when 'restore' button is pressed.
     * @param req incoming StaplerRequest
     * @param rsp outgoing StaplerResponse
     * @throws IOException if something goes wrong
     */
    public final void doRestore(StaplerRequest req, StaplerResponse rsp)
        throws IOException {
        checkConfigurePermission();
        
        final JobConfigHistory plugin = Hudson.getInstance().getPlugin(JobConfigHistory.class);
        final String timestamp = req.getParameter("timestamp");
        final String name = req.getParameter("name");
        final String path = plugin.getJobHistoryRootDir().getPath() + "/" + name + "/" + timestamp;
        
        LOG.finest("ProjectAction doRestore Path: " + path);

        final XmlFile xmlFile = getConfigXml(path);
        final String oldConfig = xmlFile.asString();
        final InputStream is = new ByteArrayInputStream(oldConfig.getBytes("UTF-8"));

        project.updateByXml(new StreamSource(is));
        project.save();
        rsp.sendRedirect(Hudson.getInstance().getRootUrl() + project.getUrl());
    }
    
    /**
     * Action when 'restore' button in showDiffFiles.jelly is pressed.
     * Gets required parameters and forwards to restoreQuestion.jelly.
     * @param req StaplerRequest created by pressing the button
     * @param rsp outgoing StaplerResponse
     * @throws IOException If XML file can't be read
     */
    public final void doForwardToRestoreQuestion(StaplerRequest req, StaplerResponse rsp)
        throws IOException {
        final String timestamp = req.getParameter("timestamp");
        final String name = req.getParameter("name");
        rsp.sendRedirect("restoreQuestion?timestamp=" + timestamp + "&name=" + name);
    }
    
    public final Calendar getDateFromString(String dateString) {
        Date date = null;
        Calendar cal = Calendar.getInstance();
        try {
            date = new SimpleDateFormat(JobConfigHistoryConsts.ID_FORMATTER).parse(dateString);
            cal.setTime(date);
        } catch (ParseException e) {
            LOG.finest("Could not parse Date: " + e);
        }
        return cal;
    }
}
