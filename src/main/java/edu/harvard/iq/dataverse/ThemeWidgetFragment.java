/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package edu.harvard.iq.dataverse;

import edu.harvard.iq.dataverse.engine.command.Command;
import edu.harvard.iq.dataverse.engine.command.impl.UpdateDataverseThemeCommand;
import edu.harvard.iq.dataverse.settings.JvmSettings;
import edu.harvard.iq.dataverse.util.BundleUtil;
import edu.harvard.iq.dataverse.util.JsfHelper;
import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.logging.Level;
import java.util.logging.Logger;
import jakarta.annotation.PreDestroy;
import jakarta.ejb.EJB;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.component.UIComponent;
import jakarta.faces.component.html.HtmlInputText;
import jakarta.faces.context.FacesContext;
import jakarta.faces.validator.ValidatorException;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;

import org.apache.commons.lang3.StringUtils;
import org.primefaces.PrimeFaces;

import org.primefaces.event.FileUploadEvent;
import org.primefaces.model.file.UploadedFile;

/**
 *
 * @author ellenk
 */
@ViewScoped
@Named
public class ThemeWidgetFragment implements java.io.Serializable {
    static final String DEFAULT_LOGO_BACKGROUND_COLOR = "FFFFFF";
    static final String DEFAULT_BACKGROUND_COLOR = "FFFFFF";
    static final String DEFAULT_LINK_COLOR = "428BCA";
    static final String DEFAULT_TEXT_COLOR = "888888";
    private static final Logger logger = Logger.getLogger(ThemeWidgetFragment.class.getCanonicalName());   

    public static final String LOGOS_SUBDIR = "logos";
    public static final String LOGOS_TEMP_SUBDIR = LOGOS_SUBDIR + File.separator + "temp";

    private File tempDir;
    private File uploadedFile;
    private File uploadedFileThumbnail;
    private File uploadedFileFooter;
    private Dataverse editDv= new Dataverse();
    private HtmlInputText linkUrlInput;
    private HtmlInputText taglineInput;
 

    @EJB
    EjbDataverseEngine commandEngine;
    @EJB
    DataverseServiceBean dataverseServiceBean;
    @Inject
    DataverseRequestServiceBean dvRequestService;
    

    @Inject PermissionsWrapper permissionsWrapper;
    
    
    public HtmlInputText getLinkUrlInput() {
        return linkUrlInput;
    }

    public void setLinkUrlInput(HtmlInputText linkUrlInput) {
        this.linkUrlInput = linkUrlInput;
    }

    public HtmlInputText getTaglineInput() {
        return taglineInput;
    }

    public void setTaglineInput(HtmlInputText taglineInput) {
        this.taglineInput = taglineInput;
    }

 
    public static Path getLogoDir(String ownerId) {
        return Path.of(JvmSettings.DOCROOT_DIRECTORY.lookup(), LOGOS_SUBDIR, ownerId);
    }
    
    private void createTempDir() {
          try {
            // Create the temporary space if not yet existing (will silently ignore preexisting)
            // Note that the docroot directory is checked within ConfigCheckService for presence and write access.
            Path tempRoot = Path.of(JvmSettings.DOCROOT_DIRECTORY.lookup(), LOGOS_TEMP_SUBDIR);
            Files.createDirectories(tempRoot);
            
            this.tempDir = Files.createTempDirectory(tempRoot, editDv.getId().toString()).toFile();
        } catch (IOException e) {
            throw new RuntimeException("Error creating temp directory", e); // improve error handling
        }
    }
    
    @PreDestroy
    /**
     *  Cleanup by deleting temp directory and uploaded files  
     */
    public void cleanupTempDirectory() {
        try {
           
            if (tempDir != null) {
                for (File f : tempDir.listFiles()) {
                    Files.deleteIfExists(f.toPath());
                }
                Files.deleteIfExists(tempDir.toPath());
            }
        } catch (IOException e) {
            throw new RuntimeException("Error deleting temp directory", e); // improve error handling
        }
        uploadedFile=null;
        uploadedFileThumbnail=null;
        uploadedFileFooter=null;
        tempDir=null;
    }
    
    public void checkboxListener() {
        // not sure if this is needed for the ajax component
    }
   

    public String initEditDv() {
        editDv = dataverseServiceBean.find(editDv.getId());
        
        // check if dv exists and user has permission
        if (editDv == null) {
            return permissionsWrapper.notFound();
        }
        if (!permissionsWrapper.canIssueCommand(editDv, UpdateDataverseThemeCommand.class)) {
            return permissionsWrapper.notAuthorized();
        }        
        
        
        if (editDv.getOwner()==null) {
            editDv.setThemeRoot(true);
        }
        if (editDv.getDataverseTheme()==null && editDv.isThemeRoot()) {
            editDv.setDataverseTheme(initDataverseTheme());
            
        }
        return null;
     }
    
    private DataverseTheme initDataverseTheme() {
        DataverseTheme dvt = new DataverseTheme();
        dvt.setLinkColor(DEFAULT_LINK_COLOR);
        dvt.setLogoBackgroundColor(DEFAULT_LOGO_BACKGROUND_COLOR);
        dvt.setLogoFooterBackgroundColor(DEFAULT_LOGO_BACKGROUND_COLOR);
        dvt.setBackgroundColor(DEFAULT_BACKGROUND_COLOR);
        dvt.setTextColor(DEFAULT_TEXT_COLOR);
        dvt.setDataverse(editDv);
        return dvt;
    }
    
    public Dataverse getEditDv() {
        return editDv; 
    }

    public void setEditDv(Dataverse editDV) {
         this.editDv = editDV;
    }

    public void validateTagline(FacesContext context, UIComponent component, Object value) throws ValidatorException {

        if (!StringUtils.isEmpty((String) value) && ((String) value).length() > 140) {
            FacesMessage msg = new FacesMessage(BundleUtil.getStringFromBundle("theme.validateTagline"));
            msg.setSeverity(FacesMessage.SEVERITY_ERROR);

            throw new ValidatorException(msg);
        }

    }
    
    public void validateUrl(FacesContext context, UIComponent component, Object value) throws ValidatorException {
        try {
            if (!StringUtils.isEmpty((String) value)) {
                URL test = new URL((String) value);
            }
        } catch (MalformedURLException e) {
            FacesMessage msg
                    = new FacesMessage(BundleUtil.getStringFromBundle("theme.urlValidate"),
                    BundleUtil.getStringFromBundle("theme.urlValidate.msg"));
            msg.setSeverity(FacesMessage.SEVERITY_ERROR);

            throw new ValidatorException(msg);
        }
    }
   
    
    public String getTempDirName() {
        if (tempDir!=null) {
            return tempDir.getName();
        } else {
            return null;
        }
    }
    
    public boolean uploadExists() {
        return uploadedFile!=null;
    }

    public boolean uploadExistsThumbnail() {
        return uploadedFileThumbnail != null;
    }

    public boolean uploadExistsFooter() {
        return uploadedFileFooter!=null;
    }

    public void handleImageThumbnailFileUpload(FileUploadEvent event) {
        logger.finer("entering handleImageFooterFileUpload");
        if (this.tempDir == null) {
            createTempDir();
            logger.finer("created tempDir");
        }
        final UploadedFile uFile = event.getFile();
        try {
            this.uploadedFileThumbnail = new File(tempDir, uFile.getFileName());
            if (!this.uploadedFileThumbnail.exists()) {
                this.uploadedFileThumbnail.createNewFile();
            }
            logger.finer("created file");
            Files.copy(uFile.getInputStream(), this.uploadedFileThumbnail.toPath(), StandardCopyOption.REPLACE_EXISTING);
            logger.finer("copied inputstream to file");
            this.editDv.getDataverseTheme().setLogoThumbnail(uFile.getFileName());
        } catch (IOException e) {
            logger.finer("caught IOException");
            logger.throwing("ThemeWidgetFragment", "handleImageFileUpload", e);
            throw new RuntimeException("Error uploading logo file", e); // improve error handling
        }
        logger.finer("end handleImageFooterFileUpload");
    }

    /**
     * This method is for footer image.
     * Copy uploaded file to temp area, until we are ready to save
     * Copy filename into Dataverse logo 
     * @param event 
     */
    public void handleImageFooterFileUpload(FileUploadEvent event) {
        logger.finer("entering handleImageFooterFileUpload");
        if (this.tempDir==null) {
            createTempDir();
            logger.finer("created tempDir");
        }
        UploadedFile uFile = event.getFile();
        try {
            uploadedFileFooter = new File(tempDir, uFile.getFileName());
            if (!uploadedFileFooter.exists()) {
                uploadedFileFooter.createNewFile();
            }
            logger.finer("created file");
            Files.copy(uFile.getInputStream(), uploadedFileFooter.toPath(), StandardCopyOption.REPLACE_EXISTING);
            logger.finer("copied inputstream to file");
            editDv.getDataverseTheme().setLogoFooter(uFile.getFileName());

        } catch (IOException e) {
            logger.finer("caught IOException");
            logger.throwing("ThemeWidgetFragment", "handleImageFileUpload", e);
            throw new RuntimeException("Error uploading logo file", e); // improve error handling
        }
        logger.finer("end handleImageFooterFileUpload");
    }

    public void handleImageFileUpload(FileUploadEvent event) {
        logger.finer("entering handleImageFileUpload");
        if (this.tempDir==null) {
            createTempDir();
            logger.finer("created tempDir");
        }
        UploadedFile uFile = event.getFile();
        try {         
            uploadedFile = new File(tempDir, uFile.getFileName());     
            if (!uploadedFile.exists()) {
                uploadedFile.createNewFile();
            }
            logger.finer("created file");
            Files.copy(uFile.getInputStream(), uploadedFile.toPath(),StandardCopyOption.REPLACE_EXISTING);
            logger.finer("copied inputstream to file");
            editDv.getDataverseTheme().setLogo(uFile.getFileName());

        } catch (IOException e) {
            logger.finer("caught IOException");
            logger.throwing("ThemeWidgetFragment", "handleImageFileUpload", e);
            throw new RuntimeException("Error uploading logo file", e); // improve error handling
        }
        // If needed, set the default values for the logo
        if (editDv.getDataverseTheme().getLogoFormat()==null) {
            editDv.getDataverseTheme().setLogoFormat(DataverseTheme.ImageFormat.SQUARE);
        }
        logger.finer("end handleImageFileUpload");
    }

    public void removeLogo() {
        editDv.getDataverseTheme().setLogo(null);
        this.cleanupTempDirectory();
    }

    public void removeLogoFooter() {
        editDv.getDataverseTheme().setLogoFooter(null);
        this.cleanupTempDirectory();
    }

    public void removeLogoThumbnail() {
        editDv.getDataverseTheme().setLogoThumbnail(null);
        this.cleanupTempDirectory();
    }

    public boolean getInheritCustomization() {
        boolean inherit= editDv==null ? true : !editDv.getThemeRoot();
         return inherit;
    }
    
    public void setInheritCustomization(boolean inherit) {
        editDv.setThemeRoot(!inherit);
        if (!inherit) {
            if (editDv.getDataverseTheme(true)==null) {
                editDv.setDataverseTheme(initDataverseTheme());
            }
        }
    }
    public void resetForm() {
        //RequestContext context = RequestContext.getCurrentInstance();
        //context.reset(":dataverseForm:themeWidgetsTabView");
        PrimeFaces.current().resetInputs(":dataverseForm:themeWidgetsTabView");
    }
    
    public String cancel() {
         return "dataverse.xhtml?faces-redirect=true&alias="+editDv.getAlias();  // go to dataverse page 
    }
    
    
    public String save() {
        // If this Dv isn't the root, delete the uploaded file and remove theme
        // before saving.
        if (!editDv.isThemeRoot()) {
            uploadedFile=null;
            editDv.setDataverseTheme(null);
        }

        // Update files : logo, footer, thumbnail
        final Dataverse currentDv = dataverseServiceBean.find(editDv.getId());
        final Path logoDir = getLogoDir(editDv.getId().toString());
        String currentLogo = null;
        String editedLogo = null;
        String currentLogoFooter = null;
        String editedLogoFooter = null;
        String currentLogoThumbnail = null;
        String editedLogoThumbnail = null;
        if (currentDv.getDataverseTheme() != null) {
            currentLogo = currentDv.getDataverseTheme().getLogo();
            currentLogoFooter = currentDv.getDataverseTheme().getLogoFooter();
            currentLogoThumbnail = currentDv.getDataverseTheme().getLogoThumbnail();
        }
        if (editDv.getDataverseTheme() != null) {
            editedLogo = editDv.getDataverseTheme().getLogo();
            editedLogoFooter = editDv.getDataverseTheme().getLogoFooter();
            editedLogoThumbnail = editDv.getDataverseTheme().getLogoThumbnail();
        }
        updateFile(this.uploadedFile, currentLogo, editedLogo, logoDir);
        updateFile(this.uploadedFileFooter, currentLogoFooter, editedLogoFooter, logoDir);
        updateFile(this.uploadedFileThumbnail, currentLogoThumbnail, editedLogoThumbnail, logoDir);

        // Save dataverse theme into db
        final Command<Dataverse> cmd = new UpdateDataverseThemeCommand(editDv, dvRequestService.getDataverseRequest());
        if (!exectThemeCommand(cmd)) {
            return null;
        }

        JsfHelper.addSuccessMessage(BundleUtil.getStringFromBundle("dataverse.theme.success"));    
        return "dataverse.xhtml?faces-redirect=true&alias="+editDv.getAlias();  // go to dataverse page 
    }

    /**
     * Create, update, or delete file logo.
     *
     * @param uploadedLogoFile the logo physical file to update on disk
     * @param currentLogo logo file name from database before update. {@code null} if absent.
     * @param editedLogo logo file name updated. {@code null} if absent.
     * @param logoDir folder path containing all collection logos
     */
    private void updateFile(File uploadedLogoFile, String currentLogo, String editedLogo, Path logoDir) {
        try {
            if (!Files.isDirectory(logoDir)) {
                Files.createDirectory(logoDir);
            }
            if (StringUtils.isBlank(editedLogo)) {
                // If edited logo field is empty, and a logoFile currently exists, delete it
                if (StringUtils.isNotBlank(currentLogo)) {
                    Files.deleteIfExists(Path.of(logoDir.toString(), currentLogo));
                }
            } else if (uploadedLogoFile != null) {
                // If edited logo file isn't empty, and uploaded File exists, delete currentFile and copy uploaded file from temp dir to logos dir
                if (StringUtils.isNotBlank(currentLogo)) {
                    Files.deleteIfExists(Path.of(logoDir.toString(), currentLogo));
                }
                final Path newFile = Path.of(logoDir.toString(), editedLogo);
                Files.copy(uploadedLogoFile.toPath(), newFile, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new RuntimeException("Error saving logo file", e); // improve error handling
        }
    }


    public  boolean exectThemeCommand(Command<Dataverse> cmd){
        try {
            commandEngine.submit(cmd);
        } catch (Exception ex) {
            logger.log(Level.SEVERE, "error updating dataverse theme", ex);
            FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(FacesMessage.SEVERITY_FATAL, BundleUtil.getStringFromBundle("dataverse.save.failed"), BundleUtil.getStringFromBundle("dataverse.theme.failure")));
            return false;
        } finally {
            this.cleanupTempDirectory();
        }
        return true;
    }
      
 }



